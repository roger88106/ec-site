import { expect, test } from '@playwright/test'
import { ACCOUNTS, loginAdmin } from '../src/auth'
import { createPublishedProduct, taxCategoryWithRate } from '../src/data'
import { STORE_URL } from '../src/urls'

test('E2E の土台：API で作った公開の商品が、管理画面の一覧と店舗画面の詳細に出る', async ({ page }) => {
  const { id: taxCategoryId } = await taxCategoryWithRate(10)
  const productId = await createPublishedProduct({
    code: 'SMOKE',
    name: '土台の確認の商品',
    taxCategoryId,
    priceExclTax: 1000,
    variants: [{ code: 'A', stock: 1 }],
  })

  await loginAdmin(page, ACCOUNTS.administrator, `/products/${productId}`)
  await expect(page.getByText('公開', { exact: true }).first()).toBeVisible()

  await page.goto(`${STORE_URL}/products/${productId}`)
  await expect(page.getByRole('heading', { name: '土台の確認の商品' })).toBeVisible()
  await expect(page.getByText('1,100 円（税抜 1,000 円）')).toBeVisible()
})
