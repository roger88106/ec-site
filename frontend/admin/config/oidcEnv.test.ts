import { describe, expect, test } from 'vitest'
import { checkOidcEnv } from './oidcEnv.ts'

/** 本番ビルドに必要な値がそろった環境変数 */
const FULL = { VITE_OIDC_AUTHORITY: 'https://idp.example/realms/ec-admin', VITE_OIDC_CLIENT_ID: 'ec-admin-web' }

describe('本番ビルドの IdP の設定', () => {
  test('AC-238 本番モードのビルドで、IdP の接続先と client の ID がなければ、足りない名前を並べて失敗する', () => {
    expect(() => checkOidcEnv('build', 'production', {})).toThrow('VITE_OIDC_AUTHORITY, VITE_OIDC_CLIENT_ID')
  })

  test('AC-238 本番モードのビルドで、空の値はないものとして扱う', () => {
    expect(() => checkOidcEnv('build', 'production', { ...FULL, VITE_OIDC_CLIENT_ID: '' })).toThrow(
      'VITE_OIDC_CLIENT_ID',
    )
  })

  test('AC-238 本番モードのビルドで、値がそろえば失敗しない', () => {
    expect(() => checkOidcEnv('build', 'production', FULL)).not.toThrow()
  })

  test('AC-239 開発のサーバーとテストでは、値がなくても失敗しない（ローカルの IdP の既定値を使う）', () => {
    expect(() => checkOidcEnv('serve', 'development', {})).not.toThrow()
    expect(() => checkOidcEnv('serve', 'test', {})).not.toThrow()
  })
})
