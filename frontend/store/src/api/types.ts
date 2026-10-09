import type { components } from '@/api/generated/store'

/** store.yaml のスキーマの型。画面からはこの名前で使う */
type Schemas = components['schemas']

export type Problem = Schemas['Problem']
export type Price = Schemas['Price']
export type ProductSort = Schemas['ProductSort']
export type ProductSummary = Schemas['ProductSummary']
export type ProductSummaryPage = Schemas['ProductSummaryPage']
export type ProductDetail = Schemas['ProductDetail']
export type VariantAxis = Schemas['VariantAxis']
export type Variant = Schemas['Variant']
export type CategoryNode = Schemas['CategoryNode']
