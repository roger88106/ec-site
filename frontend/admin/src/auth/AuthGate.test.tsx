import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { User } from 'oidc-client-ts'
import { StrictMode } from 'react'
import type { AuthContextProps } from 'react-oidc-context'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { AuthGate } from '@/auth/AuthGate'
import { LAST_ACTIVITY_KEY } from '@/auth/idle'
import { useOperator } from '@/auth/operator'
import { LOGOUT_KEY } from '@/auth/sharedLogout'
import { currentAccessToken, handleUnauthorized } from '@/auth/token'
import { I18nProvider } from '@/i18n/I18nProvider'
import { advanceClock, comeBackToTab, MINUTE, otherTabWrites, START, startClock } from '@/test/idle'

/** 差し替えた useAuth が返す値（テストごとに再作成する） */
let auth: Partial<AuthContextProps>

vi.mock('react-oidc-context', () => ({
  useAuth: () => auth,
  hasAuthParams: () => false,
}))

/**
 * ログインした運営者（IdP の token の中身。門が使わない必須の項目は、固定の値で埋める）を作る。
 *
 * @param loggedInAt IdP でパスワードを入れた時刻（ミリ秒。秒にして ID token の auth_time に置く）。省くと auth_time を置かない
 * @returns 運営者
 */
function operatorUser(loggedInAt?: number): User {
  return new User({
    access_token: 'access-token-1',
    id_token: 'id-token-1',
    token_type: 'Bearer',
    profile: {
      iss: 'http://idp.test',
      sub: 'operator-1',
      aud: 'admin',
      exp: 4102444800,
      iat: 1767225600,
      ...(loggedInAt === undefined ? {} : { auth_time: Math.floor(loggedInAt / 1000) }),
      family_name: '管理',
      given_name: '太郎',
    },
  })
}

/** ログインした運営者（ID token に auth_time がない） */
const USER = operatorUser()

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

/**
 * IdP でパスワードを入れてログインした状態（ID token に auth_time がある）
 *
 * @param loggedInAt パスワードを入れた時刻（ミリ秒）
 * @returns ログインした状態
 */
function signedInSince(loggedInAt: number): Partial<AuthContextProps> {
  return { ...signedOut(), isAuthenticated: true, user: operatorUser(loggedInAt) }
}

/** ログインした状態。IdP のログアウトへ移り始めるのに、1 回目だけ失敗する */
function signedInFailingSignoutOnce(): Partial<AuthContextProps> {
  return {
    ...signedIn(),
    signoutRedirect: vi.fn(async () => {}).mockRejectedValueOnce(new Error('IdP に届かない')),
  }
}

/** IdP のログアウトの失敗（reject）を、門が受け取るまで待つ */
async function settleSignout(): Promise<void> {
  await act(async () => {})
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

  test('AC-132 ログインしていなければ、画面を出さず、今の画面を戻り先にして IdP へ移る', () => {
    auth = signedOut()

    renderGate()

    expect(screen.queryByText(/画面：/)).not.toBeInTheDocument()
    expect(auth.signinRedirect).toHaveBeenCalledWith({ state: { returnTo: '/orders?status=ACCEPTED' } })
  })

  test('effect が 2 回動いても（StrictMode）、IdP へ移るのは 1 回', () => {
    auth = signedOut()

    renderGate()

    expect(auth.signinRedirect).toHaveBeenCalledTimes(1)
  })

  test('ログインの途中（読み込み中・IdP とのやり取り中）は、IdP へ移らない', () => {
    const loading = { ...signedOut(), isLoading: true }
    auth = loading
    renderGate()
    const navigating = { ...signedOut(), activeNavigator: 'signinRedirect' as const }
    auth = navigating
    renderGate()

    expect(loading.signinRedirect).not.toHaveBeenCalled()
    expect(navigating.signinRedirect).not.toHaveBeenCalled()
  })

  test('ログインしていれば、画面と運営者の名前を出し、API に token を渡す。ログアウトは IdP に ID token を渡す', async () => {
    auth = signedIn()
    const user = userEvent.setup()

    renderGate()

    expect(screen.getByText('画面：管理 太郎')).toBeInTheDocument()
    expect(currentAccessToken()).toBe('access-token-1')
    expect(auth.signinRedirect).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'ログアウト' }))
    expect(auth.signoutRedirect).toHaveBeenCalledWith({ id_token_hint: 'id-token-1' })
  })

  test('画面を閉じると、API に token を渡さなくなる', () => {
    auth = signedIn()

    renderGate().unmount()

    expect(currentAccessToken()).toBeUndefined()
  })

  test('API が 401 を返すと、今の画面を戻り先にしてログインし直し、その直後の 401 では繰り返さない', () => {
    auth = signedIn()
    renderGate()

    handleUnauthorized()
    handleUnauthorized()

    expect(auth.signinRedirect).toHaveBeenCalledTimes(1)
    expect(auth.signinRedirect).toHaveBeenCalledWith({ state: { returnTo: '/orders?status=ACCEPTED' } })
  })

  test('ログインに失敗すると、理由を出し、ログインし直すとトップに戻る', async () => {
    const error = Object.assign(new Error('IdP に届かない'), { source: 'signinCallback' as const })
    auth = { ...signedOut(), error }
    const user = userEvent.setup()
    renderGate()

    expect(screen.getByText('ログインできませんでした。（IdP に届かない）')).toBeInTheDocument()
    expect(auth.signinRedirect).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'ログインし直す' }))
    expect(auth.signinRedirect).toHaveBeenCalledWith({ state: { returnTo: '/' } })
  })
})

