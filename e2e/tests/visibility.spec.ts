import { expect, type Page, test } from '@playwright/test'
import { ACCOUNTS, loginAdmin } from '../src/auth'
import {
  createCategory,
  createPublishedProduct,
  discontinue,
  RUN_ID,
  taxCategoryWithRate,
  unpublish,
} from '../src/data'
import { productCard } from '../src/store'
import { STORE_URL } from '../src/urls'
import { CHECK_TIMEOUT, reopenUntil } from '../src/waits'

/** 店舗画面の詳細が「見つかりません」であることを確認する（AC-65） */
async function expectNotFound(page: Page, productId: string) {
  const response = await page.goto(`${STORE_URL}/products/${productId}`)
  expect(response?.status()).toBe(404)
  await expect(page.getByRole('heading', { name: 'ページが見つかりません' })).toBeVisible()
}

for (const [label, change, button, toast] of [
  ['AC-30 非公開', unpublish, '非公開にする', '非公開にしました'],
  ['AC-32 販売終了', discontinue, '販売終了にする', '販売終了にしました'],
] as const) {
  test(`${label}にすると、店舗画面の詳細はすぐ「見つかりません」になり、一覧から消える`, async ({ page }) => {
    const { id: taxCategoryId } = await taxCategoryWithRate(10)
    const { id: categoryId } = await createCategory('E2E カテゴリ')
    const name = `E2E ${label} ${RUN_ID}`
    const productId = await createPublishedProduct({
      code: change === unpublish ? 'HIDE' : 'END',
      name,
      taxCategoryId,
      priceExclTax: 1000,
      categoryIds: [categoryId],
      variants: [{ code: 'A', stock: 1 }],
    })
    const listUrl = `${STORE_URL}/products?categoryId=${categoryId}`

    await reopenUntil(page, listUrl, async () => {
      await expect(productCard(page, name)).toBeVisible({ timeout: CHECK_TIMEOUT })
    })
    await page.goto(`${STORE_URL}/products/${productId}`)
    await expect(page.getByRole('heading', { name })).toBeVisible()

    await loginAdmin(page, ACCOUNTS.administrator, `/products/${productId}`)
    await page.getByRole('button', { name: button, exact: true }).click()
    if (change === discontinue) {
      // 販売終了は確認のダイアログで確定する
      await page.getByRole('alertdialog').getByRole('button', { name: button, exact: true }).click()
    }
    await expect(page.getByText(toast)).toBeVisible()

    // 詳細はキャッシュを使わないので、すぐ「見つかりません」になる
    await expectNotFound(page, productId)
    await reopenUntil(page, listUrl, async () => {
      await expect(page.getByText('条件に合う商品はありません。')).toBeVisible({ timeout: CHECK_TIMEOUT })
    })
  })
}

test('AC-54 価格の安い順は、税込の価格で並べる', async ({ page }) => {
  const standard = await taxCategoryWithRate(10)
  const reduced = await taxCategoryWithRate(8)
  const { id: categoryId } = await createCategory('E2E カテゴリ')
  // 税抜では A が安いが、税込では B（1,010 × 1.08）が A（1,000 × 1.10）より安い
  const a = `E2E 並び A ${RUN_ID}`
  const b = `E2E 並び B ${RUN_ID}`
  await createPublishedProduct({
    code: 'SORT-A',
    name: a,
    taxCategoryId: standard.id,
    priceExclTax: 1000,
    categoryIds: [categoryId],
    variants: [{ code: 'X', stock: 1 }],
  })
  await createPublishedProduct({
    code: 'SORT-B',
    name: b,
    taxCategoryId: reduced.id,
    priceExclTax: 1010,
    categoryIds: [categoryId],
    variants: [{ code: 'X', stock: 1 }],
  })

  await reopenUntil(page, `${STORE_URL}/products?categoryId=${categoryId}&sort=price_asc`, async () => {
    await expect(page.getByRole('link').filter({ hasText: RUN_ID })).toHaveText([new RegExp(b), new RegExp(a)], {
      timeout: CHECK_TIMEOUT,
    })
  })
  await reopenUntil(page, `${STORE_URL}/products?categoryId=${categoryId}&sort=price_desc`, async () => {
    await expect(page.getByRole('link').filter({ hasText: RUN_ID })).toHaveText([new RegExp(a), new RegExp(b)], {
      timeout: CHECK_TIMEOUT,
    })
  })
})
