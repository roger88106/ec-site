// @vitest-environment jsdom
import { screen } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { ActionError } from '@/components/ActionError'
import { renderInBrowser } from '@/test/render'

describe('サーバーアクションの失敗の表示', () => {
  test('失敗の文を、読み上げの対象として出す', () => {
    renderInBrowser(<ActionError message="在庫が足りません。" />)

    expect(screen.getByText('在庫が足りません。').getAttribute('aria-live')).toBe('polite')
  })

  test('文のあとに、子の要素を添えられる', () => {
    renderInBrowser(
      <ActionError message="在庫が足りません。">
        <a href="/cart">カートを見る</a>
      </ActionError>,
    )

    const message = screen.getByText('在庫が足りません。', { exact: false })
    expect(message.textContent).toBe('在庫が足りません。カートを見る')
    expect(screen.getByRole('link', { name: 'カートを見る' }).parentElement).toBe(message)
  })

  test('親が読み上げの領域を持つとき（inLiveRegion）は、自分では領域にしない', () => {
    renderInBrowser(<ActionError inLiveRegion message="在庫が足りません。" />)

    expect(screen.getByText('在庫が足りません。').hasAttribute('aria-live')).toBe(false)
  })

  test.each([null, undefined, ''])('文がなければ（%j）何も出さない', (message) => {
    renderInBrowser(<ActionError message={message}>子</ActionError>)

    expect(document.body.textContent).toBe('')
  })
})
