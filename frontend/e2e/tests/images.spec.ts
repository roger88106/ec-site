import { expect, test } from '@playwright/test'
import { createCategory, createPublishedProduct, RUN_ID, taxCategoryWithRate } from '../src/data'
import { expectEventually, productCard } from '../src/store'
import { ADMIN_URL, STORE_URL } from '../src/urls'

/** 1×1 の PNG */
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
  'base64',
)

test('AC-37 管理画面で画像を登録すると、店舗画面の一覧と詳細に出る', async ({ page }) => {
  const { id: taxCategoryId } = await taxCategoryWithRate(10)
  const { id: categoryId } = await createCategory('E2E 画像')
  const name = `E2E 画像 ${RUN_ID}`
  const productId = await createPublishedProduct({
    code: 'IMAGE',
    name,
    taxCategoryId,
    priceExclTax: 1000,
    categoryIds: [categoryId],
    variants: [{ code: 'A', stock: 1 }],
  })

  await test.step('管理画面：画像を選ぶと、ストレージへ送って登録する', async () => {
    await page.goto(`${ADMIN_URL}/products/${productId}`)
    await page.getByLabel('画像のファイル').setInputFiles({ name: 'item.png', mimeType: 'image/png', buffer: PNG })
    await expect(page.getByText('画像を 1 枚登録しました')).toBeVisible()
    await expect(page.getByRole('img', { name: '画像 1' })).toBeVisible()
  })

  const imageUrl = await page.getByRole('img', { name: '画像 1' }).getAttribute('src')
  expect(imageUrl).toBeTruthy()

  await test.step('店舗画面：一覧の代表画像と、詳細の画像に出る', async () => {
    await expectEventually(page, `${STORE_URL}/products?categoryId=${categoryId}`, async () => {
      await expect(productCard(page, name).locator('img')).toHaveAttribute('src', imageUrl ?? '', { timeout: 1_000 })
    })
    await page.goto(`${STORE_URL}/products/${productId}`)
    const image = page.getByRole('img', { name: '商品の画像' })
    await expect(image).toHaveAttribute('src', imageUrl ?? '')
    // 配信のバケットから実際に読み込めている
    await expect.poll(() => image.evaluate((element: HTMLImageElement) => element.naturalWidth)).toBe(1)
  })
})
