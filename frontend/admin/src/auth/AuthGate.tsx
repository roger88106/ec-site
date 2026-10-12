import { fullName } from '@ec-site/shared/lib/login'
import { type ReactNode, useEffect, useLayoutEffect, useMemo, useRef } from 'react'
import { hasAuthParams, useAuth } from 'react-oidc-context'
import { type Operator, OperatorProvider } from '@/auth/operator'
import { setTokenSource } from '@/auth/token'
import { Button } from '@/components/ui/button'

/**
 * すべての画面を包み、ログインを求める（AC-132。管理画面の構成 第 11 節）。<br />
 * ログインしていなければ、何も表示せずに IdP のログインへ移り、戻ってきたら移る前の画面に戻す。
 * ログインしていれば、運営者（氏名とログアウト）と、API に付ける token を渡してから画面を出す。
 */
export function AuthGate({ children }: { children: ReactNode }) {
  const { isLoading, isAuthenticated, activeNavigator, error, user, signinRedirect, signoutRedirect } = useAuth()
  // IdP へ移るのは 1 回だけ（開発時の StrictMode で effect が 2 回動いても、2 つ目の state を作らない）
  const redirecting = useRef(false)

  useEffect(() => {
    if (!hasAuthParams() && !isLoading && !isAuthenticated && !activeNavigator && !error && !redirecting.current) {
      redirecting.current = true
      void signinRedirect({ state: { returnTo: currentPath() } })
    }
  }, [isLoading, isAuthenticated, activeNavigator, error, signinRedirect])

  // 画面（子の部品）の取得は、子の effect で始まり、親のこの部品の effect より先に動く。
  // token を読む口は、子の effect より先に動く layout effect で渡す（ログインの直後の最初の取得に token を付けるため）
  useLayoutEffect(() => {
    setTokenSource({
      accessToken: () => user?.access_token,
      onUnauthorized: () => {
        if (recentlyReloggedIn()) {
          // ログインし直した直後にまた 401 なら、繰り返さない（IdP と画面の間を回り続けないため）。エラーは画面に出る
          return
        }
        markRelogin()
        void signinRedirect({ state: { returnTo: currentPath() } })
      },
    })
    return () => setTokenSource(null)
  }, [user, signinRedirect])

  const operator = useMemo<Operator | null>(
    () =>
      user
        ? {
            name: fullName(user.profile.family_name, user.profile.given_name),
            logout: () => void signoutRedirect({ id_token_hint: user.id_token }),
          }
        : null,
    [user, signoutRedirect],
  )

  if (error && !isAuthenticated) {
    return (
      <div className="mx-auto max-w-md space-y-4 p-8 text-sm">
        <p>ログインできませんでした。（{error.message}）</p>
        <Button onClick={() => void signinRedirect({ state: { returnTo: '/' } })}>ログインし直す</Button>
      </div>
    )
  }
  if (!isAuthenticated || operator === null) {
    return null
  }
  return <OperatorProvider value={operator}>{children}</OperatorProvider>
}

/** 401 でログインし直した時刻を持つ sessionStorage のキー */
const RELOGIN_AT_KEY = 'ec-admin.reloginAt'

/** 401 でログインし直してから、もう一度ログインし直さない時間（ミリ秒） */
const RELOGIN_GUARD_MS = 30_000

/** 401 でログインし直してから、間もないか */
function recentlyReloggedIn(): boolean {
  return Date.now() - Number(sessionStorage.getItem(RELOGIN_AT_KEY) ?? 0) < RELOGIN_GUARD_MS
}

/** 401 でログインし直す時刻を残す */
function markRelogin(): void {
  sessionStorage.setItem(RELOGIN_AT_KEY, String(Date.now()))
}

/** いまの画面のパスとクエリ（ログインの後に戻る） */
function currentPath(): string {
  return window.location.pathname + window.location.search
}
