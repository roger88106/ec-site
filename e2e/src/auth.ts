import { createHash, randomBytes } from 'node:crypto'
import { expect, type Locator, type Page } from '@playwright/test'
import { RUN_ID } from './data'
import { ADMIN_URL, IDP_URL, MAILPIT_URL, STORE_URL } from './urls'
import { MAIL_TIMEOUT } from './waits'

/** ログインに使うアカウント */
type Account = { email: string; password: string }

/** realm の JSON にあるローカルと CI 用のアカウント（docs/guides/setup.md の「認証（Keycloak）」） */
export const ACCOUNTS = {
  /** 運営者の realm の管理者（すべての権限） */
  administrator: { email: 'admin@example.com', password: 'admin-pass-1' },
  /** 運営者の realm の閲覧者（:read の権限だけ） */
  viewer: { email: 'viewer@example.com', password: 'viewer-pass-1' },
  /** 運営者の realm の管理職（参照の権限と、すべてのレポート） */
  manager: { email: 'manager@example.com', password: 'manager-pass-1' },
  /** 運営者の realm の営業（参照の権限と、売上の推移を除くレポート） */
  sales: { email: 'sales@example.com', password: 'sales-pass-1' },
  /** 消費者の realm の会員 */
  member: { email: 'member@example.com', password: 'member-pass-1' },
  /** 消費者の realm の 2 人目の会員（他人のデータ、別々の会員の同時の操作） */
  member2: { email: 'member2@example.com', password: 'member2-pass-1' },
} satisfies Record<string, Account>

/**
 * E2E で登録する会員のメールアドレス。実行ごとの印を付け、前の実行で登録した会員と重ならないようにする。
 *
 * @param label テストの中での呼び名（英小文字と数字）
 */
export function memberEmail(label: string): string {
  return `e2e-${RUN_ID.toLowerCase()}-${label}@example.com`
}

/**
 * IdP（Keycloak）のログインの画面で、メールアドレスとパスワードを入れてログインする。
 *
 * @param page IdP のログインの画面を開いているページ
 */
export async function submitIdpLogin(page: Page, account: Account): Promise<void> {
  await page.locator('#username').fill(account.email)
  await page.locator('#password').fill(account.password)
  await page.getByRole('button', { name: 'サインイン' }).click()
}

/**
 * 管理画面を開き、運営者としてログインする（管理画面は、開くと IdP のログインへ移る。AC-132）。
 *
 * @param path ログインの後に開く管理画面のパス
 */
export async function loginAdmin(page: Page, account: Account, path = '/'): Promise<void> {
  await page.goto(`${ADMIN_URL}${path}`)
  await submitIdpLogin(page, account)
  await expect(page.getByRole('button', { name: 'ログアウト' })).toBeVisible()
}

/**
 * 店舗画面で会員としてログインする。ログインの後は returnTo の画面に戻る（AC-113）。
 *
 * @param returnTo ログインの後に戻る店舗画面のパス
 */
export async function loginStore(page: Page, account: Account, returnTo = '/'): Promise<void> {
  await page.goto(`${STORE_URL}/login?returnTo=${encodeURIComponent(returnTo)}`)
  await submitIdpLogin(page, account)
  await expect(page.getByRole('button', { name: 'ログアウト' })).toBeVisible()
}

/** 会員登録で入れる、会員の任意の属性（IdP の画面の表示のとおり） */
export type MemberProfile = {
  /** 生年月日（yyyy-MM-dd） */
  birthdate?: string
  /** 性別 */
  gender?: '男性' | '女性' | 'その他' | '回答しない'
}

/**
 * 店舗画面から会員登録する。IdP の会員登録の画面でメールアドレス・氏名・任意の属性を入れ、確認のメールのリンクでパスワードを設定する。
 * 同じブラウザーでリンクを開くので、ログインした状態で登録を始めた画面に戻る（AC-112）。
 *
 * @param page 店舗画面（ヘッダーに「会員登録」のリンクがある画面）を開いているページ
 * @param name 氏名
 * @param profile 生年月日と性別（省けば入れない）
 */
export async function registerMember(
  page: Page,
  account: Account,
  name: { lastName: string; firstName: string },
  profile: MemberProfile = {},
): Promise<void> {
  await submitSignup(page, account, name, profile)
  await setPasswordFromMail(page, account)
  await expect(page.getByRole('button', { name: 'ログアウト' })).toBeVisible()
}

/**
 * 店舗画面のヘッダーの「会員登録」から、IdP の会員登録の画面でメールアドレス・氏名・任意の属性を入れて送る。
 * 確認のメールを送ったと出るまで待つ（パスワードは確認のメールのリンクで決める。{@link setPasswordFromMail}）。
 *
 * @param page 店舗画面（ヘッダーに「会員登録」のリンクがある画面）を開いているページ
 * @param name 氏名
 * @param profile 生年月日と性別（省けば入れない）
 */
