import { useCallback, useEffect, useRef, useState } from 'react'
import {
  ACTIVITY_EVENTS,
  IDLE_WARNING_BEFORE_MS,
  idleStatus,
  LAST_ACTIVITY_KEY,
  logoutAt,
  parseLastActivity,
  readLastActivity,
  writeLastActivity,
} from '@/auth/idle'

/** 操作のたびに共有の時刻を書く最短の間隔（ミリ秒）。キーの入力のたびにローカルストレージへ書かないため */
const SHARE_INTERVAL_MS = 1000

/** 無操作のログアウトの状態 */
export type IdleLogout = {
  /** 警告を出す間は true */
  warning: boolean
  /** ログアウトする時刻（ミリ秒）。最後に判定したときの値で、警告の間は正しい */
  logoutAt: number
  /** 警告を閉じ、30 分の計測を最初から始める（「操作を続ける」） */
  keepWorking: () => void
}

/**
 * 無操作が続いたら onExpire を 1 回だけ呼ぶ。警告の状態を返す。<br />
 * 最後に操作した時刻をローカルストレージでタブの間に共有し、現在の時刻との比較で判定する。
 * 判定は、次に状態が変わる時刻のタイマーと、タブが再び見えたときに行う。
 *
 * @param options.onExpire ログアウトするときに呼ぶ
 * @returns 警告の状態
 */
export function useIdleLogout({ onExpire }: { onExpire: () => void }): IdleLogout {
  const [warning, setWarning] = useState(false)
  const [logoutAtMs, setLogoutAtMs] = useState(() => logoutAt(Date.now()))
  const onExpireRef = useRef(onExpire)
  const keepWorkingRef = useRef<() => void>(() => {})

  useEffect(() => {
    onExpireRef.current = onExpire
  }, [onExpire])

  useEffect(() => {
    let lastActivityAt = Date.now()
    let sharedAt = Number.NEGATIVE_INFINITY
    let warned = false
    let expired = false
    let timer: ReturnType<typeof setTimeout> | undefined

    /** 他のタブの操作の時刻を取り込む。今の値より古ければ戻さず、未来の時刻は現在の時刻にする */
    const adopt = (at: number | null) => {
      if (at !== null) {
        lastActivityAt = Math.max(lastActivityAt, Math.min(at, Date.now()))
      }
    }

    /** 自分のタブの操作の時刻を共有する。force でなければ、間隔が短いときは書かない */
    const share = (at: number, force: boolean) => {
      if (force || at - sharedAt >= SHARE_INTERVAL_MS) {
        writeLastActivity(at)
        sharedAt = at
      }
    }

    /** 共有した時刻を取り込んで判定し、次に状態が変わる時刻にタイマーを置く */
    const evaluate = () => {
      if (expired) {
        return
      }
      clearTimeout(timer)
      adopt(readLastActivity())
      const now = Date.now()
      const status = idleStatus(lastActivityAt, now)
      if (status === 'expired') {
        expired = true
        onExpireRef.current()
        return
      }
      warned = status === 'warning'
      setWarning(warned)
      setLogoutAtMs(logoutAt(lastActivityAt))
      const nextAt = warned ? logoutAt(lastActivityAt) : logoutAt(lastActivityAt) - IDLE_WARNING_BEFORE_MS
      timer = setTimeout(evaluate, nextAt - now)
    }

    /** 操作を記録する。警告の間なら閉じる */
    const record = (force: boolean) => {
      if (expired) {
        return
      }
      const now = Date.now()
      // スリープの後の最初の操作で計測を始め直さないよう、先に期限切れかを判定する
      if (idleStatus(lastActivityAt, now) === 'expired') {
        evaluate()
        if (expired) {
          return
        }
      }
      lastActivityAt = now
      share(now, force)
      // 操作中のタイマーは、鳴ったときに新しい時刻から置き直すので、警告の間だけ判定し直す
      if (warned || force) {
        evaluate()
      }
    }

    const onActivity = () => record(false)

    // 他のタブが書いた、最後に操作した時刻のキーだけを取り込む
    const onStorage = (event: StorageEvent) => {
      if (expired || event.key !== LAST_ACTIVITY_KEY) {
        return
      }
      adopt(parseLastActivity(event.newValue))
      if (warned) {
        evaluate()
      }
    }

    // スリープなどでタイマーが止まった後、タブが再び見えたときに判定する
    const onVisibilityChange = () => evaluate()

    keepWorkingRef.current = () => record(true)
    share(lastActivityAt, true)
    // 子の部品が操作のイベントを止めても数えるよう、捕捉の段階で受け取る
    for (const type of ACTIVITY_EVENTS) {
      window.addEventListener(type, onActivity, { capture: true, passive: true })
    }
    window.addEventListener('storage', onStorage)
    document.addEventListener('visibilitychange', onVisibilityChange)
    evaluate()

    return () => {
      expired = true
      clearTimeout(timer)
      keepWorkingRef.current = () => {}
      for (const type of ACTIVITY_EVENTS) {
        window.removeEventListener(type, onActivity, { capture: true })
      }
      window.removeEventListener('storage', onStorage)
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [])

  const keepWorking = useCallback(() => keepWorkingRef.current(), [])

  return { warning, logoutAt: logoutAtMs, keepWorking }
}
