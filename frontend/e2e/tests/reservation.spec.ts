import { expect, test } from '@playwright/test'
import { createPublishedProduct, RUN_ID, taxCategoryWithRate } from '../src/data'
import { STORE_URL } from '../src/urls'

/** 公開開始までの時間。商品の用意と、開始前の表示の確認が終わるまでに足りる長さ */
const RESERVATION_MILLIS = 8_000

test('AC-35 公開開始日時を予約して公開すると、開始前は店舗画面に出ず、開始日時になると出る', async ({ page }) => {
  const { id: taxCategoryId } = await taxCategoryWithRate(10)
  const name = `E2E 予約 ${RUN_ID}`
  const publishStart = new Date(Date.now() + RESERVATION_MILLIS)
  const productId = await createPublishedProduct({
    code: 'RESERVE',
    name,
    taxCategoryId,
    priceExclTax: 1000,
    variants: [{ code: 'A', stock: 1 }],
    publishStart,
  })
  const url = `${STORE_URL}/products/${productId}`

  expect(Date.now()).toBeLessThan(publishStart.getTime())
  const before = await page.goto(url)
  expect(before?.status()).toBe(404)
  await expect(page.getByRole('heading', { name: 'ページが見つかりません' })).toBeVisible()

  // 運営者の操作なしに、開始日時で切り替わる。詳細はキャッシュを使わない
  await expect(async () => {
    const response = await page.goto(url)
    expect(response?.status()).toBe(200)
  }).toPass({ timeout: RESERVATION_MILLIS + 10_000, intervals: [1_000] })
  await expect(page.getByRole('heading', { name })).toBeVisible()
  expect(Date.now()).toBeGreaterThanOrEqual(publishStart.getTime())
})
