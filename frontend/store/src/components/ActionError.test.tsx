import { describe, expect, test } from 'vitest'
import { ActionError } from '@/components/ActionError'
import { renderMarkup } from '@/test/render'

describe('サーバーアクションの失敗の表示', () => {
  test('失敗の文を、読み上げの対象として出す', () => {
    const html = renderMarkup(<ActionError message="在庫が足りません。" />)

    expect(html).toMatch(/<p aria-live="polite"[^>]*>在庫が足りません。<\/p>/)
  })

  test('文のあとに、子の要素を添えられる', () => {
    const html = renderMarkup(
      <ActionError message="在庫が足りません。">
        <a href="/cart">カートを見る</a>
      </ActionError>,
    )

    expect(html).toMatch(/>在庫が足りません。<a href="\/cart">カートを見る<\/a><\/p>/)
  })

  test.each([null, undefined, ''])('文がなければ（%j）何も出さない', (message) => {
    expect(renderMarkup(<ActionError message={message}>子</ActionError>)).toBe('')
  })
})
