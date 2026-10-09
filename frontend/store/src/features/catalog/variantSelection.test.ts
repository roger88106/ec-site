import { describe, expect, test } from 'vitest'
import type { Variant, VariantAxis } from '@/api/types'
import {
  emptySelection,
  isValueSelectable,
  selectedVariant,
  stockLabel,
  toggleValue,
} from '@/features/catalog/variantSelection'

const axes: VariantAxis[] = [
  { name: '色', values: ['白', '黒', '赤'] },
  { name: 'サイズ', values: ['M', 'L'] },
]

const variant = (id: string, axisValues: string[], inStock: boolean, salesSuspended = false): Variant => ({
  id,
  axisValues,
  inStock,
  salesSuspended,
})

// 白 M：在庫あり、白 L：在庫切れ、黒 M：販売停止（在庫はある）、黒 L：在庫あり。赤のバリアントはない
const variants = [
  variant('wm', ['白', 'M'], true),
  variant('wl', ['白', 'L'], false),
  variant('bm', ['黒', 'M'], true, true),
  variant('bl', ['黒', 'L'], true),
]

describe('バリアントの選択', () => {
  test('AC-62 何も選んでいなければ、選べるバリアントのある値はすべて選べる', () => {
    const selection = emptySelection(axes)
    expect(isValueSelectable(axes, variants, selection, 0, '白')).toBe(true)
    expect(isValueSelectable(axes, variants, selection, 0, '黒')).toBe(true)
    expect(isValueSelectable(axes, variants, selection, 1, 'M')).toBe(true)
    expect(isValueSelectable(axes, variants, selection, 1, 'L')).toBe(true)
  })

  test('AC-62 バリアントのない値は選べない', () => {
    expect(isValueSelectable(axes, variants, emptySelection(axes), 0, '赤')).toBe(false)
  })

  test('AC-62 在庫切れのバリアントにしかならない値は、選べない', () => {
    // 白を選ぶと、L は在庫切れの白 L にしかならない
    expect(isValueSelectable(axes, variants, ['白', null], 1, 'L')).toBe(false)
    expect(isValueSelectable(axes, variants, ['白', null], 1, 'M')).toBe(true)
  })

  test('AC-28 販売停止のバリアントにしかならない値は、選べない', () => {
    // 黒を選ぶと、M は販売停止の黒 M にしかならない
    expect(isValueSelectable(axes, variants, ['黒', null], 1, 'M')).toBe(false)
    expect(isValueSelectable(axes, variants, ['黒', null], 1, 'L')).toBe(true)
  })

  test('AC-62 他の軸の選択と組み合わせて判断する（選んだ軸自身の選択は見ない）', () => {
    // L を選んでいると、白は選べず、黒は選べる
    expect(isValueSelectable(axes, variants, [null, 'L'], 0, '白')).toBe(false)
    expect(isValueSelectable(axes, variants, [null, 'L'], 0, '黒')).toBe(true)
    // 白を選んでいても、色の軸の他の値（黒）は、色以外の選択で判断する
    expect(isValueSelectable(axes, variants, ['白', null], 0, '黒')).toBe(true)
  })

  test('AC-63 すべての軸を選ぶと、そのバリアントが決まる', () => {
    expect(selectedVariant(axes, variants, ['白', 'M'])?.id).toBe('wm')
    expect(selectedVariant(axes, variants, ['白', null])).toBeNull()
  })

  test('AC-63 軸のない商品は、ただ 1 つのバリアントが選ばれている', () => {
    const single = [variant('only', [], false)]
    expect(selectedVariant([], single, emptySelection([]))?.id).toBe('only')
  })

  test('選んだ値をもう一度選ぶと、選択を外す', () => {
    expect(toggleValue(['白', null], 0, '白')).toEqual([null, null])
    expect(toggleValue(['白', null], 0, '黒')).toEqual(['黒', null])
    expect(toggleValue(['白', null], 1, 'M')).toEqual(['白', 'M'])
  })

  test('AC-64 在庫状況は「在庫あり」「在庫なし」だけ。販売停止も「在庫なし」', () => {
    expect(stockLabel(variant('a', [], true))).toBe('在庫あり')
    expect(stockLabel(variant('b', [], false))).toBe('在庫なし')
    expect(stockLabel(variant('c', [], true, true))).toBe('在庫なし')
  })
})
