// @vitest-environment jsdom
import { screen } from '@testing-library/react'
import { describe, expect, test, vi } from 'vitest'
import { requestCancellation } from '@/actions/order'
import { CancellationRequestForm } from '@/features/order/components/CancellationRequestForm'
import { OrderStatusBadge } from '@/features/order/components/OrderStatusBadge'
import { renderInBrowser, renderMarkup } from '@/test/render'

// 申請はサーバーアクションを通るので、送る前の確かめ（ブラウザーの中だけで終わる操作）だけを確かめる（テストの書き方 第 1 節）
vi.mock('@/actions/order', () => ({ requestCancellation: vi.fn(async () => ({ ok: true })) }))

describe('キャンセルの申請', () => {
  test('理由の欄と上限を出し、送る前に確かめて、やめると申請しない', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const { user } = renderInBrowser(<CancellationRequestForm orderId="order-1" />)

    expect(screen.getByLabelText('理由（任意）').getAttribute('maxlength')).toBe('500')
    expect(screen.getByText('500 文字まで')).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'キャンセルを申請する' }))

    expect(confirm).toHaveBeenCalledWith('キャンセルを申請します。申請は 1 回だけできます。よろしいですか？')
    expect(requestCancellation).not.toHaveBeenCalled()
  })
})

describe('注文の状態', () => {
  test('注文の状態を出し、出荷済みの注文は配送状態を出す', () => {
    expect(renderMarkup(<OrderStatusBadge status="ACCEPTED" />)).toContain('>受付</span>')
    expect(renderMarkup(<OrderStatusBadge shipmentStatus="DELIVERED" status="SHIPPED" />)).toContain('>配達完了</span>')
  })
})
