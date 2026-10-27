import { expect, test } from '@playwright/test'
import { ACCOUNTS, loginAdmin } from '../src/auth'
import { createPublishedProduct, taxCategoryWithRate } from '../src/data'
import { orderAsMember, orderIdOf, payOnMockPage } from '../src/store'

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
    await orderAsMember(store, ACCOUNTS.member2, [productId])
    orderId = orderIdOf(await payOnMockPage(store))
    store.once('dialog', (dialog) => dialog.accept())
    await store.getByRole('button', { name: 'キャンセルを申請する', exact: true }).click()
    await expect(store.getByText('キャンセル申請中', { exact: true })).toBeVisible()
    await store.context().close()
  })

  await test.step('管理画面：閲覧者は注文の詳細を見られるが、承認すると「権限がない」と示され、状態は変わらない', async () => {
    const admin = await browser.newPage()
    await loginAdmin(admin, ACCOUNTS.viewer, { path: `/orders/${orderId}` })
    await expect(admin.getByText('閲覧 次郎')).toBeVisible()
    await admin.getByRole('button', { name: '申請を承認する', exact: true }).click()
    await admin.getByRole('alertdialog').getByRole('button', { name: '承認してキャンセルする', exact: true }).click()
    await expect(admin.getByText('この操作を行う権限がありません。')).toBeVisible()
    await admin.reload()
    await expect(admin.getByText('キャンセル申請中', { exact: true })).toBeVisible()
    await admin.context().close()
  })
})
