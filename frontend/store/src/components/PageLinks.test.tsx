import type { Route } from 'next'
import { describe, expect, test, vi } from 'vitest'
import { LoginLinks } from '@/components/LoginLinks'
import { PageLinks } from '@/components/PageLinks'
import { renderMarkup } from '@/test/render'

// ヘッダーのログインのリンクは、いまの画面（パスとクエリ）を戻り先にする
vi.mock('next/navigation', async (importOriginal) => ({
  ...(await importOriginal<typeof import('next/navigation')>()),
  usePathname: () => '/products',
  useSearchParams: () => new URLSearchParams('categoryId=c1&page=2'),
}))

/** ページの URL */
const hrefFor = (page: number) => `/orders?page=${page}` as Route

describe('ページ送り', () => {
  test('中のページでは、件数とページと、前後のページへのリンクを出す', () => {
    const html = renderMarkup(<PageLinks hrefFor={hrefFor} page={2} totalItems={45} totalPages={3} />)

    expect(html).toContain('<nav aria-label="ページ"')
    expect(html).toContain('全 45 件　2 / 3 ページ')
    expect(html).toContain('href="/orders?page=1"')
    expect(html).toContain('href="/orders?page=3"')
  })

  test('最初のページの前へ、最後のページの次へは、押せないボタンにする', () => {
    const first = renderMarkup(<PageLinks hrefFor={hrefFor} page={1} totalItems={45} totalPages={3} />)
    const last = renderMarkup(<PageLinks hrefFor={hrefFor} page={3} totalItems={45} totalPages={3} />)

    expect(first).toMatch(/disabled=""[^>]*>前へ<\/button>/)
    expect(first).toContain('href="/orders?page=2"')
    expect(last).toMatch(/disabled=""[^>]*>次へ<\/button>/)
    expect(last).toContain('href="/orders?page=2"')
  })

  test('1 ページに収まれば、件数だけを出す', () => {
    const html = renderMarkup(<PageLinks hrefFor={hrefFor} page={1} totalItems={5} totalPages={1} />)

    expect(html).toBe('<p class="text-muted-foreground text-sm">全 5 件</p>')
  })
})

describe('ヘッダーのログインと会員登録', () => {
  test('AC-113 いまの画面（パスとクエリ）を戻り先にして、ログインと会員登録の口へ移る', () => {
    const html = renderMarkup(<LoginLinks />)
    const returnTo = encodeURIComponent('/products?categoryId=c1&page=2')

    expect(html).toContain(`href="/login?returnTo=${returnTo}">ログイン</a>`)
    expect(html).toContain(`href="/login?returnTo=${returnTo}&amp;register=1">会員登録</a>`)
  })
})
