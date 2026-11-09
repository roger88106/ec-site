// @vitest-environment jsdom
import { act, screen } from '@testing-library/react'
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
  test('NFR-417-3 メニューを開くボタンは、クリックでもキーボード（Enter）でも aria-expanded が true と false に切り替わる', async () => {
    const { user } = renderInBrowser(menu())
    const button = screen.getByRole('button', { name: 'メニュー' })
    expect(button).toHaveAttribute('aria-expanded', 'false')
    expect(button).toHaveAttribute('aria-controls')

    await user.click(button)
    expect(button).toHaveAttribute('aria-expanded', 'true')

    await user.click(button)
    expect(button).toHaveAttribute('aria-expanded', 'false')

    button.focus()
    await user.keyboard('{Enter}')
    expect(button).toHaveAttribute('aria-expanded', 'true')

    await user.keyboard('{Enter}')
    expect(button).toHaveAttribute('aria-expanded', 'false')
  })

  test('AC-417-5 パスが同じでも、メニューの中のリンクを押すと閉じる', async () => {
    const { user } = renderInBrowser(menu())
    const button = screen.getByRole('button', { name: 'メニュー' })
    await user.click(button)
    expect(button).toHaveAttribute('aria-expanded', 'true')

    await user.click(screen.getByRole('link', { name: '商品一覧' }))

    expect(button).toHaveAttribute('aria-expanded', 'false')
  })

  test('AC-417-5 修飾キーを押したままリンクを押す（別のタブで開く）と、メニューは閉じない', async () => {
    const { user } = renderInBrowser(menu())
    const button = screen.getByRole('button', { name: 'メニュー' })
    await user.click(button)
    const link = screen.getByRole('link', { name: '商品一覧' })
    // jsdom は別のタブを開かないので、既定の遷移を止めて、クリックの扱いだけを確認する
    link.addEventListener('click', (event) => event.preventDefault())

    await user.keyboard('{Control>}')
    await user.click(link)
    await user.keyboard('{/Control}')

    expect(button).toHaveAttribute('aria-expanded', 'true')
  })

  test('AC-417-5 メニューを開いた状態で画面を遷移する（パスが変わる）と、メニューが閉じる', async () => {
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

  test('NFR-417-4 フォーカスがメニューの外（body）にあるときも、Esc キーでメニューが閉じ、フォーカスがボタンに戻る', async () => {
    const { user } = renderInBrowser(menu())
    const button = screen.getByRole('button', { name: 'メニュー' })
    await user.click(button)
    button.blur()
    expect(document.body).toHaveFocus()

    await user.keyboard('{Escape}')

    expect(button).toHaveAttribute('aria-expanded', 'false')
    expect(button).toHaveFocus()
  })

  test('NFR-417-4 日本語の変換の取り消しの Esc キーでは、メニューは閉じない', async () => {
    const { user } = renderInBrowser(menu())
    const button = screen.getByRole('button', { name: 'メニュー' })
    await user.click(button)

    act(() => {
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', isComposing: true }))
    })

    expect(button).toHaveAttribute('aria-expanded', 'true')
  })

  test('NFR-417-4 フォーカスがメニューの外の入力欄にあるときは、Esc キーでメニューが閉じ、フォーカスは入力欄に残る', async () => {
    const { user } = renderInBrowser(
      <>
        <input aria-label="検索" />
        {menu()}
      </>,
    )
    const button = screen.getByRole('button', { name: 'メニュー' })
    await user.click(button)
    const input = screen.getByRole('textbox', { name: '検索' })
    input.focus()

    await user.keyboard('{Escape}')

    expect(button).toHaveAttribute('aria-expanded', 'false')
    expect(input).toHaveFocus()
  })
})