export async function submitSignup(
  page: Page,
  account: Account,
  name: { lastName: string; firstName: string },
  profile: MemberProfile = {},
): Promise<void> {
  await page.getByRole('link', { name: '会員登録' }).click()
  await page.locator('#email').fill(account.email)
  await page.locator('#lastName').fill(name.lastName)
  await page.locator('#firstName').fill(name.firstName)
  if (profile.birthdate) {
    await page.locator('#birthdate').fill(profile.birthdate)
  }
  if (profile.gender) {
    await page.locator('#gender').selectOption({ label: profile.gender })
  }
  await page.getByRole('button', { name: '登録' }).click()
  await expect(page.getByText(/メールアドレスを確認する手順を記載したメール/)).toBeVisible()
}

/**
 * 確認のメールのリンクを開き、IdP の画面でパスワードを決めて送る。送った後の画面は確認しない（呼ぶ側が確認する）。
 *
 * @param page 使うページ（登録を始めたのと同じブラウザーなら、ログインした状態で登録を始めた画面に戻る。AC-112）
 */
export async function setPasswordFromMail(page: Page, account: Account): Promise<void> {
  await page.goto(await latestIdpLink(account.email))
  await page.locator('#password-new').fill(account.password)
  await page.locator('#password-confirm').fill(account.password)
  await page.getByRole('button', { name: '送信' }).click()
}

/**
 * IdP のログインの画面の、パスワードの欄（ログインし直すのにパスワードが必要なことを確認する）。
 *
 * @param page IdP のログインの画面を開いているページ
 */
export function idpPasswordInput(page: Page): Locator {
  return page.locator('#password')
}

/**
 * Mailpit に届いた、宛先への最新のメールから、IdP の確認・再設定のリンクを取り出す。届くまで待つ。
 *
 * @param to 宛先のメールアドレス
 */
async function latestIdpLink(to: string): Promise<string> {
  let link: string | undefined
  await expect
    .poll(
      async () => {
        const search = await fetch(`${MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(`to:${to}`)}`)
        const { messages } = (await search.json()) as { messages: { ID: string }[] }
        if (messages.length === 0) {
          return undefined
        }
        const message = await fetch(`${MAILPIT_URL}/api/v1/message/${messages[0].ID}`)
        const { Text } = (await message.json()) as { Text: string }
        link = Text.match(/https?:\/\/\S+\/login-actions\/action-token\S+/)?.[0]
        return link
      },
      { message: `${to} へのメールが Mailpit に届かない`, timeout: MAIL_TIMEOUT },
    )
    .toBeDefined()
  return link as string
}

/**
 * 運営者の access token を、管理画面と同じ認可コード + PKCE の流れで取る（ブラウザーを使わない）。<br />
 * テストのデータを管理 API で直接作るときに使う。web の client はパスワードで token を取る方式を無効にしているため。
 */
export async function operatorAccessToken(account: Account): Promise<string> {
  const redirectUri = `${ADMIN_URL}/`
  const verifier = randomBytes(32).toString('base64url')
  const challenge = createHash('sha256').update(verifier).digest('base64url')
  const cookies = new Map<string, string>()
  const request = async (url: string, init: RequestInit = {}) => {
    const response = await fetch(url, {
      ...init,
      redirect: 'manual',
      headers: { ...init.headers, cookie: [...cookies].map(([name, value]) => `${name}=${value}`).join('; ') },
    })
    for (const cookie of response.headers.getSetCookie()) {
      const [pair] = cookie.split(';')
      const index = pair.indexOf('=')
      cookies.set(pair.slice(0, index), pair.slice(index + 1))
    }
    return response
  }

  const query = new URLSearchParams({
    client_id: 'ec-admin-web',
    redirect_uri: redirectUri,
    response_type: 'code',
    scope: 'openid',
    state: 'e2e',
    code_challenge: challenge,
    code_challenge_method: 'S256',
  })
  const loginPage = await (await request(`${IDP_URL}/realms/ec-admin/protocol/openid-connect/auth?${query}`)).text()
  const action = loginPage.match(/action="([^"]*login-actions\/authenticate[^"]*)"/)?.[1]?.replaceAll('&amp;', '&')
  if (!action) {
    throw new Error('IdP のログインの画面を開けない')
  }
  const login = await request(action, {
    method: 'POST',
    body: new URLSearchParams({ username: account.email, password: account.password }),
  })
  const code = new URL(login.headers.get('location') ?? '', redirectUri).searchParams.get('code')
  if (!code) {
    throw new Error(`${account.email} でログインできない（HTTP ${login.status}）`)
  }
  const token = await fetch(`${IDP_URL}/realms/ec-admin/protocol/openid-connect/token`, {
    method: 'POST',
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: 'ec-admin-web',
      code,
      redirect_uri: redirectUri,
      code_verifier: verifier,
    }),
  })
  if (!token.ok) {
    throw new Error(`token を取れない（HTTP ${token.status}）：${await token.text()}`)
  }
  return ((await token.json()) as { access_token: string }).access_token
}
