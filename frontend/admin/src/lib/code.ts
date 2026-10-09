/**
 * 品番の入力を、送る形にする。半角の英小文字を大文字にする（T709）。<br />
 * それ以外の文字はそのまま残し、形式の誤りはサーバーが検証する（AC-15、21）。
 */
export function normalizeCode(value: string): string {
  return value.replace(/[a-z]+/g, (letters) => letters.toUpperCase())
}
