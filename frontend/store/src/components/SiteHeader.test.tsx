// @vitest-environment jsdom
import { screen } from '@testing-library/react'
import { describe, expect, test, vi } from 'vitest'
import { ACCOUNT_URL } from '@/auth'
import { SiteHeader } from '@/components/SiteHeader'
import { signInAs } from '@/test/auth'
import { renderInBrowser } from '@/test/render'

vi.mock('@/actions/auth', () => ({ logout: async () => {} }))
// 「ログイン」「会員登録」は現在の画面の位置を読むため、ここでは置き換える（LoginLinks.test.tsx で確認する）
vi.mock('@/components/LoginLinks', () => ({ LoginLinks: () => <a href="/login">ログイン</a> }))

/** ヘッダーを描画する（サーバーコンポーネントなので、関数として呼んでから描画する） */
async function renderHeader() {
  renderInBrowser(await SiteHeader())
}

describe('ヘッダーのログインの状態', () => {
  test('AC-119 ログインしている会員には、IdP のアカウントの画面へのリンクが出る', async () => {
    signInAs()

    await renderHeader()

    expect(screen.getByRole('link', { name: 'アカウント' }).getAttribute('href')).toBe(ACCOUNT_URL)
    expect(screen.getByText('山田 太郎 様')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'ログアウト' })).toBeTruthy()
    expect(screen.queryByRole('link', { name: 'ログイン' })).toBeNull()
  })

  test('ログインしていなければ、アカウントの画面へのリンクとログアウトの操作は出ず、ログインのリンクが出る', async () => {
    await renderHeader()

    expect(screen.queryByRole('link', { name: 'アカウント' })).toBeNull()
    expect(screen.queryByRole('link', { name: 'ご注文履歴' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'ログアウト' })).toBeNull()
    expect(screen.getByRole('link', { name: 'ログイン' })).toBeTruthy()
  })
})
