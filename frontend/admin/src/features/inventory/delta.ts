import { LOCALE } from '@ec-site/shared/lib/locale'

/** 符号付きの数の書式（0 以外は + か - を付ける） */
const number = new Intl.NumberFormat(LOCALE, { signDisplay: 'exceptZero' })

/** 在庫の増減量の表示。増加は「+」を付ける（例：「+10」「-3」） */
export function formatDelta(delta: number): string {
  return number.format(delta)
}
