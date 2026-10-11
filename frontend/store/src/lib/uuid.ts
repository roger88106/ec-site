/** UUID の形 */
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/**
 * UUID の形か。URL や Cookie の ID が形の違う値なら、API を呼ばずに「見つかりません」・「ない」として扱う。
 *
 * @param value 調べる値
 */
export function isUuid(value: string): boolean {
  return UUID.test(value)
}
