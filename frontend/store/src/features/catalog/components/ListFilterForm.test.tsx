import { describe, expect, test } from 'vitest'
import { ListFilterForm } from '@/features/catalog/components/ListFilterForm'
import { renderMarkup } from '@/test/render'

/** 衣類とその子のシャツ */
const CLOTHES = '00000000-0000-0000-0000-000000000301'
const SHIRTS = '00000000-0000-0000-0000-000000000302'

/** HTML の選択肢のうち、選んでいるものの値と文字 */
function selectedOptions(html: string): string[] {
  return [...html.matchAll(/<option[^>]* value="([^"]*)" selected="">([^<]*)<\/option>/g)].map(
    ([, value, label]) => `${value}:${label}`,
  )
}

describe('商品一覧の絞り込み', () => {
  test('URL のクエリの条件を初めの値にし、商品一覧へ GET で送る', () => {
    const html = renderMarkup(
      <ListFilterForm
        categories={[
          { id: CLOTHES, name: '衣類', depth: 1 },
          { id: SHIRTS, name: 'シャツ', depth: 2 },
        ]}
        query={{ categoryId: SHIRTS, minPrice: 1000, maxPrice: null, sort: 'price_asc', page: 2 }}
      />,
    )

    expect(html).toMatch(/<form [^>]*action="\/products"/)
    expect(selectedOptions(html)).toEqual([`${SHIRTS}:　シャツ`, 'price_asc:価格の安い順'])
    expect(html).toContain(`value="${CLOTHES}">衣類</option>`)
    expect(html).toMatch(/name="minPrice"[^>]*value="1000"/)
    expect(html).toMatch(/name="maxPrice"[^>]*value=""/)
    // ページは送らない（絞り込むと 1 ページ目に戻る）
    expect(html).not.toContain('name="page"')
  })

  test('条件がなければ、すべてのカテゴリと新着順を選ぶ', () => {
    const html = renderMarkup(
      <ListFilterForm
        categories={[]}
        query={{ categoryId: null, minPrice: null, maxPrice: null, sort: 'newest', page: 1 }}
      />,
    )

    expect(selectedOptions(html)).toEqual([':すべて', 'newest:新着順'])
  })
})
