import type { JWT } from 'next-auth/jwt'
import { readServerConfig } from '@/auth/config'
import { needsTokenRefresh } from '@/lib/login'
import { timeoutSignal } from '@/lib/server/timeout'

/** セッションの Cookie に持つ値（token はサーバーだけで扱い、画面から読める session には入れない） */
export type MemberJwt = JWT & {
  accessToken?: string
  refreshToken?: string
  idToken?: string
  /** access token の期限（UNIX 時刻の秒） */
  expiresAt?: number
}

/** 接続先と client */
const {
  issuer: ISSUER,
  clientId: CLIENT_ID,
  clientSecret: CLIENT_SECRET,
  idpTimeoutSeconds: IDP_TIMEOUT_SECONDS,
} = readServerConfig()

/**
 * access token の期限が近ければ、refresh token で取り直す。
 *
 * @param token         セッションの token
 * @param marginSeconds 期限までの残りがこの秒数以下なら取り直す
 * @returns 再取得した（または再取得しなくてよい）token。IdP に refresh token を拒否されたら null（ログインしていないものとする）。
 *          他の失敗（IdP に届かない、IdP の障害など）では、今の token
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
      // 上限を過ぎたら、IdP に届かなかったときと同じに扱う（下の catch）
      signal: timeoutSignal(IDP_TIMEOUT_SECONDS),
    })
    if (!response.ok) {
      // 拒否（IdP のログインが切れた）だけをログアウトにする。IdP の障害や client の設定の誤りでは、
      // 会員のセッションの Cookie を削除しない（Auth.js は null を受けると Cookie を削除する）
      return (await isRefreshTokenRejected(response)) ? null : token
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
    // IdP に届かなかった、または応答が読めなかった：ログインの状態は残す（期限が切れていれば、API が 401 を返し、ログインの画面へ移る）
    return token
  }
}

/** IdP が refresh token を拒否した（応答の本文の error が invalid_grant）か */
async function isRefreshTokenRejected(response: Response): Promise<boolean> {
  try {
    const body = (await response.json()) as { error?: unknown } | null
    return body?.error === 'invalid_grant'
  } catch {
    // 本文が JSON でない（前段のプロキシーが返すエラー画面など）
    return false
  }
}
