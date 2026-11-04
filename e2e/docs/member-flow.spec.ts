import path from 'node:path'
import { expect, test } from '@playwright/test'
import { fillNewPassword, fillSignup, openMailLink } from '../src/auth'
import { makeShot } from '../src/screenshot'
import { SHOWCASE_STORE_URL as STORE } from '../src/showcase-urls'

/** 撮った画像の置き場所（docs/01-概要/member.md が載せる） */
const OUT = path.resolve(import.meta.dirname, '../../docs/01-概要/img/member')

/** 画面を撮って OUT に置く */
const shot = makeShot(OUT)

/** 登録する会員の氏名（注文のお届け先 RECIPIENT と同じ人物） */
const NAME = { lastName: '山田', firstName: '花子' }

test('会員登録とログインの流れのスクリーンショットを撮る', async ({ page }) => {
  // 画像に写るメールアドレス。撮影ごとに新しくし、前に登録した会員と重ならないようにする
  const account = { email: `hanako-${Date.now()}@example.com`, password: 'Hanako-pass-2026' }

  await test.step('店舗画面：ヘッダーの「会員登録」から、会員登録の画面を入力する', async () => {
    await page.goto(`${STORE}/`)
    await shot(page, '01-store', page.getByRole('link', { name: '会員登録' }))

    await fillSignup(page, account, NAME)
    await shot(page, '02-signup', page.getByRole('button', { name: '登録', exact: true }))
  })

  await test.step('確認のメールを送り、そのリンクでパスワードを決める', async () => {
    await page.getByRole('button', { name: '登録', exact: true }).click()
    await shot(page, '03-mail-sent', page.getByText(/メールアドレスを確認する手順を記載したメール/))

    await openMailLink(page, account)
    await fillNewPassword(page, account)
    await shot(page, '04-set-password', page.getByRole('button', { name: '送信', exact: true }))
  })

  await test.step('ログインした店舗画面を撮り、ログアウトしてログインの画面を撮る', async () => {
    await page.getByRole('button', { name: '送信', exact: true }).click()
    await shot(page, '05-logged-in', page.getByText(`${NAME.lastName} ${NAME.firstName} 様`))

    await page.getByRole('button', { name: 'ログアウト', exact: true }).click()
    await page.getByRole('link', { name: 'ログイン' }).click()
    await expect(page.locator('#password')).toBeVisible()
    await shot(page, '06-login', page.getByRole('button', { name: 'サインイン', exact: true }))
  })
})
