import { describe, expect, test } from 'vitest'
import {
  type CartMergeNotice,
  cartMergeNotice,
  parseCartMergeNotice,
  serializeCartMergeNotice,
} from '@/lib/cartMergeNotice'

/** 移す前のカートの品目（商品名とバリアントを引くため） */
const SOURCE_ITEMS = [
  { variantId: 'v-white', productName: 'T シャツ', axisValues: [{ name: '色', value: '白' }] },
  { variantId: 'v-black', productName: 'T シャツ', axisValues: [{ name: '色', value: '黒' }] },
  { variantId: 'v-mug', productName: 'マグカップ', axisValues: [] },
]

describe('カートを移した結果の知らせ', () => {
  test('AC-123 切り詰めた品目と移せなかった品目を、商品名・バリアント・数量で示す', () => {
    const notice = cartMergeNotice(
      { truncatedItems: [{ variantId: 'v-white', quantity: 10 }], skippedItems: [{ variantId: 'v-mug', quantity: 2 }] },
      SOURCE_ITEMS,
    )

    expect(notice).toEqual({
      truncated: { items: [{ label: 'T シャツ（色：白）', quantity: 10 }], more: 0 },
      skipped: { items: [{ label: 'マグカップ', quantity: 2 }], more: 0 },
    })
  })

  test('AC-122 切り詰めも移せなかった品目もなければ、知らせない', () => {
    expect(cartMergeNotice({ truncatedItems: [], skippedItems: [] }, SOURCE_ITEMS)).toBeNull()
  })

  test('移す前のカートにない品目は、商品名の代わりに「商品」とする', () => {
    const notice = cartMergeNotice({ truncatedItems: [], skippedItems: [{ variantId: 'v-gone', quantity: 1 }] }, [])

    expect(notice?.skipped.items).toEqual([{ label: '商品', quantity: 1 }])
  })

  test('品目は 10 までにし、残りは数だけ示す（Cookie の大きさを抑える）', () => {
    const skippedItems = Array.from({ length: 12 }, (_, i) => ({ variantId: `v-${i}`, quantity: 1 }))

    const notice = cartMergeNotice({ truncatedItems: [], skippedItems }, [])

    expect(notice?.skipped.items).toHaveLength(10)
    expect(notice?.skipped.more).toBe(2)
  })
})

describe('知らせの Cookie の値', () => {
  /** 知らせ */
  const NOTICE: CartMergeNotice = {
    truncated: { items: [{ label: 'T シャツ（色：白）', quantity: 10 }], more: 0 },
    skipped: { items: [], more: 3 },
  }

  test('Cookie に書いた値から、同じ知らせを読める', () => {
    expect(parseCartMergeNotice(serializeCartMergeNotice(NOTICE))).toEqual(NOTICE)
  })

  test('Cookie がない・形の違う値なら、知らせはない', () => {
    expect(parseCartMergeNotice(undefined)).toBeNull()
    expect(parseCartMergeNotice('not-json')).toBeNull()
    expect(parseCartMergeNotice(serializeJson({ truncated: 1 }))).toBeNull()
  })
})

/** 形の違う値を Cookie の値にする */
function serializeJson(value: unknown): string {
  return Buffer.from(JSON.stringify(value)).toString('base64url')
}
