import { describe, expect, test } from 'vitest'
import { loadServerConfig, REQUIRED_IN_PRODUCTION, readServerConfig } from '@/auth/config'

/** 本番で渡す値のすべて */
const ALL_SET = {
  NODE_ENV: 'production',
  AUTH_SECRET: 'secret',
  AUTH_KEYCLOAK_SECRET: 'client-secret',
  AUTH_KEYCLOAK_ISSUER: 'https://idp.example/realms/ec-store',
  AUTH_KEYCLOAK_ID: 'ec-store-web',
  STORE_API_BASE_URL: 'https://api.example/api/store',
  AUTH_URL: 'https://shop.example',
}

describe('店舗画面のサーバーの設定', () => {
  test('本番のモードで必須の値がなければ、足りない名前を並べて失敗する', () => {
    const { AUTH_SECRET: _secret, AUTH_KEYCLOAK_SECRET: _clientSecret, ...missingTwo } = ALL_SET

    expect(() => loadServerConfig(missingTwo)).toThrow(/AUTH_SECRET.*AUTH_KEYCLOAK_SECRET/)
  })

  test('本番のモードで空の値は、ないものとして扱う', () => {
    expect(() => loadServerConfig({ ...ALL_SET, AUTH_KEYCLOAK_ISSUER: '' })).toThrow(/AUTH_KEYCLOAK_ISSUER/)
  })

  test('本番のモードで必須の値がそろえば、その値を使う', () => {
    const config = loadServerConfig(ALL_SET)

    expect(config.issuer).toBe(ALL_SET.AUTH_KEYCLOAK_ISSUER)
    expect(config.clientId).toBe(ALL_SET.AUTH_KEYCLOAK_ID)
    expect(config.clientSecret).toBe(ALL_SET.AUTH_KEYCLOAK_SECRET)
    expect(config.secret).toBe(ALL_SET.AUTH_SECRET)
    expect(config.apiBaseUrl).toBe(ALL_SET.STORE_API_BASE_URL)
    expect(config.publicUrl).toBe(ALL_SET.AUTH_URL)
  })

  test('開発のモードとテストでは、値がなければローカル用の既定の値を使う', () => {
    const config = loadServerConfig({ NODE_ENV: 'development' })

    expect(config.issuer).toBe('http://localhost:8180/realms/ec-store')
    expect(config.apiBaseUrl).toBe('http://localhost:8080/api/store')
    expect(loadServerConfig({ NODE_ENV: 'test' }).clientId).toBe('ec-store-web')
  })

  test('AC-302 待つ時間の上限は、店舗 API が既定 30 秒、IdP が既定 10 秒で、環境変数で変えられる', () => {
    const defaults = loadServerConfig({ NODE_ENV: 'development' })
    expect(defaults.apiTimeoutSeconds).toBe(30)
    expect(defaults.idpTimeoutSeconds).toBe(10)

    const config = loadServerConfig({
      ...ALL_SET,
      STORE_API_TIMEOUT_SECONDS: '45',
      AUTH_KEYCLOAK_TIMEOUT_SECONDS: '5',
    })
    expect(config.apiTimeoutSeconds).toBe(45)
    expect(config.idpTimeoutSeconds).toBe(5)
  })

  test.each(['0', '-1', 'abc'])('AC-302 待つ時間の上限が正の数でなければ（%s）、名前を出して失敗する', (value) => {
    expect(() => loadServerConfig({ NODE_ENV: 'development', STORE_API_TIMEOUT_SECONDS: value })).toThrow(
      /STORE_API_TIMEOUT_SECONDS/,
    )
    expect(() => loadServerConfig({ ...ALL_SET, AUTH_KEYCLOAK_TIMEOUT_SECONDS: value })).toThrow(
      /AUTH_KEYCLOAK_TIMEOUT_SECONDS/,
    )
  })

  test('必須の値は 6 つ', () => {
    expect([...REQUIRED_IN_PRODUCTION]).toEqual([
      'AUTH_SECRET',
      'AUTH_KEYCLOAK_SECRET',
      'AUTH_KEYCLOAK_ISSUER',
      'AUTH_KEYCLOAK_ID',
      'STORE_API_BASE_URL',
      'AUTH_URL',
    ])
  })
})

describe('店舗画面の Cookie の接頭辞の設定', () => {
  test('DC-104-12 STORE_COOKIE_PREFIX は任意で、なければ空（今の名前のまま）。本番でも必須にしない', () => {
    expect(loadServerConfig(ALL_SET).cookiePrefix).toBe('')
    expect(loadServerConfig({ NODE_ENV: 'development' }).cookiePrefix).toBe('')
    expect(readServerConfig({}).cookiePrefix).toBe('')
    expect([...REQUIRED_IN_PRODUCTION]).not.toContain('STORE_COOKIE_PREFIX')
  })

  test('DC-104-12 STORE_COOKIE_PREFIX の値を使う', () => {
    expect(loadServerConfig({ ...ALL_SET, STORE_COOKIE_PREFIX: 's1-' }).cookiePrefix).toBe('s1-')
    expect(readServerConfig({ STORE_COOKIE_PREFIX: 'Slot_2-' }).cookiePrefix).toBe('Slot_2-')
  })

  test.each(['s 1-', 's1;', 's1=', 'あ-', 's1.'])(
    'DC-104-12 STORE_COOKIE_PREFIX は英数字と - _ だけ。それ以外（%s）は名前を出して失敗する',
    (value) => {
      expect(() => loadServerConfig({ NODE_ENV: 'development', STORE_COOKIE_PREFIX: value })).toThrow(
        /STORE_COOKIE_PREFIX/,
      )
    },
  )
})
