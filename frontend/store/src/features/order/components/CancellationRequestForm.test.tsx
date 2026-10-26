// @vitest-environment jsdom
import { screen, waitFor } from '@testing-library/react'
import { describe, expect, test, vi } from 'vitest'
import { requestCancellation } from '@/actions/order'
import { CancellationRequestForm } from '@/features/order/components/CancellationRequestForm'
import { renderInBrowser } from '@/test/render'

// 申請はサーバーアクションを通るので、送る前の確認（ブラウザーの中だけで終わる操作）と、結果の表示の遷移を、差し替えた結果で確認する。動作はサーバーアクションのテストと E2E で確認する（テストの書き方 第 1 節）
vi.mock('@/actions/order', () => ({ requestCancellation: vi.fn(async () => ({ ok: true })) }))

describe('キャンセルの申請', () => {
  test('理由の欄と上限を出し、送る前に確認して、やめると申請しない', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const { user } = renderInBrowser(<CancellationRequestForm orderId="order-1" />)

    const reason = screen.getByLabelText('理由（任意）')
    expect(reason).toHaveAttribute('maxlength', '500')
    expect(screen.getByText('500 文字まで')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'キャンセルを申請する' }))

    expect(confirm).toHaveBeenCalledWith('キャンセルを申請します。申請は 1 回だけできます。よろしいですか？')
    expect(requestCancellation).not.toHaveBeenCalled()
  })

  test('AC-193-1 失敗の文は、送る前からある状態の知らせ（status）の領域に入る', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(requestCancellation).mockResolvedValueOnce({
      ok: false,
      message: 'この注文はキャンセルを申請できません。',
      errorCode: 'CANCELLATION_NOT_REQUESTABLE',
    })
    const { user } = renderInBrowser(<CancellationRequestForm orderId="order-1" />)

    // 送る前も、領域は 1 つあり、文はない
    expect(screen.getAllByRole('status')).toHaveLength(1)
    const status = screen.getByRole('status')
    expect(status).toBeEmptyDOMElement()

    await user.click(screen.getByRole('button', { name: 'キャンセルを申請する' }))

    // 失敗の後は、同じ領域に文が入る
    expect(await screen.findByText('この注文はキャンセルを申請できません。')).toBeInTheDocument()
    expect(screen.getAllByRole('status')).toHaveLength(1)
    expect(screen.getByRole('status')).toBe(status)
    expect(status).toHaveTextContent('この注文はキャンセルを申請できません。')
  })

  test('AC-193-3 失敗の後、入力を直して再送して成功したとき、同じ領域が残り、前の失敗の文は消える', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(requestCancellation).mockResolvedValueOnce({
      ok: false,
      message: 'この注文はキャンセルを申請できません。',
      errorCode: 'CANCELLATION_NOT_REQUESTABLE',
    })
    const { user } = renderInBrowser(<CancellationRequestForm orderId="order-1" />)
    const status = screen.getByRole('status')

    await user.click(screen.getByRole('button', { name: 'キャンセルを申請する' }))
    expect(await screen.findByText('この注文はキャンセルを申請できません。')).toBeInTheDocument()
    expect(screen.getByRole('status')).toBe(status)

    // 入力を直して再送する。2 回目は、モックの既定の成功を返す
    await user.type(screen.getByLabelText('理由（任意）'), '誤って注文したため')
    await user.click(screen.getByRole('button', { name: 'キャンセルを申請する' }))

    await waitFor(() => expect(status).toBeEmptyDOMElement())
    expect(screen.getAllByRole('status')).toHaveLength(1)
    expect(screen.getByRole('status')).toBe(status)
  })
})
