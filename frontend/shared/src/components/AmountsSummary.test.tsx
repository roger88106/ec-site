import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, test } from 'vitest'
import { AmountsSummary } from './AmountsSummary'

const amounts = {
  itemsTotal: 3462,
  shippingFee: 1000,
  total: 4462,
  taxes: [
    { ratePercent: 10, taxableAmount: 4300, taxAmount: 390 },
    { ratePercent: 8, taxableAmount: 162, taxAmount: 12 },
  ],
}

/** 見える文言が、この順に出ている */
function expectInOrder(html: string, texts: string[]) {
  const positions = texts.map((text) => html.indexOf(text))
  expect(positions.every((index) => index >= 0)).toBe(true)
  expect(positions).toEqual([...positions].sort((a, b) => a - b))
}

const ORDER = ['商品の合計', '3,462 円', '送料', '1,000 円', '総額（税込）', '4,462 円'].concat([
  '10% 対象 4,300 円（内消費税 390 円）',
  '8% 対象 162 円（内消費税 12 円）',
])

describe('金額の内訳（BR-7、8）', () => {
  test('AC-77 商品の合計、送料、総額、税率ごとの消費税額の順に出す', () => {
    const html = renderToStaticMarkup(<AmountsSummary amounts={amounts} variant="page" />)
    expectInOrder(html, ORDER)
  })

  test('見た目の種類 card でも、商品の合計、送料、総額、税率ごとの消費税額を金額と一緒に順に出す', () => {
    const html = renderToStaticMarkup(<AmountsSummary amounts={amounts} variant="card" />)
    expectInOrder(html, ORDER)
  })
})
