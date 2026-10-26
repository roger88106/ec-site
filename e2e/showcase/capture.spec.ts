import path from 'node:path'
import { expect, type Locator, type Page, test } from '@playwright/test'
import { ACCOUNTS, loginAdmin, loginStore } from '../src/auth'
import { notifyCarrier, shipOnAdmin } from '../src/shipping'
import {
  SHOWCASE_ADMIN_URL as ADMIN,
  SHOWCASE_BACKEND_URL as BACKEND,
  SHOWCASE_STORE_URL as STORE,
} from '../src/showcase-urls'
import { addToCart, emptyCart, leaveMockPage, orderCart, orderIdOf, payOnMockPage } from '../src/store'

/** 撮った画像の置き場所 */
const OUT = path.resolve(import.meta.dirname, '../../showcase/img')

/** 注文のお届け先の氏名（撮った画像に写る） */
const RECIPIENT = '山田 花子'

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
 * 画面の全体を撮る。表示が出て、画像が読み込まれるのを待ってから撮る。
 *
 * @param page 撮るページ
 * @param name ファイルの名前（拡張子なし）
 * @param ready 表示されたら撮ってよい要素
 */
async function shot(page: Page, name: string, ready: Locator) {
  await expect(ready).toBeVisible()
  await expect.poll(() => page.evaluate(() => Array.from(document.images).every((image) => image.complete))).toBe(true)
  // 店舗画面の開発のサーバーが出す Next.js の印（画面の左下）は、本番の画面にないので写さない
  await page.screenshot({
    path: path.join(OUT, `${name}.png`),
    fullPage: true,
    style: 'nextjs-portal { display: none; }',
  })
}

test('機能紹介のページのスクリーンショットを撮る', async ({ page }) => {
  let paidOrderId = ''

  await test.step('店舗画面：トップと商品の詳細', async () => {
    await page.goto(`${STORE}/`)
    await shot(page, 'store-top', page.getByRole('link', { name: /マグカップ/ }))
    await page.goto(`${STORE}/products/${SEED.mug}`)
    await page.getByRole('button', { name: '白', exact: true }).click()
    await shot(page, 'store-product', page.getByRole('button', { name: 'カートに入れる' }))
  })

  await test.step('店舗画面：会員として、税率の違う 2 品目を注文して支払う', async () => {
    await loginStore(page, ACCOUNTS.member, '/cart', STORE)
    // 会員のカートは残るので、前に撮ったときの品目を消す
    await emptyCart(page, STORE)
    await addToCart(page, SEED.mug, { option: '白' }, STORE)
    await addToCart(page, SEED.chocolate, {}, STORE)
    await orderCart(page, STORE, RECIPIENT)
    // 支払の通知が反映されて「受付」になるまで待つ
    const orderUrl = await payOnMockPage(page)
    paidOrderId = orderIdOf(orderUrl)
    await shot(page, 'store-order', page.getByText('受付', { exact: true }))
  })

  await test.step('店舗画面：支払わずに戻った、支払待ちの注文', async () => {
    await addToCart(page, SEED.mug, { option: '白' }, STORE)
    await orderCart(page, STORE, RECIPIENT)
    const orderUrl = await leaveMockPage(page, 'やめる')
    await page.goto(orderUrl)
    await shot(page, 'store-payment', page.getByRole('button', { name: '支払う' }))
  })

  await test.step('管理画面：商品、在庫、税率、注文', async () => {
    await loginAdmin(page, ACCOUNTS.administrator, '/', ADMIN)

    await page.goto(`${ADMIN}/products`)
    await shot(page, 'admin-products', page.getByRole('link', { name: 'MUG-001' }))
    await page.goto(`${ADMIN}/products/${SEED.mug}`)
    await shot(page, 'admin-product', page.getByRole('cell', { name: 'MUG-001-WH', exact: true }))
    await page.goto(`${ADMIN}/products/${SEED.mug}/variants/${SEED.mugWhite}/stock`)
    // 在庫の履歴に注文の行が出てから撮る
    await expect(page.getByRole('cell', { name: '注文', exact: true })).not.toHaveCount(0)
    await shot(page, 'admin-stock', page.getByRole('status', { name: '在庫数' }))
    await page.goto(`${ADMIN}/tax-categories`)
    await shot(page, 'admin-tax', page.getByRole('table'))
    await page.goto(`${ADMIN}/orders`)
    await shot(page, 'admin-orders', page.getByRole('columnheader', { name: '配送' }))
  })

  await test.step('管理画面で出荷を登録し、mock の物流で配送中にして、管理画面と店舗画面の注文を撮る', async () => {
    await page.goto(`${ADMIN}/orders/${paidOrderId}`)
    await shipOnAdmin(page, '4321-0987-6543')
    await notifyCarrier(page, paidOrderId, '配送中', BACKEND)

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
