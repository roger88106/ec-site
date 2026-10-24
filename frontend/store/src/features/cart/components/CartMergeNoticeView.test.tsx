import { within } from '@testing-library/dom'
import { describe, expect, test } from 'vitest'
import { CartMergeNoticeView } from '@/features/cart/components/CartMergeNoticeView'
import { renderMarkup, renderStatic } from '@/test/render'

/** 一覧の項目の文言 */
function itemTexts(list: HTMLElement): string[] {
  return within(list)
    .getAllByRole('listitem')
    .map((item) => item.textContent)
}

/** 指定した項目を持つ一覧。なければテストを失敗させる */
function listWith(container: HTMLElement, itemText: string): HTMLElement {
  const found = within(container)
    .getAllByRole('list')
    .find((list) => within(list).queryByText(itemText) !== null)
  if (!found) throw new Error('「' + itemText + '」を持つ一覧がない')
  return found
}

describe('カートを移した結果の知らせ', () => {
  test('AC-123 数量を切り詰めた品目と、移せなかった品目と、省いた数を示す', () => {
    const view = renderStatic(
      <CartMergeNoticeView
        notice={{
          truncated: { items: [{ label: 'T シャツ（色：赤）', quantity: 10 }], more: 0 },
          skipped: { items: [{ label: 'マグカップ', quantity: 2 }], more: 3 },
        }}
      />,
    )

    const notice = view.getByRole('alert')
    expect(within(notice).getByText('ログインする前のカートの商品を、会員のカートに移しました')).toBeInTheDocument()
    expect(
      within(notice).getByText('次の商品は、数量の上限（10 点）を超えたため、10 点にしました。'),
    ).toBeInTheDocument()
    expect(
      within(notice).getByText('カートに入る商品の数の上限（30 品目）を超えたため、次の商品は移せませんでした。'),
    ).toBeInTheDocument()

    expect(within(notice).getAllByRole('list')).toHaveLength(2)
    expect(itemTexts(listWith(notice, 'T シャツ（色：赤）（10 点）'))).toEqual(['T シャツ（色：赤）（10 点）'])
    expect(itemTexts(listWith(notice, 'マグカップ（2 点）'))).toEqual(['マグカップ（2 点）', 'ほか 3 品目'])
  })

  test('切り詰めた品目だけなら、移せなかった品目の欄を出さない', () => {
    const view = renderStatic(
      <CartMergeNoticeView
        notice={{
          truncated: { items: [{ label: 'マグカップ', quantity: 10 }], more: 0 },
          skipped: { items: [], more: 0 },
        }}
      />,
    )

    expect(view.getByText('次の商品は、数量の上限（10 点）を超えたため、10 点にしました。')).toBeInTheDocument()
    expect(
      view.queryByText('カートに入る商品の数の上限（30 品目）を超えたため、次の商品は移せませんでした。'),
    ).not.toBeInTheDocument()
    expect(view.getAllByRole('list')).toHaveLength(1)
  })

  test('知らせがなければ、何も出さない', () => {
    expect(renderMarkup(<CartMergeNoticeView notice={null} />)).toBe('')
  })
})
