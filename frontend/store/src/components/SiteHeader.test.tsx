import { describe, expect, test, vi } from 'vitest'
import { ACCOUNT_URL } from '@/auth'
import { SiteHeader } from '@/components/SiteHeader'
import { signInAs } from '@/test/auth'
import { renderMarkup } from '@/test/render'

vi.mock('@/actions/auth', () => ({ logout: async () => {} }))
// 「ログイン」「会員登録」は現在の画面の位置を読むため、ここでは置き換える（LoginLinks.test.tsx で確認する）
vi.mock('@/components/LoginLinks', () => ({ LoginLinks: () => null }))

/** ヘッダーを HTML にする（サーバーコンポーネントなので、関数として呼んでから描画する） */
async function renderHeader() {
  return renderMarkup(await SiteHeader())
}

describe('ヘッダーのログインの状態', () => {
  test('AC-119 ログインしている会員には、IdP のアカウントの画面へのリンクが出る', async () => {
    signInAs()

    const html = await renderHeader()

    expect(html).toContain(`href="${ACCOUNT_URL}">アカウント</a>`)
    expect(html).toContain('山田 太郎 様')
    expect(html).toContain('href="/orders">ご注文履歴</a>')
    expect(html).toContain('>ログアウト</button>')
  })

  test('ログインしていなければ、アカウントの画面へのリンク、ご注文履歴、ログアウトの操作は出ない', async () => {
    const html = await renderHeader()

    expect(html).not.toContain(ACCOUNT_URL)
    expect(html).not.toContain('アカウント')
    expect(html).not.toContain('ご注文履歴')
    expect(html).not.toContain('ログアウト')
  })
})
