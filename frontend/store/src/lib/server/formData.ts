/** 整数の形（符号と数字だけ） */
const INTEGER = /^-?\d+$/

/**
 * フォームの文字の項目を取り出す。前後の空白は除く。
 *
 * @param formData フォーム
 * @param name     項目の名前
 * @returns 値。項目がなければ空文字
 */
export function readString(formData: FormData, name: string): string {
  return String(formData.get(name) ?? '').trim()
}

/**
 * フォームの整数の項目を取り出す。範囲は確認しない（API の契約が確認する）。
 *
 * @param formData フォーム
 * @param name     項目の名前
 * @returns 整数。空、数でない、小数なら null
 */
export function readInt(formData: FormData, name: string): number | null {
  const value = readString(formData, name)
  return INTEGER.test(value) ? Number(value) : null
}

/**
 * フォームの整数の項目を、整数か NaN で取り出す。<br />
 * 数でない値を握りつぶさず、NaN のまま API に送って入力の検証の 400 で断らせる。
 *
 * @param formData フォーム
 * @param name     項目の名前
 * @returns 整数。空、数でない、小数なら NaN
 */
export function readIntOrNaN(formData: FormData, name: string): number {
  return readInt(formData, name) ?? Number.NaN
}
