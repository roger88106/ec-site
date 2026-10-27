import { expect, type Locator, type Page } from '@playwright/test'
import { type Account, loginStore } from './auth'
import { STORE_URL } from './urls'
import { ASYNC_TIMEOUT, CHECK_TIMEOUT, reopenUntil } from './waits'

/**
 * 店舗画面の一覧・検索の結果の、商品のカード（商品名で探す）。
 *
 * @param page 店舗画面の一覧か検索の結果を開いているページ
 * @param name 商品名（一部でもよい）
 * @returns 商品のカード
 */
export function productCard(page: Page, name: string): Locator {
  return page.getByRole('link').filter({ hasText: name })
}

/**
 * 注文手続きの画面の、注文するボタン。名前は金額を添えた「注文する（3,200 円）」で、金額は注文ごとに違うので、名前の全体を形で探す。
 *
 * @param page 注文手続きの画面を開いているページ
 * @returns 注文するボタン
 */
export function placeOrderButton(page: Page): Locator {
  return page.getByRole('button', { name: /^注文する（[\d,]+ 円）$/ })
}

/**
 * ログインしている会員のカートを空にする。会員のカートは会員ごとに 1 つで実行をまたいで残るので（AC-121）、
 * 共用のアカウントで注文するテストは、前の実行の品目を消してから始める。
 *
 * @param page 店舗画面のページ（会員としてログインしている）
 * @param storeUrl 店舗画面の URL（機能紹介の撮影は、E2E でなくローカルの環境の店舗画面を使う）
 */
export async function emptyCart(page: Page, storeUrl = STORE_URL): Promise<void> {
  await page.goto(`${storeUrl}/cart`)
  // 品目の描画の前に読むと空に見えるので、品目があってもなくても出る見出しで、画面の表示が終わるのを待つ
  await expect(page.getByRole('heading', { name: 'カート', exact: true })).toBeVisible()
  const items = page.getByRole('listitem').filter({ has: page.getByRole('button', { name: '削除', exact: true }) })
  // 品目は画面に見える文字（商品名、軸の値、単価。数量の欄より前の行）で見分ける。同じ商品のバリアントが複数あっても、軸の値が違う
  for (const text of await items.allInnerTexts()) {
    const lines = text.split('\n')
    const quantityIndex = lines.indexOf('数量')
    if (quantityIndex === -1) {
      throw new Error(`カートの品目の表示が想定と違い、数量の行が見つからない：${text}`)
    }
    const [productName, ...details] = lines.slice(0, quantityIndex)
    let item = items.filter({ has: page.getByRole('link', { name: productName, exact: true }) })
    for (const detail of details) {
      item = item.filter({ hasText: detail })
    }
    await item.getByRole('button', { name: '削除', exact: true }).click()
    await expect(item).toHaveCount(0)
  }
}

/** 商品の詳細からカートに入れるときの選択 */
export type CartChoice = {
  /** 数量（数量の欄の選択肢の文言。省けば初めの 1） */
  quantity?: string
  /** 選ぶバリアントのボタンの名前（色など。省けば選ばない） */
  option?: string
}

/**
 * 商品の詳細を開き、数量とバリアントを選んで、カートに入れる。「カートに入れました。」と出るまで待つ。
 *
 * @param page 店舗画面のページ
 * @param productId 商品 ID
 * @param choice 数量とバリアントの選択
 * @param storeUrl 店舗画面の URL（既定は E2E の店舗画面）
 */
export async function addToCart(
  page: Page,
  productId: string,
  { quantity, option }: CartChoice = {},
  storeUrl = STORE_URL,
): Promise<void> {
  await page.goto(`${storeUrl}/products/${productId}`)
  if (option !== undefined) {
    await page.getByRole('button', { name: option, exact: true }).click()
  }
  if (quantity !== undefined) {
    await page.getByLabel('数量').selectOption(quantity)
  }
  await page.getByRole('button', { name: 'カートに入れる', exact: true }).click()
  await expect(page.getByText('カートに入れました。')).toBeVisible()
}

