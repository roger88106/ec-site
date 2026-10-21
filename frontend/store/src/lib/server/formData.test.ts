import { describe, expect, test } from 'vitest'
import { readInt, readString } from '@/lib/server/formData'

/** 項目を入れたフォーム */
function form(entries: Record<string, string>): FormData {
  const data = new FormData()
  for (const [name, value] of Object.entries(entries)) {
    data.set(name, value)
  }
  return data
}

describe('フォームの項目の取り出し', () => {
  test('文字は前後の空白を除いて返す。項目がなければ空文字', () => {
    expect(readString(form({ name: '  山田 太郎 ' }), 'name')).toBe('山田 太郎')
    expect(readString(form({}), 'name')).toBe('')
  })

  test('整数はその値を返す。負の数も整数として返す（範囲は API が確認する）', () => {
    expect(readInt(form({ quantity: '3' }), 'quantity')).toBe(3)
    expect(readInt(form({ quantity: ' 10 ' }), 'quantity')).toBe(10)
    expect(readInt(form({ quantity: '-1' }), 'quantity')).toBe(-1)
  })

  test('空、数でない、小数は null', () => {
    expect(readInt(form({}), 'quantity')).toBeNull()
    expect(readInt(form({ quantity: '' }), 'quantity')).toBeNull()
    expect(readInt(form({ quantity: 'abc' }), 'quantity')).toBeNull()
    expect(readInt(form({ quantity: '1.5' }), 'quantity')).toBeNull()
    expect(readInt(form({ quantity: '1e3' }), 'quantity')).toBeNull()
  })
})
