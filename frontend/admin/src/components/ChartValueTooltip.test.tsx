import { formatNumber, formatYen } from '@ec-site/shared/lib/format'
import { render, screen } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { ChartValueTooltip } from '@/components/ChartValueTooltip'
import { type ChartConfig, ChartContainer } from '@/components/ui/chart'

const chartConfig = {
  amount: { label: '売上金額', color: 'var(--chart-1)' },
  quantity: { label: '販売数量', color: 'var(--chart-2)' },
} satisfies ChartConfig

function renderTooltip(dataKey: 'amount' | 'quantity', formatValue: (value: number) => string) {
  render(
    <ChartContainer config={chartConfig}>
      <ChartValueTooltip
        active
        formatValue={formatValue}
        payload={[{ graphicalItemId: dataKey, dataKey, name: dataKey, value: 1234, payload: { [dataKey]: 1234 } }]}
      />
    </ChartContainer>,
  )
}

describe('グラフの吹き出し', () => {
  test('金額の整形を渡すと、値が「1,234 円」で出て、系列の名前もそのまま出る', () => {
    renderTooltip('amount', formatYen)

    expect(screen.getByText('1,234 円')).toBeInTheDocument()
    expect(screen.getAllByText('売上金額')).toHaveLength(2) // 見出しと系列の行
  })

  test('数の整形を渡すと、値が「1,234」で出て、系列の名前もそのまま出る', () => {
    renderTooltip('quantity', formatNumber)

    expect(screen.getByText('1,234')).toBeInTheDocument()
    expect(screen.getAllByText('販売数量')).toHaveLength(2) // 見出しと系列の行
  })
})
