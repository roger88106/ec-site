// @vitest-environment jsdom
import { screen } from '@testing-library/react'
import { afterEach, describe, expect, test, vi } from 'vitest'
import { retryCartMerge } from '@/actions/cart'
import { CartMergeRetry } from '@/features/cart/components/CartMergeRetry'
import { renderInBrowser } from '@/test/render'

vi.mock('@/actions/cart', () => ({ retryCartMerge: vi.fn() }))

const RETRYING = 'ログインする前のカートを移しています。'
const FAILED = '会員のカートに移せませんでした'

afterEach(() => {
  vi.mocked(retryCartMerge).mockReset()
})

describe('カートを移す再試行', () => {
  test('AC-415-2 pending なら、再試行を 1 回だけ呼び、移している間の文の後に結果を示す', async () => {
    vi.mocked(retryCartMerge).mockResolvedValueOnce({ kind: 'failed' })

    const { rerender } = renderInBrowser(<CartMergeRetry pending />)

    expect(screen.getByRole('status')).toHaveTextContent(RETRYING)
    expect(await screen.findByText(FAILED, { exact: false })).toBeInTheDocument()
    expect(screen.getByRole('status')).not.toHaveTextContent(RETRYING)

    // 失敗のときはカートの画面を再表示しないが、pending が変わって届いても、結果は残し、再び呼ばない
    rerender(<CartMergeRetry pending={false} />)
    expect(screen.getByText(FAILED, { exact: false })).toBeInTheDocument()
    expect(retryCartMerge).toHaveBeenCalledTimes(1)
  })

  test('AC-415-5 再試行の呼び出しが拒否されたら、移している間の文を消し、移せなかったことを示す', async () => {
    vi.mocked(retryCartMerge).mockRejectedValueOnce(new Error('network'))

    renderInBrowser(<CartMergeRetry pending />)

    expect(await screen.findByText(FAILED, { exact: false })).toBeInTheDocument()
    expect(screen.queryByText(RETRYING)).toBeNull()
  })

  test('AC-415-4 移せたときは、再表示で pending が false になっても、切り詰めた品目の知らせを示し続ける', async () => {
    vi.mocked(retryCartMerge).mockResolvedValueOnce({
      kind: 'merged',
      notice: {
        truncated: { items: [{ label: 'マグカップ', quantity: 10 }], more: 0 },
        skipped: { items: [], more: 0 },
      },
    })

    const { rerender } = renderInBrowser(<CartMergeRetry pending />)
    expect(await screen.findByText('マグカップ（10 点）')).toBeInTheDocument()

    rerender(<CartMergeRetry pending={false} />)
    expect(screen.getByText('マグカップ（10 点）')).toBeInTheDocument()
  })

  test('pending でなければ、再試行を呼ばず、何も表示しない', () => {
    renderInBrowser(<CartMergeRetry pending={false} />)

    expect(retryCartMerge).not.toHaveBeenCalled()
    expect(screen.queryByRole('status')).toBeNull()
  })
})
