import { describe, expect, test } from 'vitest'
import { fullName, isSafeReturnPath } from './login'

describe('ログインの後に戻ってよいパス', () => {
  test('画面の中のパス（クエリを含む）なら戻ってよい', () => {
    expect(isSafeReturnPath('/products?page=2')).toBe(true)
    expect(isSafeReturnPath('/')).toBe(true)
  })

  test('外のサイト、パスでない値、文字でない値は戻らない', () => {
    expect(isSafeReturnPath('//evil.example')).toBe(false)
    expect(isSafeReturnPath('/\\evil.example')).toBe(false)
    expect(isSafeReturnPath('https://evil.example')).toBe(false)
    expect(isSafeReturnPath(undefined)).toBe(false)
    expect(isSafeReturnPath(42)).toBe(false)
  })
})

describe('氏名', () => {
  test('AC-114、135 姓と名を「姓 名」の順に並べる。片方しかなければ、それだけにする', () => {
    expect(fullName('山田', '太郎')).toBe('山田 太郎')
    expect(fullName(undefined, '太郎')).toBe('太郎')
    expect(fullName('山田', undefined)).toBe('山田')
  })
})
