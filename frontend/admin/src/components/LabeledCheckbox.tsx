import type { ReactNode } from 'react'
import { Checkbox } from '@/components/ui/checkbox'

/**
 * ラベル付きのチェックボックス。ラベルを押しても切り替わる。
 *
 * @param checked         選んでいるか
 * @param onCheckedChange 切り替えたとき
 * @param children        ラベル
 */
export function LabeledCheckbox({
  checked,
  onCheckedChange,
  children,
}: {
  checked: boolean
  onCheckedChange: (checked: boolean) => void
  children: ReactNode
}) {
  return (
    // biome-ignore lint/a11y/noLabelWithoutControl: Checkbox（base-ui）はラベルの中の button 要素
    <label className="flex items-center gap-2 text-sm">
      <Checkbox checked={checked} onCheckedChange={onCheckedChange} />
      {children}
    </label>
  )
}
