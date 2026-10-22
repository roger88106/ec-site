/** 郵便番号の形（ハイフンを除いて数字 7 桁） */
const POSTAL_CODE_DIGITS = /^[0-9]{7}$/

/**
 * 郵便番号の形を確かめ、ハイフンを除いた 7 桁の数字にする（店舗画面の構成 第 4.8 節）。<br />
 * 除くのは最初のハイフン 1 つだけで、2 つ以上あると形が違うものとして扱う。
 *
 * @param value 郵便番号（ハイフンは任意）
 * @returns 7 桁の数字。形が違うなら null
 */
export function normalizePostalCode(value: string): string | null {
  const digits = value.replace('-', '')
  return POSTAL_CODE_DIGITS.test(digits) ? digits : null
}
