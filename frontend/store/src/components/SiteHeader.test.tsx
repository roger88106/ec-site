// @vitest-environment jsdom
import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import { SiteHeader } from '@/components/SiteHeader'
import { renderInBrowser } from '@/test/render'

/** IdP のアカウントの画面（テストの値） */
const ACCOUNT_URL = 'http://idp.test/realms/ec-store/account'

const currentMember = vi.fn()

vi.mock('@/auth', () => ({
  ACCOUNT_URL: 'http://idp.test/realms/ec-store/account',
  currentMember: () => currentMember(),
}))
vi.mock('@/actions/auth', () => ({ logout: async () => {} }))
vi.mock('@/components/LoginLinks', () => ({ LoginLinks: () => <a href="/login">ログイン</a> }))

/** ヘッダーを描画する（サーバーコンポーネントなので、関数として呼んでから描画する） */
async function renderHeader() {
  renderInBrowser(await SiteHeader())
}

describe('ヘッダーのログインの状態', () => {
  beforeEach(() => {
    currentMember.mockReset()
  })

  test('AC-119 ログインしている会員には、IdP のアカウントの画面へのリンクが出る', async () => {
    currentMember.mockResolvedValue({ name: '山田 太郎' })

    await renderHeader()

    expect(screen.getByRole('link', { name: 'アカウント' }).getAttribute('href')).toBe(ACCOUNT_URL)
    expect(screen.getByText('山田 太郎 様')).toBeDefined()
  })

  test('ログインしていなければ、アカウントの画面へのリンクは出ない', async () => {
    currentMember.mockResolvedValue(null)

    await renderHeader()

    expect(screen.queryByRole('link', { name: 'アカウント' })).toBeNull()
    expect(screen.getByRole('link', { name: 'ログイン' })).toBeDefined()
  })
})
