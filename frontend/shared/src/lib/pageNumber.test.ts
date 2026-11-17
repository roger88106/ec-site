import { describe, expect, test } from 'vitest'
import { toPageNumber } from './pageNumber'

describe('ページ番号', () => {
  test('1 以上の整数はそのページ', () => {
    expect(toPageNumber('1')).toBe(1)
    expect(toPageNumber('3')).toBe(3)
  })

  test('なし、0 以下、整数でない値、数でない値は 1 ページ目', () => {
    expect(toPageNumber(null)).toBe(1)
    expect(toPageNumber(undefined)).toBe(1)
    expect(toPageNumber('')).toBe(1)
    expect(toPageNumber('0')).toBe(1)
    expect(toPageNumber('-2')).toBe(1)
    expect(toPageNumber('1.5')).toBe(1)
    expect(toPageNumber('abc')).toBe(1)
  })
})
