import type { Variant, VariantAxis } from '@/api/types'

/** 軸ごとの選んだ値（軸と同じ並び）。null は未選択 */
export type Selection = (string | null)[]

/** 何も選んでいない状態 */
export function emptySelection(axes: readonly VariantAxis[]): Selection {
  return axes.map(() => null)
}

/** 買えるバリアントか。在庫があり、販売停止でない（AC-28、62） */
export function isPurchasable(variant: Variant): boolean {
  return variant.inStock && !variant.salesSuspended
}

/**
 * 軸の値が選べるか（AC-62、28）。<br />
 * その値と、他の軸で選んでいる値の組み合わせに、買えるバリアントが 1 つ以上あれば選べる。
 * 選ぼうとしている軸自身の選択は判断に使わない（選んだ後も、同じ軸の他の値に替えられるようにする）。
 */
export function isValueSelectable(
  axes: readonly VariantAxis[],
  variants: readonly Variant[],
  selection: Selection,
  axisIndex: number,
  value: string,
): boolean {
  return variants.some(
    (variant) =>
      isPurchasable(variant) &&
      axes.every((_, index) => {
        const wanted = index === axisIndex ? value : selection[index]
        return wanted === null || variant.axisValues[index] === wanted
      }),
  )
}

/**
 * 選んだバリアント（AC-63）。すべての軸を選んだときだけ決まる。<br />
 * 軸のない商品は、ただ 1 つのバリアントを返す。
 */
export function selectedVariant(
  axes: readonly VariantAxis[],
  variants: readonly Variant[],
  selection: Selection,
): Variant | null {
  if (axes.length === 0) {
    return variants[0] ?? null
  }
  if (selection.some((value) => value === null)) {
    return null
  }
  return variants.find((variant) => axes.every((_, index) => variant.axisValues[index] === selection[index])) ?? null
}

/** 値を選ぶ。選んでいる値をもう一度選ぶと、選択を外す */
export function toggleValue(selection: Selection, axisIndex: number, value: string): Selection {
  return selection.map((current, index) => {
    if (index !== axisIndex) {
      return current
    }
    return current === value ? null : value
  })
}

/** 在庫状況の表示（AC-64）。「在庫あり」「在庫なし」だけで、販売停止も「在庫なし」とする */
export function stockLabel(variant: Variant): '在庫あり' | '在庫なし' {
  return isPurchasable(variant) ? '在庫あり' : '在庫なし'
}

/**
 * 選んだバリアントのときに出す画像（AC-38）。バリアントに指定した画像、なければ代表画像（画像の先頭）。
 *
 * @param imageUrls 商品の画像（先頭が代表画像）
 * @param variant   選んだバリアント。選んでいなければ null
 * @returns 画像の URL。商品に画像がなく、バリアントにも指定がなければ null
 */
export function imageForVariant(
  imageUrls: readonly string[],
  variant: Pick<Variant, 'imageUrl'> | null,
): string | null {
  return variant?.imageUrl ?? imageUrls[0] ?? null
}
