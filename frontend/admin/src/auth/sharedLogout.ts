import { useCallback, useEffect, useRef, useState } from 'react'
import { clearLastActivity } from '@/auth/idle'

/** ログアウトの印（ログアウトした時刻のミリ秒の数値）を置くローカルストレージのキー。タブの間で共有する */
export const LOGOUT_KEY = 'ec-admin.loggedOutAt'

/** タブの間で共有するログアウト */
type SharedLogout = {
  /** ログアウトする（手動と無操作のどちらも）。印を書き、最後に操作した時刻を削除してから、IdP のログアウトへ移る */
  logout: () => void
  /** IdP のログアウトへ移り始めたら true（このタブでも、他のタブの印を受けても） */
  loggingOut: boolean
}

/**
 * ログアウトを、同じブラウザーの他のタブに伝え、他のタブのログアウトを受ける（管理画面の構成の「ログイン」）。<br />
 * 他のタブの印を受けたら、このタブも IdP のログアウトへ移る。token を捨てるだけにしないのは、
 * 他のタブが IdP のログインへ移り、残っている IdP のセッションでパスワードなしに再ログインしないため。
 * IdP のログアウトへ移るのは 1 回だけ。
 *
 * @param options.enabled ログインしているときだけ true（ログインしていないタブでは、印を受けても何もしない）
 * @param options.signout IdP のログアウトへ移る
 * @returns ログアウトの処理と、移り始めたか
 */
export function useSharedLogout({ enabled, signout }: { enabled: boolean; signout: () => void }): SharedLogout {
  const [loggingOut, setLoggingOut] = useState(false)
  // 印を受けた後や、手動と無操作のログアウトが重なっても、IdP へ移るのは 1 回だけにする
  const leaving = useRef(false)
  // storage の受け取りを signout が変わるたびに置き直さないよう、ref で最新の関数を渡す
  const signoutRef = useRef(signout)

  useEffect(() => {
    signoutRef.current = signout
  }, [signout])

  /** IdP のログアウトへ移る。最後に操作した時刻を削除し、無操作の判定を止める（印より後に時刻を書かないため） */
  const leave = useCallback(() => {
    if (leaving.current) {
      return
    }
    leaving.current = true
    clearLastActivity()
    setLoggingOut(true)
    signoutRef.current()
  }, [])

  const logout = useCallback(() => {
    if (!leaving.current) {
      markLogout(Date.now())
    }
    leave()
  }, [leave])

  useEffect(() => {
    if (!enabled) {
      return
    }
    // 他のタブが書いた印だけを受ける。印の削除（newValue が null）は受けない
    const onStorage = (event: StorageEvent) => {
      if (event.key === LOGOUT_KEY && event.newValue !== null) {
        leave()
      }
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [enabled, leave])

  return { logout, loggingOut }
}

/**
 * ログアウトの印を書き、他のタブに伝える。置くのは時刻だけで、token は置かない。
 *
 * @param at ログアウトした時刻（ミリ秒）
 */
function markLogout(at: number): void {
  try {
    localStorage.setItem(LOGOUT_KEY, String(at))
  } catch {
    // ローカルストレージを使えないときは、他のタブに伝えずに、このタブだけログアウトする
  }
}
