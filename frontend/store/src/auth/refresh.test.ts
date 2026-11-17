import { delay, HttpResponse, http as mswHttp } from 'msw'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import { type MemberJwt, refreshIfNeeded } from '@/auth/refresh'
import { nowSeconds } from '@/test/auth'
import { networkError, server, TOKEN_ENDPOINT } from '@/test/server'
import { recordStderr } from '@/test/stderr'

/** 期限が切れた token（再取得の対象） */
function expiredToken(refreshToken = 'old-refresh'): MemberJwt {
  return { accessToken: 'old', refreshToken, expiresAt: nowSeconds() - 10 }
}

describe('refreshIfNeeded の再取得のまとめ', () => {
  /** IdP への再取得のリクエストの回数 */
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

  test('同じ refresh token の同時の再取得は、IdP へのリクエストが 1 回で、どちらも新しい token を受け取る', async () => {
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

  test('保持した結果から、呼び出し側の token には更新された値だけを反映する', async () => {
    await refreshIfNeeded({ ...expiredToken(), name: '最初の呼び出し' }, 60)

    const second = await refreshIfNeeded({ ...expiredToken(), name: '後の呼び出し', idToken: 'later-id' }, 60)

    expect(requests).toBe(1)
    expect(second).toMatchObject({
      name: '後の呼び出し',
      idToken: 'later-id',
      accessToken: 'new-access-1',
      refreshToken: 'new-refresh',
    })
  })

  test('refresh token が違えば、まとめずに IdP を呼ぶ', async () => {
    await Promise.all([refreshIfNeeded(expiredToken('refresh-a'), 60), refreshIfNeeded(expiredToken('refresh-b'), 60)])

    expect(requests).toBe(2)
  })
})

describe('refreshIfNeeded の失敗の保持', () => {
  /** IdP への再取得のリクエストの回数 */
  let requests: number

  /** 失敗の応答の本文に入れる文字（ログに出ないことの確認に使う） */
  const BODY_SECRET = 'idp-response-body-secret'

  /** 失敗の例（応答の作り方）。IdP に届かない失敗は、別に networkError で作る */
  const failures: [string, () => Response][] = [
    ['IdP の障害（500、別のエラー）', () => HttpResponse.json({ error: BODY_SECRET }, { status: 500 })],
    ['IdP の障害（503、本文なし）', () => new HttpResponse(null, { status: 503 })],
    ['前段のプロキシーが返す HTML の 400', () => HttpResponse.html(`<html>${BODY_SECRET}</html>`, { status: 400 })],
    ['成功の応答の本文が JSON でない', () => new HttpResponse(BODY_SECRET, { status: 200 })],
    ['成功の応答に access token がない', () => HttpResponse.json({ expires_in: 300 }, { status: 200 })],
  ]

  /** 失敗する IdP を用意する */
  function stubFailingIdp(response: () => Response) {
    requests = 0
    server.use(
      mswHttp.post(TOKEN_ENDPOINT, () => {
        requests += 1
        return response()
      }),
    )
  }

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
  })

  test.each(failures)('%s の後の 30 秒の間は、IdP を呼ばずに今の token を返す', async (_, response) => {
    recordStderr()
    stubFailingIdp(response)
    const token = expiredToken()
    expect(await refreshIfNeeded(token, 60)).toEqual(token)

    vi.advanceTimersByTime(29_000)

    expect(await refreshIfNeeded(token, 60)).toEqual(token)
    expect(requests).toBe(1)
  })

  test.each(failures)('%s の後の 30 秒を過ぎたら、IdP を呼ぶ', async (_, response) => {
    recordStderr()
    stubFailingIdp(response)
    const token = expiredToken()
    await refreshIfNeeded(token, 60)

    vi.advanceTimersByTime(30_001)
    await refreshIfNeeded(token, 60)

    expect(requests).toBe(2)
  })

  test('IdP に届かなかった後の 30 秒の間は、IdP を呼ばずに今の token を返し、30 秒を過ぎたら呼ぶ', async () => {
    recordStderr()
    let calls = 0
    server.use(
      mswHttp.post(TOKEN_ENDPOINT, () => {
        calls += 1
        return HttpResponse.error()
      }),
    )
    const token = expiredToken()
    expect(await refreshIfNeeded(token, 60)).toEqual(token)

    vi.advanceTimersByTime(29_000)
    expect(await refreshIfNeeded(token, 60)).toEqual(token)
    expect(calls).toBe(1)

    vi.advanceTimersByTime(1_001)
    await refreshIfNeeded(token, 60)
    expect(calls).toBe(2)
  })

  test('失敗の保持は refresh token ごとで、別の refresh token の再取得は IdP を呼ぶ', async () => {
    recordStderr()
    stubFailingIdp(() => new HttpResponse(null, { status: 503 }))

    await refreshIfNeeded(expiredToken('refresh-a'), 60)
    await refreshIfNeeded(expiredToken('refresh-b'), 60)

    expect(requests).toBe(2)
  })

  test('失敗したら warn が 1 件出て、token と応答の本文を含まない', async () => {
    const stderr = recordStderr()
    stubFailingIdp(() => HttpResponse.json({ error: BODY_SECRET }, { status: 500 }))
    const token: MemberJwt = { ...expiredToken('secret-refresh-token'), accessToken: 'secret-access-token' }

    await refreshIfNeeded(token, 60)

    expect(stderr.entries()).toEqual([
      expect.objectContaining({ level: 'warn', reason: 'error-response', status: 500 }),
    ])
    for (const secret of ['secret-refresh-token', 'secret-access-token', BODY_SECRET]) {
      expect(stderr.text()).not.toContain(secret)
    }
  })

  test('IdP に届かなければ、状態コードのない warn が 1 件出る', async () => {
    const stderr = recordStderr()
    server.use(networkError('post', TOKEN_ENDPOINT))

    await refreshIfNeeded(expiredToken(), 60)

    const [entry] = stderr.entries()
    expect(stderr.entries()).toHaveLength(1)
    expect(entry).toMatchObject({ level: 'warn', reason: 'unreachable' })
    expect(entry.status ?? null).toBeNull()
  })

  test('保持した失敗を返すときは、warn が増えない', async () => {
    const stderr = recordStderr()
    stubFailingIdp(() => new HttpResponse(null, { status: 503 }))
    const token = expiredToken()
    await refreshIfNeeded(token, 60)

    await refreshIfNeeded(token, 60)
    await refreshIfNeeded(token, 60)

    expect(stderr.entries()).toHaveLength(1)
  })

  test('IdP が refresh token を拒否したとき（invalid_grant）は、warn を出さない', async () => {
    const stderr = recordStderr()
    stubFailingIdp(() => HttpResponse.json({ error: 'invalid_grant' }, { status: 400 }))

    expect(await refreshIfNeeded(expiredToken(), 60)).toBeNull()

    expect(stderr.entries()).toEqual([])
  })

  test('再取得が例外で終わったときは、結果を保持せず、次の再取得で IdP を呼ぶ', async () => {
    stubFailingIdp(() => new HttpResponse(null, { status: 503 }))
    // warn の書き込みが例外になり、結果を返すはずの再取得が、例外で終わる
    vi.spyOn(process.stderr, 'write').mockImplementation(() => {
      throw new Error('書き込めない')
    })
    const token = expiredToken()
    await expect(refreshIfNeeded(token, 60)).rejects.toThrow('書き込めない')

    vi.restoreAllMocks()
    recordStderr()
    expect(await refreshIfNeeded(token, 60)).toEqual(token)
    expect(requests).toBe(2)
  })

  test('IdP が refresh token を拒否した後の 10 秒の間は、IdP を呼ばずにログインしていないものとし、10 秒を過ぎたら呼ぶ', async () => {
    stubFailingIdp(() => HttpResponse.json({ error: 'invalid_grant' }, { status: 400 }))
    const token = expiredToken()
    expect(await refreshIfNeeded(token, 60)).toBeNull()

    vi.advanceTimersByTime(9_000)
    expect(await refreshIfNeeded(token, 60)).toBeNull()
    expect(requests).toBe(1)

    vi.advanceTimersByTime(1_001)
    await refreshIfNeeded(token, 60)
    expect(requests).toBe(2)
  })
})
