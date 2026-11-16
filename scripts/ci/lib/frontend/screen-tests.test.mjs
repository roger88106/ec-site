import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findIndexedMultiples } from './screen-tests.mjs'

describe('テストの書き方の検査', () => {
  const lines = (hits) => hits.map((hit) => hit.line)

  test('getAllBy・findAllBy・queryAllBy の結果を、並びのインデックスで選ぶ所を見つける', () => {
    const text = [
      "screen.getAllByRole('link')[0]",
      "(await screen.findAllByText('a')).at(1)",
      "within(x).queryAllByRole('row')[2]",
      "screen.getAllByRole('link').at(-1)",
      "expect(screen.getAllByRole('link')).toHaveLength(2)",
      "screen.getAllByRole('row').slice(1)",
      "const items = screen.getAllByRole('listitem')",
      'expect(items[0]).toHaveTextContent(/a/)',
      "const [first] = screen.getAllByRole('link')",
      "const rows = screen.getAllByRole('row').map((row) => row)",
      'rows[0]',
      "// screen.getAllByRole('link')[0]",
    ].join('\n')
    assert.deepEqual(lines(findIndexedMultiples(text)), [1, 2, 3, 4, 8, 9])
  })

  test('変数に入れる形（within、型の変換、改行、スプレッド、Array.from、型の注釈、あとの代入）も見つける', () => {
    const cases = [
      ["const rows = within(table).getAllByRole('row')", 'rows[0]'],
      ["const rows = screen.getAllByRole('row') as HTMLElement[]", 'rows[0]'],
      ["const rows = (await screen.findAllByRole('row')) as unknown as HTMLElement[]", 'rows.at(1)'],
      ['const rows =', "  screen.getAllByRole('row')", 'rows[1]'],
      ["const rows = [...screen.getAllByRole('row')]", 'rows[0]'],
      ["const rows = Array.from(screen.getAllByRole('row'))", 'rows[0]'],
      ["const rows: HTMLElement[] = screen.getAllByRole('row')", 'rows[0]'],
      ['let rows', "rows = screen.getAllByRole('row')", 'rows[0]'],
    ]
    for (const parts of cases) {
      assert.deepEqual(lines(findIndexedMultiples(parts.join('\n'))), [parts.length], parts.join(' / '))
    }
    assert.deepEqual(lines(findIndexedMultiples(["const rows = screen.getAllByRole('row')", 'rows[0]', ''].join('\r\n'))), [2])
  })

  test('配列の分割代入は、スプレッドと Array.from、改行を含む形でも見つける', () => {
    const cases = ["const [a] = [...screen.getAllByRole('row')]", "const [a] = Array.from(screen.getAllByRole('row'))", "const [a] = (await screen.findAllByRole('row')) as HTMLElement[]"]
    for (const text of cases) assert.deepEqual(lines(findIndexedMultiples(text)), [1], text)
    assert.deepEqual(lines(findIndexedMultiples("const [a, b] =\n  within(x).getAllByRole('row')")), [2])
  })

  test('変数に入れる前に変換するもの、関数の引数に渡すものは見つけない', () => {
    const text = [
      "const names = screen.getAllByRole('row').map((row) => row)",
      'names[0]',
      "const sorted = sortRows(screen.getAllByRole('row'))",
      'sorted[0]',
      "const first = [...screen.getAllByRole('row')].filter(Boolean)",
      'first[0]',
    ].join('\n')
    assert.deepEqual(lines(findIndexedMultiples(text)), [])
  })

  test('同じ名前の別の変数は、別のテストや別の宣言の範囲では見つけない', () => {
    const text = [
      "test('a', () => {",
      "  const items = screen.getAllByRole('row')",
      '  expect(items).toHaveLength(2)',
      '})',
      "test('b', () => {",
      '  const items = [1, 2]',
      '  expect(items[0]).toBe(1)',
      '})',
      'const items = [3]',
      'items[0]',
    ].join('\n')
    assert.deepEqual(lines(findIndexedMultiples(text)), [])
    const shadowed = ["const rows = screen.getAllByRole('row')", 'rows[0]', 'function f(rows) { return rows[1] }', 'const g = (rows: string[]) => rows[2]'].join('\n')
    assert.deepEqual(lines(findIndexedMultiples(shadowed)), [2])
    const arrow = ["const rows = screen.getAllByRole('row')", 'rows[0]', 'const g = (rows: string[]) => rows[2]'].join('\n')
    assert.deepEqual(lines(findIndexedMultiples(arrow)), [2])
    const afterBlock = ['{', "  const rows = screen.getAllByRole('row')", '  rows[0]', '}', 'rows[1]'].join('\n')
    assert.deepEqual(lines(findIndexedMultiples(afterBlock)), [3])
  })
})
