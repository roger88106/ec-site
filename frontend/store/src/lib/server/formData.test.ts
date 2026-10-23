import { describe, expect, test } from 'vitest'
import { readIntOrNaN, readString } from '@/lib/server/formData'
import { form } from '@/test/fixtures'

describe('フォームの項目の取り出し', () => {
  test('文字は前後の空白を除いて返す。項目がなければ空文字', () => {
    expect(readString(form({ name: '  山田 太郎 ' }), 'name')).toBe('山田 太郎')
    expect(readString(form({}), 'name')).toBe('')
  })

  test('整数はその値を返す。前後の空白は除き、負の数も整数として返す（範囲は API が確認する）', () => {
    expect(readIntOrNaN(form({ quantity: '3' }), 'quantity')).toBe(3)
    expect(readIntOrNaN(form({ quantity: ' 10 ' }), 'quantity')).toBe(10)
    expect(readIntOrNaN(form({ quantity: '-1' }), 'quantity')).toBe(-1)
  })

  test('空、数でない、小数、指数の書き方は NaN を返す', () => {
    expect(readIntOrNaN(form({}), 'quantity')).toBeNaN()
    expect(readIntOrNaN(form({ quantity: '' }), 'quantity')).toBeNaN()
    expect(readIntOrNaN(form({ quantity: 'abc' }), 'quantity')).toBeNaN()
    expect(readIntOrNaN(form({ quantity: '1.5' }), 'quantity')).toBeNaN()
    expect(readIntOrNaN(form({ quantity: '1e3' }), 'quantity')).toBeNaN()
  })
})
