import { describe, expect, test } from 'vitest'
import LoginFailedPage from '@/app/login/failed/page'
import { authState } from '@/test/auth'
import { resetRequest } from '@/test/next'
import { renderStatic } from '@/test/render'
import messages from '../../../../messages/ja.json'

const text = messages.app.loginFailed

/** ログインしていない状態で、ログインできなかった画面を DOM に読み込む */
async function renderLoginFailedPage() {
  resetRequest()
  authState.session = null
  authState.token = null
  return renderStatic(await LoginFailedPage())
}

describe('ログインできなかった画面の表示', () => {
  test('AC-430-2 ログインしていなくても、ログインできなかったことを見出しで示す', async () => {
    const view = await renderLoginFailedPage()

    expect(view.getByRole('heading', { level: 1, name: text.title })).toBeInTheDocument()
  })

  test('AC-430-2 時間をおいてもう一度ログインする案内を示す', async () => {
    const view = await renderLoginFailedPage()

    expect(view.getByText(text.message)).toBeInTheDocument()
  })

  test('AC-430-2 ログインの操作へのリンクとトップへのリンクがある', async () => {
    const view = await renderLoginFailedPage()

    expect(view.getByRole('link', { name: text.login })).toHaveAttribute('href', '/login')
    expect(view.getByRole('link', { name: text.top })).toHaveAttribute('href', '/')
  })
})
