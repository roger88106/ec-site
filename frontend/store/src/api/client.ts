import 'server-only'
import { connection } from 'next/server'
import createClient from 'openapi-fetch'
import type { paths } from '@/api/generated/store'

/**
 * API の結果をキャッシュする秒数（NFR-1：変更は 1 分以内に C 側へ反映する）。<br />
 * 環境変数 STORE_API_REVALIDATE_SECONDS で短くできる（E2E で、60 秒を待たずに一覧への反映を確かめるため）。
 */
const REVALIDATE_SECONDS = Number(process.env.STORE_API_REVALIDATE_SECONDS ?? 60)

/**
 * 店舗 API（store.yaml）のクライアント（店舗画面の構成 第 4 節）。<br />
 * 接続先は環境変数 STORE_API_BASE_URL。結果は Next.js の fetch のキャッシュで最大 60 秒（REVALIDATE_SECONDS）使い回す。
 */
const api = createClient<paths>({
  baseUrl: process.env.STORE_API_BASE_URL ?? 'http://localhost:8080/api/store',
  requestInitExt: { next: { revalidate: REVALIDATE_SECONDS } },
})

/**
 * キャッシュを使わずに呼ぶときの、リクエストのオプション（店舗画面の構成 第 4 節）。<br />
 * 1 件を取る API（商品の詳細）に使う。Next.js のキャッシュは、取得し直した応答が 404 だと古い 200 を
 * 置き換えないので、非公開・販売終了にした商品がいつまでも表示されてしまうため。
 */
export const UNCACHED = {
  fetch: (request: Request) => fetch(request, { cache: 'no-store' }),
}

/**
 * サーバーコンポーネントから API を呼ぶときに使う。<br />
 * 画面をリクエストのたびに作るようにしてから（connection）、クライアントを返す。
 * ビルドのときに画面を作って API を呼ぶ（バックエンドが要る）のを避けるため。API の結果のキャッシュは別に効く。
 */
export async function getApi() {
  await connection()
  return api
}