describe('ログインの門の、無操作のログアウト', () => {
  beforeEach(() => {
    localStorage.clear()
    startClock()
  })

  afterEach(() => {
    localStorage.clear()
    sessionStorage.clear()
  })

  test('AC-413-1 ログインしていて、操作しないまま 30 分たつと、IdP のログアウトへ移る', () => {
    auth = signedIn()
    renderGate()

    advanceClock(30 * MINUTE - 1)

    expect(auth.signoutRedirect).not.toHaveBeenCalled()

    advanceClock(1)

    expect(auth.signoutRedirect).toHaveBeenCalledTimes(1)
    expect(auth.signoutRedirect).toHaveBeenCalledWith({ id_token_hint: 'id-token-1' })
  })

  test('AC-413-7 28 分の直前までは警告を出さず、28 分でログアウトする時刻と「操作を続ける」をダイアログに出す', () => {
    auth = signedIn()
    renderGate()

    advanceClock(28 * MINUTE - 1)

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()

    advanceClock(1)

    const dialog = screen.getByRole('alertdialog')
    expect(within(dialog).getByText(/2026\/01\/01 09:30/)).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: '操作を続ける' })).toBeInTheDocument()
  })

  test('AC-413-8 「操作を続ける」を押すと、警告を閉じ、そこから 30 分たつまでログアウトしない', async () => {
    // Testing Library は操作の後に setTimeout で待ち、vitest の偽の時計を進めないので、偽の時計を実際の時間でも進める
    startClock({ shouldAdvanceTime: true })
    auth = signedIn()
    const user = userEvent.setup({ advanceTimers: (ms) => vi.advanceTimersByTime(ms) })
    renderGate()
    advanceClock(28 * MINUTE)

    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: '操作を続ける' }))

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()

    advanceClock(30 * MINUTE - 1000)

    expect(auth.signoutRedirect).not.toHaveBeenCalled()

    advanceClock(1000)

    expect(auth.signoutRedirect).toHaveBeenCalledTimes(1)
  })

  test('AC-413-8 警告の間に Esc を押すと、警告を閉じる', async () => {
    // 「操作を続ける」のテストと同じく、偽の時計を実際の時間でも進める
    startClock({ shouldAdvanceTime: true })
    auth = signedIn()
    const user = userEvent.setup({ advanceTimers: (ms) => vi.advanceTimersByTime(ms) })
    renderGate()
    advanceClock(28 * MINUTE)
    expect(screen.getByRole('alertdialog')).toBeInTheDocument()

    await user.keyboard('{Escape}')

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  test('ログインしていない間は、30 分たっても警告もログアウトもしない', () => {
    auth = signedOut()
    renderGate()

    advanceClock(30 * MINUTE)

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(auth.signoutRedirect).not.toHaveBeenCalled()
  })
})

/** ローカルストレージの中身（キーと値） */
function storageEntries(): Record<string, string | null> {
  const entries: Record<string, string | null> = {}
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    if (key !== null) {
      entries[key] = localStorage.getItem(key)
    }
  }
  return entries
}

/** ログインした状態。IdP のログアウトへ移るたびに、そのときのローカルストレージの中身を atSignout に残す */
function signedInRecordingStorage() {
  const atSignout: Record<string, string | null>[] = []
  const state: Partial<AuthContextProps> = {
    ...signedIn(),
    signoutRedirect: vi.fn(async () => {
      atSignout.push(storageEntries())
    }),
  }
  return { state, atSignout }
}

