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
 * フォームの整数の項目を取り出す。範囲は確かめない（API の契約が確かめる）。
 *
 * @param formData フォーム
 * @param name     項目の名前
 * @returns 整数。空、数でない、小数なら null
 */
export function readInt(formData: FormData, name: string): number | null {
  const value = readString(formData, name)
  return INTEGER.test(value) ? Number(value) : null
}
