const number = new Intl.NumberFormat('ja-JP', { signDisplay: 'exceptZero' })

/** 在庫の増減量の表示。増加は「+」を付ける（例：「+10」「-3」） */
export function formatDelta(delta: number): string {
  return number.format(delta)
}
