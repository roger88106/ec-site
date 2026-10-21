import { describe, expect, test } from 'vitest'
import { logout } from '@/actions/auth'
import { loadServerConfig, readServerConfig } from '@/auth/config'
import { signInAs } from '@/test/auth'
import { redirectedTo, resetRequest } from '@/test/next'

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

describe('店舗画面の公開する URL', () => {
  test('AC-240 本番のモードで公開する URL（AUTH_URL）がなければ、起動に失敗する', () => {
    const { AUTH_URL: _url, ...missing } = ALL_SET

    expect(() => loadServerConfig(missing)).toThrow(/AUTH_URL/)
  })

  test('AC-240 本番のモードで公開する URL があれば、その値を使う', () => {
    expect(loadServerConfig(ALL_SET).publicUrl).toBe('https://shop.example')
  })

  test('AC-240 開発のモードとテストでは、値がなければローカルの店舗画面の URL を使う', () => {
    expect(loadServerConfig({ NODE_ENV: 'development' }).publicUrl).toBe('http://localhost:3000')
  })

  test('AC-241 ログアウトの後の戻り先は公開する URL から作り、リクエストヘッダーのホストとプロトコルを使わない', async () => {
    signInAs({ idToken: 'member-id-token' })
    resetRequest({ headers: { host: 'evil.test', 'x-forwarded-proto': 'https', 'x-forwarded-host': 'evil.test' } })

    const url = new URL(await redirectedTo(() => logout()))

    expect(url.searchParams.get('post_logout_redirect_uri')).toBe(`${readServerConfig().publicUrl}/`)
  })
})

describe('店舗画面の任意の設定', () => {
  test('AC-242 API の応答を取り直す間隔は、設定の 1 か所で読み、なければ 60 秒', () => {
    expect(readServerConfig({ STORE_API_REVALIDATE_SECONDS: '1' }).apiRevalidateSeconds).toBe(1)
    expect(readServerConfig({}).apiRevalidateSeconds).toBe(60)
  })
})
