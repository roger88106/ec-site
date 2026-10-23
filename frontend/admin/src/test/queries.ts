import { type BoundFunctions, type queries, screen, within } from '@testing-library/react'

/** 探す範囲。省略すると画面全体。`within(要素)` の結果を渡すと、その要素の中だけを探す */
type Scope = BoundFunctions<typeof queries>

/** 正規表現の記号をそのままの文字として扱えるようにする */
function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * 項目名の `dt` の次の要素（対の `dd`）を返す。
 *
 * @param term 項目名の `dt`
 * @returns 対の `dd`
 * @throws 次の要素が `dd` でないとき
 */
function detailValueOf(term: HTMLElement): HTMLElement {
  const value = term.nextElementSibling
  if (!(value instanceof HTMLElement) || value.tagName !== 'DD') {
    throw new Error(`項目「${term.textContent}」の次に値（dd）がない`)
  }
  return value
}

/**
 * 詳細の項目の値（`dd`）を返す。見つからなければ失敗する。
 * 例：`expect(getDetailValue('与信期限')).toHaveTextContent('2026/10/20')`
 *
 * @param name 項目名（`dt` の文字）
 * @param scope 探す範囲。省略すると画面全体
 * @returns 項目名の次の `dd`
 */
export function getDetailValue(name: string, scope: Scope = screen): HTMLElement {
  return detailValueOf(scope.getByText(name, { selector: 'dt' }))
}

/**
 * {@link getDetailValue} の、項目が現れるまで待つ版。
 *
 * @param name 項目名（`dt` の文字）
 * @param scope 探す範囲。省略すると画面全体
 * @returns 項目名の次の `dd`
 */
export async function findDetailValue(name: string, scope: Scope = screen): Promise<HTMLElement> {
  return detailValueOf(await scope.findByText(name, { selector: 'dt' }))
}

/**
 * 一覧の行を、名前に含む文字で返す。行の名前は、行の中のセルの文字をつないだもの。見つからなければ失敗する。
 * 例：`within(getRow('標準税率')).getByRole('cell', { name: '10%' })`
 *
 * @param name 行の名前に含む文字
 * @param scope 探す範囲。省略すると画面全体
 * @returns 名前に含む文字の行
 */
export function getRow(name: string, scope: Scope = screen): HTMLElement {
  return scope.getByRole('row', { name: new RegExp(escapeRegExp(name)) })
}

/**
 * {@link getRow} の、行が現れるまで待つ版。
 *
 * @param name 行の名前に含む文字
 * @param scope 探す範囲。省略すると画面全体
 * @returns 名前に含む文字の行
 */
export function findRow(name: string, scope: Scope = screen): Promise<HTMLElement> {
  return scope.findByRole('row', { name: new RegExp(escapeRegExp(name)) })
}

/**
 * 一覧のセルを、行の名前と列の見出しの名前で返す。セルの並びの位置を、テストのファイルに書かない。
 * 見出しは、行のある表の `columnheader`。見つからなければ失敗する。
 * 例：`expect(getCell('ABC-001', '在庫数の合計')).toHaveTextContent('12')`
 *
 * @param rowName 行の名前に含む文字（{@link getRow} と同じ）
 * @param columnName 列の見出しの文字
 * @param scope 探す範囲。省略すると画面全体
 * @returns 行と列が交わる `cell`
 * @throws 列の見出しがないとき
 */
export function getCell(rowName: string, columnName: string, scope: Scope = screen): HTMLElement {
  const row = getRow(rowName, scope)
  const table = row.closest('table')
  if (table === null) {
    throw new Error(`行「${rowName}」が表の中にない`)
  }
  const column = within(table)
    .getAllByRole('columnheader')
    .findIndex((heading) => heading.textContent === columnName)
  if (column < 0) {
    throw new Error(`列「${columnName}」がない`)
  }
  return within(row).getAllByRole('cell')[column]
}
