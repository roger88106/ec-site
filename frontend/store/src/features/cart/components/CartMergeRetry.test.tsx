// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react'
import { afterEach, describe, expect, test, vi } from 'vitest'
import { retryCartMerge } from '@/actions/cart'
import { CartMergeRetry } from '@/features/cart/components/CartMergeRetry'
import { renderInBrowser } from '@/test/render'

vi.mock('@/actions/cart', () => ({ retryCartMerge: vi.fn() }))

afterEach(() => {
  cleanup()
  vi.mocked(retryCartMerge).mockReset()
})

describe('カートを移す再試行', () => {
  test('AC-415-2 pending なら、再試行を 1 回だけ呼び、移している間の文の後に結果を示す', async () => {
    vi.mocked(retryCartMerge).mockResolvedValueOnce({ kind: 'failed' })

    const { rerender } = renderInBrowser(<CartMergeRetry pending />)

    expect(screen.getByRole('status')).toHaveTextContent('ログインする前のカートを移しています。')
    expect(await screen.findByRole('alert')).toHaveTextContent('会員のカートに移せませんでした')
    expect(screen.getByRole('status')).toBeEmptyDOMElement()

    // 成功の後は、カートの画面が再表示されて pending が false で届く。結果は残し、再び呼ばない
    rerender(<CartMergeRetry pending={false} />)
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(retryCartMerge).toHaveBeenCalledTimes(1)
  })

  test('AC-415-3 移せたときは、再表示で pending が false になっても、知らせを示し続ける', async () => {
    vi.mocked(retryCartMerge).mockResolvedValueOnce({
      kind: 'merged',
      notice: { truncated: { items: [{ label: 'マグカップ', quantity: 10 }], more: 0 }, skipped: { items: [], more: 0 } },
    })

    const { rerender } = renderInBrowser(<CartMergeRetry pending />)
    expect(await screen.findByText('マグカップ（10 点）')).toBeInTheDocument()

    rerender(<CartMergeRetry pending={false} />)
    expect(screen.getByText('マグカップ（10 点）')).toBeInTheDocument()
  })

  test('pending でなければ、再試行を呼ばず、何も描かない', () => {
    renderInBrowser(<CartMergeRetry pending={false} />)

    expect(retryCartMerge).not.toHaveBeenCalled()
    expect(screen.queryByRole('status')).toBeNull()
  })
})
