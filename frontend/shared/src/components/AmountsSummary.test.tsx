// 照合の関数（toBeInTheDocument など）の型を得る。製品のコードの型の範囲を変えないよう、tsconfig でなくここで読み込む
import '@testing-library/jest-dom/vitest'
import { within } from '@testing-library/dom'
import { JSDOM } from 'jsdom'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, test } from 'vitest'
import { AmountsSummary, type AmountsSummaryVariant } from './AmountsSummary'

/** 内訳に渡す金額（商品の合計、送料、総額、税率ごとの課税対象額と消費税額） */
const amounts = {
  itemsTotal: 3462,
  shippingFee: 1000,
  total: 4462,
  taxes: [
    { ratePercent: 10, taxableAmount: 4300, taxAmount: 390 },
    { ratePercent: 8, taxableAmount: 162, taxAmount: 12 },
  ],
}

/**
 * 金額の内訳を HTML にし、jsdom の文書の body に読み込んで、探す関数を返す。
 * 共有のパッケージは店舗画面のテストの補助を使えないので、このファイルの中で作る。
 */
function renderSummary(variant: AmountsSummaryVariant) {
  const { document: jsdomDocument } = new JSDOM('<!doctype html><html><body></body></html>').window
  jsdomDocument.body.innerHTML = renderToStaticMarkup(<AmountsSummary amounts={amounts} variant={variant} />)
  return within(jsdomDocument.body)
}

/** 見える文言が、この順に出ている。見つからない文言があれば、その文言が失敗のメッセージに出る */
function expectInOrder(view: ReturnType<typeof within>, texts: string[]) {
  const elements = texts.map((text) => view.getByText(text))
  elements.slice(1).forEach((element, index) => {
    const previous = elements[index]
    expect(previous.compareDocumentPosition(element), `「${texts[index]}」の後に「${texts[index + 1]}」`).toBe(
      previous.DOCUMENT_POSITION_FOLLOWING,
    )
  })
}

/** 見える文言の、画面に出る順（商品の合計、送料、総額、税率ごとの消費税額） */
const ORDER = ['商品の合計', '3,462 円', '送料', '1,000 円', '総額（税込）', '4,462 円'].concat([
  '10% 対象 4,300 円（内消費税 390 円）',
  '8% 対象 162 円（内消費税 12 円）',
])

describe('金額の内訳（BR-7、8）', () => {
  test('AC-77 見た目の種類 page は、商品の合計、送料、区切り線、総額、税率ごとの消費税額の順に出す', () => {
    const view = renderSummary('page')
    expectInOrder(view, ORDER)
    const separator = view.getByRole('separator')
    const shippingFee = view.getByText('1,000 円')
    const total = view.getByText('総額（税込）')
    expect(shippingFee.compareDocumentPosition(separator)).toBe(shippingFee.DOCUMENT_POSITION_FOLLOWING)
    expect(separator.compareDocumentPosition(total)).toBe(separator.DOCUMENT_POSITION_FOLLOWING)
  })

  test('見た目の種類 card は、区切り線を出さずに、商品の合計、送料、総額、税率ごとの消費税額を金額と一緒に順に出す', () => {
    const view = renderSummary('card')
    expectInOrder(view, ORDER)
    expect(view.queryByRole('separator')).not.toBeInTheDocument()
  })
})
