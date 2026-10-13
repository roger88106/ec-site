import { beforeEach, describe, expect, test, vi } from 'vitest'
import { SiteHeader } from '@/components/SiteHeader'
import { renderMarkup } from '@/test/render'

/** IdP のアカウントの画面（テストの値） */
const ACCOUNT_URL = 'http://idp.test/realms/ec-store/account'

const currentMember = vi.fn()

vi.mock('@/auth', () => ({
  ACCOUNT_URL: 'http://idp.test/realms/ec-store/account',
  currentMember: () => currentMember(),
}))
vi.mock('@/actions/auth', () => ({ logout: async () => {} }))
vi.mock('@/components/LoginLinks', () => ({ LoginLinks: () => <a href="/login">ログイン</a> }))

/** ヘッダーを HTML にする（サーバーコンポーネントなので、関数として呼んでから描画する） */
async function renderHeader() {
  return renderMarkup(await SiteHeader())
}

describe('ヘッダーのログインの状態', () => {
  beforeEach(() => {
    currentMember.mockReset()
  })

  test('AC-119 ログインしている会員には、IdP のアカウントの画面へのリンクが出る', async () => {
    currentMember.mockResolvedValue({ name: '山田 太郎' })

    const html = await renderHeader()

    expect(html).toContain(`<a class="hover:underline" href="${ACCOUNT_URL}">アカウント</a>`)
    expect(html).toContain('山田 太郎 様')
  })

  test('ログインしていなければ、アカウントの画面へのリンクは出ない', async () => {
    currentMember.mockResolvedValue(null)

    const html = await renderHeader()

    expect(html).not.toContain('アカウント')
    expect(html).toContain('ログイン')
  })
})
