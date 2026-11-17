import { describe, expect, test } from 'vitest'
import { enumParam, pageParam, writeParams } from '@/lib/searchParams'

describe('URL のページ番号の読み取り', () => {
  test('1 以上の整数は、そのままページ番号にする', () => {
    expect(pageParam(new URLSearchParams('page=1'))).toBe(1)
    expect(pageParam(new URLSearchParams('page=3'))).toBe(3)
  })

  test('パラメータがないときは 1 ページ目にする', () => {
    expect(pageParam(new URLSearchParams())).toBe(1)
  })

  test('0、負の数は 1 ページ目にする', () => {
    expect(pageParam(new URLSearchParams('page=0'))).toBe(1)
    expect(pageParam(new URLSearchParams('page=-2'))).toBe(1)
  })

  test('小数は 1 ページ目にする', () => {
    expect(pageParam(new URLSearchParams('page=1.5'))).toBe(1)
  })

  test('空と文字は 1 ページ目にする', () => {
    expect(pageParam(new URLSearchParams('page='))).toBe(1)
    expect(pageParam(new URLSearchParams('page=abc'))).toBe(1)
  })
})

describe('クエリの列挙の値を読む', () => {
  const values = ['A', 'B'] as const

  test('列挙にある値は、そのまま返す', () => {
    expect(enumParam(values, 'B', null)).toBe('B')
  })

  test('列挙にない値は、既定を返す', () => {
    expect(enumParam(values, 'C', 'A')).toBe('A')
    expect(enumParam(values, '', null)).toBeNull()
  })

  test('値がないときは、既定を返す', () => {
    expect(enumParam(values, null, 'A')).toBe('A')
  })
})

describe('URL のクエリの書き出し', () => {
  test('空の値と既定と同じ値は書かず、残りを項目の順に書く', () => {
    const params = writeParams({ code: 'A', name: '', status: undefined, returned: false, page: 1 }, { page: 1 })
    expect(params.toString()).toBe('code=A')
  })

  test('真と既定と違う値は文字にして書く', () => {
    expect(writeParams({ returned: true, page: 2 }, { page: 1 }).toString()).toBe('returned=true&page=2')
  })
})
