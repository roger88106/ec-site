import type { ProductDetail } from '@/api/types'

/** 商品の基本情報の入力（登録と編集で共通。AC-13、18） */
export type ProductFieldsValues = {
  name: string
  description: string
  taxCategoryId: string | null
  priceExclTax: string
  categoryIds: string[]
}

/** 基本情報の入力欄の名前（サーバーの入力検証エラーを付ける先） */
export const PRODUCT_FIELD_NAMES = ['name', 'description', 'taxCategoryId', 'priceExclTax', 'categoryIds'] as const

/** 登録直後の基本情報 */
export const EMPTY_PRODUCT_FIELDS: ProductFieldsValues = {
  name: '',
  description: '',
  taxCategoryId: null,
  priceExclTax: '',
  categoryIds: [],
}

/** 商品の詳細から、基本情報の入力を作る */
export function toProductFields(product: ProductDetail): ProductFieldsValues {
  return {
    name: product.name,
    description: product.description,
    taxCategoryId: product.taxCategoryId,
    priceExclTax: String(product.priceExclTax),
    categoryIds: product.categoryIds,
  }
}

/** 基本情報の入力を、API の本文にする。形式の検証はサーバーが行う */
export function toProductBody(values: ProductFieldsValues) {
  return {
    name: values.name,
    description: values.description,
    taxCategoryId: values.taxCategoryId ?? '',
    priceExclTax: Number(values.priceExclTax),
    categoryIds: values.categoryIds,
  }
}

/** 基本情報の入力が送れる状態か（必須の未入力だけを見る） */
export function isProductFieldsFilled(values: ProductFieldsValues): boolean {
  return values.name.trim() !== '' && values.taxCategoryId !== null && values.priceExclTax.trim() !== ''
}

/** C 側に一度も表示されていないか。初回表示日時が空白か未来なら、表示されていない（AC-33、71） */
export function isNeverShown(product: Pick<ProductDetail, 'firstShownAt'>, now: number = Date.now()): boolean {
  return !product.firstShownAt || new Date(product.firstShownAt).getTime() > now
}
