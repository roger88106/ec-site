import { screen } from '@testing-library/react'
import { expect } from 'vitest'

/**
 * 役割と名前で探したボタン（押せるかどうかと、押している状態を見る。店舗画面の構成の「テスト」）。
 *
 * @param name ボタンの名前
 * @returns ボタン。ボタンでなければ、テストを失敗させる
 */
export function button(name: string): HTMLButtonElement {
  const element = screen.getByRole('button', { name })
  if (!(element instanceof HTMLButtonElement)) {
    throw new Error(`button でない要素です: ${name}`)
  }
  return element
}

/**
 * 見出しで探した選択欄。
 *
 * @param label 欄の見出し
 * @returns 選択欄。選択欄でなければ、テストを失敗させる
 */
export function select(label: string): HTMLSelectElement {
  const element = screen.getByLabelText(label)
  if (!(element instanceof HTMLSelectElement)) {
    throw new Error(`選択欄ではない：${label}`)
  }
  return element
}

/**
 * 見出しで探した入力欄。
 *
 * @param label 欄の見出し
 * @returns 入力欄。入力欄でなければ、テストを失敗させる
 */
export function input(label: string): HTMLInputElement {
  const element = screen.getByLabelText(label)
  if (!(element instanceof HTMLInputElement)) {
    throw new Error(`入力欄ではない：${label}`)
  }
  return element
}

/**
 * 要素が、渡した順に文書の中で出ることを確認する（見える文言の順）。隣り合う要素の組ごとに、後の要素が前の要素より後にある。
 * 定数は要素から読む（テストの環境が Node のままでも使える）。
 * 例：`expectInOrder(view.getByLabelText('郵便番号'), error, view.getByLabelText('都道府県'))`
 *
 * @param elements 文書の順に並ぶはずの要素（2 つ以上）
 * @throws 順が違うとき、または要素が 2 つに満たないとき
 */
export function expectInOrder(...elements: Element[]): void {
  if (elements.length < 2) {
    throw new Error('順を確認するには、要素が 2 つ以上いる')
  }
  elements.slice(1).forEach((element, index) => {
    const previous = elements[index]
    expect(previous.compareDocumentPosition(element), `${index + 1} 番目の要素の後に ${index + 2} 番目の要素`).toBe(
      previous.DOCUMENT_POSITION_FOLLOWING,
    )
  })
}
