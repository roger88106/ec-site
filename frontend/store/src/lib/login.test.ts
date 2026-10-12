import { describe, expect, test } from 'vitest'
import { loginPath, memberName, needsTokenRefresh, safeReturnTo } from '@/lib/login'

describe('ログインの後に戻る画面', () => {
  test('AC-113 店舗画面の中のパス（クエリを含む）なら、そのまま戻す', () => {
    expect(safeReturnTo('/orders')).toBe('/orders')
    expect(safeReturnTo('/products?page=2&sort=NEWEST')).toBe('/products?page=2&sort=NEWEST')
  })

  test('外のサイトへは戻さず、トップにする', () => {
    expect(safeReturnTo('https://evil.example.com/')).toBe('/')
    expect(safeReturnTo('//evil.example.com/')).toBe('/')
    expect(safeReturnTo('/\\evil.example.com/')).toBe('/')
    expect(safeReturnTo('orders')).toBe('/')
  })

  test('指定がなければトップにする', () => {
    expect(safeReturnTo(null)).toBe('/')
    expect(safeReturnTo(undefined)).toBe('/')
    expect(safeReturnTo('')).toBe('/')
  })

  test('ログインの口のパスに、戻る画面をクエリで付ける', () => {
    expect(loginPath('/orders?page=2')).toBe('/login?returnTo=%2Forders%3Fpage%3D2')
    expect(loginPath('/cart', { register: true })).toBe('/login?returnTo=%2Fcart&register=1')
  })
})

describe('会員の氏名', () => {
  test('AC-114 姓と名を「姓 名」の順に並べる', () => {
    expect(memberName('会員', '太郎')).toBe('会員 太郎')
  })

  test('片方しかなければ、それだけにする', () => {
    expect(memberName('会員', undefined)).toBe('会員')
    expect(memberName(undefined, '太郎')).toBe('太郎')
    expect(memberName(undefined, undefined)).toBe('')
  })
})

describe('access token を取り直すか', () => {
  /** 今の時刻（秒） */
  const NOW = 1_800_000_000

  test('期限まで余裕があれば取り直さない', () => {
    expect(needsTokenRefresh(NOW + 61, NOW, 60)).toBe(false)
  })

  test('期限までの残りが余裕を切った・期限を過ぎたら取り直す', () => {
    expect(needsTokenRefresh(NOW + 60, NOW, 60)).toBe(true)
    expect(needsTokenRefresh(NOW - 1, NOW, 0)).toBe(true)
  })

  test('期限が分からなければ取り直す', () => {
    expect(needsTokenRefresh(undefined, NOW, 60)).toBe(true)
  })
})
