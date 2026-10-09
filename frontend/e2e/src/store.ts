import { expect, type Locator, type Page } from '@playwright/test'

/** 店舗画面の API の結果のキャッシュ（E2E では 1 秒。playwright.config.ts）が切れて、表示が変わるまでに待つ時間 */
const CACHE_TIMEOUT = 20_000

/**
 * 店舗画面を開き直しながら、表示が期待どおりになるのを待つ。<br />
 * 店舗画面は API の結果をキャッシュし、期限の切れた後の最初の表示では古い結果を返して裏で取り直す。
 * そのため、管理画面での変更は、開き直すうちに反映される。
 *
 * @param page 店舗画面のページ
 * @param url 開く URL
 * @param check 表示を確かめる。満たさなければ投げる
 */
export async function expectEventually(page: Page, url: string, check: (page: Page) => Promise<void>) {
  await expect(async () => {
    await page.goto(url)
    await check(page)
  }).toPass({ timeout: CACHE_TIMEOUT, intervals: [500, 1_000] })
}

/** 店舗画面の一覧・検索の結果の、商品のカード（商品名で探す） */
export function productCard(page: Page, name: string): Locator {
  return page.getByRole('link').filter({ hasText: name })
}
