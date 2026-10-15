import { describe, expect, it } from 'vitest'
import { toCsv } from '@/features/report/csv'

describe('CSV の組み立て', () => {
  it('AC-218 UTF-8 の BOM で始め、項目をカンマで、行を CRLF で区切る', () => {
    const csv = toCsv([
      ['期間', '売上額', '販売数量'],
      ['2026 年 4 月', 12000, 3],
    ])

    expect(csv).toBe('\uFEFF期間,売上額,販売数量\r\n2026 年 4 月,12000,3\r\n')
  })

  it('AC-218 カンマ・引用符・改行を含む項目は引用符で囲み、引用符は 2 つ重ねる', () => {
    const csv = toCsv([['T シャツ, 白', '"限定"の品', '1 行目\n2 行目']])

    expect(csv).toBe('\uFEFF"T シャツ, 白","""限定""の品","1 行目\n2 行目"\r\n')
  })

  it('AC-218 表計算ソフトが式として読む先頭の文字（= + - @）は、前に引用符を付けて文字にする', () => {
    const csv = toCsv([['=SUM(A1)', '+81', '-', '@x', -500]])

    expect(csv).toBe("\uFEFF'=SUM(A1),'+81,'-,'@x,-500\r\n")
  })
})
