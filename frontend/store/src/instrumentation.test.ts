import { beforeEach, describe, expect, type MockInstance, test, vi } from 'vitest'
import { register } from '@/instrumentation'
import { PRODUCTION_ENV } from '@/test/env'
import { recordStderr, type StderrRecord } from '@/test/stderr'

/** 差し替えた process.exit が投げる例外の文言 */
const EXIT_ERROR = 'process.exit が呼ばれた'

describe('起動時の設定の確認', () => {
  let exit: MockInstance
  let stderr: StderrRecord

  beforeEach(() => {
    // process.exit は never を返す型なので、差し替えも例外を投げて終わる関数にする（終了の代わりに例外で止まる）
    exit = vi.spyOn(process, 'exit').mockImplementation(() => {
      throw new Error(EXIT_ERROR)
    })
    stderr = recordStderr()
    vi.stubEnv('NEXT_RUNTIME', 'nodejs')
    for (const [name, value] of Object.entries(PRODUCTION_ENV)) {
      vi.stubEnv(name, value)
    }
  })

  test('DC-135-23 本番のモードで必須の値がなければ、足りない設定の名前を標準エラー出力に書いて終了する', () => {
    vi.stubEnv('AUTH_SECRET', '')
    vi.stubEnv('STORE_API_BASE_URL', '')

    expect(() => register()).toThrow(EXIT_ERROR)

    expect(stderr.text()).toContain('AUTH_SECRET')
    expect(stderr.text()).toContain('STORE_API_BASE_URL')
    expect(stderr.text()).not.toContain('AUTH_KEYCLOAK_ID')
    expect(exit).toHaveBeenCalledWith(1)
  })

  test('必須の値がそろっていれば、終了せず何も書かない', () => {
    register()

    expect(exit).not.toHaveBeenCalled()
    expect(stderr.text()).toBe('')
  })

  test('DC-135-24 Node.js 以外の実行環境では何もしない（設定を読まず、終了しない）', () => {
    vi.stubEnv('NEXT_RUNTIME', 'edge')
    vi.stubEnv('AUTH_SECRET', '')

    register()

    expect(exit).not.toHaveBeenCalled()
    expect(stderr.text()).toBe('')
  })
})
