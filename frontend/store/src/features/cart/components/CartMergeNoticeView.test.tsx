import { describe, expect, test } from 'vitest'
import { CartMergeNoticeView } from '@/features/cart/components/CartMergeNoticeView'
import { renderMarkup } from '@/test/render'

describe('カートを移した結果の知らせ', () => {
  test('AC-123 数量を切り詰めた品目と、移せなかった品目と、省いた数を示す', () => {
    const html = renderMarkup(
      <CartMergeNoticeView
        notice={{
          truncated: { items: [{ label: 'T シャツ（色：赤）', quantity: 10 }], more: 0 },
          skipped: { items: [{ label: 'マグカップ', quantity: 2 }], more: 3 },
        }}
      />,
    )

    expect(html).toContain('ログインする前のカートの商品を、会員のカートに移しました')
    expect(html).toContain('数量の上限（10 点）を超えたため、10 点にしました。')
    expect(html).toContain('<li>T シャツ（色：赤）（10 点）</li>')
    expect(html).toContain('次の商品は移せませんでした。')
    expect(html).toContain('<li>マグカップ（2 点）</li>')
    expect(html).toContain('<li>ほか 3 品目</li>')
  })

  test('切り詰めた品目だけなら、移せなかった品目の欄を出さない', () => {
    const html = renderMarkup(
      <CartMergeNoticeView
        notice={{
          truncated: { items: [{ label: 'マグカップ', quantity: 10 }], more: 0 },
          skipped: { items: [], more: 0 },
        }}
      />,
    )

    expect(html).toContain('10 点にしました。')
    expect(html).not.toContain('移せませんでした')
  })

  test('知らせがなければ、何も出さない', () => {
    expect(renderMarkup(<CartMergeNoticeView notice={null} />)).toBe('')
  })
})
