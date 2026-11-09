import { describe, expect, test } from 'vitest'
import { toHalfWidth } from '@/lib/halfWidth'

describe('郵便番号と電話番号の半角への変換', () => {
  test('BR-416-1 全角の数字と全角のハイフンマイナス（U+FF0D）を半角にする', () => {
    expect(toHalfWidth('１００－０００１')).toBe('100-0001')
  })

  test.each([
    ['長音符 U+30FC', 'ー'],
    ['半角の長音符 U+FF70（NFKC で U+30FC になる）', 'ｰ'],
    ['マイナス記号 U+2212', '−'],
    ['ハイフン U+2010', '‐'],
    ['非改行ハイフン U+2011', '‑'],
    ['エヌダッシュ U+2013', '–'],
    ['エムダッシュ U+2014', '—'],
    ['水平線 U+2015', '―'],
  ])('BR-416-1 ハイフンの類（%s）を - にする', (_name, hyphen) => {
    expect(toHalfWidth(`100${hyphen}0001`)).toBe('100-0001')
  })

  test('BR-416-1 半角の入力はそのまま返す', () => {
    expect(toHalfWidth('100-0001')).toBe('100-0001')
    expect(toHalfWidth('0312345678')).toBe('0312345678')
    expect(toHalfWidth('')).toBe('')
  })
})
