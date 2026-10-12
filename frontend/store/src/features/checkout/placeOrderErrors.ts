import type { ActionResult } from '@/lib/actionResult'

/** 注文のフォームの入力欄（メールアドレスは会員のものを使い、入力しない。AC-127） */
export type OrderField = 'name' | 'postalCode' | 'prefecture' | 'address' | 'phone'

/** 注文のフォームに出すエラー。入力欄ごとのメッセージ、フォームの下のメッセージ、カートへのリンクを添えるか */
export type PlaceOrderErrors = {
  fields: Partial<Record<OrderField, string>>
  message: string | null
  backToCart: boolean
}

/** 入力検証エラーの API の項目名 → 入力欄 */
const FIELD_BY_PATH: Record<string, OrderField> = {
  'shippingAddress.name': 'name',
  'shippingAddress.postalCode': 'postalCode',
  'shippingAddress.prefecture': 'prefecture',
  'shippingAddress.address': 'address',
  'shippingAddress.phone': 'phone',
}

/**
 * 入力検証エラー（API の契約の形の検証）のときに、入力欄に出すメッセージ。<br />
 * API が返すメッセージは検証の部品の既定の文（正規表現など）なので、業務の検証エラーと同じ文に揃える。
 */
const FIELD_MESSAGES: Record<OrderField, string> = {
  name: '氏名を 50 文字以内で入力してください。',
  postalCode: '郵便番号は数字 7 桁で入力してください。',
  prefecture: '都道府県を選んでください。',
  address: '住所を 200 文字以内で入力してください。',
  phone: '電話番号は数字 10〜11 桁で入力してください。',
}

/** 業務の検証エラーのエラーコード → 入力欄（AC-86） */
const FIELD_BY_ERROR_CODE: Record<string, OrderField> = {
  INVALID_RECIPIENT_NAME: 'name',
  INVALID_POSTAL_CODE: 'postalCode',
  INVALID_PREFECTURE: 'prefecture',
  INVALID_ADDRESS: 'address',
  INVALID_PHONE_NUMBER: 'phone',
}

/** 次にすべきことがカートの画面にあるエラーコード（在庫不足、注文手続きの後のカートの変更） */
const BACK_TO_CART_ERROR_CODES = new Set(['OUT_OF_STOCK', 'CART_CHANGED'])

/**
 * 注文のサーバーアクションの結果を、フォームに出すエラーにする（店舗画面の構成 第 6 節）。<br />
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
  for (const path of Object.keys(result.fieldErrors ?? {})) {
    const field = FIELD_BY_PATH[path]
    if (field) {
      fields[field] = FIELD_MESSAGES[field]
    }
  }
  return {
    fields,
    message: result.message,
    backToCart: result.errorCode !== undefined && BACK_TO_CART_ERROR_CODES.has(result.errorCode),
  }
}
