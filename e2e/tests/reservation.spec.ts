import { expect, test } from '@playwright/test'
import { changePublishStart, createPublishedProduct, RUN_ID, taxCategoryWithRate } from '../src/data'
import { STORE_URL } from '../src/urls'
import { ASYNC_TIMEOUT, reopenUntil } from '../src/waits'

/** 準備の間に開始日時にならないよう、初めに予約しておく先の時間 */
const FAR_FUTURE_MILLIS = 60 * 60_000

/** 開始前の表示を確認した後、開始日時を何ミリ秒後に変えるか */
const RESERVATION_MILLIS = 3_000

test('AC-35 公開開始日時を予約して公開すると、開始前は店舗画面に出ず、開始日時になると出る', async ({ page }) => {
  const { id: taxCategoryId } = await taxCategoryWithRate(10)
  const name = `E2E 予約 ${RUN_ID}`
  // 準備（在庫ができるのを待つ など）にかかる時間によらないよう、先の日時で予約して作り、開始前を確認してから近づける
  const productId = await createPublishedProduct({
    code: 'RESERVE',
    name,
    taxCategoryId,
    priceExclTax: 1000,
    variants: [{ code: 'A', stock: 1 }],
    publishStart: new Date(Date.now() + FAR_FUTURE_MILLIS),
  })
  const url = `${STORE_URL}/products/${productId}`

  const before = await page.goto(url)
  expect(before?.status()).toBe(404)
  await expect(page.getByRole('heading', { name: 'ページが見つかりません' })).toBeVisible()

  await changePublishStart(productId, new Date(Date.now() + RESERVATION_MILLIS))

  // 運営者の操作なしに、開始日時で切り替わる（404 から 200 になる）。詳細はキャッシュを使わない
  await reopenUntil(
    page,
    url,
    async (response) => {
      expect(response?.status()).toBe(200)
    },
    { timeout: RESERVATION_MILLIS + ASYNC_TIMEOUT },
  )
  await expect(page.getByRole('heading', { name })).toBeVisible()
})
