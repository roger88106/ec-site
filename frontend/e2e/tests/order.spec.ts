import { expect, test } from '@playwright/test'
import { ACCOUNTS, loginAdmin, loginStore } from '../src/auth'
import { createPublishedProduct, taxCategoryWithRate, variantIds } from '../src/data'
import { emptyCart, fillShipping } from '../src/store'
import { ADMIN_URL, STORE_URL } from '../src/urls'

test('AC-72、77、82、85〜87、93、95、101、107 店舗画面でカートに入れて注文し、キャンセルを申請して管理画面で承認すると、在庫が戻る', async ({
  page,
}) => {
  const { id: taxCategoryId } = await taxCategoryWithRate(10)
  const name = '注文の流れの商品'
  const productId = await createPublishedProduct({
    code: 'ORDER',
    name,
    taxCategoryId,
    priceExclTax: 1000,
    variants: [{ code: 'A', stock: 3 }],
  })
  const [variantId] = await variantIds(productId)
  let orderUrl = ''

  await test.step('店舗画面：会員としてログインし、商品の詳細から 2 つカートに入れ、カートで金額の内訳を確かめる', async () => {
    await loginStore(page, ACCOUNTS.member)
    await emptyCart(page)
    await page.goto(`${STORE_URL}/products/${productId}`)
    await page.getByLabel('数量').selectOption('2')
    await page.getByRole('button', { name: 'カートに入れる' }).click()
    await expect(page.getByText('カートに入れました。')).toBeVisible()
    await page.getByRole('link', { name: 'カートを見る' }).click()

    await expect(page.getByRole('heading', { name: 'カート' })).toBeVisible()
    await expect(page.getByRole('link', { name })).toBeVisible()
    await expect(page.getByText('2,200 円', { exact: true }).first()).toBeVisible()
    await expect(page.getByText('3,200 円', { exact: true })).toBeVisible()
    await expect(page.getByText('10% 対象 3,200 円（内消費税 290 円）')).toBeVisible()
  })

  await test.step('注文手続き：郵便番号の形の誤りは欄の下に出て入力が残り、直すと注文できる', async () => {
    await page.getByRole('button', { name: '注文手続きへ進む' }).click()
    await expect(page.getByRole('heading', { name: 'ご注文手続き' })).toBeVisible()
    await expect(page.getByText(/在庫を確保しています。残り/)).toBeVisible()
    await expect(page.getByText(ACCOUNTS.member.email)).toBeVisible()

    await fillShipping(page, '12')
    await page.getByRole('button', { name: /^注文する/ }).click()
    await expect(page.getByText('郵便番号は数字 7 桁で入力してください。')).toBeVisible()
    await expect(page.getByLabel('氏名')).toHaveValue('E2E 太郎')

    await page.getByLabel('郵便番号').fill('100-0001')
    await page.getByRole('button', { name: /^注文する/ }).click()
    await expect(page.getByRole('heading', { name: 'ご注文の内容' })).toBeVisible()
    await expect(page.getByText('受付', { exact: true })).toBeVisible()
    orderUrl = page.url()
  })

  await test.step('注文の画面：キャンセルを申請すると「キャンセル申請中」になる', async () => {
    page.once('dialog', (dialog) => dialog.accept())
    await page.getByLabel('理由（任意）').fill('間違えて注文した')
    await page.getByRole('button', { name: 'キャンセルを申請する' }).click()
    await expect(page.getByText('キャンセル申請中', { exact: true })).toBeVisible()
    await expect(page.getByText(/キャンセルの申請を受け付けました/)).toBeVisible()
  })

  await test.step('管理画面：申請を承認するとキャンセルになり、在庫数が戻って履歴に残る', async () => {
    const orderId = new URL(orderUrl).pathname.split('/').at(-1)
    await loginAdmin(page, ACCOUNTS.administrator, `/orders/${orderId}`)
    await page.getByRole('button', { name: '申請を承認する' }).click()
    await page.getByRole('alertdialog').getByRole('button', { name: '承認してキャンセルする' }).click()
    await expect(page.getByText('申請を承認し、キャンセルしました')).toBeVisible()
    await expect(page.getByText('お客様の申請の承認')).toBeVisible()

    await page.goto(`${ADMIN_URL}/products/${productId}/variants/${variantId}/stock`)
    await expect(page.locator('p', { hasText: /^3$/ })).toBeVisible()
    await expect(page.getByRole('cell', { name: '注文のキャンセル' })).toBeVisible()
    await expect(page.getByRole('cell', { name: '注文', exact: true })).toBeVisible()
  })
})
