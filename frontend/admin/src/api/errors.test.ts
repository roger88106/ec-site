import { describe, expect, test } from 'vitest'
import { ApiError, errorMessage, fieldErrors, toApiError } from '@/api/errors'

/** Problem Details の応答の本文を作る */
const problem = (status: number, errorCode: string, message: string) => ({
  status,
  errorCode,
  message,
  traceId: 'trace',
})

describe('API のエラー', () => {
  test('エラーレスポンスの message をそのまま出す', () => {
    const error = toApiError(
      409,
      problem(409, 'PRODUCT_CODE_ALREADY_EXISTS', '商品品番 ABC-001 はすでに使われています。'),
    )
    expect(errorMessage(error)).toBe('商品品番 ABC-001 はすでに使われています。')
    expect(error.isConcurrentModification).toBe(false)
  })

  test('バージョン不一致（409）は再読み込みを案内する', () => {
    const error = toApiError(409, problem(409, 'CONCURRENT_MODIFICATION', '他の人が先に更新しました。'))
    expect(error.isConcurrentModification).toBe(true)
    expect(errorMessage(error)).toBe('他の人が先に更新しました。再読み込みしてください')
  })

  test('本文がエラーレスポンスでなければ、HTTP の状態を出す', () => {
    expect(errorMessage(toApiError(502, 'Bad Gateway'))).toBe('通信に失敗しました（HTTP 502）')
  })

  test('AC-141 権限がない（403）は、エラーレスポンスの message を出し、本文がなくても権限がないことを出す', () => {
    expect(errorMessage(toApiError(403, problem(403, 'FORBIDDEN', 'この操作を行う権限がありません。')))).toBe(
      'この操作を行う権限がありません。',
    )
    expect(errorMessage(toApiError(403, undefined))).toBe('この操作を行う権限がありません。')
  })

  test('通信そのものの失敗は、接続できなかったことを出す', () => {
    expect(errorMessage(new TypeError('Failed to fetch'))).toBe('サーバーに接続できませんでした。')
  })

  test('入力検証エラーは、項目ごとのメッセージを取り出せる', () => {
    const error = toApiError(400, {
      ...problem(400, 'VALIDATION_ERROR', '入力に誤りがあります。'),
      errors: [{ field: 'name', message: '商品名を入力してください。' }],
    })
    expect(fieldErrors(error)).toEqual([{ field: 'name', message: '商品名を入力してください。' }])
    expect(fieldErrors(new Error('x'))).toEqual([])
  })

  test('4xx だけを、繰り返さないエラーとして扱う', () => {
    expect(new ApiError(404, undefined).isClientError).toBe(true)
    expect(new ApiError(500, undefined).isClientError).toBe(false)
  })
})
