import { Input } from '@ec-site/shared/ui/input'
import { normalizeCode } from '@/lib/code'

/** 品番の入力。半角の英小文字は、入力した時点で大文字にする（管理画面の構成の「フォーム」）。長さの上限は使う所で渡す */
export function CodeInput({
  value,
  onChange,
  ...props
}: { value: string; onChange: (value: string) => void } & Omit<React.ComponentProps<'input'>, 'value' | 'onChange'>) {
  return (
    <Input
      className="font-mono"
      onChange={(event) => onChange(normalizeCode(event.target.value))}
      value={value}
      {...props}
    />
  )
}