describe('ログインの門の、タブの間のログアウト', () => {
  beforeEach(() => {
    localStorage.clear()
    startClock()
  })

  afterEach(() => {
    localStorage.clear()
    sessionStorage.clear()
  })

  test('AC-413-9 ログアウトの印は、ec-admin. で始まるキーに書く', () => {
    expect(LOGOUT_KEY.startsWith('ec-admin.')).toBe(true)
  })

  test('AC-413-9 手動でログアウトすると、IdP のログアウトへ移る前に、ログアウトの印（時刻だけ）を書き、最後に操作した時刻を削除する', async () => {
    // 「操作を続ける」のテストと同じく、偽の時計を実際の時間でも進める
    startClock({ shouldAdvanceTime: true })
    const { state, atSignout } = signedInRecordingStorage()
    auth = state
    const user = userEvent.setup({ advanceTimers: (ms) => vi.advanceTimersByTime(ms) })
    renderGate()
    expect(localStorage.getItem(LAST_ACTIVITY_KEY)).not.toBeNull()

    await user.click(screen.getByRole('button', { name: 'ログアウト' }))

    expect(auth.signoutRedirect).toHaveBeenCalledTimes(1)
    expect(auth.signoutRedirect).toHaveBeenCalledWith({ id_token_hint: 'id-token-1' })
    expect(atSignout).toEqual([{ [LOGOUT_KEY]: expect.stringMatching(/^\d+$/) }])
  })

  test('AC-413-9 操作しないまま 30 分たってログアウトするときも、IdP のログアウトへ移る前に、印を書き、最後に操作した時刻を削除する', () => {
    const { state, atSignout } = signedInRecordingStorage()
    auth = state
    renderGate()

    advanceClock(30 * MINUTE)

    expect(auth.signoutRedirect).toHaveBeenCalledTimes(1)
    expect(atSignout).toEqual([{ [LOGOUT_KEY]: String(START + 30 * MINUTE) }])
  })

  test('AC-413-9 他のタブがログアウトの印を書くと、このタブも IdP のログアウトへ 1 回だけ移る', () => {
    auth = signedIn()
    renderGate()

    otherTabWrites(LOGOUT_KEY, String(START + MINUTE))

    expect(auth.signoutRedirect).toHaveBeenCalledTimes(1)
    expect(auth.signoutRedirect).toHaveBeenCalledWith({ id_token_hint: 'id-token-1' })

    // 2 つ目の印と、無操作の 30 分では、もう移らない
    otherTabWrites(LOGOUT_KEY, String(START + 2 * MINUTE))
    advanceClock(30 * MINUTE)

    expect(auth.signoutRedirect).toHaveBeenCalledTimes(1)
  })

  test('AC-413-9 他のタブの印を受けたタブも、IdP のログアウトへ移る前に、最後に操作した時刻を削除する', () => {
    const { state, atSignout } = signedInRecordingStorage()
    auth = state
    renderGate()
    localStorage.setItem(LAST_ACTIVITY_KEY, String(START + MINUTE))

    otherTabWrites(LOGOUT_KEY, String(START + MINUTE))

    expect(atSignout).toEqual([{ [LOGOUT_KEY]: String(START + MINUTE) }])
  })

  test('AC-413-9 関係のないキーの書き込みや、印の削除では、IdP のログアウトへ移らない', () => {
    auth = signedIn()
    renderGate()

    otherTabWrites('ec-admin.other', String(START))
    otherTabWrites(LAST_ACTIVITY_KEY, String(START))
    otherTabWrites(LOGOUT_KEY, null)

    expect(auth.signoutRedirect).not.toHaveBeenCalled()
  })

  test('AC-413-9 ログインしていないタブは、他のタブの印を受けても何もしない', () => {
    auth = signedOut()
    renderGate()

    otherTabWrites(LOGOUT_KEY, String(START))

    expect(auth.signoutRedirect).not.toHaveBeenCalled()
  })

  test('AC-413-9 ログアウトしてからログインし直すと、前のログインの時刻や印で早くログアウトせず、ログインから 30 分でログアウトする', async () => {
    // 「操作を続ける」のテストと同じく、偽の時計を実際の時間でも進める
    startClock({ shouldAdvanceTime: true })
    auth = signedIn()
    const user = userEvent.setup({ advanceTimers: (ms) => vi.advanceTimersByTime(ms) })
    const before = renderGate()
    advanceClock(20 * MINUTE)
    await user.click(screen.getByRole('button', { name: 'ログアウト' }))
    before.unmount()
    advanceClock(MINUTE)

    auth = signedIn()
    renderGate()
    advanceClock(30 * MINUTE - 1000)

    expect(auth.signoutRedirect).not.toHaveBeenCalled()

    advanceClock(1000)

    expect(auth.signoutRedirect).toHaveBeenCalledTimes(1)
  })

  test.each(['pageshow', 'visibilitychange', 'focus'] as const)(
    'AC-413-9 storage イベントが届かなかったタブ（戻る・進むのキャッシュの中にあった）も、%s のときに、ログインより後の印があれば IdP のログアウトへ 1 回だけ移る',
    (type) => {
      auth = signedInSince(START)
      renderGate()
      // 止まっていたタブには storage イベントが届かないので、印を書くだけにする
      localStorage.setItem(LOGOUT_KEY, String(START + MINUTE))

      expect(auth.signoutRedirect).not.toHaveBeenCalled()

      comeBackToTab(type)
      comeBackToTab(type)

      expect(auth.signoutRedirect).toHaveBeenCalledTimes(1)
      expect(auth.signoutRedirect).toHaveBeenCalledWith({ id_token_hint: 'id-token-1' })
    },
  )

  test('AC-413-9 印が書かれたときに読み込み中だったタブは、残っている IdP のセッションで前のログインのまま戻ると、IdP のログアウトへ移る', () => {
    localStorage.setItem(LOGOUT_KEY, String(START + MINUTE))
    vi.setSystemTime(START + 2 * MINUTE)
    // 前のログインは、印より時計のずれの幅（5 分）を超えて前
    auth = signedInSince(START - 10 * MINUTE)

    renderGate()

    expect(auth.signoutRedirect).toHaveBeenCalledTimes(1)
  })

  test('AC-413-9 印より後にパスワードでログインし直したタブは、印があってもログアウトしない', () => {
    localStorage.setItem(LOGOUT_KEY, String(START + MINUTE))
    vi.setSystemTime(START + 2 * MINUTE)
    auth = signedInSince(START + 2 * MINUTE)

    renderGate()
    comeBackToTab('pageshow')
    comeBackToTab('visibilitychange')
    comeBackToTab('focus')

    expect(auth.signoutRedirect).not.toHaveBeenCalled()
  })

  test('AC-413-9 ブラウザーの時計が IdP より 3 分進んでいても、印より後にパスワードでログインし直したタブはログアウトしない', () => {
    // ブラウザーの時計で、印の 1 分後にログインし直す。IdP の時計の auth_time は、ずれの分だけ印より前になる
    localStorage.setItem(LOGOUT_KEY, String(START + MINUTE))
    vi.setSystemTime(START + 2 * MINUTE)
    auth = signedInSince(START + 2 * MINUTE - 3 * MINUTE)

    renderGate()
    comeBackToTab('pageshow')
    comeBackToTab('visibilitychange')
    comeBackToTab('focus')

    expect(auth.signoutRedirect).not.toHaveBeenCalled()
  })

  test('AC-413-9 ID token に auth_time がないときは、ログインしたタブで画面を出した時刻より後の印でだけ、IdP のログアウトへ移る', () => {
    localStorage.setItem(LOGOUT_KEY, String(START - MINUTE))
    auth = signedIn()
    renderGate()
    comeBackToTab('focus')

    expect(auth.signoutRedirect).not.toHaveBeenCalled()

    localStorage.setItem(LOGOUT_KEY, String(START + MINUTE))
    comeBackToTab('focus')

    expect(auth.signoutRedirect).toHaveBeenCalledTimes(1)
  })

  test('AC-413-9 手動のログアウトで IdP へ移り始めるのに失敗すると、画面に留まり、もう一度ログアウトできる', async () => {
    // 「操作を続ける」のテストと同じく、偽の時計を実際の時間でも進める
    startClock({ shouldAdvanceTime: true })
    auth = signedInFailingSignoutOnce()
    const user = userEvent.setup({ advanceTimers: (ms) => vi.advanceTimersByTime(ms) })
    renderGate()

    await user.click(screen.getByRole('button', { name: 'ログアウト' }))
    await settleSignout()

    expect(screen.getByText('画面：管理 太郎')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'ログアウト' }))

    expect(auth.signoutRedirect).toHaveBeenCalledTimes(2)
  })

  test('AC-413-1 無操作のログアウトで IdP へ移り始めるのに失敗すると、無操作の判定を再び動かし、そこから 30 分でもう一度ログアウトする', async () => {
    auth = signedInFailingSignoutOnce()
    renderGate()
    advanceClock(30 * MINUTE)

    expect(auth.signoutRedirect).toHaveBeenCalledTimes(1)

    await settleSignout()
    advanceClock(30 * MINUTE - 1)

    expect(auth.signoutRedirect).toHaveBeenCalledTimes(1)

    advanceClock(1)

    expect(auth.signoutRedirect).toHaveBeenCalledTimes(2)
  })
})
