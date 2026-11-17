import { createHash } from 'node:crypto'
import type { JWT } from 'next-auth/jwt'
import { readServerConfig } from '@/auth/config'
import { needsTokenRefresh } from '@/lib/login'
import { log } from '@/lib/server/log'
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

/** 再取得できた結果と、IdP の拒否（invalid_grant）の結果を保持する時間（ミリ秒） */
const SETTLED_RETENTION_MS = 10_000

/** 上の以外の失敗の結果を保持する時間（ミリ秒）。障害の間の IdP の呼び出しを、この時間に 1 回に減らす */
const FAILURE_RETENTION_MS = 30_000

/** 失敗の理由の種類（ログの項目。token や応答の本文は含めない） */
type FailureReason = 'unreachable' | 'error-response' | 'unreadable-body'

/** 再取得で新しくなる値（IdP が返さなかった refresh token と ID token は undefined で、今の値を使う） */
type RefreshedFields = Pick<MemberJwt, 'accessToken' | 'expiresAt' | 'refreshToken' | 'idToken'>

/**
 * 再取得の結果。
 *
 * token の全体でなく、新しくなる値だけを持つ。保持した結果を返すときは、呼び出しに渡された今の token に反映するため。
 */
type Outcome = { kind: 'refreshed'; fields: RefreshedFields } | { kind: 'rejected' } | { kind: 'failed' }

/** 保持している結果。実行中は retainedUntil が null で、終わると保持の期限（`Date.now()` のミリ秒）が入る */
type Retained = { outcome: Promise<Outcome>; retainedUntil: number | null }

/**
 * 保持する場所の名前。
 *
 * 保持はモジュールの変数でなく、`globalThis` の上に置く。proxy とアプリのサーバーは別のバンドルで、
 * 同じモジュールを読んでも変数を共有しないため。
 */
const RETAINED_KEY = Symbol.for('ec-site.store.auth.refresh.retained')

/** 保持している結果（refresh token のハッシュごと） */
function retainedResults(): Map<string, Retained> {
  const holder = globalThis as { [RETAINED_KEY]?: Map<string, Retained> }
  holder[RETAINED_KEY] ??= new Map()
  return holder[RETAINED_KEY]
}

/** テストの間で、保持している結果を全て削除する（setup.ts が各テストの前に呼ぶ） */
export function resetRefreshRetention(): void {
  retainedResults().clear()
}

/** refresh token のハッシュ。token そのものをメモリーの保持のキーにしないため */
function retentionKey(refreshToken: string): string {
  return createHash('sha256').update(refreshToken).digest('hex')
}

/** 保持の時間を過ぎた結果を削除する（保持が増え続けないようにする） */
function removeExpired(retained: Map<string, Retained>): void {
  const now = Date.now()
  for (const [key, entry] of retained) {
    if (entry.retainedUntil !== null && entry.retainedUntil <= now) {
      retained.delete(key)
    }
  }
}

/**
 * access token の期限が近ければ、refresh token で取り直す。
 *
 * 同じ refresh token の再取得が実行中なら、その結果を待って共有する。終わった結果は一定の時間保持し、その間は IdP を呼ばない。
 *
 * @param token         セッションの token
 * @param marginSeconds 期限までの残りがこの秒数以下なら取り直す
 * @returns 再取得した（または再取得しなくてよい）token。IdP に refresh token を拒否されたら null（ログインしていないものとする）。
 *          他の失敗（IdP に届かない、IdP の障害など）では、今の token
 */
