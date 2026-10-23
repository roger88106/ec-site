/**
 * ログインの後に戻ってよいパスか。画面の中のパス（`/` で始まり、`//`・`/\` で始まらず、制御文字を含まない）だけを
 * 受け付け、外のサイトへは戻さない。URL の解析は TAB と改行を除くので（`/\t/evil.example` は `//evil.example`
 * になる）、制御文字は一律に受け付けない。
 *
 * @param path 戻り先
 * @returns 戻ってよければ true
 */
export function isSafeReturnPath(path: unknown): path is string {
  return (
    typeof path === 'string' &&
    path.startsWith('/') &&
    !path.startsWith('//') &&
    !path.startsWith('/\\') &&
    !hasControlCharacter(path)
  )
}

/** 制御文字（U+0000〜U+001F と U+007F）を 1 つでも含むか。 */
function hasControlCharacter(text: string): boolean {
  for (let i = 0; i < text.length; i++) {
    const code = text.charCodeAt(i)
    if (code <= 0x1f || code === 0x7f) return true
  }
  return false
}

/**
 * 氏名を「姓 名」の順に並べる。片方しかなければ、それだけにする。
 *
 * @param familyName 姓（token の family_name）
 * @param givenName  名（token の given_name）
 * @returns 氏名
 */
export function fullName(familyName: string | undefined, givenName: string | undefined): string {
  return [familyName, givenName].filter((part) => part).join(' ')
}
