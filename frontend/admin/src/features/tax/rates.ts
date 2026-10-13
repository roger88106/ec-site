import { translate } from '@/i18n/translate'

/** 税率の行の状態の判定に使う値 */
type RateLike = { rateId: string | null; effectiveFrom: string | null; effectiveTo: string | null }

/**
 * 税率の行の状態（AC-5 の変更できる範囲の目安）。<br />
 * 新しい行は「新規」。保存済みの行は、現在と適用期間を比べて「終了済み」「適用中」「未開始」。
 */
export function rateState(row: RateLike | undefined, now: number = Date.now()): string {
  if (!row || row.rateId === null) {
    return translate('tax.rateState.NEW') // 新規
  }
  if (row.effectiveTo !== null && new Date(row.effectiveTo).getTime() <= now) {
    return translate('tax.rateState.ENDED') // 終了済み
  }
  if (row.effectiveFrom === null || new Date(row.effectiveFrom).getTime() <= now) {
    return translate('tax.rateState.ACTIVE') // 適用中
  }
  return translate('tax.rateState.SCHEDULED') // 未開始
}
