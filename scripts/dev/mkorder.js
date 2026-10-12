// ============================================================================
// 動作確認用の注文を、店舗 API で作る
//
//   使い方：node scripts/dev/mkorder.js [--variant <バリアント ID>] [--qty <数量>] [--name <氏名>] [--request [理由]]
//                                       [--member <メールアドレス> <パスワード>]
//     例：node scripts/dev/mkorder.js                       # seed のバリアント（在庫 100）を 1 つ注文する
//         node scripts/dev/mkorder.js --qty 2 --request     # 2 つ注文し、キャンセルを申請する
//
// 会員としてログインし（既定は README の会員 member@example.com）、会員のカートを空にしてから
// 品目の追加 → 注文手続き → 注文 の順に呼ぶ。--request を付けると、続けてキャンセルを申請する。
// token は scripts/dev/keycloak.sh access-token で取る（ローカルの Keycloak が要る）。
// 結果は「注文 ID、注文番号、状態」と、店舗画面・管理画面の注文の URL を出す。
// 接続先は環境変数 STORE_API_BASE_URL（既定 http://localhost:8080/api/store）。local profile のバックエンドが要る。
// ============================================================================

const { execFileSync } = require('node:child_process')
const path = require('node:path')

const BASE = process.env.STORE_API_BASE_URL ?? 'http://localhost:8080/api/store'

/** seed のバリアント（R__inventory_seed.sql で在庫 100） */
const DEFAULT_VARIANT_ID = '00000000-0000-0000-0000-000000003021'

/** コマンドラインの引数を読む */
function parseArgs(argv) {
  const options = {
    variant: DEFAULT_VARIANT_ID,
    qty: 1,
    name: '山田 太郎',
    request: null,
    email: 'member@example.com',
    password: 'member-pass-1',
  }
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i]
    const next = argv[i + 1]
    if (arg === '--variant') options.variant = argv[++i]
    else if (arg === '--qty') options.qty = Number(argv[++i])
    else if (arg === '--name') options.name = argv[++i]
    else if (arg === '--member') {
      options.email = argv[++i]
      options.password = argv[++i]
    } else if (arg === '--request') {
      // 理由は任意。次の引数がオプションでなければ理由とみなす
      options.request = next !== undefined && !next.startsWith('--') ? argv[++i] : 'サイズを間違えました'
    } else throw new Error(`知らない引数：${arg}`)
  }
  return options
}

/** 会員の access token を取る */
function memberToken(email, password) {
  const script = path.join(__dirname, 'keycloak.sh')
  return execFileSync('bash', [script, 'access-token', 'ec-store', email, password], { encoding: 'utf8' }).trim()
}

/** 会員の token を付けて API を呼ぶ関数を作る。失敗なら手順の名前と応答を付けて投げる */
function client(token) {
  return async (step, method, path, body) => {
    const headers = { Authorization: `Bearer ${token}` }
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    const response = await fetch(`${BASE}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
    const text = await response.text()
    if (!response.ok) throw new Error(`${step}に失敗しました（HTTP ${response.status}）：${text}`)
    return text ? JSON.parse(text) : null
  }
}

async function main() {
  const options = parseArgs(process.argv.slice(2))
  const call = client(memberToken(options.email, options.password))
  const { cartId } = await call('会員のカートの取得', 'GET', '/me/cart')
  // 前に入れた品目が一緒に注文されないよう、会員のカートを空にする
  const cart = await call('カートの取得', 'GET', `/carts/${cartId}`)
  for (const item of cart.items) {
    await call('品目の削除', 'DELETE', `/carts/${cartId}/items/${item.variantId}`)
  }
  await call('品目の追加', 'POST', `/carts/${cartId}/items`, { variantId: options.variant, quantity: options.qty })
  const checkout = await call('注文手続き', 'POST', `/carts/${cartId}/checkouts`)
  const view = await call('注文手続きの取得', 'GET', `/checkouts/${checkout.id}`)
  const order = await call('注文', 'POST', `/checkouts/${checkout.id}/order`, {
    shippingAddress: {
      name: options.name,
      postalCode: '1000001',
      prefecture: '東京都',
      address: '千代田区千代田1-1',
      phone: '0312345678',
    },
    expectedTotal: view.amounts.total,
  })
  if (options.request !== null) {
    await call('キャンセルの申請', 'PUT', `/orders/${order.id}/cancellation-request`, { reason: options.request })
  }
  const result = await call('注文の取得', 'GET', `/orders/${order.id}`)
  console.log(result.id, result.orderNumber, result.status)
  console.log(`店舗画面：http://localhost:3000/orders/${result.id}（${options.email} でログインして開く）`)
  console.log(`管理画面：http://localhost:5173/orders/${result.id}`)
}

main().catch((error) => {
  console.error(error.message)
  process.exit(1)
})
