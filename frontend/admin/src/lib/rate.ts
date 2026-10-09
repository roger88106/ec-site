const percent = new Intl.NumberFormat('ja-JP', { maximumFractionDigits: 2 })

/** 税率の表示。例：「10%」「8.5%」。null は「なし」 */
export function formatRate(ratePercent: number | null | undefined): string {
  return ratePercent == null ? 'なし' : `${percent.format(ratePercent)}%`
}
