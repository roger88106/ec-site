import type { JWT } from 'next-auth/jwt'
import { readServerConfig } from '@/auth/config'
import { needsTokenRefresh } from '@/lib/login'

/** セッションの Cookie に持つ値（token はサーバーだけで扱い、画面から読める session には入れない） */
export type MemberJwt = JWT & {
  accessToken?: string
  refreshToken?: string
  idToken?: string
  /** access token の期限（UNIX 時刻の秒） */
  expiresAt?: number
}

/** 接続先と client */
const { issuer: ISSUER, clientId: CLIENT_ID, clientSecret: CLIENT_SECRET } = readServerConfig()

/**
 * access token の期限が近ければ、refresh token で取り直す。
 *
 * @param token         セッションの token
 * @param marginSeconds 期限までの残りがこの秒数以下なら取り直す
 * @returns 取り直した（または取り直さなくてよい）token。IdP に断られたら null（ログインしていないものとする）
 */
export async function refreshIfNeeded(token: MemberJwt, marginSeconds: number): Promise<MemberJwt | null> {
  if (!needsTokenRefresh(token.expiresAt, Math.floor(Date.now() / 1000), marginSeconds)) {
    return token
  }
  if (!token.refreshToken) {
    return null
  }
  try {
    const response = await fetch(`${ISSUER}/protocol/openid-connect/token`, {
      method: 'POST',
      body: new URLSearchParams({
        grant_type: 'refresh_token',
        client_id: CLIENT_ID,
        client_secret: CLIENT_SECRET,
        refresh_token: token.refreshToken,
      }),
      cache: 'no-store',
    })
    if (!response.ok) {
      return null
    }
    const refreshed = (await response.json()) as {
      access_token: string
      expires_in: number
      refresh_token?: string
      id_token?: string
    }
    return {
      ...token,
      accessToken: refreshed.access_token,
      expiresAt: Math.floor(Date.now() / 1000) + refreshed.expires_in,
      refreshToken: refreshed.refresh_token ?? token.refreshToken,
      idToken: refreshed.id_token ?? token.idToken,
    }
  } catch {
    // IdP に届かなかった：ログインの状態は残す（期限が切れていれば、API が 401 を返し、ログインの画面へ移る）
    return token
  }
}
