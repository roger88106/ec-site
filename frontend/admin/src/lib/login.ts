/**
 * 運営者の氏名を「姓 名」の順に並べる（AC-135）。
 *
 * @param familyName 姓（token の family_name）
 * @param givenName  名（token の given_name）
 */
export function operatorName(familyName: string | undefined, givenName: string | undefined): string {
  return [familyName, givenName].filter((part) => part).join(' ')
}

/**
 * ログインの後に戻る画面（管理画面の構成 第 11 節）。IdP へ移る前に state に入れたパスを読む。
 * 画面の中のパスでなければ、トップにする（外のサイトへは戻さない）。
 *
 * @param state IdP から戻ってきた state
 */
export function returnPath(state: unknown): string {
  const returnTo = typeof state === 'object' && state !== null ? (state as { returnTo?: unknown }).returnTo : undefined
  if (typeof returnTo !== 'string' || !returnTo.startsWith('/') || returnTo.startsWith('//')) {
    return '/'
  }
  return returnTo
}
