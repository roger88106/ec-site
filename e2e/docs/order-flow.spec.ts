import path from 'node:path'
import { test } from '@playwright/test'
import { ACCOUNTS, loginAdmin, loginStore } from '../src/auth'
import { makeShot, RECIPIENT, SEED } from '../src/screenshot'
import { notifyCarrier, shipOnAdmin } from '../src/shipping'
import {
  SHOWCASE_ADMIN_URL as ADMIN,
  SHOWCASE_BACKEND_URL as BACKEND,
  SHOWCASE_STORE_URL as STORE,
} from '../src/showcase-urls'
import {
  addToCart,
  emptyCart,
  fillShipping,
  orderIdOf,
  payOnMockPage,
  placeOrderButton,
  proceedToCheckout,
} from '../src/store'

/** 撮った画像の置き場所（docs/01-概要/order.md が載せる） */
const OUT = path.resolve(import.meta.dirname, '../../docs/01-概要/img/order')

/** 画面を撮って OUT に置く */
const shot = makeShot(OUT)

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

    await proceedToCheckout(page)
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
