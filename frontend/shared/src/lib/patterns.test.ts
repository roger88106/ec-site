import { describe, expect, test } from 'vitest'
import { isUuid } from './patterns'

describe('値の形の判定', () => {
  test('UUID の形か（大文字も認める）', () => {
    expect(isUuid('0b5d6c1e-8f3a-4d2b-9c7e-1a2b3c4d5e6f')).toBe(true)
    expect(isUuid('0B5D6C1E-8F3A-4D2B-9C7E-1A2B3C4D5E6F')).toBe(true)
    expect(isUuid('0b5d6c1e-8f3a-4d2b-9c7e')).toBe(false)
    expect(isUuid('not-a-uuid')).toBe(false)
  })
})
