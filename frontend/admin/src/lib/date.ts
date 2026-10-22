/** 日付の形（yyyy-MM-dd） */
const DATE = /^\d{4}-\d{2}-\d{2}$/

/**
 * 日付の形（yyyy-MM-dd）か。実在する日付か（2 月 30 日など）は見ない。
 *
 * @param value 調べる値
 */
export function isDateString(value: string): boolean {
  return DATE.test(value)
}
