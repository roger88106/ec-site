import { delay, HttpResponse, http as mswHttp } from 'msw'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import { type MemberJwt, refreshIfNeeded } from '@/auth/refresh'
import { nowSeconds } from '@/test/auth'
import { server, TOKEN_ENDPOINT } from '@/test/server'

/** 期限が切れた token（再取得の対象） */
function expiredToken(refreshToken = 'old-refresh'): MemberJwt {
  return { accessToken: 'old', refreshToken, expiresAt: nowSeconds() - 10 }
}

describe('refreshIfNeeded の再取得のまとめ', () => {
  /** IdP への再取得の要求の回数 */
  let requests: number

  beforeEach(() => {
    requests = 0
    server.use(
      mswHttp.post(TOKEN_ENDPOINT, async () => {
        requests += 1
        // 同時の呼び出しが重なるように、応答を少し待たせる
        await delay(20)
        return HttpResponse.json({
          access_token: `new-access-${requests}`,
          expires_in: 300,
          refresh_token: 'new-refresh',
        })
      }),
    )
    // 時刻だけを偽にする（保持の時間の確認。通信のタイマーは本物のまま）
    vi.useFakeTimers({ toFake: ['Date'] })
  })

  test('同じ refresh token の同時の再取得は、IdP への要求が 1 回で、どちらも新しい token を受け取る', async () => {
    const [first, second] = await Promise.all([
      refreshIfNeeded(expiredToken(), 60),
      refreshIfNeeded(expiredToken(), 60),
    ])

    expect(requests).toBe(1)
    expect(first).toMatchObject({ accessToken: 'new-access-1', refreshToken: 'new-refresh' })
    expect(second).toMatchObject({ accessToken: 'new-access-1', refreshToken: 'new-refresh' })
  })

  test('再取得できた後の 10 秒の間は、同じ refresh token の再取得で IdP を呼ばず、同じ結果を返す', async () => {
    const first = await refreshIfNeeded(expiredToken(), 60)

    vi.advanceTimersByTime(9_000)
    const second = await refreshIfNeeded(expiredToken(), 60)

    expect(requests).toBe(1)
    expect(second).toEqual(first)
  })

  test('再取得できた後の 10 秒を過ぎたら、同じ refresh token の再取得で IdP を呼ぶ', async () => {
    await refreshIfNeeded(expiredToken(), 60)

    vi.advanceTimersByTime(10_001)
    const second = await refreshIfNeeded(expiredToken(), 60)

    expect(requests).toBe(2)
    expect(second).toMatchObject({ accessToken: 'new-access-2' })
  })

  test('refresh token が違えば、まとめずに IdP を呼ぶ', async () => {
    await Promise.all([refreshIfNeeded(expiredToken('refresh-a'), 60), refreshIfNeeded(expiredToken('refresh-b'), 60)])

    expect(requests).toBe(2)
  })
})
