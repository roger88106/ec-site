import { describe, expect, test } from 'vitest'
import { pageParam } from '@/lib/searchParams'

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
