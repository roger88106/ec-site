import { type BoundFunctions, type queries, screen, within } from '@testing-library/react'

/** 探す範囲。省略すると画面全体。`within(要素)` の結果を渡すと、その要素の中だけを探す */
type Scope = BoundFunctions<typeof queries>

/** 正規表現の記号をそのままの文字として扱えるようにする */
function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/** 行の名前が、渡した文字で始まる行に当たる正規表現 */
function rowNamePattern(name: string): RegExp {
  return new RegExp(`^${escapeRegExp(name)}`)
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
 * 項目が現れるまで待つ {@link getDetailValue}。
 *
 * @param name 項目名（`dt` の文字）
 * @param scope 探す範囲。省略すると画面全体
 * @returns 項目名の次の `dd`
 */
export async function findDetailValue(name: string, scope: Scope = screen): Promise<HTMLElement> {
  return detailValueOf(await scope.findByText(name, { selector: 'dt' }))
}

/**
 * 一覧の行を、名前の始まりの文字で返す。行の名前は、行の中のセルの文字をつないだもの。見つからなければ失敗する。
 * 「標準」と「標準（旧）」のような似た名前は、後者の全体を渡して区別する。
 * 例：`within(getRow('標準税率')).getByRole('cell', { name: '10%' })`
 *
 * @param name 行の名前の始まりの文字
 * @param scope 探す範囲。省略すると画面全体
 * @returns 名前が渡した文字で始まる行
 */
export function getRow(name: string, scope: Scope = screen): HTMLElement {
  return scope.getByRole('row', { name: rowNamePattern(name) })
}

/**
 * 行が現れるまで待つ {@link getRow}。
 *
 * @param name 行の名前の始まりの文字
 * @param scope 探す範囲。省略すると画面全体
 * @returns 名前が渡した文字で始まる行
 */
export function findRow(name: string, scope: Scope = screen): Promise<HTMLElement> {
  return scope.findByRole('row', { name: rowNamePattern(name) })
}

/**
 * 一覧のセルを、行の名前と列の見出しの名前で返す。セルの並びの位置を、テストのファイルに書かない。
 * 見出しは、行のある表の `columnheader`。見つからなければ失敗する。
 * 行の見出しのセル（`th scope="row"`、`rowheader`）も 1 つの列に数える。入れ子の表の見出しとセルは、外の表の列に数えない。
 * 例：`expect(getCell('ABC-001', '在庫数の合計')).toHaveTextContent('12')`
 *
 * @param rowName 行の名前の始まりの文字（{@link getRow} と同じ）
 * @param columnName 列の見出しの文字
 * @param scope 探す範囲。省略すると画面全体
 * @returns 行と列が交わる `cell`
 * @throws 列の見出しがないとき、または行にその列のセルがないとき
 */
export function getCell(rowName: string, columnName: string, scope: Scope = screen): HTMLElement {
  const row = getRow(rowName, scope)
  const table = row.closest('table')
  if (table === null) {
    throw new Error(`行「${rowName}」が表の中にない`)
  }
  // 入れ子の表の見出しとセルは、外の表の列に数えない
  const column = within(table)
    .getAllByRole('columnheader')
    .filter((heading) => heading.closest('table') === table)
    .findIndex((heading) => heading.textContent === columnName)
  if (column < 0) {
    throw new Error(`列「${columnName}」がない`)
  }
  // 行の見出しのセル（th scope="row"）も、1 つの列に数える
  const cells = Array.from(row.children).filter((child) => child.matches('td, th'))
  const cell = cells[column]
  if (!(cell instanceof HTMLElement)) {
    throw new Error(`行「${rowName}」に列「${columnName}」のセルがない`)
  }
  return cell
}

/**
 * 渡した要素を含む `listitem` を返す。入れ子の一覧では、一番外の項目（子の項目も中に入る）を返す。含む項目がなければ失敗する。
 * 例：`within(getListItemContaining(await findGroup('衣類'))).getByText('トップス')`
 *
 * @param element 項目の中にある要素
 * @param scope 探す範囲。省略すると画面全体
 * @returns 渡した要素を含む `listitem`
 * @throws 含む `listitem` がないとき
 */
export function getListItemContaining(element: HTMLElement, scope: Scope = screen): HTMLElement {
  const item = scope.getAllByRole('listitem').find((candidate) => candidate.contains(element))
  if (item === undefined) {
    throw new Error('渡した要素を含む listitem がない')
  }
  return item
}

/**
 * 名前を持つ `group` を、名前の全体が一致するもので返す。現れるまで待つ。
 * 例：`within(await findGroup('衣類')).getByRole('button', { name: '子を追加' })`
 *
 * @param name `group` の名前（aria-label など）
 * @param scope 探す範囲。省略すると画面全体
 * @returns 名前が一致する `group`
 */
export function findGroup(name: string, scope: Scope = screen): Promise<HTMLElement> {
  return scope.findByRole('group', { name })
}
