import { within } from '@testing-library/dom'
import { describe, expect, test } from 'vitest'
import { CartMergeRetryView } from '@/features/cart/components/CartMergeRetryView'
import { renderStatic } from '@/test/render'

describe('カートを移す再試行の表示', () => {
  test('AC-415-7 再試行している間は、カートを移していることを読み上げの領域に示す', () => {
    const view = renderStatic(<CartMergeRetryView state="retrying" />)

    expect(view.getByRole('status')).toHaveTextContent('ログインする前のカートを移しています。')
    expect(view.queryByRole('alert')).toBeNull()
  })

  test('AC-415-5 一時的な失敗のときは、移せなかったことと、もう一度開くことを示す', () => {
    const view = renderStatic(<CartMergeRetryView state={{ kind: 'failed' }} />)

    expect(view.getByRole('alert')).toHaveTextContent(
      'ログインする前のカートを、会員のカートに移せませんでした。時間をおいて、カートの画面をもう一度開いてください。',
    )
    expect(view.getByRole('status')).toBeEmptyDOMElement()
  })

  test('AC-415-4 移せたときは、切り詰めた品目と移せなかった品目を、ログインの直後と同じ形で示す', () => {
    const view = renderStatic(
      <CartMergeRetryView
        state={{
          kind: 'merged',
          notice: {
            truncated: { items: [{ label: 'T シャツ（色：赤）', quantity: 10 }], more: 0 },
            skipped: { items: [{ label: 'マグカップ', quantity: 2 }], more: 3 },
          },
        }}
      />,
    )

    const notice = view.getByRole('alert')
    expect(
      within(notice).getByText('次の商品は、数量の上限（10 点）を超えたため、10 点にしました。'),
    ).toBeInTheDocument()
    expect(within(notice).getByText('T シャツ（色：赤）（10 点）')).toBeInTheDocument()
    expect(within(notice).getByText('マグカップ（2 点）')).toBeInTheDocument()
    expect(within(notice).getByText('ほか 3 品目')).toBeInTheDocument()
  })

  test.each([
    ['移せて知らせがない', { kind: 'merged', notice: null }],
    ['再試行する対象がない', { kind: 'none' }],
  ] as const)('%s のときは、文を出さない（領域は残す）', (_name, state) => {
    const view = renderStatic(<CartMergeRetryView state={state} />)

    expect(view.queryByRole('alert')).toBeNull()
    expect(view.getByRole('status')).toBeEmptyDOMElement()
  })
})
