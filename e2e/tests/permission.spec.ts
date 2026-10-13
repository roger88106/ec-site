import { expect, test } from '@playwright/test'
import { ACCOUNTS, loginAdmin, loginStore } from '../src/auth'
import { createPublishedProduct, taxCategoryWithRate } from '../src/data'
import { emptyCart, fillShipping, payOnMockPage } from '../src/store'
import { STORE_URL } from '../src/urls'

test('AC-136、141 閲覧者は注文を見られるが、キャンセルの申請を承認すると「権限がない」と示される', async ({
  browser,
}) => {
  const { id: taxCategoryId } = await taxCategoryWithRate(10)
  const productId = await createPublishedProduct({
    code: 'VIEWER',
    name: '閲覧者の確認の商品',
    taxCategoryId,
    priceExclTax: 1000,
    variants: [{ code: 'A', stock: 3 }],
  })
  let orderId = ''

  await test.step('店舗画面：会員が注文し、キャンセルを申請する', async () => {
    const store = await browser.newPage()
    await loginStore(store, ACCOUNTS.member2)
    await emptyCart(store)
    await store.goto(`${STORE_URL}/products/${productId}`)
    await store.getByRole('button', { name: 'カートに入れる' }).click()
    await expect(store.getByText('カートに入れました。')).toBeVisible()
    await store.goto(`${STORE_URL}/cart`)
    await store.getByRole('button', { name: '注文手続きへ進む' }).click()
    await fillShipping(store, '100-0001')
    await store.getByRole('button', { name: /^注文する/ }).click()
    await expect(store.getByRole('heading', { name: 'mock の支払' })).toBeVisible()
    orderId = new URL(await payOnMockPage(store)).pathname.split('/').at(-1) ?? ''
    store.once('dialog', (dialog) => dialog.accept())
    await store.getByRole('button', { name: 'キャンセルを申請する' }).click()
    await expect(store.getByText('キャンセル申請中', { exact: true })).toBeVisible()
    await store.context().close()
  })

  await test.step('管理画面：閲覧者は注文の詳細を見られるが、承認すると「権限がない」と示され、状態は変わらない', async () => {
    const admin = await browser.newPage()
    await loginAdmin(admin, ACCOUNTS.viewer, `/orders/${orderId}`)
    await expect(admin.getByText('閲覧 次郎')).toBeVisible()
    await admin.getByRole('button', { name: '申請を承認する' }).click()
    await admin.getByRole('alertdialog').getByRole('button', { name: '承認してキャンセルする' }).click()
    await expect(admin.getByText('この操作を行う権限がありません。')).toBeVisible()
    await admin.reload()
    await expect(admin.getByText('キャンセル申請中', { exact: true })).toBeVisible()
    await admin.context().close()
  })
})
