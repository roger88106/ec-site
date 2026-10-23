// @vitest-environment jsdom
import { screen } from '@testing-library/react'
import { describe, expect, test, vi } from 'vitest'
import { requestCancellation } from '@/actions/order'
import { CancellationRequestForm } from '@/features/order/components/CancellationRequestForm'
import { renderInBrowser } from '@/test/render'

// 申請はサーバーアクションを通るので、送る前の確認（ブラウザーの中だけで終わる操作）だけを確認する（テストの書き方 第 1 節）
vi.mock('@/actions/order', () => ({ requestCancellation: vi.fn(async () => ({ ok: true })) }))

describe('キャンセルの申請', () => {
  test('理由の欄と上限を出し、送る前に確認して、やめると申請しない', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const { user } = renderInBrowser(<CancellationRequestForm orderId="order-1" />)

    const reason = screen.getByLabelText('理由（任意）')
    expect(reason.getAttribute('maxlength')).toBe('500')
    expect(reason.parentElement?.textContent).toContain('500 文字まで')
    await user.click(screen.getByRole('button', { name: 'キャンセルを申請する' }))

    expect(confirm).toHaveBeenCalledWith('キャンセルを申請します。申請は 1 回だけできます。よろしいですか？')
    expect(requestCancellation).not.toHaveBeenCalled()
  })
})
