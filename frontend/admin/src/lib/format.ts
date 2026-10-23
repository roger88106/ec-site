import { japanDateTimeFormat } from '@ec-site/shared/lib/format'

/** 1 分のミリ秒 */
const MINUTE_MS = 60 * 1000

/**
 * 日時の入力欄（datetime-local）の値を、API に送る日時にする。<br />
 * 入力は表示のタイムゾーン（日本時間）とみなし、そのオフセット付きにする。空の入力は空白（null）。
 */
export function fromDateTimeInput(value: string): string | null {
  if (!value) {
    return null
  }
  const [date, time] = value.split('T')
  const [year, month, day] = date.split('-').map(Number)
  const [hour, minute] = time.split(':').map(Number)
  // 入力の時刻を UTC とみなした時点で、表示のタイムゾーンのずれを求める
  const asUtc = Date.UTC(year, month - 1, day, hour, minute)
  return `${value}:00${offsetText(offsetMinutes(new Date(asUtc)))}`
}

/** API の日時を、日時の入力欄（datetime-local）の日本時間の値「yyyy-MM-ddTHH:mm」にする */
export function toDateTimeInput(iso: string | null | undefined): string {
  if (!iso) {
    return ''
  }
  const parts = zonedParts(new Date(iso))
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`
}

/** 表示のタイムゾーン（日本時間）の日付「yyyy-MM-dd」 */
export function toZonedDate(date: Date): string {
  const parts = zonedParts(date)
  return `${parts.year}-${parts.month}-${parts.day}`
}

/** 表示のタイムゾーンでの年月日・時分（ゼロ埋め）。ブラウザのタイムゾーンに依存しない */
function zonedParts(date: Date) {
  const parts = Object.fromEntries(japanDateTimeFormat.formatToParts(date).map((part) => [part.type, part.value]))
  return { year: parts.year, month: parts.month, day: parts.day, hour: parts.hour, minute: parts.minute }
}

/** その時点の、表示のタイムゾーンの UTC からのずれ（分） */
function offsetMinutes(date: Date): number {
  const parts = zonedParts(date)
  const zonedAsUtc = Date.UTC(
    Number(parts.year),
    Number(parts.month) - 1,
    Number(parts.day),
    Number(parts.hour),
    Number(parts.minute),
  )
  return Math.round((zonedAsUtc - Math.floor(date.getTime() / MINUTE_MS) * MINUTE_MS) / MINUTE_MS)
}

/** ずれ（分）を、日時のオフセットの文字（例：「+09:00」）にする */
function offsetText(minutes: number): string {
  const sign = minutes < 0 ? '-' : '+'
  const absolute = Math.abs(minutes)
  /** 2 桁にゼロ埋めする */
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${sign}${pad(Math.floor(absolute / 60))}:${pad(absolute % 60)}`
}
