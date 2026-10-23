import { afterEach, beforeEach, describe, expect, type MockInstance, test, vi } from 'vitest'
import { register } from '@/instrumentation'

/** 本番のモードで必須の環境変数 */
const REQUIRED_ENV = {
  AUTH_KEYCLOAK_ISSUER: 'https://idp.example.com/realms/shop',
  AUTH_KEYCLOAK_ID: 'store',
  AUTH_KEYCLOAK_SECRET: 'secret',
  AUTH_SECRET: 'auth-secret',
  STORE_API_BASE_URL: 'https://api.example.com',
  AUTH_URL: 'https://shop.example.com',
}

describe('起動時の設定の確認', () => {
  let exit: MockInstance
  let stderr: MockInstance

  beforeEach(() => {
    exit = vi.spyOn(process, 'exit').mockImplementation((() => undefined) as never)
    stderr = vi.spyOn(process.stderr, 'write').mockImplementation(() => true)
    vi.stubEnv('NEXT_RUNTIME', 'nodejs')
    vi.stubEnv('NODE_ENV', 'production')
    for (const [name, value] of Object.entries(REQUIRED_ENV)) {
      vi.stubEnv(name, value)
    }
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.restoreAllMocks()
  })

  /** 標準エラー出力に書いた内容 */
  function written(): string {
    return stderr.mock.calls.map(([text]) => String(text)).join('')
  }

  test('DC-135-23 本番のモードで必須の値がなければ、足りない設定の名前を標準エラー出力に書いて終了する', () => {
    vi.stubEnv('AUTH_SECRET', '')
    vi.stubEnv('STORE_API_BASE_URL', '')

    register()

    expect(written()).toContain('AUTH_SECRET')
    expect(written()).toContain('STORE_API_BASE_URL')
    expect(written()).not.toContain('AUTH_KEYCLOAK_ID')
    expect(exit).toHaveBeenCalledWith(1)
  })

  test('必須の値がそろっていれば、終了せず何も書かない', () => {
    register()

    expect(exit).not.toHaveBeenCalled()
    expect(stderr).not.toHaveBeenCalled()
  })

  test('DC-135-24 Node.js 以外の実行環境では何もしない（設定を読まず、終了しない）', () => {
    vi.stubEnv('NEXT_RUNTIME', 'edge')
    vi.stubEnv('AUTH_SECRET', '')

    register()

    expect(exit).not.toHaveBeenCalled()
    expect(stderr).not.toHaveBeenCalled()
  })
})
