import { describe, expect, test, vi } from 'vitest'
import type { Variant } from '@/api/types'
import { AddToCartForm } from '@/features/catalog/components/AddToCartForm'
import type { ActionResult } from '@/lib/actionResult'
import { VARIANT_ID } from '@/test/fixtures'
import { renderMarkup } from '@/test/render'

/** カートに入れるサーバーアクションの結果として、フォームに渡す値（null は送る前） */
let actionResult: ActionResult | null = null

// カートに入れる操作はサーバーアクションを通るので、送った結果の表示だけを、結果を差し替えて HTML で確認する（テストの書き方 第 1 節）
vi.mock('react', async (importOriginal) => {
  const react = await importOriginal<typeof import('react')>()
  return { ...react, useActionState: () => [actionResult, () => {}, false] }
})

/** 買えるバリアント */
const VARIANT: Variant = { id: VARIANT_ID, axisValues: [], inStock: true, salesSuspended: false, imageUrl: null }

/** 常にある読み上げの領域（aria-live を持つ要素）の中身 */
function liveRegionOf(html: string): string {
  const match = html.match(/<div aria-live="polite"[^>]*>(.*?)<\/div>/)
  if (!match) {
    throw new Error('読み上げの領域がない')
  }
  return match[1] ?? ''
}

describe('カートに入れる操作の失敗', () => {
  test('DC-135-10 失敗の文は、常にある読み上げの領域の中に出す（領域を入れ子にしない）', () => {
    actionResult = null
    const before = renderMarkup(<AddToCartForm variant={VARIANT} />)
    // 失敗の前から、領域はある
    expect(before.match(/aria-live/g)).toHaveLength(1)
    expect(liveRegionOf(before)).toBe('')

    actionResult = { ok: false, message: '入力内容に誤りがあります。', errorCode: 'INVALID_REQUEST' }
    const html = renderMarkup(<AddToCartForm variant={VARIANT} />)

    expect(html.match(/aria-live/g)).toHaveLength(1)
    expect(liveRegionOf(html)).toContain('入力内容に誤りがあります。')
    expect(html).toMatch(/<p [^>]*>入力内容に誤りがあります。<\/p>/)
    expect(html).not.toMatch(/<p [^>]*aria-live[^>]*>入力内容に誤りがあります。/)
  })
})
