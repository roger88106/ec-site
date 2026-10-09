import 'server-only'
import createClient from 'openapi-fetch'
import type { paths } from '@/api/generated/store'

/** API の結果をキャッシュする秒数（NFR-1：変更は 1 分以内に C 側へ反映する） */
const REVALIDATE_SECONDS = 60

/**
 * 店舗 API（store.yaml）のクライアント（店舗画面の構成 第 4 節）。サーバーコンポーネントからだけ使う。<br />
 * 接続先は環境変数 STORE_API_BASE_URL。結果は Next.js の fetch のキャッシュで最大 60 秒使い回す。
 */
export const api = createClient<paths>({
  baseUrl: process.env.STORE_API_BASE_URL ?? 'http://localhost:8080/api/store',
  requestInitExt: { next: { revalidate: REVALIDATE_SECONDS } },
})
