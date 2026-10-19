/**
 * 引当の期限までの残り時間（例：「14:59」）。秒の端数は切り捨てる。
 *
 * @param expiresAt 引当の期限
 * @param now       現在時刻
 * @returns 残り時間。期限を過ぎていれば null
 */
export function remainingTime(expiresAt: string, now: Date): string | null {
  const seconds = Math.floor((new Date(expiresAt).getTime() - now.getTime()) / 1000)
  if (seconds <= 0) {
    return null
  }
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}
