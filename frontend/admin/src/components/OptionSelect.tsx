import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

/** 選択肢の 1 つ。value が null の選択肢は「すべて」「なし」などに使う */
export type Option<V extends string> = { value: V | null; label: string }

/**
 * 決まった選択肢から 1 つ選ぶ。<br />
 * 選択肢を Select の items に渡し、選んだ値の表示名を出す。
 */
export function OptionSelect<V extends string>({
  id,
  options,
  value,
  onChange,
  placeholder = '選択してください',
  className,
}: {
  id?: string
  options: readonly Option<V>[]
  value: V | null
  onChange: (value: V | null) => void
  placeholder?: string
  className?: string
}) {
  return (
    <Select items={options} onValueChange={(selected) => onChange(selected as V | null)} value={value}>
      <SelectTrigger className={className} id={id}>
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        {options.map((option) => (
          <SelectItem key={option.value ?? ''} value={option.value}>
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
