import path from 'node:path'
import { expect, type Locator, type Page, test } from '@playwright/test'
import { ACCOUNTS, submitIdpLogin } from '../src/auth'
import { emptyCart } from '../src/store'
import { CHECK_TIMEOUT, reopenUntil } from '../src/waits'

/** ローカルの店舗画面（開発のサーバー） */
const STORE = 'http://localhost:3000'
/** ローカルの管理画面（開発のサーバー） */
const ADMIN = 'http://localhost:5173'
/** ローカルのバックエンド（mock の物流の画面） */
const BACKEND = 'http://localhost:8080'
/** 撮った画像の置き場所 */
const OUT = path.resolve(import.meta.dirname, '../../showcase/img')

/** local の seed の ID（db/seed/local/） */
const SEED = {
  /** マグカップ（色を選ぶ商品） */
  mug: '00000000-0000-0000-0000-000000002014',
  /** マグカップの白 */
  mugWhite: '00000000-0000-0000-0000-000000003131',
  /** 板チョコレート（軽減税率 8%） */
  chocolate: '00000000-0000-0000-0000-000000002012',
}

/** local の seed のレポート用の注文の期間（db/seed/local/README.md「レポート用の注文」） */
const SEED_ORDERS = {
  /** 最初の日 */
  from: '2024-10-01',
  /** 最後の 1 年の最初の日 */
  lastYear: '2025-10-01',
  /** 最後の日 */
  to: '2026-09-30',
}

/**
 * 画面の全体を撮る。表示が落ち着く（画像が読み込まれる）のを待ってから撮る。
 *
 * @param page 撮るページ
 * @param name ファイルの名前（拡張子なし）
 * @param ready 表示されたら撮ってよい要素
 */
async function shot(page: Page, name: string, ready: Locator) {
  await expect(ready).toBeVisible()
  await page.waitForLoadState('networkidle')
  await expect.poll(() => page.evaluate(() => Array.from(document.images).every((image) => image.complete))).toBe(true)
  // 店舗画面の開発のサーバーが出す Next.js の印（画面の左下）は、本番の画面にないので写さない
  await page.screenshot({
    path: path.join(OUT, `${name}.png`),
    fullPage: true,
    style: 'nextjs-portal { display: none; }',
  })
}

/** 商品の詳細からカートに入れる（色があれば選ぶ） */
async function addToCart(page: Page, productId: string, value?: string) {
  await page.goto(`${STORE}/products/${productId}`)
  if (value) {
    await page.getByRole('button', { name: value, exact: true }).click()
  }
  await page.getByRole('button', { name: 'カートに入れる' }).click()
  await expect(page.getByText('カートに入れました。')).toBeVisible()
}

/** カートから注文し、mock の支払画面に移る */
async function order(page: Page) {
  await page.goto(`${STORE}/cart`)
  await page.getByRole('button', { name: '注文手続きへ進む' }).click()
  await page.getByLabel('氏名').fill('山田 花子')
  // 郵便番号で都道府県・市区町村・町域が補われてから、番地を追加する
  await page.getByLabel('郵便番号').fill('100-0001')
  await expect(page.getByLabel('住所')).toHaveValue('千代田')
  await page.getByLabel('住所').fill('千代田 1-1')
  await page.getByLabel('電話番号').fill('03-1234-5678')
  await page.getByRole('button', { name: /^注文する/ }).click()
  await expect(page.getByRole('heading', { name: 'mock の支払' })).toBeVisible()
}

/** 支払画面で操作して注文の画面に戻り、注文の画面の URL（クエリなし）を返す */
async function finishPayment(page: Page, button: '支払う' | 'やめる'): Promise<string> {
  await page.getByRole('button', { name: button }).click()
  await expect(page).toHaveURL(/\/orders\/[^/?]+\?payment=returned$/)
  return page.url().split('?')[0]
}

