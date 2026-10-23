import type { Route } from 'next'
import { describe, expect, test } from 'vitest'
import { PageLinks } from '@/components/PageLinks'
import { renderMarkup } from '@/test/render'

/** ページの URL（typedRoutes の Route は、実行時に作った文字を型で受け付けないため、型を合わせる） */
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

    expect(html).toContain('全 5 件')
    expect(html).not.toContain('<nav')
  })
})
