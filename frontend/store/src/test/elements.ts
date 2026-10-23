import { screen } from '@testing-library/react'

/**
 * 役割と名前で探したボタン（押せるかどうかと、押している状態を見る。店舗画面の構成 第 7 節）。
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
