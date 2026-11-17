import path from 'node:path'
import { expect, type Page, test } from '@playwright/test'
import {
  fillNewPassword,
  fillSignup,
  idpMailSentNotice,
  idpPasswordInput,
  idpPasswordSubmitButton,
  idpSignInButton,
  idpSignupButton,
  logoutButton,
  openMailLink,
} from '../src/auth'
import { makeShot } from '../src/screenshot'
import { SHOWCASE_BACKEND_URL as BACKEND, SHOWCASE_STORE_URL as STORE } from '../src/showcase-urls'
import { productCard } from '../src/store'
import { CHECK_TIMEOUT, reopenUntil, SHOWCASE_CACHE_TIMEOUT } from '../src/waits'

/** 撮った画像の置き場所（概要の会員登録とログインの流れの文書が載せる） */
const OUT = path.resolve(import.meta.dirname, '../../docs/01-概要/img/member')

/** 画面を撮って OUT に置く */
const shot = makeShot(OUT)

/** 登録する会員の氏名（注文のお届け先 RECIPIENT と同じ人物） */
const NAME = { lastName: '山田', firstName: '花子' }

/**
 * トップの新着欄に、DB にある最新の商品が出るまで開き直す。
 *
 * 店舗画面のキャッシュは、期限の切れた後の最初の表示で古い一覧を返す。そのままだと、前の流れで登録した商品が写るかどうかが、撮影のたびに変わる。
 *
 * @param page 使うページ
 */
async function openLatestStoreTop(page: Page): Promise<void> {
  const response = await fetch(`${BACKEND}/api/store/products?sort=newest&size=1`)
  if (!response.ok) throw new Error(`新着の商品を取得できない（${response.status}）`)
  const { items } = (await response.json()) as { items: { name: string }[] }
  const newest = items.at(0)
  if (newest === undefined) throw new Error('新着の商品がない')
  await reopenUntil(
    page,
    `${STORE}/`,
    async () => {
      await expect(productCard(page, newest.name)).toBeVisible({ timeout: CHECK_TIMEOUT })
    },
    { timeout: SHOWCASE_CACHE_TIMEOUT },
  )
}

test('会員登録とログインの流れのスクリーンショットを撮る', async ({ page }) => {
  // 画像に写るメールアドレス。撮影ごとに新しくし、前に登録した会員と重ならないようにする
  const account = { email: `hanako-${Date.now()}@example.com`, password: 'Hanako-pass-2026' }

  await test.step('店舗画面：ヘッダーの「会員登録」から、会員登録の画面に入力する', async () => {
    await openLatestStoreTop(page)
    await shot(page, '01-store', page.getByRole('link', { name: '会員登録' }))

    await fillSignup(page, account, NAME)
    await shot(page, '02-signup', idpSignupButton(page))
  })

  await test.step('確認のメールを送り、そのリンクでパスワードを設定する', async () => {
    await idpSignupButton(page).click()
    await shot(page, '03-mail-sent', idpMailSentNotice(page))

    await openMailLink(page, account)
    await fillNewPassword(page, account)
    await shot(page, '04-set-password', idpPasswordSubmitButton(page))
  })

  await test.step('ログインした店舗画面を撮り、ログアウトしてログインの画面を撮る', async () => {
    await idpPasswordSubmitButton(page).click()
    await shot(page, '05-logged-in', page.getByText(`${NAME.lastName} ${NAME.firstName} 様`))

    await logoutButton(page).click()
    await page.getByRole('link', { name: 'ログイン', exact: true }).click()
    await expect(idpPasswordInput(page)).toBeVisible()
    await shot(page, '06-login', idpSignInButton(page))
  })
})
