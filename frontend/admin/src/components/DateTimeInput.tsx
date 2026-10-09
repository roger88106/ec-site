import { Input } from '@/components/ui/input'
import { fromDateTimeInput, toDateTimeInput } from '@/lib/format'

/**
 * 日時の入力（BR-3）。日本時間で入力し、オフセット付きの日時（空なら null）を返す。<br />
 * 値は API の日時の文字列で受け渡す。
 */
export function DateTimeInput({
  value,
  onChange,
  ...props
}: {
  value: string | null
  onChange: (value: string | null) => void
} & Omit<React.ComponentProps<'input'>, 'value' | 'onChange' | 'type'>) {
  return (
    <Input
      type="datetime-local"
      value={toDateTimeInput(value)}
      onChange={(event) => onChange(fromDateTimeInput(event.target.value))}
      {...props}
    />
  )
}
