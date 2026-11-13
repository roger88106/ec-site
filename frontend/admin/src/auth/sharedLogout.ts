import { useCallback, useEffect, useRef, useState } from 'react'
import { clearLastActivity, readStoredTime, writeStoredTime } from '@/auth/idle'

/**
 * ログアウトの印（ログアウトした時刻のミリ秒の数値）を書くローカルストレージのキー
 *
 * タブの間で共有する。
 */
export const LOGOUT_KEY = 'ec-admin.loggedOutAt'

/**
 * 印の時刻（ブラウザーの時計）と auth_time（IdP の時計）を比べるときに許す、時計のずれ（ミリ秒）。
 *
 * ブラウザーの時計が進んでいると、印の後にパスワードでログインし直しても auth_time が印より古くなり、
 * ログインし直すたびにログアウトするため。
 */
const CLOCK_SKEW_MS = 5 * 60 * 1000

/** タブの間で共有するログアウト */
type SharedLogout = {
  /** ログアウトする（手動と無操作のどちらも）。印を書き、最後に操作した時刻を削除してから、IdP のログアウトへ移る */
  logout: () => void
  /** IdP のログアウトへ移り始めたら true（このタブでも、他のタブの印を受けても）。移り始めに失敗したら false に戻る */
  loggingOut: boolean
}

/**
 * ログアウトを、同じブラウザーの他のタブに伝え、他のタブのログアウトを受ける（管理画面の構成の「ログイン」）。
 *
 * 他のタブのログアウトの印を受け取ったら、このタブも IdP のログアウトへ移る。
 * 印は storage イベントで受けるほか、このタブが画面を出した時刻か、ログインの時刻（auth_time に時計のずれの幅を加える）より
 * 新しい印があるかを、ログインしたとき、ページが表示されたとき、タブが再び見えた・前面に戻ったときにも確認する。
 * IdP のログアウトへ移るのは 1 回だけ。
 *
 * @param options.enabled    ログインしているときだけ true（ログインしていないタブでは、印を受けても何もしない）
 * @param options.loggedInAt このタブのログインの時刻（ミリ秒。ID token の auth_time）。ないときは undefined
 * @param options.signout    IdP のログアウトへ移る。移り始めに失敗したら reject する
 * @returns ログアウトの処理と、移り始めたか
 */
export function useSharedLogout({
  enabled,
  loggedInAt,
  signout,
}: {
  enabled: boolean
  loggedInAt: number | undefined
  signout: () => Promise<void>
}): SharedLogout {
  const [loggingOut, setLoggingOut] = useState(false)
  // 印を受けた後や、手動と無操作のログアウトが重なっても、IdP へ移るのは 1 回だけにする
  const leaving = useRef(false)
  // storage の受け取りを signout が変わるたびに置き直さないよう、ref で最新の関数を渡す
  const signoutRef = useRef(signout)

  useEffect(() => {
    signoutRef.current = signout
  }, [signout])

  /**
   * IdP のログアウトへ移る。
   *
   * 最後に操作した時刻を削除し、無操作の判定を止める。印より後に時刻を書かないため。
   */
  const leave = useCallback(() => {
    if (leaving.current) {
      return
    }
    leaving.current = true
    clearLastActivity()
    setLoggingOut(true)
    // token を捨てるだけにしないのは、他のタブが IdP のログインへ移り、残っている IdP のセッションでパスワードなしに再ログインしないため
    // 移り始めに失敗したら（IdP に届かないなど）、このタブに留まるので、ログアウトをやり直せるようにし、無操作の判定を再び動かす
    signoutRef.current().catch(() => {
      leaving.current = false
      setLoggingOut(false)
    })
  }, [])

  const logout = useCallback(() => {
    if (!leaving.current) {
      writeStoredTime(LOGOUT_KEY, Date.now())
    }
    leave()
  }, [leave])

  useEffect(() => {
    if (!enabled) {
      return
    }
    const enabledAt = Date.now()
    /** このタブのログインより後に書かれた印があれば、IdP のログアウトへ移る。 */
    const check = () => {
      const at = readStoredTime(LOGOUT_KEY)
      if (at === null) {
        return
      }
      // 画面を出した後の印（戻る・進むのキャッシュから戻ったタブ）は、同じブラウザーの時計どうしで比べる。
      // 画面を出す前の印（読み込み中だったタブ）は、IdP の時計の auth_time と、時計のずれの幅を空けて比べる
      if (at > enabledAt || (loggedInAt !== undefined && at > loggedInAt + CLOCK_SKEW_MS)) {
        leave()
      }
    }
    // 他のタブが書いた印だけを受ける。印の削除（newValue が null）は受けない
    const onStorage = (event: StorageEvent) => {
      if (event.key === LOGOUT_KEY && event.newValue !== null) {
        leave()
      }
    }
    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        check()
      }
    }
    window.addEventListener('storage', onStorage)
    // 戻る・進むのキャッシュから戻ったタブや、印が書かれたときに読み込み中だったタブには、storage イベントが届かないので、
    // ページが表示されたとき、タブが再び見えた・前面に戻ったときにも確認する
    window.addEventListener('pageshow', check)
    window.addEventListener('focus', check)
    document.addEventListener('visibilitychange', onVisibilityChange)
    check()
    return () => {
      window.removeEventListener('storage', onStorage)
      window.removeEventListener('pageshow', check)
      window.removeEventListener('focus', check)
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [enabled, loggedInAt, leave])

  return { logout, loggingOut }
}
