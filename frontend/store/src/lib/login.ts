/** ログインの後に戻る画面の既定（店舗のトップ） */
const HOME = '/'

/**
 * ログインの後に戻る画面（店舗画面の構成 第 9 節）。店舗画面の中のパスだけを受け付け、外のサイトへは戻さない。
 *
 * @param value 指定された戻り先
 * @returns 戻り先のパス（クエリを含む）。受け付けなければ店舗のトップ
 */
export function safeReturnTo(value: string | null | undefined): string {
  if (!value?.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) {
    return HOME
  }
  return value
}

/**
 * ログインの口（/login）のパス。
 *
 * @param returnTo ログインの後に戻る画面
 * @param options  register：会員登録の画面にする（AC-108）
 */
export function loginPath(returnTo: string, options: { register?: boolean } = {}): string {
  const query = new URLSearchParams({ returnTo })
  if (options.register) {
    query.set('register', '1')
  }
  return `/login?${query}`
}

/**
 * 会員の氏名を「姓 名」の順に並べる（AC-114）。
 *
 * @param familyName 姓（token の family_name）
 * @param givenName  名（token の given_name）
 */
export function memberName(familyName: string | undefined, givenName: string | undefined): string {
  return [familyName, givenName].filter((part) => part).join(' ')
}

/**
 * access token を取り直すか（店舗画面の構成 第 9 節）。
 *
 * @param expiresAt     access token の期限（UNIX 時刻の秒）。分からなければ undefined
 * @param nowSeconds    今の時刻（UNIX 時刻の秒）
 * @param marginSeconds 期限までの残りがこれ以下なら取り直す
 */
export function needsTokenRefresh(expiresAt: number | undefined, nowSeconds: number, marginSeconds: number): boolean {
  return expiresAt === undefined || expiresAt - nowSeconds <= marginSeconds
}
