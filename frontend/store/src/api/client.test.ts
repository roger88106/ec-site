import { HttpResponse, http as mswHttp } from 'msw'
import { describe, expect, test } from 'vitest'
import { getMemberApi } from '@/api/client'
import { TRACEPARENT_HEADER } from '@/lib/server/trace'
import { resetRequest } from '@/test/next'
import { STORE_API, server } from '@/test/server'

/** 要求のヘッダーの traceparent（proxy が付けたもの） */
const TRACEPARENT = '00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01'

describe('店舗 API の口', () => {
  test('会員の口の要求には、いまの要求の traceparent を付ける（バックエンドのログと同じ追跡の ID にする）', async () => {
    resetRequest({ headers: { [TRACEPARENT_HEADER]: TRACEPARENT } })
    let received: string | null = null
    server.use(
      mswHttp.get(`${STORE_API}/me/orders`, ({ request }) => {
        received = request.headers.get(TRACEPARENT_HEADER)
        return HttpResponse.json({ items: [], page: 1, size: 20, totalItems: 0, totalPages: 0 })
      }),
    )

    await (await getMemberApi()).GET('/me/orders')

    expect(received).toBe(TRACEPARENT)
  })
})
