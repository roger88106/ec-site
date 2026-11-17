import type { Variant, VariantAxis } from '@/api/types'
import { translate } from '@/i18n/translate'

/** 軸ごとの選んだ値（軸と同じ並び）。null は未選択 */
export type Selection = (string | null)[]

/** 何も選んでいない状態を作る */
export function emptySelection(axes: readonly VariantAxis[]): Selection {
  return axes.map(() => null)
}

/** 在庫があり、販売停止でない（買える）バリアントかを判定する */
export function isPurchasable(variant: Variant): boolean {
  return variant.inStock && !variant.salesSuspended
}

/**
 * 軸の値が選べるかを判定する。
 *
 * その値と、他の軸で選んでいる値の組み合わせに、買えるバリアントが 1 つ以上あれば選べる。
 * 選ぼうとしている軸自身の選択は判断に使わない（選んだ後も、同じ軸の他の値に替えられるようにする）。
 *
 * @param axes      商品の軸
 * @param variants  商品のバリアント
 * @param selection 今の選択
 * @param axisIndex 値の軸の位置
 * @param value     調べる値
 * @returns 選べるなら true
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
        // 選ぼうとしている軸はその値、ほかの軸は選んでいる値と比べる。未選択の軸はどの値でもよい
        const wanted = index === axisIndex ? value : selection[index]
        return wanted === null || variant.axisValues[index] === wanted
      }),
  )
}

/**
 * 選択で決まるバリアントを返す。
 *
 * すべての軸を選んだときだけ決まる。軸のない商品は、ただ 1 つのバリアントを返す。
 *
 * @param axes      商品の軸
 * @param variants  商品のバリアント
 * @param selection 今の選択
 * @returns 選んだバリアント。決まらなければ null
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

/**
 * 軸の値を選んだ後の選択を返す。
 *
 * 選んでいる値をもう一度選ぶと、選択を外す。
 *
 * @param selection 今の選択
 * @param axisIndex 値の軸の位置
 * @param value     選んだ値
 * @returns 新しい選択
 */
export function toggleValue(selection: Selection, axisIndex: number, value: string): Selection {
  return selection.map((current, index) => {
    if (index !== axisIndex) {
      return current
    }
    return current === value ? null : value
  })
}

/**
 * バリアントの在庫状況の表示を返す。
 *
 * 「在庫あり」「在庫なし」だけで、販売停止も「在庫なし」とする。
 *
 * @param variant バリアント
 * @returns 在庫状況の表示
 */
export function stockLabel(variant: Variant): string {
  return isPurchasable(variant)
    ? translate('catalog.stock.available') // 在庫あり
    : translate('catalog.stock.unavailable') // 在庫なし
}

/**
 * 選んだバリアントのときに出す画像を返す。
 *
 * バリアントに指定した画像、なければ代表画像（画像の先頭）。
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
