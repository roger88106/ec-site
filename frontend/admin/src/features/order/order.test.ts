import { describe, expect, test } from 'vitest'
import { availableActions, cancellationRequestResultLabel, formatPostalCode, taxLabel } from '@/features/order/order'
import { orderStatusLabels } from '@/lib/labels'

describe('注文の状態の表示名（BR-9）', () => {
  test('BR-9 受付、キャンセル申請中、キャンセル', () => {
    expect(orderStatusLabels).toEqual({
      ACCEPTED: '受付',
      CANCELLATION_REQUESTED: 'キャンセル申請中',
      CANCELLED: 'キャンセル',
    })
  })

  test('AC-100 キャンセルの申請の結果', () => {
    expect(cancellationRequestResultLabel('PENDING')).toBe('申請中')
    expect(cancellationRequestResultLabel('APPROVED')).toBe('承認')
    expect(cancellationRequestResultLabel('REJECTED')).toBe('却下')
  })
})

describe('注文に行える操作（AC-101、102、103、105）', () => {
  test('AC-101 AC-102 AC-103 キャンセル申請中の注文は、承認・却下・キャンセルができる', () => {
    expect(availableActions('CANCELLATION_REQUESTED')).toEqual({ approve: true, reject: true, cancel: true })
  })

  test('AC-103 受付の注文は、キャンセルだけができる', () => {
    expect(availableActions('ACCEPTED')).toEqual({ approve: false, reject: false, cancel: true })
  })

  test('AC-105 キャンセル済みの注文には、操作を出さない', () => {
    expect(availableActions('CANCELLED')).toEqual({ approve: false, reject: false, cancel: false })
  })
})

describe('税率ごとの消費税額（BR-8）', () => {
  test('BR-8 対象額と消費税額を並べる', () => {
    expect(taxLabel({ ratePercent: 10, taxableAmount: 11000, taxAmount: 1000 })).toBe(
      '10% 対象 11,000 円（内消費税 1,000 円）',
    )
  })
})

describe('お届け先の表示', () => {
  test('郵便番号は 3 桁と 4 桁の間にハイフンを入れる', () => {
    expect(formatPostalCode('1000001')).toBe('100-0001')
  })
})
