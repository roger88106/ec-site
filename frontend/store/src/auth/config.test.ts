import { describe, expect, test } from 'vitest'
import { loadServerConfig, readServerConfig } from '@/auth/config'
import { PRODUCTION_ENV } from '@/test/env'

describe('店舗画面のサーバーの設定', () => {
  test('本番のモードで必須の値がなければ、足りない名前を並べて失敗する', () => {
    const { AUTH_SECRET: _secret, AUTH_KEYCLOAK_SECRET: _clientSecret, ...missingTwo } = PRODUCTION_ENV

    expect(() => loadServerConfig(missingTwo)).toThrow(/AUTH_SECRET.*AUTH_KEYCLOAK_SECRET/)
  })

  test('本番のモードで空の値は、ないものとして扱う', () => {
    expect(() => loadServerConfig({ ...PRODUCTION_ENV, AUTH_KEYCLOAK_ISSUER: '' })).toThrow(/AUTH_KEYCLOAK_ISSUER/)
  })

  test('本番のモードで必須の値がそろえば、その値を使う', () => {
    const config = loadServerConfig(PRODUCTION_ENV)

    expect(config.issuer).toBe(PRODUCTION_ENV.AUTH_KEYCLOAK_ISSUER)
    expect(config.clientId).toBe(PRODUCTION_ENV.AUTH_KEYCLOAK_ID)
    expect(config.clientSecret).toBe(PRODUCTION_ENV.AUTH_KEYCLOAK_SECRET)
    expect(config.secret).toBe(PRODUCTION_ENV.AUTH_SECRET)
    expect(config.apiBaseUrl).toBe(PRODUCTION_ENV.STORE_API_BASE_URL)
  })

  test('AC-240 本番のモードで公開する URL（AUTH_URL）がなければ、起動に失敗する', () => {
    const { AUTH_URL: _url, ...missing } = PRODUCTION_ENV

    expect(() => loadServerConfig(missing)).toThrow(/AUTH_URL/)
  })

  test('AC-240 本番のモードで公開する URL があれば、その値を使う', () => {
    expect(loadServerConfig(PRODUCTION_ENV).publicUrl).toBe(PRODUCTION_ENV.AUTH_URL)
  })

  test('AC-240 開発のモードとテストでは、値がなければローカルの店舗画面の URL を使う', () => {
    expect(loadServerConfig({ NODE_ENV: 'development' }).publicUrl).toBe('http://localhost:3000')
  })

  test('AC-242 API の応答を取り直す間隔は、設定の 1 か所で読み、なければ 60 秒', () => {
    expect(readServerConfig({ STORE_API_REVALIDATE_SECONDS: '1' }).apiRevalidateSeconds).toBe(1)
    expect(readServerConfig({}).apiRevalidateSeconds).toBe(60)
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
      ...PRODUCTION_ENV,
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
    expect(() => loadServerConfig({ ...PRODUCTION_ENV, AUTH_KEYCLOAK_TIMEOUT_SECONDS: value })).toThrow(
      /AUTH_KEYCLOAK_TIMEOUT_SECONDS/,
    )
  })

  test('DC-135-15 再取得の間隔は、未設定か空なら既定の 60 秒で、0 以上の整数ならその値を使う', () => {
    expect(loadServerConfig({ NODE_ENV: 'development' }).apiRevalidateSeconds).toBe(60)
    expect(loadServerConfig({ NODE_ENV: 'development', STORE_API_REVALIDATE_SECONDS: '' }).apiRevalidateSeconds).toBe(
      60,
    )
    expect(loadServerConfig({ ...PRODUCTION_ENV, STORE_API_REVALIDATE_SECONDS: '1' }).apiRevalidateSeconds).toBe(1)
    expect(loadServerConfig({ ...PRODUCTION_ENV, STORE_API_REVALIDATE_SECONDS: '3600' }).apiRevalidateSeconds).toBe(
      3600,
    )
  })

  test('DC-135-16 再取得の間隔の 0 は、キャッシュしない設定として受け付ける', () => {
    expect(loadServerConfig({ NODE_ENV: 'development', STORE_API_REVALIDATE_SECONDS: '0' }).apiRevalidateSeconds).toBe(
      0,
    )
  })

  test.each(['-1', '1.5', '0.5', 'abc', ' ', '1e3', '1 '])(
    'DC-135-15 再取得の間隔が 0 以上の整数でなければ（"%s"）、モードによらず名前を出して失敗する',
    (value) => {
      expect(() => loadServerConfig({ NODE_ENV: 'development', STORE_API_REVALIDATE_SECONDS: value })).toThrow(
        /STORE_API_REVALIDATE_SECONDS/,
      )
      expect(() => loadServerConfig({ ...PRODUCTION_ENV, STORE_API_REVALIDATE_SECONDS: value })).toThrow(
        /STORE_API_REVALIDATE_SECONDS/,
      )
    },
  )
})

describe('店舗画面の Cookie の接頭辞の設定', () => {
  test('DC-104-12 STORE_COOKIE_PREFIX は任意で、なければ空（今の名前のまま）。本番でも必須にしない', () => {
    expect(() => loadServerConfig(PRODUCTION_ENV)).not.toThrow()
    expect(loadServerConfig(PRODUCTION_ENV).cookiePrefix).toBe('')
    expect(loadServerConfig({ NODE_ENV: 'development' }).cookiePrefix).toBe('')
    expect(readServerConfig({}).cookiePrefix).toBe('')
  })

  test('DC-104-12 STORE_COOKIE_PREFIX の値を使う', () => {
    expect(loadServerConfig({ ...PRODUCTION_ENV, STORE_COOKIE_PREFIX: 's1-' }).cookiePrefix).toBe('s1-')
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
