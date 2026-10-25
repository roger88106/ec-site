import { describe, expect, test, vi } from 'vitest'
import { LoginLinks } from '@/components/LoginLinks'
import { renderStatic } from '@/test/render'

// ヘッダーのログインのリンクは、いまの画面（パスとクエリ）を戻り先にする
vi.mock('next/navigation', async (importOriginal) => ({
  ...(await importOriginal<typeof import('next/navigation')>()),
  usePathname: () => '/products',
  useSearchParams: () => new URLSearchParams('categoryId=c1&page=2'),
}))

describe('ヘッダーのログインと会員登録', () => {
  test('AC-113 いまの画面（パスとクエリ）を戻り先にして、ログインと会員登録の口へ移る', () => {
    const view = renderStatic(<LoginLinks />)
    const returnTo = encodeURIComponent('/products?categoryId=c1&page=2')

    expect(view.getByRole('link', { name: 'ログイン' })).toHaveAttribute('href', `/login?returnTo=${returnTo}`)
    expect(view.getByRole('link', { name: '会員登録' })).toHaveAttribute(
      'href',
      `/login?returnTo=${returnTo}&register=1`,
    )
  })
})
