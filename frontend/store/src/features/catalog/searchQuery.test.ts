import { describe, expect, test } from 'vitest'
import { parseSearchQuery, searchHref } from '@/features/catalog/searchQuery'

describe('検索の URL のクエリ', () => {
  test('AC-58、61 キーワードとページを読む。前後の空白は除く', () => {
    expect(parseSearchQuery({ q: ' シャツ ', page: '2' })).toEqual({ q: 'シャツ', page: 2 })
  })

  test('キーワードがなければ空文字にする（API を呼ばない）', () => {
    expect(parseSearchQuery({})).toEqual({ q: '', page: 1 })
  })

  test('検索結果の URL。1 ページ目はページを書かない', () => {
    expect(searchHref({ q: 'T シャツ', page: 1 })).toBe('/search?q=T+%E3%82%B7%E3%83%A3%E3%83%84')
    expect(searchHref({ q: 'お茶', page: 3 })).toBe('/search?q=%E3%81%8A%E8%8C%B6&page=3')
  })
})
