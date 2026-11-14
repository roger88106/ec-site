import { expect, test } from '@playwright/test'
import { newMemberAccount, registerMember } from '../src/auth'
import { createSimpleProduct } from '../src/data'
import { detailValue } from '../src/detail'
import {
  addToCart,
  checkoutButton,
  checkoutHeading,
  fillShipping,
  leaveMockPage,
  mockPaymentHeading,
  orderCart,
  PENDING_ORDERS_TEXT,
  pendingOrdersHeading,
  submitOrder,
} from '../src/store'
import { STORE_URL } from '../src/urls'

test('AC-406-4、AC-406-5、AC-406-7、AC-406-8、AC-406-9 支払待ちの注文があると注文手続きの前に確認の画面が出て、戻る・支払へ進む・新しく注文するを選べる', async ({
  page,
}) => {
  const productId = await createSimpleProduct({
    code: 'PENDCONF',
    name: '支払待ちの確認の商品',
    stock: 10,
  })
  // 共用の会員には同じ実行の前のテストの支払待ちが残るので、このテストで登録した会員で始める
  const account = newMemberAccount('pendconf')
  await page.goto(STORE_URL)
  await registerMember(page, account, { lastName: '確認', firstName: '太郎' })

  const orderNumber = await test.step('注文して支払わずに戻る', async () => {
    await addToCart(page, productId)
    await orderCart(page)
    await leaveMockPage(page, 'やめる')
    const orderNumberValue = detailValue(page, '注文番号')
    await expect(orderNumberValue).toHaveText(/\d{8}-\d{6}/)
    return (await orderNumberValue.textContent()) ?? ''
  })

  const proceed = checkoutButton(page)

  await test.step('カートから注文手続きに進むと、確認の画面に支払待ちの注文の注文番号が出る（AC-406-4）', async () => {
    await addToCart(page, productId)
    await page.goto(`${STORE_URL}/cart`)
    await proceed.click()
    await expect(pendingOrdersHeading(page)).toBeVisible()
    await expect(page.getByText(orderNumber)).toBeVisible()
  })

  await test.step('「戻る」でカートに戻る（AC-406-8）', async () => {
    await page.getByRole('link', { name: PENDING_ORDERS_TEXT.back, exact: true }).click()
    await expect(page).toHaveURL(`${STORE_URL}/cart`)
    await expect(proceed).toBeVisible()
  })

  await test.step('再び進み「支払へ進む」を選ぶと、mock の支払画面に移る（AC-406-5）', async () => {
    await proceed.click()
    await page.getByRole('button', { name: PENDING_ORDERS_TEXT.pay, exact: true }).click()
    await expect(mockPaymentHeading(page)).toBeVisible()
  })

  await test.step('戻って再び進み「新しく注文する」を選ぶと、2 件目を注文できる（AC-406-7、AC-406-9）', async () => {
    await page.goto(`${STORE_URL}/cart`)
    await proceed.click()
    await expect(pendingOrdersHeading(page)).toBeVisible()
    await page.getByRole('button', { name: PENDING_ORDERS_TEXT.newOrder, exact: true }).click()
    await expect(checkoutHeading(page)).toBeVisible()
    await fillShipping(page, '100-0001')
    await submitOrder(page)
  })
})
