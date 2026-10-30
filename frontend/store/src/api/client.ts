import 'server-only'
import { headers } from 'next/headers'
import { connection } from 'next/server'
import createClient, { type Middleware } from 'openapi-fetch'
import type { paths } from '@/api/generated/store'
import { getAccessToken } from '@/auth'
import { readServerConfig } from '@/auth/config'
import { log } from '@/lib/server/log'
import { timeoutSignal } from '@/lib/server/timeout'
import { TRACEPARENT_HEADER } from '@/lib/server/trace'

/**
 * 店舗 API の接続先と、結果をキャッシュする秒数。<br />
 * 秒数は環境変数 STORE_API_REVALIDATE_SECONDS で短くできる（E2E で、60 秒を待たずに一覧への反映を確認するため）。
 */
const {
  apiBaseUrl: BASE_URL,
  apiRevalidateSeconds: REVALIDATE_SECONDS,
  apiTimeoutSeconds: TIMEOUT_SECONDS,
} = readServerConfig()

/**
 * 店舗 API の応答を待つ時間の上限を、要求に付ける（店舗画面の構成の「エラー」）。上限を過ぎた要求は例外で失敗し、
 * サーバーアクションでは「接続できない」の結果になる。呼ぶときに fetch を替えても（{@link UNCACHED}）効く。
 */
const timeout: Middleware = {
  onRequest({ request }) {
    return new Request(request, { signal: timeoutSignal(TIMEOUT_SECONDS, request.signal) })
  },
}

/**
 * 画面の要求と同じ追跡の ID（traceparent）を、店舗 API の要求に付ける（店舗画面の構成の「エラー」）。
 * バックエンドのログを、画面の要求から辿れるようにする。<br />
 * Next.js はキャッシュの鍵から traceparent を除くので、キャッシュする要求に付けてもキャッシュは使われる。
 * 呼ぶ口（{@link getApi}、{@link getMemberApi}）は connection() の後に返すので、必ず要求の中で動く。
 */
const trace: Middleware = {
  async onRequest({ request }) {
    const traceparent = (await headers()).get(TRACEPARENT_HEADER)
    if (traceparent !== null) {
      request.headers.set(TRACEPARENT_HEADER, traceparent)
    }
    return request
  },
}

/**
 * 店舗 API の 5xx の応答を、warn のログに出す（店舗画面の構成の「エラー」）。<br />
 * 出すのは状態コード、エラーコード、メソッド、API の定義のパス（`/orders/{orderId}` の形）だけ。
 * token、Cookie、要求の本文、クエリ（メールアドレスなどを含みうる）は出さない。
 */
const serverErrorLog: Middleware = {
  async onResponse({ request, response, schemaPath }) {
    if (response.status >= 500) {
      await log.warn('店舗 API がサーバーのエラーを返しました', {
        status: response.status,
        errorCode: await errorCodeOf(response),
        method: request.method,
        path: schemaPath,
      })
    }
    return response
  },
}

/**
 * 応答の本文のエラーコード。呼んだ側が本文を読めるよう、複製して読む。
 *
 * @param response 応答
 * @returns エラーコード。本文がエラーレスポンスでなければ null
 */
async function errorCodeOf(response: Response): Promise<string | null> {
  try {
    const body: unknown = await response.clone().json()
    return typeof body === 'object' && body !== null && 'errorCode' in body && typeof body.errorCode === 'string'
      ? body.errorCode
      : null
  } catch {
    return null
  }
}

/**
 * 店舗 API（store.yaml）の公開の口（店舗画面の構成の「API の呼び出し」）。token を付けない。<br />
 * 接続先は環境変数 STORE_API_BASE_URL。結果は Next.js の fetch のキャッシュで最大 60 秒（REVALIDATE_SECONDS）使い回す。
 */
const api = createClient<paths>({
  baseUrl: BASE_URL,
  requestInitExt: { next: { revalidate: REVALIDATE_SECONDS } },
})
api.use(timeout, trace, serverErrorLog)

/**
 * 店舗 API の会員の口（店舗画面の構成の「会員の token を付けて呼ぶ」）。会員の access token を付け、キャッシュを使わない
 * （会員ごとに違う結果を、共有のキャッシュに載せないため）。ログインしていなければ token を付けない。
 */
const memberApi = createClient<paths>({
  baseUrl: BASE_URL,
  fetch: (request: Request) => fetch(request, { cache: 'no-store' }),
})
memberApi.use(timeout, trace, serverErrorLog)
memberApi.use({
  async onRequest({ request }) {
    // 呼ぶ側が token を付けたとき（ログインの直後で、セッションの Cookie がまだないとき）は、そのまま使う
    const accessToken = request.headers.has('Authorization') ? null : await getAccessToken()
    if (accessToken !== null) {
      request.headers.set('Authorization', `Bearer ${accessToken}`)
    }
    return request
  },
})

/**
 * キャッシュを使わずに呼ぶときの、リクエストのオプション（店舗画面の構成の「API の呼び出し」）。<br />
 * 1 件を取る API（商品の詳細）に使う。Next.js のキャッシュは、取得し直した応答が 404 だと古い 200 を
 * 置き換えないので、非公開・販売終了にした商品がいつまでも表示されてしまうため。
 */
export const UNCACHED = {
  fetch: (request: Request) => fetch(request, { cache: 'no-store' }),
}

/**
 * サーバーコンポーネントから API を呼ぶときに使う。<br />
 * 画面をリクエストのたびに作るようにしてから（connection）、クライアントを返す。
 * ビルドのときに画面を作って API を呼ぶ（バックエンドが必要だ）のを避けるため。API の結果のキャッシュは別に効く。
 */
export async function getApi() {
  await connection()
  return api
}

/**
 * 会員の操作（/me/**、会員のカート、注文手続き、注文）を呼ぶときに使う（店舗画面の構成の「会員の token を付けて呼ぶ」）。<br />
 * 401 は、呼んだ側でログインの画面へ移す（取得は result.ts の unwrapMember、操作は auth.ts の redirectToLogin）。
 */
export async function getMemberApi() {
  await connection()
  return memberApi
}
