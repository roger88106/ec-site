import path from 'node:path'
import { expect, type Locator, type Page, test } from '@playwright/test'
import { ACCOUNTS, loginAdmin, loginStore } from '../src/auth'
import { notifyCarrier, shipOnAdmin } from '../src/shipping'
import {
  SHOWCASE_ADMIN_URL as ADMIN,
  SHOWCASE_BACKEND_URL as BACKEND,
  SHOWCASE_STORE_URL as STORE,
} from '../src/showcase-urls'
import { addToCart, emptyCart, fillShipping, orderIdOf, payOnMockPage, placeOrderButton } from '../src/store'

/** 撮った画像の置き場所（docs/01-概要/order.md が載せる） */
const OUT = path.resolve(import.meta.dirname, '../../docs/01-概要/img/order')

/** 注文のお届け先の氏名（撮った画像に写る） */
const RECIPIENT = '山田 花子'

/** local の seed の ID（db/seed/local/） */
const SEED = {
  /** マグカップ（色を選ぶ商品） */
  mug: '00000000-0000-0000-0000-000000002014',
  /** 板チョコレート（軽減税率 8%） */
  chocolate: '00000000-0000-0000-0000-000000002012',
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
  // 店舗画面の開発のサーバーが表示する Next.js の印（画面の左下）は、本番の画面にないので写さない
  await page.screenshot({
    path: path.join(OUT, `${name}.png`),
    fullPage: true,
    style: 'nextjs-portal { display: none; }',
  })
}

test('注文の流れのスクリーンショットを撮る', async ({ page }) => {
  let orderId = ''

  await test.step('店舗画面：カートから注文して支払う', async () => {
    await loginStore(page, ACCOUNTS.member, { returnTo: '/cart', storeUrl: STORE })
    // 会員のカートは残るので、前に撮ったときの品目を削除する
    await emptyCart(page, { storeUrl: STORE })
    await addToCart(page, SEED.mug, { option: '白', storeUrl: STORE })
    await addToCart(page, SEED.chocolate, { storeUrl: STORE })
    await page.goto(`${STORE}/cart`)
    await shot(page, '01-cart', page.getByRole('button', { name: '注文手続きへ進む', exact: true }))

    await page.getByRole('button', { name: '注文手続きへ進む', exact: true }).click()
    await fillShipping(page, '100-0001', RECIPIENT)
    await shot(page, '02-checkout', placeOrderButton(page))

    await placeOrderButton(page).click()
    await shot(page, '03-payment', page.getByRole('heading', { name: 'mock の支払' }))

    const orderUrl = await payOnMockPage(page)
    orderId = orderIdOf(orderUrl)
    await shot(page, '04-accepted', page.getByText('受付', { exact: true }))
  })

  await test.step('管理画面：注文を見て、出荷を登録する', async () => {
    await loginAdmin(page, ACCOUNTS.administrator, { adminUrl: ADMIN })
    await page.goto(`${ADMIN}/orders/${orderId}`)
    await shot(page, '05-admin-order', page.getByRole('button', { name: '出荷を登録する', exact: true }))
    await shipOnAdmin(page, '4321-0987-6543')
    await notifyCarrier(page, orderId, '配送中', { backendUrl: BACKEND })
  })

  await test.step('店舗画面：配送の状況', async () => {
    await page.goto(`${STORE}/orders/${orderId}`)
    await shot(page, '06-shipping', page.getByText('配送中', { exact: true }))
  })
})
