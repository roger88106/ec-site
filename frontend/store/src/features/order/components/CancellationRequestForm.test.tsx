// @vitest-environment jsdom
import { screen, waitFor } from '@testing-library/react'
import { describe, expect, test, vi } from 'vitest'
import { requestCancellation } from '@/actions/order'
import { CancellationRequestForm } from '@/features/order/components/CancellationRequestForm'
import type { ActionResult } from '@/lib/actionResult'
import { renderInBrowser } from '@/test/render'

// 申請はサーバーアクションを通るので、送る前の確認（ブラウザーの中だけで終わる操作）と、結果の表示の遷移を、差し替えた結果で確認する。動作はサーバーアクションのテストと E2E で確認する（テストの書き方の「テストの種類と基準」）
vi.mock('@/actions/order', () => ({ requestCancellation: vi.fn(async () => ({ ok: true })) }))

describe('キャンセルの申請', () => {
  test('理由の欄と上限を出し、送る前に確認して、やめると申請しない', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const { user } = renderInBrowser(<CancellationRequestForm orderId="order-1" />)

    const reason = screen.getByLabelText('理由（任意）')
    expect(reason).toHaveAttribute('maxlength', '500')
    expect(screen.getByText('500 文字まで')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'キャンセルを申請する' }))

    expect(confirmSpy).toHaveBeenCalledWith('キャンセルを申請します。申請は 1 回だけできます。よろしいですか？')
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
    const statusRegion = screen.getByRole('status')
    expect(statusRegion).toBeEmptyDOMElement()

    await user.click(screen.getByRole('button', { name: 'キャンセルを申請する' }))

    // 失敗の後は、同じ領域に文が入る
    expect(await screen.findByText('この注文はキャンセルを申請できません。')).toBeInTheDocument()
    expect(screen.getAllByRole('status')).toHaveLength(1)
    expect(screen.getByRole('status')).toBe(statusRegion)
    expect(statusRegion).toHaveTextContent('この注文はキャンセルを申請できません。')
  })

  test('AC-193-3 失敗の後、入力を直して再送して成功したとき、同じ領域が残り、前の失敗の文は消える', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(requestCancellation).mockResolvedValueOnce({
      ok: false,
      message: 'この注文はキャンセルを申請できません。',
      errorCode: 'CANCELLATION_NOT_REQUESTABLE',
    })
    const { user } = renderInBrowser(<CancellationRequestForm orderId="order-1" />)
    const statusRegion = screen.getByRole('status')

    await user.click(screen.getByRole('button', { name: 'キャンセルを申請する' }))
    expect(await screen.findByText('この注文はキャンセルを申請できません。')).toBeInTheDocument()
    expect(screen.getByRole('status')).toBe(statusRegion)

    // 入力を直して再送する。2 回目は、モックの既定の成功を返す
    await user.type(screen.getByLabelText('理由（任意）'), '誤って注文したため')
    await user.click(screen.getByRole('button', { name: 'キャンセルを申請する' }))

    await waitFor(() => expect(statusRegion).toBeEmptyDOMElement())
    expect(screen.getAllByRole('status')).toHaveLength(1)
    expect(screen.getByRole('status')).toBe(statusRegion)
  })

  test('AC-414-1 申請が失敗しても、理由の欄に入力した文字列が残る', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(requestCancellation).mockResolvedValueOnce({
      ok: false,
      message: '入力を確かめてください。',
      errorCode: 'INVALID_REQUEST',
    })
    const { user } = renderInBrowser(<CancellationRequestForm orderId="order-1" />)

    await user.type(screen.getByLabelText('理由（任意）'), '誤って注文したため')
    await user.click(screen.getByRole('button', { name: 'キャンセルを申請する' }))

    expect(await screen.findByText('入力を確かめてください。')).toBeInTheDocument()
    expect(screen.getByLabelText('理由（任意）')).toHaveValue('誤って注文したため')
    // 送った内容も、入力した文字列と同じ
    const formData = vi.mocked(requestCancellation).mock.lastCall?.[1]
    expect(formData?.get('reason')).toBe('誤って注文したため')
  })

  test('AC-414-2 確認でやめたとき、申請せず、理由の欄に入力した文字列が残る', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    // 前のテストの呼び出しを数えない
    vi.mocked(requestCancellation).mockClear()
    const { user } = renderInBrowser(<CancellationRequestForm orderId="order-1" />)

    await user.type(screen.getByLabelText('理由（任意）'), '誤って注文したため')
    await user.click(screen.getByRole('button', { name: 'キャンセルを申請する' }))

    expect(requestCancellation).not.toHaveBeenCalled()
    expect(screen.getByLabelText('理由（任意）')).toHaveValue('誤って注文したため')
  })

  test('送信中はボタンを押せず、結果を受け取ると押せるようになる', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    let finish: (result: ActionResult) => void = () => {}
    vi.mocked(requestCancellation).mockImplementationOnce(() => new Promise((resolve) => (finish = resolve)))
    const { user } = renderInBrowser(<CancellationRequestForm orderId="order-1" />)

    await user.click(screen.getByRole('button', { name: 'キャンセルを申請する' }))
    expect(await screen.findByRole('button', { name: '申請しています…' })).toBeDisabled()

    finish({ ok: false, message: 'この注文はキャンセルを申請できません。', errorCode: 'CANCELLATION_NOT_REQUESTABLE' })
    expect(await screen.findByRole('button', { name: 'キャンセルを申請する' })).toBeEnabled()
  })
})
