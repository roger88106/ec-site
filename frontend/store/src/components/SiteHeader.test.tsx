import { describe, expect, test, vi } from 'vitest'
import { ACCOUNT_URL } from '@/auth'
import { SiteHeader } from '@/components/SiteHeader'
import { signInAs } from '@/test/auth'
import { renderStatic } from '@/test/render'

vi.mock('@/actions/auth', () => ({ logout: async () => {} }))
// 「ログイン」「会員登録」は現在の画面の位置を読むため、ここでは置き換える（LoginLinks.test.tsx で確認する）
vi.mock('@/components/LoginLinks', () => ({ LoginLinks: () => null }))

/** ヘッダーを DOM に読み込む（サーバーコンポーネントなので、関数として呼んでから描画する） */
async function renderHeader() {
  return renderStatic(await SiteHeader())
}

describe('ヘッダーのログインの状態', () => {
  test('AC-119 ログインしている会員には、IdP のアカウントの画面へのリンクが出る', async () => {
    signInAs()

    const view = await renderHeader()

    expect(view.getByRole('link', { name: 'アカウント' })).toHaveAttribute('href', ACCOUNT_URL)
    expect(view.getByText('山田 太郎 様')).toBeInTheDocument()
    expect(view.getByRole('link', { name: 'ご注文履歴' })).toHaveAttribute('href', '/orders')
    expect(view.getByRole('button', { name: 'ログアウト' })).toBeInTheDocument()
  })

  test('ログインしていなければ、アカウントの画面へのリンク、ご注文履歴、ログアウトの操作は出ない', async () => {
    const view = await renderHeader()

    for (const link of view.getAllByRole('link')) {
      expect(link).not.toHaveAttribute('href', ACCOUNT_URL)
    }
    expect(view.queryByRole('link', { name: 'アカウント' })).toBeNull()
    expect(view.queryByRole('link', { name: 'ご注文履歴' })).toBeNull()
    expect(view.queryByRole('button', { name: 'ログアウト' })).toBeNull()
    expect(view.queryByText('アカウント')).toBeNull()
    expect(view.queryByText('ご注文履歴')).toBeNull()
    expect(view.queryByText('ログアウト')).toBeNull()
  })
})
