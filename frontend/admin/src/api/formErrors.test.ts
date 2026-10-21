import { describe, expect, test, vi } from 'vitest'
import { toApiError } from '@/api/errors'
import { setServerFieldErrors } from '@/api/formErrors'

/** テスト用のフォームの値 */
type Form = { name: string; priceExclTax: string }

describe('サーバーの入力検証エラーの表示', () => {
  const error = toApiError(400, {
    status: 400,
    errorCode: 'INVALID_REQUEST',
    message: '入力に誤りがあります。',
    traceId: 'trace',
    errors: [
      { field: 'name', message: '商品名を入力してください。' },
      { field: 'version', message: 'バージョンを指定してください。' },
    ],
  })

  test('フォームにある項目は、その入力欄に付ける', () => {
    const setError = vi.fn()
    const applied = setServerFieldErrors<Form>(error, setError, ['name', 'priceExclTax'])
    expect(setError).toHaveBeenCalledWith('name', { type: 'server', message: '商品名を入力してください。' })
    expect(applied).toEqual(['name'])
  })

  test('フォームにない項目は付けない（フォームの下に出す）', () => {
    const setError = vi.fn()
    setServerFieldErrors<Form>(error, setError, ['priceExclTax'])
    expect(setError).not.toHaveBeenCalled()
  })
})
