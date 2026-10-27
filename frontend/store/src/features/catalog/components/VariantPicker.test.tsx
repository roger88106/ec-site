// @vitest-environment jsdom
import { screen } from '@testing-library/react'
import { useState } from 'react'
import { describe, expect, test, vi } from 'vitest'
import type { Variant } from '@/api/types'
import { VariantPicker } from '@/features/catalog/components/VariantPicker'
import { emptySelection, type Selection, selectedVariant } from '@/features/catalog/variantSelection'
import { button } from '@/test/elements'
import { VARIANT_AXES, variant } from '@/test/fixtures'
import { renderInBrowser } from '@/test/render'

/** 赤 M・赤 L は買える。青 M は在庫切れ、青 L は販売停止で、青は選べない */
const VARIANTS = [
  variant('赤', 'M'),
  variant('赤', 'L', { imageUrl: 'http://cdn.test/red-l.webp' }),
  variant('青', 'M', { inStock: false }),
  variant('青', 'L', { salesSuspended: true }),
]

/** 選択を親として持ち、変わるたびに onChange を呼んで知らせる。選択の状態は VariantPicker でなく親が持つ */
function Controlled({ variants, onChange }: { variants: Variant[]; onChange?: (selection: Selection) => void }) {
  const [selection, setSelection] = useState(() => emptySelection(VARIANT_AXES))
  return (
    <VariantPicker
      axes={VARIANT_AXES}
      chosen={selectedVariant(VARIANT_AXES, variants, selection)}
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

    expect(screen.getByText('色・サイズを選ぶと、在庫状況を表示します。')).toBeInTheDocument()
    expect(screen.queryByText('在庫あり')).not.toBeInTheDocument()
    expect(button('赤')).toBeEnabled()
    expect(button('青')).toBeDisabled()
  })

  test('すべての軸を選ぶと、そのバリアントの在庫状況を出して知らせ、選んだ値を押すと外す', async () => {
    const onChange = vi.fn()
    const { user } = renderInBrowser(<Controlled onChange={onChange} variants={VARIANTS} />)

    await user.click(button('赤'))
    expect(screen.getByText('サイズを選ぶと、在庫状況を表示します。')).toBeInTheDocument()
    expect(screen.queryByText('在庫あり')).not.toBeInTheDocument()
    await user.click(button('L'))
    expect(screen.getByText('在庫あり')).toBeInTheDocument()
    expect(button('L')).toHaveAttribute('aria-pressed', 'true')

    await user.click(button('L'))
    expect(screen.getByText('サイズを選ぶと、在庫状況を表示します。')).toBeInTheDocument()
    expect(screen.queryByText('在庫あり')).not.toBeInTheDocument()
    expect(button('L')).toHaveAttribute('aria-pressed', 'false')
    expect(onChange.mock.calls).toEqual([[['赤', null]], [['赤', 'L']], [['赤', null]]])
  })

  test('AC-56 すべてのバリアントが買えなければ、選ぶ前から「在庫なし」と出す', () => {
    renderInBrowser(<Controlled variants={[VARIANTS[2], VARIANTS[3]]} />)

    expect(screen.getByText('在庫なし')).toBeInTheDocument()
    expect(screen.queryByText('色・サイズを選ぶと、在庫状況を表示します。')).not.toBeInTheDocument()
  })

  test('AC-227-6 在庫状況の領域は status の役割を持ち、選択を変えると中の文が替わる', async () => {
    const { user } = renderInBrowser(<Controlled variants={VARIANTS} />)

    const region = screen.getByRole('status')
    expect(region).toHaveTextContent('色・サイズを選ぶと、在庫状況を表示します。')
    await user.click(button('赤'))
    await user.click(button('L'))

    expect(screen.getByRole('status')).toBe(region)
    expect(region).toHaveTextContent('在庫あり')
  })
})
