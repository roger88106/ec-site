import { describe, expect, test } from 'vitest'
import {
  cancellationState,
  formatDateTime,
  formatPostalCode,
  formatYen,
  orderStatusLabel,
  remainingTime,
  taxLabel,
  unpurchasableReasonLabel,
} from '@/lib/orderDisplay'

describe('金額の表示（BR-7、8）', () => {
  test('金額は 3 桁ごとに区切って「円」を付ける', () => {
    expect(formatYen(1000)).toBe('1,000 円')
    expect(formatYen(0)).toBe('0 円')
  })

  test('BR-8 税率ごとの消費税額は、対象額と消費税額を並べる', () => {
    expect(taxLabel({ ratePercent: 10, taxableAmount: 11000, taxAmount: 1000 })).toBe(
      '10% 対象 11,000 円（内消費税 1,000 円）',
    )
    expect(taxLabel({ ratePercent: 8, taxableAmount: 1080, taxAmount: 80 })).toBe('8% 対象 1,080 円（内消費税 80 円）')
  })
})

describe('購入できない理由（AC-80）', () => {
  test('AC-80 理由ごとの文言。数量は出さない', () => {
    expect(unpurchasableReasonLabel('NOT_SHOWN')).toBe('お取り扱いを終了しました')
    expect(unpurchasableReasonLabel('SALES_SUSPENDED')).toBe('販売を停止しています')
    expect(unpurchasableReasonLabel('OUT_OF_STOCK')).toBe('在庫切れです')
    expect(unpurchasableReasonLabel('INSUFFICIENT_STOCK')).toBe('在庫が足りません。数量を減らしてください')
  })
})

describe('注文の状態（BR-9）', () => {
  test('BR-9 受付、キャンセル申請中、キャンセル', () => {
    expect(orderStatusLabel('ACCEPTED')).toBe('受付')
    expect(orderStatusLabel('CANCELLATION_REQUESTED')).toBe('キャンセル申請中')
    expect(orderStatusLabel('CANCELLED')).toBe('キャンセル')
  })
})

describe('キャンセルの申請の状況（AC-95、96）', () => {
  /** API が返す旗 */
  const requestable = { requestCancellation: true }
  const notRequestable = { requestCancellation: false }

  test('AC-95 申請できる（API の旗）「受付」の注文は、申請できる', () => {
    expect(cancellationState({ status: 'ACCEPTED', actions: requestable })).toBe('REQUESTABLE')
  })

  test('AC-95 申請中', () => {
    expect(cancellationState({ status: 'CANCELLATION_REQUESTED', actions: notRequestable })).toBe('PENDING')
  })

  test('AC-96 申請できない「受付」の注文は、却下された注文（問い合わせ先を示す）', () => {
    expect(cancellationState({ status: 'ACCEPTED', actions: notRequestable })).toBe('REJECTED')
  })

  test('キャンセル済み（申請の承認、運営者の判断のどちらでも）', () => {
    expect(cancellationState({ status: 'CANCELLED', actions: notRequestable })).toBe('CANCELLED')
  })
})

describe('日時・郵便番号の表示', () => {
  test('日時は日本時間で「年/月/日 時:分」', () => {
    expect(formatDateTime('2026-10-10T15:05:00Z')).toBe('2026/10/11 00:05')
  })

  test('郵便番号は 3 桁と 4 桁の間にハイフンを入れる', () => {
    expect(formatPostalCode('1000001')).toBe('100-0001')
  })
})

describe('引当の残り時間（AC-84）', () => {
  /** 期限 */
  const expiresAt = '2026-10-10T05:15:00Z'

  test('期限までの残りを「分:秒」で出す（秒の端数は切り捨て）', () => {
    expect(remainingTime(expiresAt, new Date('2026-10-10T05:00:00Z'))).toBe('15:00')
    expect(remainingTime(expiresAt, new Date('2026-10-10T05:14:51.500Z'))).toBe('0:08')
  })

  test('期限を過ぎたら null（注文はできる。AC-87）', () => {
    expect(remainingTime(expiresAt, new Date('2026-10-10T05:15:00Z'))).toBeNull()
    expect(remainingTime(expiresAt, new Date('2026-10-10T06:00:00Z'))).toBeNull()
  })
})
