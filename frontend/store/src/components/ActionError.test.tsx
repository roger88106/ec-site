import { describe, expect, test } from 'vitest'
import { ActionError } from '@/components/ActionError'
import { renderMarkup, renderStatic } from '@/test/render'

describe('サーバーアクションの失敗の表示', () => {
  test('失敗の文を、読み上げの対象として出す', () => {
    const view = renderStatic(<ActionError message="在庫が足りません。" />)

    expect(view.getByText('在庫が足りません。')).toHaveAttribute('aria-live', 'polite')
  })

  test('文のあとに、子の要素を添えられる', () => {
    const view = renderStatic(
      <ActionError message="在庫が足りません。">
        <a href="/cart">カートを見る</a>
      </ActionError>,
    )

    const message = view.getByText('在庫が足りません。')
    const link = view.getByRole('link', { name: 'カートを見る' })
    expect(link).toHaveAttribute('href', '/cart')
    // 子の要素は、文の要素の中で、文のあとに出る
    expect(message).toContainElement(link)
    expect(message).toHaveTextContent('在庫が足りません。カートを見る')
  })

  test('親が読み上げの領域を持つとき（inLiveRegion）は、自分では領域にしない', () => {
    const view = renderStatic(<ActionError inLiveRegion message="在庫が足りません。" />)

    expect(view.getByText('在庫が足りません。')).not.toHaveAttribute('aria-live')
  })

  test('サーバーアクションの結果が失敗なら、その message を出す', () => {
    const view = renderStatic(<ActionError result={{ ok: false, message: '在庫が足りません。' }} />)

    expect(view.getByText('在庫が足りません。')).toHaveAttribute('aria-live', 'polite')
  })

  test.each([{ ok: true } as const, null, undefined])(
    'サーバーアクションの結果が成功か、まだない（%j）なら何も出さない',
    (result) => {
      expect(renderMarkup(<ActionError result={result} />)).toBe('')
    },
  )

  test.each([null, undefined, ''])('文がなければ（%j）何も出さない', (message) => {
    expect(renderMarkup(<ActionError message={message}>子</ActionError>)).toBe('')
  })
})
