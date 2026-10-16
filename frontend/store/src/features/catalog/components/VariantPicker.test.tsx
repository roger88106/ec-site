// @vitest-environment jsdom
import { screen } from '@testing-library/react'
import { describe, expect, test, vi } from 'vitest'
import type { Variant, VariantAxis } from '@/api/types'
import { ProductShowcase } from '@/features/catalog/components/ProductShowcase'
import { VariantPicker } from '@/features/catalog/components/VariantPicker'
import { renderInBrowser } from '@/test/render'

/** 色とサイズの軸 */
const AXES: VariantAxis[] = [
  { name: '色', values: ['赤', '青'] },
  { name: 'サイズ', values: ['M', 'L'] },
]

/** 色とサイズの値を持つバリアント。在庫があり、販売停止でない */
function variant(color: string, size: string, overrides: Partial<Variant> = {}): Variant {
  return {
    id: `variant-${color}-${size}`,
    axisValues: [color, size],
    inStock: true,
    salesSuspended: false,
    imageUrl: null,
    ...overrides,
  }
}

/** 赤 M・赤 L は買える。青 M は在庫切れ、青 L は販売停止で、青は選べない */
const VARIANTS = [
  variant('赤', 'M'),
  variant('赤', 'L', { imageUrl: 'http://cdn.test/red-l.webp' }),
  variant('青', 'M', { inStock: false }),
  variant('青', 'L', { salesSuspended: true }),
]

/** 押せるかどうかと、押している状態 */
function button(name: string): HTMLButtonElement {
  return screen.getByRole('button', { name }) as HTMLButtonElement
}

describe('バリアントの選択', () => {
  test('AC-62 選んでいない軸を示し、在庫切れ・販売停止のバリアントにしかならない値は選べない', () => {
    renderInBrowser(<VariantPicker axes={AXES} variants={VARIANTS} />)

    expect(screen.getByText('色・サイズを選ぶと、在庫状況を表示します。')).toBeTruthy()
    expect(button('赤').disabled).toBe(false)
    expect(button('青').disabled).toBe(true)
  })

  test('すべての軸を選ぶと、そのバリアントの在庫状況を出して知らせ、選んだ値を押すと外す', async () => {
    const onSelectVariant = vi.fn()
    const { user } = renderInBrowser(
      <VariantPicker axes={AXES} onSelectVariant={onSelectVariant} variants={VARIANTS} />,
    )

    await user.click(button('赤'))
    expect(screen.getByText('サイズを選ぶと、在庫状況を表示します。')).toBeTruthy()
    await user.click(button('L'))
    expect(screen.getByText('在庫あり')).toBeTruthy()
    expect(button('L').getAttribute('aria-pressed')).toBe('true')

    await user.click(button('L'))
    expect(screen.getByText('サイズを選ぶと、在庫状況を表示します。')).toBeTruthy()
    expect(onSelectVariant.mock.calls).toEqual([[null], [VARIANTS[1]], [null]])
  })

  test('AC-56 すべてのバリアントが買えなければ、選ぶ前から「在庫なし」と出す', () => {
    renderInBrowser(<VariantPicker axes={AXES} variants={[VARIANTS[2], VARIANTS[3]]} />)

    expect(screen.getByText('在庫なし')).toBeTruthy()
  })
})

describe('商品の詳細の画像とカートに入れる操作', () => {
  /** 大きな画像の URL */
  function shownImage(): string | null {
    return screen.getByRole('img', { name: '商品の画像' }).getAttribute('src')
  }

  test('小さな画像を押すとその画像に、バリアントを選ぶとその画像に替え、選ぶまでカートに入れられない', async () => {
    const { user } = renderInBrowser(
      <ProductShowcase
        axes={AXES}
        header={<h1>T シャツ</h1>}
        imageUrls={['http://cdn.test/1.webp', 'http://cdn.test/2.webp']}
        variants={VARIANTS}
      />,
    )

    expect(shownImage()).toBe('http://cdn.test/1.webp')
    expect(button('カートに入れる').disabled).toBe(true)

    await user.click(button('画像 2を見る'))
    expect(shownImage()).toBe('http://cdn.test/2.webp')
    expect(button('画像 2を見る').getAttribute('aria-pressed')).toBe('true')

    await user.click(button('赤'))
    await user.click(button('L'))
    expect(shownImage()).toBe('http://cdn.test/red-l.webp')
    expect(button('カートに入れる').disabled).toBe(false)

    // 画像の指定のないバリアントは、代表画像に戻す
    await user.click(button('L'))
    await user.click(button('M'))
    expect(shownImage()).toBe('http://cdn.test/1.webp')
  })

  test('軸のない商品は、ただ 1 つのバリアントをすぐカートに入れられる。画像がなければ代わりの図を出す', () => {
    renderInBrowser(
      <ProductShowcase
        axes={[]}
        header={<h1>マグカップ</h1>}
        imageUrls={[]}
        variants={[{ id: 'variant-only', axisValues: [], inStock: true, salesSuspended: false, imageUrl: null }]}
      />,
    )

    expect(screen.queryByRole('img', { name: '商品の画像' })).toBeNull()
    expect(screen.queryByRole('button', { name: /を見る$/ })).toBeNull()
    expect(button('カートに入れる').disabled).toBe(false)
  })
})
