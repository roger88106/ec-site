import { expect, type Locator, type Page } from '@playwright/test'
import { STORE_URL } from './urls'
import { ASYNC_TIMEOUT, CHECK_TIMEOUT, reopenUntil } from './waits'

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
  await reopenUntil(page, url, () => check(page))
}

/** 店舗画面の一覧・検索の結果の、商品のカード（商品名で探す） */
export function productCard(page: Page, name: string): Locator {
  return page.getByRole('link').filter({ hasText: name })
}

/**
 * ログインしている会員のカートを空にする。会員のカートは会員ごとに 1 つで実行をまたいで残るので（AC-121）、
 * 共用のアカウントで注文するテストは、前の実行の品目を消してから始める。
 *
 * @param storeUrl 店舗画面の URL（機能紹介の撮影は、E2E でなくローカルの環境の店舗画面を使う）
 */
export async function emptyCart(page: Page, storeUrl = STORE_URL): Promise<void> {
  await page.goto(`${storeUrl}/cart`)
  const remove = page.getByRole('button', { name: '削除' })
  while ((await remove.count()) > 0) {
    const before = await remove.count()
    await remove.first().click()
    await expect(remove).toHaveCount(before - 1)
  }
}

/**
 * お届け先の入力（郵便番号だけを差し替えられる）。メールアドレスは会員のものを使い、入力しない（AC-127）。<br />
 * 郵便番号が 7 桁なら、補われた都道府県（東京都）・市区町村（千代田区）・町域（千代田）を確かめて、番地を足す（AC-255）。
 * 7 桁でなければ補われないので、一覧から選ぶ。
 */
export async function fillShipping(page: Page, postalCode: string) {
  await page.getByLabel('氏名').fill('E2E 太郎')
  await page.getByLabel('郵便番号').fill(postalCode)
  if (/^[0-9]{3}-?[0-9]{4}$/.test(postalCode)) {
    await expect(page.getByLabel('市区町村')).toHaveValue('13101')
    await expect(page.getByLabel('都道府県')).toHaveValue('13')
    await expect(page.getByLabel('住所')).toHaveValue('千代田')
  } else {
    await page.getByLabel('都道府県').selectOption('東京都')
    await page.getByLabel('市区町村').selectOption('千代田区')
    await page.getByLabel('住所').fill('千代田')
  }
  await page.getByLabel('住所').pressSequentially(' 1-1')
  await page.getByLabel('電話番号').fill('03-1234-5678')
}

/**
 * カートから注文手続きに進み、お届け先を入れて注文する。注文すると mock の支払の画面に移る。
 *
 * @param page カートに商品のある店舗画面のページ
 */
export async function orderCart(page: Page) {
  await page.goto(`${STORE_URL}/cart`)
  await page.getByRole('button', { name: '注文手続きへ進む' }).click()
  await fillShipping(page, '100-0001')
  await page.getByRole('button', { name: /^注文する/ }).click()
  await expect(page.getByRole('heading', { name: 'mock の支払' })).toBeVisible()
}

/**
 * mock の支払の画面で支払い、注文の画面に戻って、注文が「受付」になるのを待つ。<br />
 * 戻った直後は通知の反映の前で「お支払いを確認しています」と出ることがあるので、開き直しながら待つ。
 *
 * @param page mock の支払の画面を開いているページ
 * @return 注文の画面の URL（クエリなし）
 */
export async function payOnMockPage(page: Page): Promise<string> {
  await page.getByRole('button', { name: '支払う' }).click()
  await expect(page).toHaveURL(/\/orders\/[^/?]+\?payment=returned$/)
  const orderUrl = page.url().split('?')[0]
  await expectOrderPage(page, orderUrl, page.getByText('受付', { exact: true }))
  return orderUrl
}

/**
 * 注文の画面を開き直しながら、表示が出るのを待つ（支払の通知や期限切れは、裏で反映される）。
 *
 * @param page 店舗画面のページ
 * @param orderUrl 注文の画面の URL
 * @param shown 出るのを待つ表示
 * @param timeout 待つ時間
 */
export async function expectOrderPage(page: Page, orderUrl: string, shown: Locator, timeout = ASYNC_TIMEOUT) {
  await reopenUntil(page, orderUrl, () => expect(shown).toBeVisible({ timeout: CHECK_TIMEOUT }), { timeout })
}
