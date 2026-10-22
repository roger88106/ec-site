// スロット（作業ツリーごとの手元の環境）の値、空きの選び方、環境変数の組み立て。
// docker と git を呼ばない純粋な関数だけを置く。呼ぶのは scripts/dev/slot.mjs。

/** スロットの数。0 がメインの作業ツリー、1〜3 が作業ツリー */
export const SLOT_COUNT = 4

/** 共有の基盤（compose）のホストのポート。スロットのポートと重ならない */
export const SHARED_PORTS = [5432, 9411, 8333, 8081, 8180, 8181, 2525, 8025]

/** E2E のポート、データベース、バケット。スロットの値と重ならない */
export const E2E = {
  ports: [8090, 9090, 4173, 3100],
  database: 'ec_site_e2e',
  buckets: ['ec-site-e2e-images', 'ec-site-e2e-images-incoming'],
}

/** 共有の SeaweedFS（S3 互換）の URL。配信の基準 URL の前半 */
const STORAGE_ORIGIN = 'http://localhost:8333'

/** 共有の PostgreSQL への JDBC URL の前半 */
const JDBC_PREFIX = 'jdbc:postgresql://localhost:5432/'

/**
 * スロットの番号が 0〜3 の整数であることを確認する。
 *
 * @param {number} slot スロットの番号
 */
function assertSlot(slot) {
  if (!Number.isInteger(slot) || slot < 0 || slot >= SLOT_COUNT) {
    throw new RangeError(`スロットの番号は 0〜${SLOT_COUNT - 1} の整数: ${slot}`)
  }
}

/**
 * スロットのポートと名前を返す。<br />
 * ポートは「スロット 0 の値 + 10000 × 番号」、データベースとバケットは名前の末尾に番号を付ける（0 は付けない）。
 *
 * @param {number} slot スロットの番号（0〜3）
 * @returns {{ slot: number, backendPort: number, managementPort: number, storePort: number, adminPort: number,
 *   database: string, publicBucket: string, incomingBucket: string, cookiePrefix: string }}
 *   運用ポートは managementPort。Cookie の接頭辞はスロット 0 では空
 */
export function slotValues(slot) {
  assertSlot(slot)
  const offset = 10000 * slot
  const suffix = slot === 0 ? '' : `-${slot}`
  return {
    slot,
    backendPort: 8080 + offset,
    managementPort: 9080 + offset,
    storePort: 3000 + offset,
    adminPort: 5173 + offset,
    database: slot === 0 ? 'ec_site' : `ec_site_${slot}`,
    publicBucket: `ec-site-images${suffix}`,
    incomingBucket: `ec-site-images-incoming${suffix}`,
    cookiePrefix: slot === 0 ? '' : `s${slot}-`,
  }
}

/**
 * スロットが使うポートを返す。
 *
 * @param {number} slot スロットの番号
 * @returns {number[]} バックエンド、運用ポート、店舗画面、管理画面の順
 */
export function slotPorts(slot) {
  const values = slotValues(slot)
  return [values.backendPort, values.managementPort, values.storePort, values.adminPort]
}

/**
 * `git worktree list --porcelain` の出力を読む。
 *
 * @param {string} text git の出力
 * @returns {{ path: string, main: boolean, prunable: boolean }[]}
 *   作業ツリーの一覧。最初の項目がメインの作業ツリー。prunable は作業ツリーのディレクトリが消えたもの
 */
export function parseWorktrees(text) {
  const worktrees = []
  for (const block of text.replace(/\r/g, '').split(/\n{2,}/)) {
    const lines = block.split('\n')
    const head = lines.find((line) => line.startsWith('worktree '))
    if (!head) continue
    worktrees.push({
      path: head.slice('worktree '.length),
      main: worktrees.length === 0,
      prunable: lines.some((line) => line === 'prunable' || line.startsWith('prunable ')),
    })
  }
  return worktrees
}

/**
 * 作業ツリーの記録（`.local/slot`）の内容からスロットの番号を読む。
 *
 * @param {string | null | undefined} content ファイルの内容。ファイルがなければ null か undefined
 * @returns {number | null} 1〜3 の番号。記録がない、壊れている、0 のときは null
 */
export function parseSlotRecord(content) {
  const text = (content ?? '').trim()
  if (!/^\d$/.test(text)) return null
  const slot = Number(text)
  return slot >= 1 && slot < SLOT_COUNT ? slot : null
}

/**
 * 使われていない最小のスロットを選ぶ。<br />
 * 使用中は、メインの作業ツリーと prunable のものを除く作業ツリーの記録で決める。一覧にない作業ツリーは数えない。
 *
 * @param {string} porcelain `git worktree list --porcelain` の出力
 * @param {Record<string, string | null | undefined>} records 作業ツリーのパス → `.local/slot` の内容
 * @returns {{ slot: number } | { full: true, users: { slot: number, path: string }[] }}
 *   選んだ番号。全て使われていれば、使っている作業ツリー（番号の順）
 */
export function pickSlot(porcelain, records) {
  const users = []
  for (const worktree of parseWorktrees(porcelain)) {
    if (worktree.main || worktree.prunable) continue
    const slot = parseSlotRecord(records[worktree.path])
    if (slot !== null) users.push({ slot, path: worktree.path })
  }
  users.sort((a, b) => a.slot - b.slot)
  for (let slot = 1; slot < SLOT_COUNT; slot++) {
    if (!users.some((user) => user.slot === slot)) return { slot }
  }
  return { full: true, users }
}

/**
 * 対象に付ける環境変数を組み立てる。スロット 0 は何も上書きしない。
 *
 * @param {number} slot スロットの番号
 * @param {'backend' | 'store' | 'admin'} target 起動する対象
 * @returns {Record<string, string>} 環境変数の名前 → 値
 */
export function slotEnv(slot, target) {
  const values = slotValues(slot)
  if (!['backend', 'store', 'admin'].includes(target)) throw new Error(`知らない対象: ${target}`)
  if (slot === 0) return {}
  const backendOrigin = `http://localhost:${values.backendPort}`
  if (target === 'backend') {
    return {
      SERVER_PORT: String(values.backendPort),
      MANAGEMENT_SERVER_PORT: String(values.managementPort),
      SPRING_DATASOURCE_URL: JDBC_PREFIX + values.database,
      ECSITE_MEDIA_STORAGE_INCOMING_BUCKET: values.incomingBucket,
      ECSITE_MEDIA_STORAGE_PUBLIC_BUCKET: values.publicBucket,
      ECSITE_MEDIA_STORAGE_PUBLIC_BASE_URL: `${STORAGE_ORIGIN}/${values.publicBucket}`,
      ECSITE_PAYMENT_RETURN_URL: `http://localhost:${values.storePort}/orders/{orderId}?payment=returned`,
      ECSITE_PAYMENT_MOCK_BASE_URL: backendOrigin,
    }
  }
  if (target === 'store') {
    return {
      STORE_API_BASE_URL: `${backendOrigin}/api/store`,
      AUTH_URL: `http://localhost:${values.storePort}`,
      STORE_COOKIE_PREFIX: values.cookiePrefix,
    }
  }
  return { ADMIN_API_PROXY_TARGET: backendOrigin }
}
