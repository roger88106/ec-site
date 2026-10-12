import { readServerConfig } from '@/auth/config'

/** 接続先と client */
const { issuer: ISSUER, clientId: CLIENT_ID } = readServerConfig()

/**
 * IdP のログアウト（RP-Initiated Logout）の URL を作る。ログアウトの後は店舗のトップに戻る。
 *
 * @param origin  店舗画面の起点（`https://shop.example` など）
 * @param idToken ID token。あれば添える（IdP が確認の画面を出さずにログアウトする）
 * @returns ログアウトの URL
 */
export function logoutUrl(origin: string, idToken?: string): string {
  const query = new URLSearchParams({ client_id: CLIENT_ID, post_logout_redirect_uri: `${origin}/` })
  if (idToken) {
    query.set('id_token_hint', idToken)
  }
  return `${ISSUER}/protocol/openid-connect/logout?${query}`
}
