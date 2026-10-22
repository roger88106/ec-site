import type { ComponentProps } from 'react'
import { ChartTooltipContent } from '@/components/ui/chart'

/** ChartTooltipContent の props に、値の整形を足したもの */
type Props = ComponentProps<typeof ChartTooltipContent> & {
  /** 値の整形。金額は formatYen、数量は formatNumber */
  formatValue: (value: number) => string
}

/**
 * グラフの吹き出し。色の印と系列の名前は ChartTooltipContent のままで、値だけを整形する。<br />
 * ChartTooltipContent は既定でロケールなしに値を整形するので、整形した文字列を渡して差し替える。
 * shadcn/ui の生成物は修正しない。
 */
export function ChartValueTooltip({ formatValue, payload, ...props }: Props) {
  const formatted = payload?.map((item) => ({
    ...item,
    value: typeof item.value === 'number' ? formatValue(item.value) : item.value,
  }))
  return <ChartTooltipContent payload={formatted} {...props} />
}
