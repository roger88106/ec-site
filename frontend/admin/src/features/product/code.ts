/**
 * 品番の入力を、送る形にする
 *
 * 半角の英小文字を大文字にする。それ以外の文字はそのまま残し、形式の誤りはサーバーが検証する。
 *
 * @param value 入力した品番
 * @returns 送る品番
 */
export function normalizeCode(value: string): string {
  return value.replace(/[a-z]+/g, (letters) => letters.toUpperCase())
}
