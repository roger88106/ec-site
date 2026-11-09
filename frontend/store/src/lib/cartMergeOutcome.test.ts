import { describe, expect, test } from 'vitest'
import { cartMergeOutcome } from '@/lib/cartMergeOutcome'

describe('カートを移した結果の区分', () => {
  test.each([
    [200, 'merged'],
    [400, 'gone'],
    [404, 'gone'],
    [401, 'unauthenticated'],
    [500, 'failed'],
    [503, 'failed'],
  ] as const)('HTTP %i は %s にする', (status, expected) => {
    expect(cartMergeOutcome(status)).toBe(expected)
  })

  test('契約にないステータスは、一時的な失敗にする', () => {
    expect(cartMergeOutcome(403)).toBe('failed')
  })
})
