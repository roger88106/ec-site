/**
 * 待つ時間の上限の signal を作る（店舗画面の構成 第 4.2 節）。元の signal があれば、どちらかで打ち切る。<br />
 * 上限を過ぎると、fetch は名前が TimeoutError の例外で失敗する。
 *
 * @param seconds 上限（秒）
 * @param signal  元の signal（呼ぶ側の打ち切り）
 * @returns 上限の signal
 */
export function timeoutSignal(seconds: number, signal?: AbortSignal | null): AbortSignal {
  const timeout = AbortSignal.timeout(seconds * 1000)
  return signal ? AbortSignal.any([signal, timeout]) : timeout
}

/**
 * 待つ時間の上限を付けた fetch を作る（Auth.js の provider の fetch の差し替えに使う）。
 *
 * @param seconds 上限（秒）
 * @returns 上限を付けた fetch
 */
export function fetchWithTimeout(seconds: number): typeof fetch {
  return (input, init) => {
    const signal = init?.signal ?? (input instanceof Request ? input.signal : undefined)
    return fetch(input, { ...init, signal: timeoutSignal(seconds, signal) })
  }
}
