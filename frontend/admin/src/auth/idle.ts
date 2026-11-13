/**
 * 無操作でログアウトするまでの時間（ミリ秒）
 *
 * 運営者の realm のアイドルタイムアウトと同じにする。
 */
export const IDLE_LIMIT_MS = 30 * 60 * 1000

/** ログアウトの何ミリ秒前に警告を出すか */
export const IDLE_WARNING_BEFORE_MS = 2 * 60 * 1000

/**
 * 状態が変わる時刻に置くタイマーとは別に、判定を繰り返す間隔（ミリ秒）。
 *
 * タブを前面に表示したままスリープから再開し、visibilitychange が起きないときも、ログアウトの遅れをこの間隔に抑える
 */
export const IDLE_CHECK_INTERVAL_MS = 15 * 1000

/**
 * 操作に数えるイベント
 *
 * ポインターの移動と API の呼び出しは数えない。
 */
export const ACTIVITY_EVENTS = ['keydown', 'pointerdown', 'wheel'] as const

/**
 * 最後に操作した時刻（ミリ秒の数値）を置くローカルストレージのキー
 *
 * タブの間で共有する。
 */
export const LAST_ACTIVITY_KEY = 'ec-admin.lastActivityAt'

/**
 * 無操作の状態
 *
 * 操作中、警告を出す間、期限切れのどれか。
 */
type IdleStatus = 'active' | 'warning' | 'expired'

/**
 * 最後に操作した時刻と現在の時刻から、無操作の状態を求める。
 *
 * 経過時間を数えずに時刻を比べるので、スリープでタイマーが止まっても正しく判定できる。
 *
 * @param lastActivityAt 最後に操作した時刻（ミリ秒）
 * @param now            現在の時刻（ミリ秒）
 * @returns 無操作の状態
 */
export function idleStatus(lastActivityAt: number, now: number): IdleStatus {
  const idle = now - lastActivityAt
  // IDLE_LIMIT_MS を過ぎたら期限切れ、その IDLE_WARNING_BEFORE_MS 前からは警告、それより前は操作中
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
 * ローカルストレージに置いた値を、時刻（最後に操作した時刻、ログアウトの印）として読む。
 *
 * @param raw 置いた値
 * @returns 時刻（ミリ秒）。値がない、0 以上の整数でない、安全に扱える整数の上限を超えるときは null
 */
export function parseStoredTime(raw: string | null): number | null {
  if (raw === null || !/^\d+$/.test(raw)) {
    return null
  }
  const at = Number(raw)
  // 桁の多い値は Infinity や丸めた値になるので、取り込まない
  return Number.isSafeInteger(at) ? at : null
}

/**
 * ローカルストレージのキーに置いた時刻を読む。
 *
 * @param key キー
 * @returns 時刻（ミリ秒）。値がない、数値でない、ローカルストレージが使えないときは null
 */
export function readStoredTime(key: string): number | null {
  try {
    return parseStoredTime(localStorage.getItem(key))
  } catch {
    // ローカルストレージを使えないブラウザーでは、共有せずにそのタブの中だけで判定する
    return null
  }
}

/**
 * ローカルストレージのキーに時刻を置き、タブの間で共有する。
 *
 * 置くのは時刻だけで、token は置かない。
 *
 * @param key キー
 * @param at  時刻（ミリ秒）
 */
export function writeStoredTime(key: string, at: number): void {
  try {
    localStorage.setItem(key, String(at))
  } catch {
    // ローカルストレージを使えない、容量が足りないときは、共有せずにそのタブの中だけで判定し、ログアウトする
  }
}

/**
 * 共有した最後に操作した時刻を読む。
 *
 * @returns 時刻（ミリ秒）。値がない、数値でない、ローカルストレージが使えないときは null
 */
export function readLastActivity(): number | null {
  return readStoredTime(LAST_ACTIVITY_KEY)
}

/**
 * 計測を始める時刻を求める。
 *
 * 共有した時刻が操作中の範囲ならそれを取り込み、ないか、警告の範囲か、期限切れなら現在の時刻にする。
 * 前のログインの時刻は、ログアウトのときに削除してある。
 *
 * @param now 現在の時刻（ミリ秒）
 * @returns 計測を始める時刻（ミリ秒）
 */
export function startingActivity(now: number): number {
  const shared = readLastActivity()
  // 再マウントで計測を延ばさないため、操作中の時刻だけ取り込む。新しいタブを開くこと自体が運営者の操作なので、
  // 警告の範囲や期限切れなら現在の時刻にし、開いた直後に警告を出してすぐにはログアウトしない。
  // 未来の時刻は、判定のときに現在の時刻にするので、ここでは取り込む
  return shared !== null && idleStatus(shared, now) === 'active' ? shared : now
}

/**
 * 共有した最後に操作した時刻を削除する。
 *
 * 次のログインで前のログインの時刻を取り込まないために削除する。
 */
export function clearLastActivity(): void {
  try {
    localStorage.removeItem(LAST_ACTIVITY_KEY)
  } catch {
    // ローカルストレージを使えないときは、共有していないので削除するものもない
  }
}

/**
 * 最後に操作した時刻を共有する。
 *
 * @param at 最後に操作した時刻（ミリ秒）
 */
export function writeLastActivity(at: number): void {
  writeStoredTime(LAST_ACTIVITY_KEY, at)
}
