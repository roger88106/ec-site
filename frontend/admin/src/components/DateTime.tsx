import { formatDateTime } from '@/lib/format'

/** 日時（BR-3）。日本時間で表示する。空白（null）は placeholder を出す */
export function DateTime({ value, placeholder = '' }: { value: string | null | undefined; placeholder?: string }) {
  const text = formatDateTime(value)
  return <span className="tabular-nums">{text || placeholder}</span>
}
