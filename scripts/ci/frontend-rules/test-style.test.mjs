import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findBuiltinShadowing, findMissingTestSpacing } from './test-style.mjs'

describe('組み込みの名前をシャドーイングする const・let の検査', () => {
  const names = new Set(['status', 'name', 'alert', 'cookieStore', 'Set'])
  const found = (text) => findBuiltinShadowing(text, names).map((hit) => `${hit.line}:${hit.name}`)

  test('const と let の宣言の名前が一覧にあれば見つける', () => {
    const text = ['const status = 200', "let name = 'a'", 'const total = 1', 'const cookieStore = createStore()'].join('\n')
    assert.deepEqual(found(text), ['1:status', '2:name', '4:cookieStore'])
  })

  test('オブジェクトの分割代入は、右側（宣言される名前）を見る', () => {
    const text = [
      'const { status } = response',
      'const { code: name } = response',
      'const { name: label } = response',
      'const { a, ...alert } = response',
      'const { name = 1 } = response',
      'const { x: { y: status } } = response',
      'const { [key]: name } = response',
    ].join('\n')
    assert.deepEqual(found(text), ['1:status', '2:name', '4:alert', '5:name', '6:status', '7:name'])
  })

  test('配列の分割代入は、全ての要素を見る', () => {
    const text = ['const [a, status] = pair', 'const [, name] = pair', 'const [first, ...Set] = pair', 'const [[alert], b] = pair', 'const [a, b] = pair'].join('\n')
    assert.deepEqual(found(text), ['1:status', '2:name', '3:Set', '4:alert'])
  })

  test('for…of と for…in の宣言を見つける', () => {
    const text = ['for (const status of statuses) {', '}', 'for (let name = 0; name < 3; name += 1) {', '}', 'for (const { name } of items) {', '}', 'for (const key in object) {', '}'].join('\n')
    assert.deepEqual(found(text), ['1:status', '3:name', '5:name'])
  })

  test('1 つの宣言に並べた名前と、型の注釈の付いた名前を見つける', () => {
    const text = ['const a = 1, status = 2', 'let b, name', 'const alert: string = "a"', 'const map: Map<string, number> = new Map()', 'const total = sum(1, 2),', '  Set = 1'].join('\n')
    assert.deepEqual(found(text), ['1:status', '2:name', '3:alert', '6:Set'])
  })

  test('初期値のジェネリクスのカンマの後ろの宣言も見つける。比較の < と > は取り違えない', () => {
    const text = [
      'const m = new Map<string, number>(), status = 3',
      'const a = f<A, B<C, D>>(1), b = 2, name = 3',
      'const cb = new Map<string, (x: number) => void>(), alert = 1',
      'const lt = a < b, Set = 1',
      'const gt = a > b, cookieStore = 1',
      'const mix = x < y ? p : q, status = 1',
    ].join('\n')
    assert.deepEqual(found(text), ['1:status', '2:name', '3:alert', '4:Set', '5:cookieStore', '6:status'])
  })

  test('型引数を持つ矢印関数（= の直後の <）の後ろの宣言も見つける。= の後ろの比較は取り違えない', () => {
    const text = [
      'const f = <T,>(a: T) => a, alert = 1',
      'const g = <T, U>(a: T, b: U) => a, status = 2',
      'const h = (<T,>(a: T) => a), name = 3',
      'const lt = a < b, Set = 1',
    ].join('\n')
    assert.deepEqual(found(text), ['1:alert', '2:status', '3:name', '4:Set'])
  })

  test('関数の引数、catch の変数、関数・クラスの宣言、import は対象外', () => {
    const text = [
      'function check(status) {}',
      'const run = (name) => name',
      'const fn = async (alert, other) => {}',
      'try {} catch (status) {}',
      'function name() {}',
      'class Set {}',
      "import { name } from './a'",
      "import status from './b'",
      'items.map((status) => status)',
      'test.each([1])("a", (name) => {})',
    ].join('\n')
    assert.deepEqual(found(text), [])
  })

  test('一覧にない名前、プロパティ、as const は見つけない', () => {
    const text = [
      'const orderStatus = 200',
      'const { code } = response',
      'const body = { status: 200, name: "a" }',
      'expect(response.status).toBe(200)',
      'const items = [1] as const',
      'status = 3',
      'const value = status',
    ].join('\n')
    assert.deepEqual(found(text), [])
  })

  test('コメントと文字列の中は見つけない', () => {
    const text = ['// const status = 1', '/* let name = 2 */', "const text = 'const alert = 1'", 'const html = `let name = ${1}`'].join('\n')
    assert.deepEqual(found(text), [])
  })

  test('複数の行にまたがる分割代入も見つける。行は宣言の始まり', () => {
    const text = ['const {', '  total,', '  status,', '} = response'].join('\n')
    assert.deepEqual(found(text), ['1:status'])
  })

  test('報告に行の番号、元の行の文字、名前を含む', () => {
    assert.deepEqual(findBuiltinShadowing('x\n  const status = 1  ', names), [{ line: 2, text: 'const status = 1', name: 'status' }])
  })
})

