import { describe, expect, test } from 'vitest'
import { ApiError, unwrap, unwrapMember } from '@/api/result'
import { expectNotFound, redirectedTo, resetRequest } from '@/test/next'

/** openapi-fetch の結果を作る */
function result<T>(status: number, data?: T, error?: unknown) {
  return { data, error, response: new Response(null, { status }) }
}

/** 投げた例外（投げなければ undefined） */
function thrown(run: () => unknown): unknown {
  try {
    run()
  } catch (error) {
    return error
  }
  return undefined
}

describe('API の結果の取り出し', () => {
  test('成功なら本文を返す', () => {
    expect(unwrap(result(200, { id: 'a' }))).toEqual({ id: 'a' })
  })

  test('404 は「見つかりません」の画面にする', async () => {
    await expectNotFound(async () => unwrap(result(404, undefined, { message: '見つかりません。' })))
  })

  test('それ以外の失敗は、エラーレスポンスのメッセージと状態を持つ例外を投げる', () => {
    const error = thrown(() => unwrap(result(409, undefined, { message: 'この注文は支払えません。' })))

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 409, message: 'この注文は支払えません。' })
  })

  test('エラーレスポンスにメッセージがなければ、状態を添えた決まりの文にする', () => {
    const error = thrown(() => unwrap(result(500)))

    expect(error).toMatchObject({ status: 500, message: expect.stringContaining('500') })
  })
})

describe('会員の口の結果の取り出し', () => {
  test('成功なら本文を返す', async () => {
    expect(await unwrapMember(result(200, { id: 'a' }))).toEqual({ id: 'a' })
  })

  test('401 なら、いまの画面に戻るようにログインの画面へ移る', async () => {
    resetRequest({ headers: { 'x-pathname': '/orders' } })

    expect(await redirectedTo(() => unwrapMember(result(401)))).toBe('/login?returnTo=%2Forders')
  })
})
