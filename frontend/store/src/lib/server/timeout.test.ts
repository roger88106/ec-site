import { delay, HttpResponse, http as mswHttp } from 'msw'
import { customFetch } from 'next-auth'
import { describe, expect, test, vi } from 'vitest'
import { withActionResult } from '@/actions/withActionResult'
import { getApi, getMemberApi } from '@/api/client'
import { authState, signInAs } from '@/test/auth'
import { IDP, STORE_API, server } from '@/test/server'
import '@/auth'
import { type MemberJwt, refreshIfNeeded } from '@/auth/refresh'
import { connectionFailedMessage } from '@/lib/actionResult'

// 上限は、モジュールを読み込むときに設定から読むので、読み込む前に短くする（0.05 秒）
vi.hoisted(() => {
  process.env.STORE_API_TIMEOUT_SECONDS = '0.05'
  process.env.AUTH_KEYCLOAK_TIMEOUT_SECONDS = '0.05'
})

/** IdP の token の口 */
const TOKEN_ENDPOINT = `${IDP}/protocol/openid-connect/token`

/** 応答を返さない（上限で打ち切られるまで待たせる）ハンドラー */
const hang = async () => {
  await delay('infinite')
  return HttpResponse.json({})
}

describe('待つ時間の上限（店舗画面のサーバー → バックエンド、IdP）', () => {
  test('AC-302 店舗 API の公開の口は、上限を過ぎたら待つのをやめて失敗する', async () => {
    server.use(mswHttp.get(`${STORE_API}/categories`, hang))

    await expect((await getApi()).GET('/categories')).rejects.toThrow(expect.objectContaining({ name: 'TimeoutError' }))
  })

  test('AC-302、303 会員の口が上限を過ぎたら、サーバーアクションは「接続できない」の結果を返す', async () => {
    signInAs()
    server.use(mswHttp.get(`${STORE_API}/me/orders`, hang))

    const result = await withActionResult(async () => (await getMemberApi()).GET('/me/orders'))

    expect(result).toEqual({ ok: false, message: connectionFailedMessage() })
  })

  test('AC-302 token の更新が上限を過ぎたら、IdP に接続できないときと同じく、ログインの状態を残す', async () => {
    server.use(mswHttp.post(TOKEN_ENDPOINT, hang))
    const token: MemberJwt = { accessToken: 'old', refreshToken: 'refresh', expiresAt: Math.floor(Date.now() / 1000) }

    expect(await refreshIfNeeded(token, 60)).toEqual(token)
  })

  test('AC-302 ログインの IdP への呼び出し（Auth.js の provider）は、上限を過ぎたら待つのをやめて失敗する', async () => {
    server.use(mswHttp.get(`${IDP}/.well-known/openid-configuration`, hang))
    // Auth.js の provider の型は、options の customFetch（Symbol の鍵）を持たないため、型を合わせる
    const provider = authState.config?.providers?.[0] as { options: Record<symbol, typeof fetch> }
    const fetchWithLimit = provider.options[customFetch]

    expect(fetchWithLimit).toBeTypeOf('function')
    await expect(fetchWithLimit(`${IDP}/.well-known/openid-configuration`)).rejects.toThrow(
      expect.objectContaining({ name: 'TimeoutError' }),
    )
  })
})
