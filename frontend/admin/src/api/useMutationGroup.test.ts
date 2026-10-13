import { describe, expect, test, vi } from 'vitest'
import { useMutationGroup } from '@/api/useMutationGroup'

/** 操作の状態の代わり */
function mutation(state: { isPending?: boolean; error?: Error | null } = {}) {
  return { isPending: state.isPending ?? false, error: state.error ?? null, reset: vi.fn() }
}

describe('複数の更新の操作のまとめ', () => {
  test('どれかが待っていれば、待ち状態にする', () => {
    expect(useMutationGroup(mutation(), mutation({ isPending: true })).pending).toBe(true)
    expect(useMutationGroup(mutation(), mutation()).pending).toBe(false)
  })

  test('エラーは、最初に失敗している操作のものを出す', () => {
    const first = new Error('1 つ目')
    const second = new Error('2 つ目')
    expect(useMutationGroup(mutation(), mutation({ error: first }), mutation({ error: second })).error).toBe(first)
    expect(useMutationGroup(mutation(), mutation()).error).toBeUndefined()
  })

  test('エラーの消去は、すべての操作のエラーを消す', () => {
    const mutations = [mutation({ error: new Error('失敗') }), mutation()]
    useMutationGroup(...mutations).resetErrors()
    for (const item of mutations) {
      expect(item.reset).toHaveBeenCalledOnce()
    }
  })
})
