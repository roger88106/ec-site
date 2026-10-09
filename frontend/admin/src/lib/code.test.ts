import { describe, expect, test } from 'vitest'
import { normalizeCode } from '@/lib/code'

describe('品番の入力', () => {
  test('AC-15 半角の英小文字は大文字にする', () => {
    expect(normalizeCode('abc-001')).toBe('ABC-001')
    expect(normalizeCode('Abc-001-wh-m')).toBe('ABC-001-WH-M')
  })

  test('英小文字以外はそのまま残す（形式の誤りはサーバーが検証する）', () => {
    expect(normalizeCode('ABC--1')).toBe('ABC--1')
    expect(normalizeCode('ａｂｃ')).toBe('ａｂｃ')
    expect(normalizeCode('')).toBe('')
  })
})
