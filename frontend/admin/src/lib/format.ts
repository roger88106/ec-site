/** 日本時間の UTC からのずれ（日本に夏時間はない） */
const JST_OFFSET_MS = 9 * 60 * 60 * 1000

/** 金額の書式（3 桁ごとのカンマ） */
const yen = new Intl.NumberFormat('ja-JP')

/** 金額（例：「1,000 円」） */
export function formatYen(amount: number): string {
  return `${yen.format(amount)} 円`
}

/**
 * 価格の表示（BR-4）。税込価格を主に、税抜価格を括弧で併記する。<br />
 * 例：「1,980 円（税抜 1,800 円）」。有効な税率がなく税込価格がなければ、税抜価格だけを出す。
 */
export function formatPrice(priceInclTax: number | null | undefined, priceExclTax: number): string {
  if (priceInclTax == null) {
    return `税抜 ${formatYen(priceExclTax)}（税込価格は計算できません）`
  }
  return `${formatYen(priceInclTax)}（税抜 ${formatYen(priceExclTax)}）`
}

/** 日時の表示（BR-3）。日本時間の「yyyy/MM/dd HH:mm」。空白（null）は空文字 */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return ''
  }
  const parts = jstParts(iso)
  return `${parts.year}/${parts.month}/${parts.day} ${parts.hour}:${parts.minute}`
}

/**
 * 日時の入力欄（datetime-local）の値を、API に送る日時にする（BR-3）。<br />
 * 入力は日本時間とみなし、+09:00 のオフセット付きにする。空の入力は空白（null）。
 */
export function fromDateTimeInput(value: string): string | null {
  if (!value) {
    return null
  }
  return `${value}:00+09:00`
}

/** API の日時を、日時の入力欄（datetime-local）の日本時間の値「yyyy-MM-ddTHH:mm」にする（BR-3） */
export function toDateTimeInput(iso: string | null | undefined): string {
  if (!iso) {
    return ''
  }
  const parts = jstParts(iso)
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`
}

/** 日本時間での年月日・時分（ゼロ埋め）。ブラウザのタイムゾーンに依存しないよう、UTC にずれを足して求める */
function jstParts(iso: string) {
  const jst = new Date(new Date(iso).getTime() + JST_OFFSET_MS)
  /** 2 桁にゼロ埋めする */
  const pad = (value: number) => String(value).padStart(2, '0')
  return {
    year: String(jst.getUTCFullYear()),
    month: pad(jst.getUTCMonth() + 1),
    day: pad(jst.getUTCDate()),
    hour: pad(jst.getUTCHours()),
    minute: pad(jst.getUTCMinutes()),
  }
}
