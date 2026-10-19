import { schemas } from '@/api/generated/constraints'

/** 1 品目の数量の選択肢（契約の数量の下限から上限まで） */
export const QUANTITY_OPTIONS = Array.from(
  { length: schemas.Quantity.maximum - schemas.Quantity.minimum + 1 },
  (_, index) => schemas.Quantity.minimum + index,
)
