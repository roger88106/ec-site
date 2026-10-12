import { expect, type Locator, type Page } from '@playwright/test'
import { STORE_URL } from './urls'

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

/**
 * ログインしている会員のカートを空にする。会員のカートは会員ごとに 1 つで実行をまたいで残るので（AC-121）、
 * 共用のアカウントで注文するテストは、前の実行の品目を消してから始める。
 */
export async function emptyCart(page: Page): Promise<void> {
  await page.goto(`${STORE_URL}/cart`)
  const remove = page.getByRole('button', { name: '削除' })
  while ((await remove.count()) > 0) {
    const before = await remove.count()
    await remove.first().click()
    await expect(remove).toHaveCount(before - 1)
  }
}

/** お届け先の入力（郵便番号だけを差し替えられる）。メールアドレスは会員のものを使い、入力しない（AC-127） */
export async function fillShipping(page: Page, postalCode: string) {
  await page.getByLabel('氏名').fill('E2E 太郎')
  await page.getByLabel('郵便番号').fill(postalCode)
  await page.getByLabel('都道府県').selectOption('東京都')
  await page.getByLabel('住所').fill('千代田区千代田1-1')
  await page.getByLabel('電話番号').fill('03-1234-5678')
}
