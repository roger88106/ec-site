/** CSV の 1 つの項目 */
export type CsvValue = string | number

/** 表計算ソフトが式として読む先頭の文字 */
const FORMULA_START = /^[=+\-@]/

/** 引用符で囲む項目（カンマ、引用符、改行を含む）。引用符は \x22 と書く（文言の検査が、引用符を文字列の始まりと読むため） */
const NEEDS_QUOTE = /[\x22,\r\n]/

/**
 * 表を CSV にする。表計算ソフトで文字化けしないよう UTF-8 の BOM で始め、行は CRLF で区切る。<br />
 * 文字の項目が式として読まれないよう、= + - @ で始まるものは前に ' を付ける。数は付けない。
 */
export function toCsv(rows: readonly (readonly CsvValue[])[]): string {
  return `\uFEFF${rows.map((row) => `${row.map(field).join(',')}\r\n`).join('')}`
}

/** 1 つの項目を CSV の形にする */
function field(value: CsvValue): string {
  if (typeof value === 'number') {
    return String(value)
  }
  const text = FORMULA_START.test(value) ? `'${value}` : value
  return NEEDS_QUOTE.test(text) ? `"${text.replaceAll('"', '""')}"` : text
}

/** CSV をファイルとして保存させる（ブラウザーのダウンロード） */
export function downloadCsv(filename: string, csv: string): void {
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
