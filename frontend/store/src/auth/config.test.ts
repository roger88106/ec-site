import { describe, expect, test } from 'vitest'
import { loadServerConfig, REQUIRED_IN_PRODUCTION } from '@/auth/config'

/** 本番で渡す値のすべて */
const ALL_SET = {
  NODE_ENV: 'production',
  AUTH_SECRET: 'secret',
  AUTH_KEYCLOAK_SECRET: 'client-secret',
  AUTH_KEYCLOAK_ISSUER: 'https://idp.example/realms/ec-store',
  AUTH_KEYCLOAK_ID: 'ec-store-web',
  STORE_API_BASE_URL: 'https://api.example/api/store',
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
  })

  test('開発のモードとテストでは、値がなければローカル用の既定の値を使う', () => {
    const config = loadServerConfig({ NODE_ENV: 'development' })

    expect(config.issuer).toBe('http://localhost:8180/realms/ec-store')
    expect(config.apiBaseUrl).toBe('http://localhost:8080/api/store')
    expect(loadServerConfig({ NODE_ENV: 'test' }).clientId).toBe('ec-store-web')
  })

  test('必須の値は 5 つ', () => {
    expect([...REQUIRED_IN_PRODUCTION]).toEqual([
      'AUTH_SECRET',
      'AUTH_KEYCLOAK_SECRET',
      'AUTH_KEYCLOAK_ISSUER',
      'AUTH_KEYCLOAK_ID',
      'STORE_API_BASE_URL',
    ])
  })
})
