import { useCallback, useEffect, useRef, useState } from 'react'
import {
  ACTIVITY_EVENTS,
  IDLE_CHECK_INTERVAL_MS,
  IDLE_WARNING_BEFORE_MS,
  idleStatus,
  LAST_ACTIVITY_KEY,
  logoutAt,
  parseStoredTime,
  readLastActivity,
  startingActivity,
  writeLastActivity,
} from '@/auth/idle'

/** 操作のたびに共有の時刻を書く最短の間隔（ミリ秒）。キーの入力のたびにローカルストレージへ書かないため */
const SHARE_INTERVAL_MS = 1000

/** スリープなどでタイマーが止まった後に、window で受けて判定し直すイベント（前面に戻った、ページが表示された） */
const WAKE_EVENTS = ['focus', 'pageshow'] as const

/** 無操作のログアウトの状態 */
type IdleLogoutState = {
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
 * 判定は、次に状態が変わる時刻のタイマー、短い間隔の繰り返し、タブが再び見えた・前面に戻ったときに行う。
 *
 * @param options.onExpire ログアウトするときに呼ぶ
 * @returns 警告の状態
 */
export function useIdleLogout({ onExpire }: { onExpire: () => void }): IdleLogoutState {
  const [warning, setWarning] = useState(false)
  const [logoutAtMs, setLogoutAtMs] = useState(() => logoutAt(Date.now()))
  // 判定の effect を onExpire が変わるたびに置き直す（計測をやり直す）ことを避けるため、ref で最新の関数を渡す
  const onExpireRef = useRef(onExpire)
  // 「操作を続ける」は effect の中で作る記録の処理を外から呼ぶため、effect が ref に入れた関数を使う
  const keepWorkingRef = useRef<() => void>(() => {})

  useEffect(() => {
    onExpireRef.current = onExpire
  }, [onExpire])

  useEffect(() => {
    let lastActivityAt = startingActivity(Date.now())
    let sharedAt = Number.NEGATIVE_INFINITY
    let warned = false
    let expired = false
    let timer: ReturnType<typeof setTimeout> | undefined

    /** 他のタブの操作の時刻を取り込む。今の値より古ければ戻さない。未来の時刻は、判定のときに現在の時刻にする */
    const adopt = (at: number | null) => {
      if (at !== null) {
        lastActivityAt = Math.max(lastActivityAt, at)
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
      if (lastActivityAt > now) {
        // 時計が戻った、未来の値を取り込んだときは、現在の時刻にする。共有の値も書き換える（他のタブが未来の値を取り込み続けないため）
        lastActivityAt = now
        share(now, true)
      }
      const status = idleStatus(lastActivityAt, now)
      if (status === 'expired') {
        expired = true
        clearInterval(interval)
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
      // 操作中のタイマーは、実行されたときに新しい時刻から置き直すので、警告の間だけ判定し直す
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
      adopt(parseStoredTime(event.newValue))
      if (warned) {
        evaluate()
      }
    }

    // スリープなどでタイマーが止まった後、タブが再び見えた・前面に戻ったときに判定する
    const onWake = () => evaluate()

    keepWorkingRef.current = () => record(true)
    share(lastActivityAt, true)
    // 子のコンポーネントがイベントの伝播を止めても数えるよう、キャプチャーフェーズで受け取る
    for (const type of ACTIVITY_EVENTS) {
      window.addEventListener(type, onActivity, { capture: true, passive: true })
    }
    window.addEventListener('storage', onStorage)
    for (const type of WAKE_EVENTS) {
      window.addEventListener(type, onWake)
    }
    document.addEventListener('visibilitychange', onWake)
    // タブを前面に表示したままスリープから再開すると、上のイベントが起きず、タイマーも止まる前の残りの時間を待つので、短い間隔でも判定する
    const interval = setInterval(evaluate, IDLE_CHECK_INTERVAL_MS)
    evaluate()

    return () => {
      expired = true
      clearTimeout(timer)
      clearInterval(interval)
      keepWorkingRef.current = () => {}
      for (const type of ACTIVITY_EVENTS) {
        window.removeEventListener(type, onActivity, { capture: true })
      }
      window.removeEventListener('storage', onStorage)
      for (const type of WAKE_EVENTS) {
        window.removeEventListener(type, onWake)
      }
      document.removeEventListener('visibilitychange', onWake)
    }
  }, [])

  const keepWorking = useCallback(() => keepWorkingRef.current(), [])

  return { warning, logoutAt: logoutAtMs, keepWorking }
}
