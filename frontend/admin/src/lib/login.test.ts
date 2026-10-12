import { describe, expect, test } from 'vitest'
import { operatorName, returnPath } from '@/lib/login'

describe('運営者の氏名', () => {
  test('AC-135 姓と名を「姓 名」の順に並べる', () => {
    expect(operatorName('管理', '花子')).toBe('管理 花子')
  })

  test('片方しかなければ、それだけにする', () => {
    expect(operatorName('管理', undefined)).toBe('管理')
    expect(operatorName(undefined, undefined)).toBe('')
  })
})

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
