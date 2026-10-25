import { describe, expect, test } from 'vitest'
import { returnPath } from '@/lib/login'

describe('ログインの後に戻る画面', () => {
  test('AC-132 移る前のパスとクエリに戻る', () => {
    expect(returnPath({ returnTo: '/products?page=2' })).toBe('/products?page=2')
  })

  test('画面の外のパスなら、トップに戻る', () => {
    expect(returnPath({ returnTo: 'https://evil.example.com/' })).toBe('/')
  })

  test('オブジェクトでない state や returnTo のない state なら、トップに戻る', () => {
    expect(returnPath(undefined)).toBe('/')
    expect(returnPath('not-an-object')).toBe('/')
    expect(returnPath(null)).toBe('/')
    expect(returnPath({})).toBe('/')
  })
})
