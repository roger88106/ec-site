import type { ErrorCode } from '@/api/types'
import type { ActionResult } from '@/lib/actionResult'

/** 注文のフォームの入力欄（メールアドレスは会員のものを使い、入力しない） */
export type OrderField = 'name' | 'postalCode' | 'prefecture' | 'municipality' | 'address' | 'phone'

/** 注文のフォームに出すエラー。入力欄ごとのメッセージ、フォームの下のメッセージ、カートへのリンクを添えるか */
type PlaceOrderErrors = {
  fields: Partial<Record<OrderField, string>>
  /** フォームの下に出すメッセージ。null は出さない */
  message: string | null
  /** カートへのリンクを添えるか（カートを直さないと注文できないエラーのとき） */
  backToCart: boolean
}

/** 入力検証エラーの API の項目名 → 入力欄 */
const FIELD_BY_PATH: Record<string, OrderField> = {
  'shippingAddress.name': 'name',
  'shippingAddress.postalCode': 'postalCode',
  'shippingAddress.prefectureCode': 'prefecture',
  'shippingAddress.municipalityCode': 'municipality',
  'shippingAddress.address': 'address',
  'shippingAddress.phone': 'phone',
}

/** 業務の検証エラーのエラーコード → 入力欄 */
const FIELD_BY_ERROR_CODE: Partial<Record<ErrorCode, OrderField>> = {
  INVALID_RECIPIENT_NAME: 'name',
  INVALID_POSTAL_CODE: 'postalCode',
  INVALID_PREFECTURE: 'prefecture',
  INVALID_MUNICIPALITY: 'municipality',
  INVALID_ADDRESS: 'address',
  INVALID_PHONE_NUMBER: 'phone',
}

/** 次にすべきことがカートの画面にあるエラーコード（在庫不足、注文手続きの後のカートの変更） */
const BACK_TO_CART_ERROR_CODES = new Set<ErrorCode>(['OUT_OF_STOCK', 'CART_CHANGED'])

/**
 * 注文のサーバーアクションの結果を、フォームに出すエラーにする（店舗画面の構成の「結果の知らせ方」）。<br />
 * 入力欄に対応するエラーはその欄に、それ以外はフォームの下に出す。
 */
export function placeOrderErrors(result: ActionResult | null): PlaceOrderErrors {
  if (result === null || result.ok) {
    return { fields: {}, message: null, backToCart: false }
  }
  const codeField = result.errorCode ? FIELD_BY_ERROR_CODE[result.errorCode] : undefined
  if (codeField) {
    return { fields: { [codeField]: result.message }, message: null, backToCart: false }
  }
  const fields: PlaceOrderErrors['fields'] = {}
  for (const [path, message] of Object.entries(result.fieldErrors ?? {})) {
    const field = FIELD_BY_PATH[path]
    if (field) {
      fields[field] = message
    }
  }
  return {
    fields,
    message: result.message,
    backToCart: result.errorCode !== undefined && BACK_TO_CART_ERROR_CODES.has(result.errorCode),
  }
}
