import { afterAll, afterEach, beforeEach, vi } from 'vitest'
import { resetAuth } from '@/test/auth'
import { resetRequest } from '@/test/next'
import { server } from '@/test/server'

// サーバーの処理（サーバーアクション、api/、auth.ts、proxy.ts）を Node の上で呼ぶための置き換え（店舗画面の構成 第 7 節）。
// next/navigation は置き換えず、本物の redirect・notFound・unstable_rethrow を使う
vi.mock('server-only', () => ({}))
vi.mock('next/headers', async () => {
  const next = await import('@/test/next')
  return { cookies: async () => next.cookieStore, headers: async () => next.currentHeaders() }
})
vi.mock('next/cache', async () => {
  const next = await import('@/test/next')
  return {
    revalidatePath: (path: string) => {
      next.revalidatedPaths.push(path)
    },
  }
})
vi.mock('next/server', async (importOriginal) => ({
  ...(await importOriginal<typeof import('next/server')>()),
  // 画面をリクエストのたびに作る印。テストでは何もしない
  connection: async () => {},
}))
vi.mock('next-auth', async () => (await import('@/test/auth')).nextAuthMock)
vi.mock('next-auth/jwt', async () => (await import('@/test/auth')).nextAuthJwtMock)
vi.mock('next-auth/providers/keycloak', () => ({ default: (options: object) => ({ id: 'keycloak', options }) }))

// テストのファイルを読み込む前に始める。API のクライアント（openapi-fetch）は、作ったときの fetch を持ち続けるので、
// MSW が fetch を差し替えた後に作らせる必要がある。
// ハンドラーのない API が呼ばれたら、その要求を失敗させ、テストの終わりにテストを失敗させる（要求の失敗だけでは、
// 処理が「通信の失敗」の結果を返すだけで、テストが通ってしまうことがあるため）
const unhandledRequests: string[] = []
server.listen({
  onUnhandledRequest: (request, print) => {
    unhandledRequests.push(`${request.method} ${request.url}`)
    print.error()
  },
})

beforeEach(() => {
  resetRequest()
  resetAuth()
})

afterEach(() => {
  server.resetHandlers()
  const requests = unhandledRequests.splice(0)
  if (requests.length > 0) {
    throw new Error(`ハンドラーのない API が呼ばれた：${requests.join('、')}`)
  }
})

afterAll(() => {
  server.close()
})
