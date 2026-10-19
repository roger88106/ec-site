/** UUID の形 */
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/** 日付の形（yyyy-MM-dd） */
const DATE = /^\d{4}-\d{2}-\d{2}$/

/**
 * UUID の形か。URL や Cookie の ID が形の違う値なら、API を呼ばずに「見つかりません」・「ない」として扱う。
 *
 * @param value 調べる値
 */
export function isUuid(value: string): boolean {
  return UUID.test(value)
}

/**
 * 日付の形（yyyy-MM-dd）か。実在する日付か（2 月 30 日など）は見ない。
 *
 * @param value 調べる値
 */
export function isDateString(value: string): boolean {
  return DATE.test(value)
}