describe('テストの間の空行の検査', () => {
  const lines = (hits) => hits.map((hit) => hit.line)

  test('}) の直後の行に test・it・describe が続く箇所を見つける。行は続く側の行', () => {
    const text = ['test("a", () => {', '  expect(1).toBe(1)', '})', 'test("b", () => {', '})', 'it("c", () => {', '})', 'describe("d", () => {', '})'].join('\n')
    assert.deepEqual(lines(findMissingTestSpacing(text)), [4, 6, 8])
  })

  test('test.each・describe.skip・test.only などの形と、インデントのある行も見つける', () => {
    const text = [
      'describe("x", () => {',
      '  test("a", () => {',
      '  })',
      '  test.each([1, 2])("b %s", (n) => {',
      '  })',
      '  it.skip("c", () => {',
      '  })',
      '  describe.only("d", () => {',
      '  })',
      '  test.fixme("e", () => {',
      '  })',
      '  test.describe("f", () => {',
      '  })',
      '})',
    ].join('\n')
    assert.deepEqual(lines(findMissingTestSpacing(text)), [4, 6, 8, 10, 12])
  })

  test('1 行で閉じるテストの直後に、空行なしで続くテストも見つける。1 行のテストが続いても同じ', () => {
    const text = [
      'test("a", () => {})',
      'test("b", () => {})',
      '',
      'it("c", async () => { await run() });',
      'test.each([1, 2])("d %s", (n) => {})',
      'test("e", () => {})',
      '',
      'const user = userEvent.setup()',
      'test("f", () => {})',
    ].join('\n')
    assert.deepEqual(lines(findMissingTestSpacing(text)), [2, 5, 6])
  })

  test('test.beforeEach・test.use・test.step・test.info などのテストでない呼び出しは見つけない', () => {
    const text = [
      'test("a", () => {',
      '})',
      '',
      'test.beforeEach(() => {',
      '})',
      'test.use({ locale: "ja" })',
      'test.afterAll(async () => {',
      '})',
      'test.step("s", async () => {',
      '})',
      'test.info().annotations.push({ type: "x" })',
      'test.describe.configure({ mode: "serial" })',
      'test.setTimeout(1000)',
    ].join('\n')
    assert.deepEqual(findMissingTestSpacing(text), [])
  })

  test('test.describe.serial・test.skip・test.todo・test.concurrent などのテスト・グループの形は見つける', () => {
    const text = [
      'test("a", () => {})',
      'test.describe.serial("b", () => {',
      '})',
      'test.skip("c", () => {',
      '})',
      'test.todo("d")',
      'test.concurrent("e", () => {})',
      'test.fail("f", () => {})',
      'test.describe.parallel("g", () => {})',
    ].join('\n')
    assert.deepEqual(lines(findMissingTestSpacing(text)), [2, 4, 6, 7, 8, 9])
  })

  test('閉じる行が }, 30_000) や }); の形でも見つける', () => {
    const text = ['test("a", async () => {', '}, 30_000)', 'test("b", () => {', '});', 'test("c", () => {', '})'].join('\n')
    assert.deepEqual(lines(findMissingTestSpacing(text)), [3, 5])
  })

  test('空行を挟むものは見つけない', () => {
    const text = ['test("a", () => {', '})', '', 'test("b", () => {', '})', '', '', 'describe("c", () => {', '})'].join('\n')
    assert.deepEqual(findMissingTestSpacing(text), [])
  })

  test('コメントの行を挟んでも、空行がなければ見つける。空行があれば見つけない', () => {
    const attached = ['test("a", () => {', '})', '// b の説明', 'test("b", () => {', '})'].join('\n')
    assert.deepEqual(lines(findMissingTestSpacing(attached)), [4])
    const blockAttached = ['test("a", () => {', '})', '/**', ' * 説明', ' */', 'test("b", () => {', '})'].join('\n')
    assert.deepEqual(lines(findMissingTestSpacing(blockAttached)), [6])
    const spaced = ['test("a", () => {', '})', '', '// b の説明', 'test("b", () => {', '})'].join('\n')
    assert.deepEqual(findMissingTestSpacing(spaced), [])
  })

  test('テストでない呼び出しの直後と、テストの前がテストの閉じでない箇所は見つけない（hook の閉じの直後と、1 行のテストの直後のテストは見つける）', () => {
    const text = [
      'beforeEach(() => {',
      '})',
      'test("a", () => {',
      '  expect(1).toBe(1)',
      '})',
      '',
      'const user = userEvent.setup()',
      'test("b", () => {})',
      'test("c", () => {})',
      'await test.step("s", async () => {',
      '})',
      'await test.step("t", async () => {',
      '})',
    ].join('\n')
    assert.deepEqual(lines(findMissingTestSpacing(text)), [3, 9])
  })

  test('文字列とコメントの中の }) と test( は見つけない', () => {
    const text = ['const a = `', '})', 'test(', '`', '// })', '// test(', "const b = '})'"].join('\n')
    assert.deepEqual(findMissingTestSpacing(text), [])
  })

  test('報告に行の番号と、元の行の文字を含む', () => {
    assert.deepEqual(findMissingTestSpacing('  })\n  test("b", () => {  '), [{ line: 2, text: 'test("b", () => {' }])
  })
})
