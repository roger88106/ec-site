import { describe, expect, test } from 'vitest'
import { returnPath } from '@/lib/login'

describe('ログインの後に戻る画面', () => {
  test('AC-132 移る前のパスとクエリに戻る', () => {
    expect(returnPath({ returnTo: '/products?page=2' })).toBe('/products?page=2')
  })

  test('画面の中のパスでなければ、トップに戻る', () => {
    expect(returnPath(undefined)).toBe('/')
    expect(returnPath({ returnTo: 'https://evil.example.com/' })).toBe('/')
    expect(returnPath({ returnTo: '//evil.example.com/' })).toBe('/')
    expect(returnPath({ returnTo: '/\\evil.example.com/' })).toBe('/')
    expect(returnPath('not-an-object')).toBe('/')
  })
})