test('機能紹介のページのスクリーンショットを撮る', async ({ page }) => {
  let paidOrderId = ''

  await test.step('店舗画面：トップと商品の詳細', async () => {
    await page.goto(`${STORE}/`)
    await shot(page, 'store-top', page.getByRole('link', { name: /マグカップ/ }).first())
    await page.goto(`${STORE}/products/${SEED.mug}`)
    await page.getByRole('button', { name: '白', exact: true }).click()
    await shot(page, 'store-product', page.getByRole('button', { name: 'カートに入れる' }))
  })

  await test.step('店舗画面：会員として、税率の違う 2 品目を注文して支払う', async () => {
    await page.goto(`${STORE}/login?returnTo=%2Fcart`)
    await submitIdpLogin(page, ACCOUNTS.member)
    await expect(page.getByRole('button', { name: 'ログアウト' })).toBeVisible()
    // 会員のカートは残るので、前に撮ったときの品目を消す
    await emptyCart(page, STORE)
    await addToCart(page, SEED.mug, '白')
    await addToCart(page, SEED.chocolate)
    await order(page)
    const orderUrl = await finishPayment(page, '支払う')
    paidOrderId = orderUrl.split('/').at(-1) ?? ''
    // 支払の通知が反映されて「受付」になるまで開き直す
    await reopenUntil(page, orderUrl, () =>
      expect(page.getByText('受付', { exact: true })).toBeVisible({ timeout: CHECK_TIMEOUT }),
    )
    await shot(page, 'store-order', page.getByText('受付', { exact: true }))
  })

  await test.step('店舗画面：支払わずに戻った、支払待ちの注文', async () => {
    await addToCart(page, SEED.mug, '白')
    await order(page)
    const orderUrl = await finishPayment(page, 'やめる')
    await page.goto(orderUrl)
    await shot(page, 'store-payment', page.getByRole('button', { name: '支払う' }))
  })

  await test.step('管理画面：商品、在庫、税率、注文', async () => {
    await page.goto(`${ADMIN}/`)
    await submitIdpLogin(page, ACCOUNTS.administrator)
    await expect(page.getByRole('button', { name: 'ログアウト' })).toBeVisible()

    await page.goto(`${ADMIN}/products`)
    await shot(page, 'admin-products', page.getByRole('link', { name: 'MUG-001' }))
    await page.goto(`${ADMIN}/products/${SEED.mug}`)
    await shot(page, 'admin-product', page.getByRole('cell', { name: 'MUG-001-WH', exact: true }))
    await page.goto(`${ADMIN}/products/${SEED.mug}/variants/${SEED.mugWhite}/stock`)
    await shot(page, 'admin-stock', page.getByRole('cell', { name: '注文', exact: true }).first())
    await page.goto(`${ADMIN}/tax-categories`)
    await shot(page, 'admin-tax', page.getByRole('table'))
    await page.goto(`${ADMIN}/orders`)
    await shot(page, 'admin-orders', page.getByRole('columnheader', { name: '配送' }))
  })

  await test.step('管理画面で出荷を登録し、mock の物流で配送中にして、管理画面と店舗画面の注文を撮る', async () => {
    await page.goto(`${ADMIN}/orders/${paidOrderId}`)
    await page.getByRole('combobox', { name: '配送業者' }).click()
    await page.getByRole('option', { name: 'ヤマト運輸' }).click()
    await page.getByLabel('送り状番号').fill('4321-0987-6543')
    await page.getByRole('button', { name: '出荷を登録する' }).click()
    await page.getByRole('alertdialog').getByRole('button', { name: '出荷する' }).click()
    await expect(page.getByText('出荷を登録しました')).toBeVisible()

    await page.goto(`${BACKEND}/mock-carrier`)
    await page.getByRole('row').filter({ hasText: paidOrderId }).getByRole('button', { name: '配送中' }).click()
    await expect(page.getByRole('row').filter({ hasText: paidOrderId }).getByRole('cell').nth(3)).toHaveText('配送中')

    await page.goto(`${ADMIN}/orders/${paidOrderId}`)
    await shot(page, 'admin-order', page.getByText('売上確定済み'))
    await page.goto(`${STORE}/orders/${paidOrderId}`)
    await shot(page, 'store-shipping', page.getByText('配送中', { exact: true }))
  })

  await test.step('管理画面：レポート（seed の注文の期間を指定する）', async () => {
    await page.goto(`${ADMIN}/reports/sales-trend?from=${SEED_ORDERS.from}&to=${SEED_ORDERS.to}`)
    await shot(page, 'admin-report-trend', page.getByRole('cell', { name: '2026 年 9 月' }))
    await page.goto(
      `${ADMIN}/reports/variant-sales?from=${SEED_ORDERS.lastYear}&to=${SEED_ORDERS.to}&productId=${SEED.mug}`,
    )
    await shot(page, 'admin-report-variants', page.getByRole('cell', { name: 'MUG-001-WH', exact: true }))
    await page.goto(`${ADMIN}/reports/customer-breakdown?from=${SEED_ORDERS.lastYear}&to=${SEED_ORDERS.to}`)
    await shot(page, 'admin-report-customers', page.getByRole('region', { name: '性別ごとの売上額' }))
  })
})
