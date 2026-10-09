import type { components } from '@/api/generated/admin'

/** admin.yaml のスキーマの型。画面からはこの名前で使う */
type Schemas = components['schemas']

export type Problem = Schemas['Problem']
export type TaxCategory = Schemas['TaxCategory']
export type TaxRate = Schemas['TaxRate']
export type CategoryNode = Schemas['CategoryNode']
export type PublicationStatus = Schemas['PublicationStatus']
export type ProductSummary = Schemas['ProductSummary']
export type ProductDetail = Schemas['ProductDetail']
export type Variant = Schemas['Variant']
export type StockAdjustmentReason = Schemas['StockAdjustmentReason']
export type StockAdjustment = Schemas['StockAdjustment']
