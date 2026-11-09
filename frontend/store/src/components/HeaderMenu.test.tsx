// @vitest-environment jsdom
import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import { HeaderMenu } from '@/components/HeaderMenu'
import { renderInBrowser } from '@/test/render'

// 画面の遷移は、パスの変更として表す
let pathname = '/'
vi.mock('next/navigation', async (importOriginal) => ({
  ...(await importOriginal<typeof import('next/navigation')>()),
  usePathname: () => pathname,
}))

beforeEach(() => {
  pathname = '/'
})

function menu() {
  return (
    <HeaderMenu>
      <a href="/products">商品一覧</a>
    </HeaderMenu>
  )
}

describe('ヘッダーのメニュー', () => {
  test('AC-417-3 メニューを開くボタンを押すと aria-expanded が true になり、もう一度押すと false に戻る', async () => {
    const { user } = renderInBrowser(menu())
    const button = screen.getByRole('button', { name: 'メニュー' })
    expect(button).toHaveAttribute('aria-expanded', 'false')
    expect(button).toHaveAttribute('aria-controls')

    await user.click(button)
    expect(button).toHaveAttribute('aria-expanded', 'true')

    await user.click(button)
    expect(button).toHaveAttribute('aria-expanded', 'false')
  })

  test('AC-417-5 メニューを開いた状態で画面を遷移（パスが変わる）すると、メニューが閉じる', async () => {
    const { user, rerender } = renderInBrowser(menu())
    const button = screen.getByRole('button', { name: 'メニュー' })
    await user.click(button)
    expect(button).toHaveAttribute('aria-expanded', 'true')

    pathname = '/products'
    rerender(menu())

    expect(button).toHaveAttribute('aria-expanded', 'false')
  })

  test('NFR-417-4 メニューを開いた状態で Esc キーを押すと、メニューが閉じ、フォーカスがボタンに戻る', async () => {
    const { user } = renderInBrowser(menu())
    const button = screen.getByRole('button', { name: 'メニュー' })
    await user.click(button)
    // フォーカスをメニューの中の項目に移してから押す
    await user.tab()
    expect(screen.getByRole('link', { name: '商品一覧' })).toHaveFocus()

    await user.keyboard('{Escape}')

    expect(button).toHaveAttribute('aria-expanded', 'false')
    expect(button).toHaveFocus()
  })
})
