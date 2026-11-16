import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findMissingTestSpacing } from './test-style.mjs'

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
