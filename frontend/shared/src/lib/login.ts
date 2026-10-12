/**
 * ログインの後に戻ってよいパスか。画面の中のパス（`/` で始まり、`//`・`/\` で始まらない）だけを受け付け、
 * 外のサイトへは戻さない。
 *
 * @param path 戻り先
 * @returns 戻ってよければ true
 */
export function isSafeReturnPath(path: unknown): path is string {
  return typeof path === 'string' && path.startsWith('/') && !path.startsWith('//') && !path.startsWith('/\\')
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
