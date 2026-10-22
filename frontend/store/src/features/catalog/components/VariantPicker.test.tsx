// @vitest-environment jsdom
import { screen } from '@testing-library/react'
import { useState } from 'react'
import { describe, expect, test, vi } from 'vitest'
import type { Variant, VariantAxis } from '@/api/types'
import { VariantPicker } from '@/features/catalog/components/VariantPicker'
import { emptySelection, type Selection, selectedVariant } from '@/features/catalog/variantSelection'
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

/** 選択を親として持ち、変わるたびに onChange を呼んで知らせる。選択の状態は VariantPicker でなく親が持つ */
function Controlled({ variants, onChange }: { variants: Variant[]; onChange?: (selection: Selection) => void }) {
  const [selection, setSelection] = useState(() => emptySelection(AXES))
  return (
    <VariantPicker
      axes={AXES}
      chosen={selectedVariant(AXES, variants, selection)}
      onChange={(next) => {
        setSelection(next)
        onChange?.(next)
      }}
      selection={selection}
      variants={variants}
    />
  )
}

describe('バリアントの選択', () => {
  test('AC-62 選んでいない軸を示し、在庫切れ・販売停止のバリアントにしかならない値は選べない', () => {
    renderInBrowser(<Controlled variants={VARIANTS} />)

    expect(screen.getByText('色・サイズを選ぶと、在庫状況を表示します。')).toBeTruthy()
    expect(button('赤').disabled).toBe(false)
    expect(button('青').disabled).toBe(true)
  })

  test('選べない値は取り消しの要素（s）で出し、選べる値は出さない。ボタンの名前は値のまま', () => {
    renderInBrowser(<Controlled variants={VARIANTS} />)

    expect(button('青').querySelector('s')?.textContent).toBe('青')
    expect(button('赤').querySelector('s')).toBeNull()
  })

  test('すべての軸を選ぶと、そのバリアントの在庫状況を出して知らせ、選んだ値を押すと外す', async () => {
    const onChange = vi.fn()
    const { user } = renderInBrowser(<Controlled onChange={onChange} variants={VARIANTS} />)

    await user.click(button('赤'))
    expect(screen.getByText('サイズを選ぶと、在庫状況を表示します。')).toBeTruthy()
    await user.click(button('L'))
    expect(screen.getByText('在庫あり')).toBeTruthy()
    expect(button('L').getAttribute('aria-pressed')).toBe('true')

    await user.click(button('L'))
    expect(screen.getByText('サイズを選ぶと、在庫状況を表示します。')).toBeTruthy()
    expect(onChange.mock.calls).toEqual([[['赤', null]], [['赤', 'L']], [['赤', null]]])
  })

  test('AC-56 すべてのバリアントが買えなければ、選ぶ前から「在庫なし」と出す', () => {
    renderInBrowser(<Controlled variants={[VARIANTS[2], VARIANTS[3]]} />)

    expect(screen.getByText('在庫なし')).toBeTruthy()
  })
})
