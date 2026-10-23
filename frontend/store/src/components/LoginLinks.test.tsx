import { describe, expect, test, vi } from 'vitest'
import { LoginLinks } from '@/components/LoginLinks'
import { renderMarkup } from '@/test/render'

// ヘッダーのログインのリンクは、いまの画面（パスとクエリ）を戻り先にする
vi.mock('next/navigation', async (importOriginal) => ({
  ...(await importOriginal<typeof import('next/navigation')>()),
  usePathname: () => '/products',
  useSearchParams: () => new URLSearchParams('categoryId=c1&page=2'),
}))

describe('ヘッダーのログインと会員登録', () => {
  test('AC-113 いまの画面（パスとクエリ）を戻り先にして、ログインと会員登録の口へ移る', () => {
    const html = renderMarkup(<LoginLinks />)
    const returnTo = encodeURIComponent('/products?categoryId=c1&page=2')

    expect(html).toContain(`href="/login?returnTo=${returnTo}">ログイン</a>`)
    expect(html).toContain(`href="/login?returnTo=${returnTo}&amp;register=1">会員登録</a>`)
  })
})
