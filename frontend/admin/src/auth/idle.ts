/** 無操作でログアウトするまでの時間（ミリ秒）。運営者の realm のアイドルタイムアウト（1800 秒）と同じにする */
export const IDLE_LIMIT_MS = 30 * 60 * 1000

/** ログアウトの何ミリ秒前に警告を出すか */
export const IDLE_WARNING_BEFORE_MS = 2 * 60 * 1000

/**
 * 状態が変わる時刻のタイマーとは別に、判定を繰り返す間隔（ミリ秒）。<br />
 * タブを前面に表示したままスリープから再開し、visibilitychange が起きないときも、ログアウトの遅れをこの間隔に抑える
 */
export const IDLE_CHECK_INTERVAL_MS = 15 * 1000

/** 操作に数えるイベント。ポインターの移動と API の呼び出しは数えない */
export const ACTIVITY_EVENTS = ['keydown', 'pointerdown', 'wheel'] as const

/** 最後に操作した時刻（ミリ秒の数値）を置くローカルストレージのキー。タブの間で共有する */
export const LAST_ACTIVITY_KEY = 'ec-admin.lastActivityAt'

/** 無操作の状態。操作中、警告を出す間、期限切れ */
type IdleStatus = 'active' | 'warning' | 'expired'

/**
 * 最後に操作した時刻と現在の時刻から、無操作の状態を求める。<br />
 * 経過時間を数えずに時刻を比べるので、スリープでタイマーが止まっても正しく判定できる。
 *
 * @param lastActivityAt 最後に操作した時刻（ミリ秒）
 * @param now            現在の時刻（ミリ秒）
 * @returns 無操作の状態
 */
export function idleStatus(lastActivityAt: number, now: number): IdleStatus {
  const idle = now - lastActivityAt
  // 30 分を過ぎたら期限切れ、その 2 分前からは警告、それより前は操作中
  if (idle >= IDLE_LIMIT_MS) {
    return 'expired'
  }
  if (idle >= IDLE_LIMIT_MS - IDLE_WARNING_BEFORE_MS) {
    return 'warning'
  }
  return 'active'
}

/**
 * 最後に操作した時刻から、ログアウトする時刻を求める。
 *
 * @param lastActivityAt 最後に操作した時刻（ミリ秒）
 * @returns ログアウトする時刻（ミリ秒）
 */
export function logoutAt(lastActivityAt: number): number {
  return lastActivityAt + IDLE_LIMIT_MS
}

/**
 * ローカルストレージに置いた値を、最後に操作した時刻として読む。
 *
 * @param raw 置いた値
 * @returns 時刻（ミリ秒）。値がない、0 以上の整数でない、安全に扱える整数の上限を超えるときは null
 */
export function parseLastActivity(raw: string | null): number | null {
  if (raw === null || !/^\d+$/.test(raw)) {
    return null
  }
  const at = Number(raw)
  // 桁の多い値は Infinity や丸めた値になるので、取り込まない
  return Number.isSafeInteger(at) ? at : null
}

/**
 * 共有した最後に操作した時刻を読む。
 *
 * @returns 時刻（ミリ秒）。値がない、数値でない、ローカルストレージが使えないときは null
 */
export function readLastActivity(): number | null {
  try {
    return parseLastActivity(localStorage.getItem(LAST_ACTIVITY_KEY))
  } catch {
    // ローカルストレージを使えないブラウザーでは、共有せずにそのタブの中だけで判定する
    return null
  }
}

/**
 * 最後に操作した時刻を共有する。置くのは時刻だけで、token は置かない。
 *
 * @param at 最後に操作した時刻（ミリ秒）
 */
export function writeLastActivity(at: number): void {
  try {
    localStorage.setItem(LAST_ACTIVITY_KEY, String(at))
  } catch {
    // ローカルストレージを使えない、容量が足りないときは、共有せずにそのタブの中だけで判定する
  }
}
