import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StrictMode } from 'react'
import type { AuthContextProps } from 'react-oidc-context'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthGate } from '@/auth/AuthGate'
import { useOperator } from '@/auth/operator'
import { currentAccessToken, handleUnauthorized } from '@/auth/token'
import { I18nProvider } from '@/i18n/I18nProvider'

/** 差し替えた useAuth が返す値（テストごとに再作成する） */
let auth: Partial<AuthContextProps>

vi.mock('react-oidc-context', () => ({
  useAuth: () => auth,
  hasAuthParams: () => false,
}))

/** ログインした運営者（IdP の token の中身のうち、門が使うものだけ） */
const USER = {
  access_token: 'access-token-1',
  id_token: 'id-token-1',
  profile: { family_name: '管理', given_name: '太郎' },
} as unknown as AuthContextProps['user']

/** ログインしていない状態 */
function signedOut(): Partial<AuthContextProps> {
  return {
    isLoading: false,
    isAuthenticated: false,
    activeNavigator: undefined,
    error: undefined,
    user: null,
    signinRedirect: vi.fn(async () => {}),
    signoutRedirect: vi.fn(async () => {}),
  }
}

/** ログインした状態 */
function signedIn(): Partial<AuthContextProps> {
  return { ...signedOut(), isAuthenticated: true, user: USER }
}

/** 門の中に置く画面。運営者の名前とログアウトを出す */
function Screen() {
  const operator = useOperator()
  return (
    <>
      <p>画面：{operator.name}</p>
      <button onClick={operator.logout} type="button">
        ログアウト
      </button>
    </>
  )
}

function renderGate() {
  return render(
    <StrictMode>
      <I18nProvider>
        <AuthGate>
          <Screen />
        </AuthGate>
      </I18nProvider>
    </StrictMode>,
  )
}

describe('ログインの門', () => {
  beforeEach(() => {
    window.history.replaceState(null, '', '/orders?status=ACCEPTED')
  })

  afterEach(() => {
    sessionStorage.clear()
    window.history.replaceState(null, '', '/')
  })

  it('AC-132 ログインしていなければ、画面を出さず、今の画面を戻り先にして IdP へ移る', () => {
    auth = signedOut()

    renderGate()

    expect(screen.queryByText(/画面：/)).not.toBeInTheDocument()
    expect(auth.signinRedirect).toHaveBeenCalledWith({ state: { returnTo: '/orders?status=ACCEPTED' } })
  })

  it('effect が 2 回動いても（StrictMode）、IdP へ移るのは 1 回', () => {
    auth = signedOut()

    renderGate()

    expect(auth.signinRedirect).toHaveBeenCalledTimes(1)
  })

  it('ログインの途中（読み込み中・IdP とのやり取り中）は、IdP へ移らない', () => {
    const loading = { ...signedOut(), isLoading: true }
    auth = loading
    renderGate()
    const navigating = { ...signedOut(), activeNavigator: 'signinRedirect' as const }
    auth = navigating
    renderGate()

    expect(loading.signinRedirect).not.toHaveBeenCalled()
    expect(navigating.signinRedirect).not.toHaveBeenCalled()
  })

  it('ログインしていれば、画面と運営者の名前を出し、API に token を渡す。ログアウトは IdP に ID token を渡す', async () => {
    auth = signedIn()
    const user = userEvent.setup()

    renderGate()

    expect(screen.getByText('画面：管理 太郎')).toBeInTheDocument()
    expect(currentAccessToken()).toBe('access-token-1')
    expect(auth.signinRedirect).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'ログアウト' }))
    expect(auth.signoutRedirect).toHaveBeenCalledWith({ id_token_hint: 'id-token-1' })
  })

  it('画面を閉じると、API に token を渡さなくなる', () => {
    auth = signedIn()

    renderGate().unmount()

    expect(currentAccessToken()).toBeUndefined()
  })

  it('API が 401 を返すと、今の画面を戻り先にしてログインし直し、その直後の 401 では繰り返さない', () => {
    auth = signedIn()
    renderGate()

    handleUnauthorized()
    handleUnauthorized()

    expect(auth.signinRedirect).toHaveBeenCalledTimes(1)
    expect(auth.signinRedirect).toHaveBeenCalledWith({ state: { returnTo: '/orders?status=ACCEPTED' } })
  })

  it('ログインに失敗すると、理由を出し、ログインし直すとトップに戻る', async () => {
    auth = { ...signedOut(), error: new Error('IdP に届かない') as AuthContextProps['error'] }
    const user = userEvent.setup()
    renderGate()

    expect(screen.getByText('ログインできませんでした。（IdP に届かない）')).toBeInTheDocument()
    expect(auth.signinRedirect).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'ログインし直す' }))
    expect(auth.signinRedirect).toHaveBeenCalledWith({ state: { returnTo: '/' } })
  })
})
