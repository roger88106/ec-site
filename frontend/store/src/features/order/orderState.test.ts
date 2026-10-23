import { describe, expect, test } from 'vitest'
import {
  cancellationState,
  paymentDeadlineLabel,
  paymentState,
  showsCancellationSection,
  showsPaymentSection,
} from '@/features/order/orderState'

describe('キャンセルの申請の状況（AC-95、96）', () => {
  /** API が返すフラグ */
  const requestable = { requestCancellation: true }
  const notRequestable = { requestCancellation: false }

  test('AC-95 申請できる（API のフラグ）「受付」の注文は、申請できる', () => {
    expect(cancellationState({ status: 'ACCEPTED', actions: requestable })).toBe('REQUESTABLE')
  })

  test('AC-95 申請中', () => {
    expect(cancellationState({ status: 'CANCELLATION_REQUESTED', actions: notRequestable })).toBe('PENDING')
  })

  test('AC-96、267 申請の結果が却下の注文は、却下された注文（問い合わせ先を示す）', () => {
    expect(
      cancellationState({
        status: 'ACCEPTED',
        actions: notRequestable,
        cancellationRequest: { requestedAt: '2026-10-10T03:00:00Z', result: 'REJECTED' },
      }),
    ).toBe('REJECTED')
  })

  test('AC-267 申請できない「受付」の注文でも、申請の結果がなければ却下にしない（キャンセルの欄を出さない）', () => {
    expect(cancellationState({ status: 'ACCEPTED', actions: notRequestable })).toBe('UNAVAILABLE')
  })

  test('キャンセル済み（申請の承認、運営者の判断のどちらでも）', () => {
    expect(cancellationState({ status: 'CANCELLED', actions: notRequestable })).toBe('CANCELLED')
    expect(
      cancellationState({
        status: 'CANCELLED',
        actions: notRequestable,
        cancellation: { cancelledAt: '2026-10-10T03:00:00Z', kind: 'OPERATOR' },
      }),
    ).toBe('CANCELLED')
  })

  test('AC-155 支払期限切れのキャンセルは、ほかのキャンセルと分ける', () => {
    expect(
      cancellationState({
        status: 'CANCELLED',
        actions: notRequestable,
        cancellation: { cancelledAt: '2026-10-10T03:00:00Z', kind: 'PAYMENT_EXPIRED' },
      }),
    ).toBe('PAYMENT_EXPIRED')
  })

  test('AC-171 出荷済みの注文は、申請できない（キャンセルの欄を出さない）', () => {
    expect(cancellationState({ status: 'SHIPPED', actions: notRequestable })).toBe('SHIPPED')
  })

  test('AC-158 支払待ちの注文は、まだ申請できない（支払の期限で自動でキャンセルになる）', () => {
    expect(cancellationState({ status: 'PENDING_PAYMENT', actions: notRequestable })).toBe('NOT_YET')
  })
})

describe('支払の状況（AC-150、151）', () => {
  /** API が返すフラグ */
  const payable = { pay: true }
  const notPayable = { pay: false }

  test('AC-151 支払待ちで支払える（API のフラグ）なら、支払える', () => {
    expect(paymentState({ status: 'PENDING_PAYMENT', actions: payable }, false)).toBe('PAYABLE')
  })

  test('AC-150 支払画面から戻った直後に、まだ支払待ちなら、支払の確認中', () => {
    expect(paymentState({ status: 'PENDING_PAYMENT', actions: payable }, true)).toBe('CONFIRMING')
    expect(paymentState({ status: 'PENDING_PAYMENT', actions: notPayable }, true)).toBe('CONFIRMING')
  })

  test('支払待ちで支払えないのは、支払の期限を過ぎた（まもなくキャンセルになる）', () => {
    expect(paymentState({ status: 'PENDING_PAYMENT', actions: notPayable }, false)).toBe('CLOSING')
  })

  test('支払待ちでなければ、支払の欄は出さない（戻った直後でも）', () => {
    expect(paymentState({ status: 'ACCEPTED', actions: notPayable }, true)).toBe('NONE')
    expect(paymentState({ status: 'CANCELLED', actions: notPayable }, false)).toBe('NONE')
  })
})

describe('注文の画面の欄を出すか', () => {
  test('AC-150、151 支払の欄は、支払待ちのとき（支払える、確認中、期限切れ）だけ出す', () => {
    expect(showsPaymentSection('PAYABLE')).toBe(true)
    expect(showsPaymentSection('CONFIRMING')).toBe(true)
    expect(showsPaymentSection('CLOSING')).toBe(true)
    expect(showsPaymentSection('NONE')).toBe(false)
  })

  test('AC-158、171 キャンセルの欄は、支払待ち、出荷済み、申請も結果もない注文では出さない', () => {
    expect(showsCancellationSection('NOT_YET')).toBe(false)
    expect(showsCancellationSection('SHIPPED')).toBe(false)
    expect(showsCancellationSection('UNAVAILABLE')).toBe(false)
  })

  test('キャンセルの欄は、申請できる、申請中、却下、キャンセル済み、支払期限切れでは出す', () => {
    expect(showsCancellationSection('REQUESTABLE')).toBe(true)
    expect(showsCancellationSection('PENDING')).toBe(true)
    expect(showsCancellationSection('REJECTED')).toBe(true)
    expect(showsCancellationSection('CANCELLED')).toBe(true)
    expect(showsCancellationSection('PAYMENT_EXPIRED')).toBe(true)
  })
})

describe('注文の一覧の支払の期限（AC-159）', () => {
  test('AC-159 支払待ちの注文は、支払の期限を示す', () => {
    expect(paymentDeadlineLabel({ status: 'PENDING_PAYMENT', paymentDeadline: '2026-10-10T15:35:00Z' })).toBe(
      'お支払い期限 2026/10/11 00:35',
    )
  })

  test('支払待ちでない注文は、示さない', () => {
    expect(paymentDeadlineLabel({ status: 'ACCEPTED' })).toBeNull()
  })
})
