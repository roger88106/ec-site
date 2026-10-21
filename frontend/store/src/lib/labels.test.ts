import { describe, expect, test } from 'vitest'
import { unpurchasableReasonLabel } from '@/lib/labels'

describe('購入できない理由（AC-80）', () => {
  test('AC-80 理由ごとの文言。数量は出さない', () => {
    expect(unpurchasableReasonLabel('NOT_SHOWN')).toBe('お取り扱いを終了しました')
    expect(unpurchasableReasonLabel('SALES_SUSPENDED')).toBe('販売を停止しています')
    expect(unpurchasableReasonLabel('OUT_OF_STOCK')).toBe('在庫切れです')
    expect(unpurchasableReasonLabel('INSUFFICIENT_STOCK')).toBe('在庫が足りません。数量を減らしてください')
  })
})
