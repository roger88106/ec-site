import { expect, type Page, test } from '@playwright/test'
import { ACCOUNTS, submitIdpLogin } from '../src/auth'
import { STORE_URL } from '../src/urls'

// このファイルは、幅 375px の project（narrow）だけで実行する（playwright.config.ts）

/** ヘッダーの中の、メニューを開くボタン */
function menuButton(page: Page) {
  return page.getByRole('banner').getByRole('button', { name: 'メニュー', exact: true })
}

/** ヘッダーの中の、リンクかボタン以外の項目を探す起点 */
function header(page: Page) {
  return page.getByRole('banner')
}

/** ページ全体の幅が、画面の幅に収まっている（横スクロールが出ない）ことを確認する */
async function expectNoHorizontalScroll(page: Page) {
  const fits = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
  expect(fits).toBe(true)
}

/**
 * 会員としてログインする。狭い幅ではログアウトのボタンがメニューの中にあり、`loginStore` は使えないので、
 * ログインの後にメニューのボタンが出るのを待つ。
 */
async function loginNarrow(page: Page) {
  await page.goto(`${STORE_URL}/login?returnTo=${encodeURIComponent('/')}`)
  await submitIdpLogin(page, ACCOUNTS.member)
  await expect(menuButton(page)).toBeVisible()
}

test('AC-417-1、AC-417-3 ログインしていない消費者がトップと商品一覧を開いても、横スクロールが出ない。メニューを開くと「ログイン」「会員登録」が出る', async ({
  page,
}) => {
  for (const path of ['/', '/products']) {
    await test.step(`${path} を開くと、横スクロールが出ない`, async () => {
      await page.goto(`${STORE_URL}${path}`)
      await expect(menuButton(page)).toBeVisible()
      await expectNoHorizontalScroll(page)
    })
  }

  await test.step('メニューを開く前は、ログインの項目が見えない。開くと、商品一覧、ログイン、会員登録が見える', async () => {
    await expect(menuButton(page)).toHaveAttribute('aria-expanded', 'false')
    await expect(header(page).getByRole('link', { name: 'ログイン' })).toBeHidden()
    await menuButton(page).click()
    await expect(menuButton(page)).toHaveAttribute('aria-expanded', 'true')
    await expect(header(page).getByRole('link', { name: '商品一覧' })).toBeVisible()
    await expect(header(page).getByRole('link', { name: 'ログイン' })).toBeVisible()
    await expect(header(page).getByRole('link', { name: '会員登録' })).toBeVisible()
    await expectNoHorizontalScroll(page)
  })
})

test('AC-417-2、AC-417-3、AC-417-5 ログインした会員が開いても横スクロールが出ない。メニューに氏名と項目が出て、「ご注文履歴」で画面を移るとメニューが閉じる', async ({
  page,
}) => {
  await loginNarrow(page)

  await test.step('トップと商品一覧を開いても、横スクロールが出ない', async () => {
    for (const path of ['/', '/products']) {
      await page.goto(`${STORE_URL}${path}`)
      await expect(menuButton(page)).toBeVisible()
      await expectNoHorizontalScroll(page)
    }
  })

  await test.step('メニューを開くと、氏名と、ご注文履歴・アカウント・ログアウトが見える', async () => {
    await menuButton(page).click()
    await expect(header(page).getByText('会員 太郎 様')).toBeVisible()
    await expect(header(page).getByRole('link', { name: '商品一覧' })).toBeVisible()
    await expect(header(page).getByRole('link', { name: 'ご注文履歴' })).toBeVisible()
    await expect(header(page).getByRole('link', { name: 'アカウント' })).toBeVisible()
    await expect(header(page).getByRole('button', { name: 'ログアウト', exact: true })).toBeVisible()
    await expectNoHorizontalScroll(page)
  })

  await test.step('「ご注文履歴」を押すと注文履歴の画面に移り、メニューが閉じる', async () => {
    await header(page).getByRole('link', { name: 'ご注文履歴' }).click()
    await expect(page).toHaveURL(`${STORE_URL}/orders`)
    await expect(page.getByRole('heading', { name: 'ご注文履歴' })).toBeVisible()
    await expect(menuButton(page)).toHaveAttribute('aria-expanded', 'false')
    await expect(header(page).getByRole('link', { name: 'ご注文履歴' })).toBeHidden()
    await expectNoHorizontalScroll(page)
  })
})

test('AC-417-4 メニューを開かずに、検索欄で検索して結果の画面に移れ、カートのリンクが使える', async ({ page }) => {
  await page.goto(STORE_URL)

  await test.step('検索欄とカートのリンクが、メニューを閉じたまま見える', async () => {
    await expect(menuButton(page)).toHaveAttribute('aria-expanded', 'false')
    await expect(header(page).getByRole('textbox', { name: 'キーワード' })).toBeVisible()
    await expect(header(page).getByRole('link', { name: 'カート' })).toBeVisible()
  })

  await test.step('検索すると、検索の結果の画面に移る', async () => {
    await header(page).getByRole('textbox', { name: 'キーワード' }).fill('シャツ')
    await header(page).getByRole('textbox', { name: 'キーワード' }).press('Enter')
    await expect(page).toHaveURL(`${STORE_URL}/search?q=${encodeURIComponent('シャツ')}`)
    await expect(page.getByRole('heading', { name: '検索結果' })).toBeVisible()
  })

  await test.step('カートのリンクを押すと、カートの画面に移る', async () => {
    await header(page).getByRole('link', { name: 'カート' }).click()
    await expect(page).toHaveURL(`${STORE_URL}/cart`)
    await expect(page.getByRole('heading', { name: 'カート', exact: true })).toBeVisible()
  })
})

test('AC-417-6 幅 1280px では、メニューのボタンが出ず、商品一覧とご注文履歴がメニューを開かずに見える', async ({
  page,
}) => {
  await loginNarrow(page)

  await test.step('幅 375px では、メニューを開くボタンが見え、商品一覧が見えない', async () => {
    await expect(menuButton(page)).toBeVisible()
    await expect(header(page).getByRole('link', { name: '商品一覧' })).toBeHidden()
  })

  await test.step('幅 1280px にすると、メニューのボタンが見えず、項目が 1 行に見える', async () => {
    await page.setViewportSize({ width: 1280, height: 800 })
    await expect(menuButton(page)).toBeHidden()
    await expect(header(page).getByRole('link', { name: '商品一覧' })).toBeVisible()
    await expect(header(page).getByRole('link', { name: 'ご注文履歴' })).toBeVisible()
    await expect(header(page).getByRole('button', { name: 'ログアウト', exact: true })).toBeVisible()
  })

  await test.step('ログアウトすると、ログインしていないときも、ボタンが見えず「ログイン」が見える', async () => {
    await header(page).getByRole('button', { name: 'ログアウト', exact: true }).click()
    await expect(header(page).getByRole('link', { name: 'ログイン' })).toBeVisible()
    await expect(menuButton(page)).toBeHidden()
    await expect(header(page).getByRole('link', { name: '商品一覧' })).toBeVisible()
  })
})