export async function refreshIfNeeded(token: MemberJwt, marginSeconds: number): Promise<MemberJwt | null> {
  if (!needsTokenRefresh(token.expiresAt, nowSeconds(), marginSeconds)) {
    return token
  }
  if (!token.refreshToken) {
    return null
  }
  const retained = retainedResults()
  removeExpired(retained)
  const key = retentionKey(token.refreshToken)
  let entry = retained.get(key)
  if (!entry) {
    const created: Retained = { outcome: requestRefresh(token.refreshToken), retainedUntil: null }
    created.outcome.then(
      (outcome) => {
        created.retainedUntil = Date.now() + (outcome.kind === 'failed' ? FAILURE_RETENTION_MS : SETTLED_RETENTION_MS)
      },
      // 結果で返す約束が破れて例外になったときは、保持を残さない（残すと、同じ refresh token の後の呼び出しが全て同じ例外になる）
      () => retained.delete(key),
    )
    retained.set(key, created)
    entry = created
  }
  const outcome = await entry.outcome
  if (outcome.kind === 'refreshed') {
    const { fields } = outcome
    return {
      ...token,
      accessToken: fields.accessToken,
      expiresAt: fields.expiresAt,
      refreshToken: fields.refreshToken ?? token.refreshToken,
      idToken: fields.idToken ?? token.idToken,
    }
  }
  // 拒否（IdP のログインが切れた）だけをログアウトにする。IdP の障害や client の設定の誤りでは、
  // 会員のセッションの Cookie を削除しない（Auth.js は null を受けると Cookie を削除する）
  return outcome.kind === 'rejected' ? null : token
}

/**
 * IdP に refresh token で再取得を求める。失敗は例外にせず、結果で返す。
 *
 * @param refreshToken 再取得に使う refresh token
 * @returns 再取得の結果。IdP に届かない・失敗の応答のときは、warn のログを 1 件出す
 */
async function requestRefresh(refreshToken: string): Promise<Outcome> {
  let response: Response
  try {
    response = await fetch(`${ISSUER}/protocol/openid-connect/token`, {
      method: 'POST',
      body: new URLSearchParams({
        grant_type: 'refresh_token',
        client_id: CLIENT_ID,
        client_secret: CLIENT_SECRET,
        refresh_token: refreshToken,
      }),
      cache: 'no-store',
      // 上限を過ぎたら、IdP に届かなかったときと同じに扱う
      signal: timeoutSignal(IDP_TIMEOUT_SECONDS),
    })
  } catch {
    // IdP に届かなかった：ログインの状態は残す（期限が切れていれば、API が 401 を返し、ログインの画面へ移る）
    return failed('unreachable')
  }
  if (!response.ok) {
    const error = await readError(response)
    if (error === UNREADABLE) {
      return failed('unreadable-body', response.status)
    }
    // invalid_grant はログアウトの通常の流れなので、失敗の warn にしない
    return error === 'invalid_grant' ? { kind: 'rejected' } : failed('error-response', response.status)
  }
  try {
    const refreshed = (await response.json()) as {
      access_token: string
      expires_in: number
      refresh_token?: string
      id_token?: string
    }
    return {
      kind: 'refreshed',
      fields: {
        accessToken: refreshed.access_token,
        expiresAt: nowSeconds() + refreshed.expires_in,
        refreshToken: refreshed.refresh_token,
        idToken: refreshed.id_token,
      },
    }
  } catch {
    // 成功の応答の本文を解析できなかった
    return failed('unreadable-body', response.status)
  }
}

/**
 * 失敗の warn のログを 1 件出し、失敗の結果を返す。
 *
 * 項目は理由の種類と状態コードだけ。token、Cookie、応答の本文は出さない。
 */
async function failed(reason: FailureReason, status?: number): Promise<Outcome> {
  await log.warn('IdP での token の再取得に失敗した', { reason, status })
  return { kind: 'failed' }
}

/** いまの UNIX 時刻（秒） */
function nowSeconds(): number {
  return Math.floor(Date.now() / 1000)
}

/** 失敗の応答の本文を解析できなかった印（error の値と取り違えない） */
const UNREADABLE = Symbol('unreadable')

/**
 * 失敗の応答の本文の error を読む。
 *
 * @returns error の値（invalid_grant など）。本文が JSON でない（前段のプロキシーが返すエラー画面など）ときは UNREADABLE
 */
async function readError(response: Response): Promise<unknown> {
  try {
    const body = (await response.json()) as { error?: unknown } | null
    return body?.error
  } catch {
    return UNREADABLE
  }
}
