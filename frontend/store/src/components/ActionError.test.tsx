import { describe, expect, test } from 'vitest'
import { ActionError } from '@/components/ActionError'
import { renderMarkup, renderStatic } from '@/test/render'

describe('サーバーアクションの失敗の表示', () => {
  test('AC-193-1、AC-193-2 失敗の文を、状態の知らせ（status）の領域の中に出す', () => {
    const view = renderStatic(<ActionError message="在庫が足りません。" />)

    expect(view.getByRole('status')).toHaveTextContent('在庫が足りません。')
  })

  test('文のあとに、子の要素を添えられる', () => {
    const view = renderStatic(
      <ActionError message="在庫が足りません。">
        <a href="/cart">カートを見る</a>
      </ActionError>,
    )

    const message = view.getByRole('status')
    const link = view.getByRole('link', { name: 'カートを見る' })
    expect(link).toHaveAttribute('href', '/cart')
    // 子の要素は、領域の中で、文のあとに出る
    expect(message).toContainElement(link)
    expect(message).toHaveTextContent('在庫が足りません。カートを見る')
  })

  test('親が読み上げの領域を持つとき（inLiveRegion）は、自分では領域にしない', () => {
    const view = renderStatic(<ActionError inLiveRegion message="在庫が足りません。" />)

    expect(view.getByText('在庫が足りません。')).toBeInTheDocument()
    // 領域を入れ子にしない
    expect(view.queryByRole('status')).toBeNull()
  })

  test('サーバーアクションの結果が失敗なら、その message を出す', () => {
    const view = renderStatic(<ActionError result={{ ok: false, message: '在庫が足りません。' }} />)

    expect(view.getByRole('status')).toHaveTextContent('在庫が足りません。')
  })

  test.each([{ ok: true } as const, null, undefined])(
    'AC-193-1 サーバーアクションの結果が成功か、まだない（%j）なら、文のない領域だけを出す',
    (result) => {
      const view = renderStatic(<ActionError result={result} />)

      expect(view.getByRole('status')).toBeEmptyDOMElement()
    },
  )

  test.each([null, undefined, ''])('AC-193-1 文がなければ（%j）子は出さず、文のない領域だけを出す', (message) => {
    const view = renderStatic(<ActionError message={message}>子</ActionError>)

    expect(view.getByRole('status')).toBeEmptyDOMElement()
  })

  test.each([null, undefined, ''])('親が領域を持つとき（inLiveRegion）、文がなければ（%j）何も出さない', (message) => {
    expect(renderMarkup(<ActionError inLiveRegion message={message} />)).toBe('')
  })
})