/**
 * 会員としてログインし、カートを空にして、商品を 1 つずつ入れる。会員のカートは実行をまたいで残るので、
 * 共用のアカウントで注文するテストは、これで始める。
 *
 * @param page 店舗画面のページ
 * @param account ログインする会員
 * @param productIds カートに入れる商品の ID（1 つずつ入れる）
 */
export async function loginWithCart(page: Page, account: Account, productIds: string[]): Promise<void> {
  await loginStore(page, account)
  await emptyCart(page)
  for (const productId of productIds) {
    await addToCart(page, productId)
  }
}

/**
 * 会員としてログインし、カートを空にして、商品を入れ、注文して mock の支払画面に移るまで進む。
 *
 * @param page 店舗画面のページ
 * @param account ログインする会員
 * @param productIds カートに入れる商品の ID
 */
export async function orderAsMember(page: Page, account: Account, productIds: string[]): Promise<void> {
  await loginWithCart(page, account, productIds)
  await orderCart(page)
}

/**
 * 注文の画面の URL から、注文 ID を読む。
 *
 * @param orderUrl 注文の画面の URL（`/orders/{注文 ID}`。クエリは付いていてもよい）
 * @returns 注文 ID
 */
export function orderIdOf(orderUrl: string): string {
  const orderId = new URL(orderUrl).pathname.split('/').at(-1)
  if (!orderId) {
    throw new Error(`注文の画面の URL から注文 ID を読めない：${orderUrl}`)
  }
  return orderId
}

/**
 * お届け先の入力（郵便番号と氏名だけを差し替えられる）。メールアドレスは会員のものを使い、入力しない（AC-127）。<br />
 * 郵便番号が 7 桁なら、補われた都道府県（東京都）・市区町村（千代田区）・町域（千代田）を確認して、番地を追加する（AC-255）。
 * 7 桁でなければ補われないので、一覧から選ぶ。
 *
 * @param page 注文手続きの画面を開いているページ
 * @param postalCode 郵便番号
 * @param name お届け先の氏名
 */
export async function fillShipping(page: Page, postalCode: string, name = 'E2E 太郎') {
  await page.getByLabel('氏名').fill(name)
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
 * カートから注文手続きに進み、お届け先を入れて注文する。注文すると mock の支払画面に移る。
 *
 * @param page カートに商品のある店舗画面のページ
 * @param storeUrl 店舗画面の URL（既定は E2E の店舗画面）
 * @param name お届け先の氏名（省けば fillShipping の既定）
 */
export async function orderCart(page: Page, storeUrl = STORE_URL, name?: string) {
  await page.goto(`${storeUrl}/cart`)
  await page.getByRole('button', { name: '注文手続きへ進む', exact: true }).click()
  await fillShipping(page, '100-0001', name)
  await placeOrderButton(page).click()
  await expect(page.getByRole('heading', { name: 'mock の支払' })).toBeVisible()
}

/**
 * mock の支払画面のボタンを押し、注文の画面に戻るのを待つ。
 *
 * @param page mock の支払画面を開いているページ
 * @param button 押すボタンの名前（支払うか、やめる）
 * @returns 注文の画面の URL（クエリなし）
 */
export async function leaveMockPage(page: Page, button: '支払う' | 'やめる'): Promise<string> {
  await page.getByRole('button', { name: button, exact: true }).click()
  await expect(page).toHaveURL(/\/orders\/[^/?]+\?payment=returned$/)
  return page.url().split('?')[0]
}

/**
 * mock の支払画面で支払い、注文の画面に戻って、注文が「受付」になるのを待つ。<br />
 * 戻った直後は通知の反映の前で「お支払いを確認しています」と出ることがあるので、開き直しながら待つ。
 *
 * @param page mock の支払画面を開いているページ
 * @returns 注文の画面の URL（クエリなし）
 */
export async function payOnMockPage(page: Page): Promise<string> {
  const orderUrl = await leaveMockPage(page, '支払う')
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
