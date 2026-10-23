// @vitest-environment jsdom
import { screen } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import type { Variant } from '@/api/types'
import { ProductShowcase } from '@/features/catalog/components/ProductShowcase'
import { VARIANT_AXES, variant } from '@/test/fixtures'
import { renderInBrowser } from '@/test/render'

/** 商品の画像。先頭が代表画像 */
const IMAGE_URLS = ['http://cdn.test/1.webp', 'http://cdn.test/2.webp']

/** 赤 L は専用の画像を持ち、赤 M は画像の指定がない。青 M は在庫切れ、青 L は販売停止 */
const VARIANTS = [
  variant('赤', 'M'),
  variant('赤', 'L', { imageUrl: 'http://cdn.test/red-l.webp' }),
  variant('青', 'M', { inStock: false }),
  variant('青', 'L', { salesSuspended: true }),
]

/** 画像、バリアントの選択、カートに入れる操作を描く */
function renderShowcase() {
  return renderInBrowser(
    <ProductShowcase axes={VARIANT_AXES} header={<h1>T シャツ</h1>} imageUrls={IMAGE_URLS} variants={VARIANTS} />,
  )
}

/** 軸のない商品の、ただ 1 つのバリアント。既定は在庫があり、販売停止でない */
function onlyVariant(overrides: Partial<Variant> = {}): Variant {
  return variant('', '', { id: 'variant-only', axisValues: [], ...overrides })
}

/** 軸のない商品を描く */
function renderWithoutAxes(variants: Variant[]) {
  return renderInBrowser(<ProductShowcase axes={[]} header={<h1>マグカップ</h1>} imageUrls={[]} variants={variants} />)
}

/** 押せるかどうかと、押している状態を見るボタン */
function button(name: string): HTMLButtonElement {
  const element = screen.getByRole('button', { name })
  if (!(element instanceof HTMLButtonElement)) {
    throw new Error(`button でない要素です: ${name}`)
  }
  return element
}

/** 大きな画像の URL */
function shownImage(): string | null {
  return screen.getByRole('img', { name: '商品の画像' }).getAttribute('src')
}

describe('商品の詳細の画像', () => {
  test('DC-135-25 バリアントを選ぶとそのバリアントの画像に、画像の指定がなければ代表画像に切り替える', async () => {
    const { user } = renderShowcase()
    expect(shownImage()).toBe('http://cdn.test/1.webp')

    await user.click(button('画像 2を見る'))
    await user.click(button('赤'))
    await user.click(button('L'))
    expect(shownImage()).toBe('http://cdn.test/red-l.webp')

    // 小さな画像で別の画像を出した後に、画像のない赤 M を選ぶと、代表画像に戻る
    await user.click(button('画像 2を見る'))
    expect(shownImage()).toBe('http://cdn.test/2.webp')
    await user.click(button('M'))
    expect(shownImage()).toBe('http://cdn.test/1.webp')
  })

  test('DC-135-26 小さな画像を押すとその画像に切り替え、押した画像を押している状態にする', async () => {
    const { user } = renderShowcase()

    await user.click(button('画像 2を見る'))

    expect(shownImage()).toBe('http://cdn.test/2.webp')
    expect(button('画像 2を見る').getAttribute('aria-pressed')).toBe('true')
    expect(button('画像 1を見る').getAttribute('aria-pressed')).toBe('false')
  })

  test('軸のない商品は、画像がなければ商品の画像も小さな画像の並びも出さない', () => {
    renderWithoutAxes([onlyVariant()])

    expect(screen.queryByRole('img', { name: '商品の画像' })).toBeNull()
    expect(screen.queryByRole('button', { name: /を見る$/ })).toBeNull()
  })
})

describe('商品の詳細のカートに入れる操作', () => {
  test('DC-135-27 バリアントを選ぶまでは「カートに入れる」を押せない', async () => {
    const { user } = renderShowcase()
    expect(button('カートに入れる').disabled).toBe(true)

    await user.click(button('赤'))
    expect(button('カートに入れる').disabled).toBe(true)

    await user.click(button('L'))
    expect(button('カートに入れる').disabled).toBe(false)

    await user.click(button('L'))
    expect(button('カートに入れる').disabled).toBe(true)
  })

  test.each([
    ['在庫切れ', { inStock: false }],
    ['販売停止', { salesSuspended: true }],
  ])('DC-135-27 買えないバリアント（%s）のときは、「カートに入れる」を押せない', (_name, overrides) => {
    // 軸のある商品は、買えない値を選べないので、バリアントが決まる軸のない商品で確認する
    renderWithoutAxes([onlyVariant(overrides)])

    expect(button('カートに入れる').disabled).toBe(true)
  })

  test('軸のない商品は、ただ 1 つのバリアントをすぐカートに入れられる', () => {
    renderWithoutAxes([onlyVariant()])

    expect(button('カートに入れる').disabled).toBe(false)
  })
})
