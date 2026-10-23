import { describe, expect, test } from 'vitest'
import { cancellationRequestResultLabel, paymentStatusLabel } from '@/features/order/labels'

describe('キャンセルの申請と支払の表示名', () => {
  test('AC-100 キャンセルの申請の結果を、申請中・承認・却下と表示する', () => {
    expect(cancellationRequestResultLabel('PENDING')).toBe('申請中')
    expect(cancellationRequestResultLabel('APPROVED')).toBe('承認')
    expect(cancellationRequestResultLabel('REJECTED')).toBe('却下')
  })

  test('AC-160、194 支払の状態を、売上確定済み・与信期限切れ・返金待ち・返金済みと表示する', () => {
    expect(paymentStatusLabel('CAPTURED')).toBe('売上確定済み')
    expect(paymentStatusLabel('AUTHORIZATION_EXPIRED')).toBe('与信期限切れ')
    expect(paymentStatusLabel('REFUND_PENDING')).toBe('返金待ち')
    expect(paymentStatusLabel('REFUNDED')).toBe('返金済み')
  })
})
