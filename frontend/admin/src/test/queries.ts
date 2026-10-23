import { type BoundFunctions, type queries, screen } from '@testing-library/react'

/** 探す範囲。省略すると画面全体。{@code within(要素)} の結果を渡すと、その要素の中だけを探す */
type Scope = BoundFunctions<typeof queries>

/** 正規表現の記号をそのままの文字として扱えるようにする */
function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/** 項目名の {@code dt} の次の要素（対の {@code dd}）を返す */
function detailValueOf(term: HTMLElement): HTMLElement {
  const value = term.nextElementSibling
  if (!(value instanceof HTMLElement) || value.tagName !== 'DD') {
    throw new Error(`項目「${term.textContent}」の次に値（dd）がない`)
  }
  return value
}

/**
 * 詳細の項目の値（{@code dd}）を返す。見つからなければ失敗する。
 * 例：{@code expect(getDetailValue('与信期限')).toHaveTextContent('2026/10/20')}
 *
 * @param name 項目名（{@code dt} の文字）
 * @param scope 探す範囲。省略すると画面全体
 */
export function getDetailValue(name: string, scope: Scope = screen): HTMLElement {
  return detailValueOf(scope.getByText(name, { selector: 'dt' }))
}

/** {@link getDetailValue} の、項目が現れるまで待つ版 */
export async function findDetailValue(name: string, scope: Scope = screen): Promise<HTMLElement> {
  return detailValueOf(await scope.findByText(name, { selector: 'dt' }))
}

/**
 * 一覧の行を、名前に含む文字で返す。行の名前は、行の中のセルの文字をつないだもの。見つからなければ失敗する。
 * 例：{@code within(getRow('標準税率')).getByRole('cell', { name: '10%' })}
 *
 * @param name 行の名前に含む文字
 * @param scope 探す範囲。省略すると画面全体
 */
export function getRow(name: string, scope: Scope = screen): HTMLElement {
  return scope.getByRole('row', { name: new RegExp(escapeRegExp(name)) })
}

/** {@link getRow} の、行が現れるまで待つ版 */
export function findRow(name: string, scope: Scope = screen): Promise<HTMLElement> {
  return scope.findByRole('row', { name: new RegExp(escapeRegExp(name)) })
}
