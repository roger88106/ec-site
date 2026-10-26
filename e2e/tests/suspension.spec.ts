import { expect, test } from '@playwright/test'
import { ACCOUNTS, loginAdmin } from '../src/auth'
import { createPublishedProduct, productCode, RUN_ID, taxCategoryWithRate } from '../src/data'
import { STORE_URL } from '../src/urls'

test('AC-28、62、63 管理画面でバリアントを販売停止にすると、店舗画面の詳細でその値が選べなくなる', async ({ page }) => {
  const { id: taxCategoryId } = await taxCategoryWithRate(10)
  const name = `E2E 販売停止 ${RUN_ID}`
  const code = productCode('SUSPEND')
  const productId = await createPublishedProduct({
    code: 'SUSPEND',
    name,
    taxCategoryId,
    priceExclTax: 1000,
    axes: ['色'],
    variants: [
      { code: 'RED', axisValues: ['赤'], stock: 3 },
      { code: 'BLUE', axisValues: ['青'], stock: 3 },
    ],
  })

  await page.goto(`${STORE_URL}/products/${productId}`)
  await expect(page.getByRole('button', { name: '赤', exact: true })).toBeEnabled()

  await loginAdmin(page, ACCOUNTS.administrator, `/products/${productId}`)
  await page.getByRole('button', { name: `${code}-REDを販売停止`, exact: true }).click()
  await expect(page.getByText('販売停止にしました')).toBeVisible()

  // 詳細はキャッシュを使わないので、すぐ反映される
  await page.goto(`${STORE_URL}/products/${productId}`)
  await expect(page.getByRole('heading', { name })).toBeVisible()
  await expect(page.getByRole('button', { name: '赤', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '青', exact: true }).click()
  await expect(page.getByText('在庫あり')).toBeVisible()
})
