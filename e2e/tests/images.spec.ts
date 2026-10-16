import path from 'node:path'
import { expect, test } from '@playwright/test'
import { ACCOUNTS, loginAdmin } from '../src/auth'
import { createCategory, createPublishedProduct, RUN_ID, taxCategoryWithRate } from '../src/data'
import { expectEventually, productCard } from '../src/store'
import { STORE_URL } from '../src/urls'
import { CHECK_TIMEOUT } from '../src/waits'

/**
 * スマートフォンの形式の画像（AVIF）。左が赤、右が青の 64x32 で、向き（時計回りに 90 度）と位置情報が付いている。
 * backend/ecsite/src/integrationTest/resources/images/source.avif と同じもの（作り方は同じ場所の generate.sh）
 */
const PHOTO = path.resolve(import.meta.dirname, '../fixtures/photo.avif')

test('AC-37、67、68 管理画面で AVIF の画像を登録すると、WebP に変換され、店舗画面の一覧と詳細に出る', async ({
  page,
}) => {
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
    await loginAdmin(page, ACCOUNTS.administrator, `/products/${productId}`)
    await page.getByLabel('画像のファイル').setInputFiles(PHOTO)
    await expect(page.getByText('画像を 1 枚登録しました')).toBeVisible()
    await expect(page.getByRole('img', { name: '画像 1' })).toBeVisible()
  })

  const imageUrl = await page.getByRole('img', { name: '画像 1' }).getAttribute('src')
  // 配信するのは、変換した WebP（AC-68）
  expect(imageUrl).toMatch(/\.webp$/)

  await test.step('店舗画面：一覧の代表画像と、詳細の画像に出る', async () => {
    await expectEventually(page, `${STORE_URL}/products?categoryId=${categoryId}`, async () => {
      await expect(productCard(page, name).locator('img')).toHaveAttribute('src', imageUrl ?? '', {
        timeout: CHECK_TIMEOUT,
      })
    })
    await page.goto(`${STORE_URL}/products/${productId}`)
    const image = page.getByRole('img', { name: '商品の画像' })
    await expect(image).toHaveAttribute('src', imageUrl ?? '')
    // 配信のバケットから実際に読み込め、向きが画素に反映されている（64x32 を時計回りに 90 度回した 32x64）
    await expect
      .poll(() => image.evaluate((element: HTMLImageElement) => [element.naturalWidth, element.naturalHeight]))
      .toEqual([32, 64])
  })
})
