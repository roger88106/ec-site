// lib/test-files.mjs のテスト（node --test scripts/ci/lib/test-files.test.mjs）
import assert from 'node:assert/strict'
import path from 'node:path'
import { describe, test } from 'node:test'
import { extractTestNames, findTestFiles, findUnreadableTsTests, readTsTests, testFileKind } from './test-files.mjs'

describe('テストのファイルの種類', () => {
  test('Java の src/test・src/integrationTest と .test.ts(x)・.spec.ts を選び、他は null にする', () => {
    const cases = [
      ['backend/ecsite/src/test/java/a/ATest.java', 'java'],
      ['backend/ecsite/src/integrationTest/java/a/BIT.java', 'java'],
      ['backend/ecsite/src/main/java/a/Main.java', null],
      ['backend/ecsite/src/test/resources/x.sql', null],
      ['frontend/store/src/a.test.ts', 'ts'],
      ['frontend/store/src/b.test.tsx', 'ts'],
      ['frontend/store/e2e/c.spec.ts', 'ts'],
      ['frontend/store/src/d.spec.tsx', null],
      ['frontend/store/src/e.ts', null],
    ]
    for (const [file, kind] of cases) assert.equal(testFileKind(file), kind, file)
  })

  test('findTestFiles は、選んだファイルを絶対パスと種類にして、順を保つ', () => {
    const files = ['backend/ecsite/src/test/java/a/ATest.java', 'frontend/store/src/e.ts', 'frontend/store/src/a.test.ts']
    assert.deepEqual(
      findTestFiles('/r', files).map((entry) => [path.relative('/r', entry.file).split(path.sep).join('/'), entry.kind]),
      [
        ['backend/ecsite/src/test/java/a/ATest.java', 'java'],
        ['frontend/store/src/a.test.ts', 'ts'],
      ],
    )
  })
})

describe('テストの関数の読み方', () => {
  test('readTsTests は、skip・fixme のテストとまとまりの中のテストに skipped を付けて、全て返す', () => {
    const source = [
      "it('通常', () => {})",
      "test.skip('飛ばす', () => {})",
      "it.fixme('保留', () => {})",
      "describe.skip('まとまり', () => {",
      "  it('中', () => {})",
      '})',
      "/re/.test('x')",
      "test.describe('グループ', () => { test.only('中の通常', () => {}) })",
    ].join('\n')
    assert.deepEqual(
      readTsTests(source).map(({ name, skipped }) => [name, skipped]),
      [
        ['通常', false],
        ['飛ばす', true],
        ['保留', true],
        ['中', true],
        ['中の通常', false],
      ],
    )
  })

  test('extractTestNames の ts は、skipped のテストを除いた名前を返す', () => {
    const source = "it('a', () => {})\ntest.skip('b', () => {})\ntest.each([1])('c %s', () => {})"
    assert.deepEqual(extractTestNames(source, 'ts'), ['a', 'c %s'])
  })

  test('each の表の中に関数の呼び出し・入れ子の括弧・文字列と正規表現の中の ) があっても、テスト名を読む', () => {
    const source = [
      "test.each([[f(1), 'a']])('AC-1 関数 %s', () => {})",
      "it.each([[[1, 2], (x) => g(h(x))]])('AC-2 入れ子 %s', () => {})",
      "test.each([['a)b', 1]])('AC-3 文字列 %s', () => {})",
      "test.each([[/\\)/.source]])('AC-4 正規表現 %s', () => {})",
      'it.each`',
      '  a | b',
      '  ${f(1)} | ${2}',
      "`('AC-5 テンプレートの表 $a', () => {})",
      "test.skip.each([[f(1)]])('AC-6 飛ばす', () => {})",
    ].join('\n')
    assert.deepEqual(
      readTsTests(source).map(({ name, skipped }) => [name, skipped]),
      [
        ['AC-1 関数 %s', false],
        ['AC-2 入れ子 %s', false],
        ['AC-3 文字列 %s', false],
        ['AC-4 正規表現 %s', false],
        ['AC-5 テンプレートの表 $a', false],
        ['AC-6 飛ばす', true],
      ],
    )
    assert.deepEqual(findUnreadableTsTests(source), [])
  })

  test('findUnreadableTsTests は、名前が文字列でない呼び出しと、括弧が閉じない呼び出しを、位置と理由で返す', () => {
    const source = ['test(name, () => {})', 'it.each([[1]])(title, () => {})', 'test.each([[1, 2]', "test('閉じない名前"].join('\n')
    const problems = findUnreadableTsTests(source)
    assert.deepEqual(
      problems.map(({ index }) => index),
      [0, source.indexOf('it.each'), source.indexOf('test.each'), source.indexOf("test('閉")],
    )
    assert.match(problems[0].reason, /テスト名/)
    assert.match(problems[1].reason, /テスト名/)
    assert.match(problems[2].reason, /括弧/)
    assert.match(problems[3].reason, /テスト名/)
  })

  test('readTsTests は、test.for と skipIf・runIf・sequential・fails・todo の付いた呼び出しを読む', () => {
    const source = [
      "test.for([[1, 2]])('AC-1 表', () => {})",
      "test.skipIf(isCi)('AC-2 条件で飛ばす', () => {})",
      "test.runIf(isCi)('AC-3 条件で実行', () => {})",
      "test.sequential('AC-4 順に', () => {})",
      "test.fails('AC-5 失敗する', () => {})",
      "it.todo('AC-6 後で')",
    ].join('\n')
    assert.deepEqual(
      readTsTests(source).map(({ name, skipped }) => [name, skipped]),
      [
        ['AC-1 表', false],
        ['AC-2 条件で飛ばす', false],
        ['AC-3 条件で実行', false],
        ['AC-4 順に', false],
        ['AC-5 失敗する', false],
        ['AC-6 後で', true],
      ],
    )
    assert.deepEqual(findUnreadableTsTests(source), [])
  })

  test('findUnreadableTsTests は、知らない修飾の付いた test・it の呼び出しを失敗にする', () => {
    const source = "test.shuffle('AC-1 知らない', () => {})\nit.unknown.only('AC-2 知らない', () => {})"
    const problems = findUnreadableTsTests(source)
    assert.deepEqual(
      problems.map(({ index }) => index),
      [0, source.indexOf('it.unknown')],
    )
    assert.match(problems[0].reason, /修飾/)
  })

  test('findUnreadableTsTests は、テストの宣言でない呼び出しを失敗にしない', () => {
    const source = [
      "test.describe('まとまり', () => {})",
      'test.use({ locale: "ja" })',
      'test.step(name, async () => {})',
      "test.skip(browserName === 'webkit', '理由')",
      'test.fixme()',
      'test.setTimeout(10)',
      'expect(/re/.test(name)).toBe(true)',
      'pattern.test(name)',
      '// test(name) はコメント',
      "const text = 'test(name)'",
    ].join('\n')
    assert.deepEqual(findUnreadableTsTests(source), [])
  })

  test('extractTestNames の java は、メソッドの @DisplayName を読み、クラスの名前を読まない', () => {
    const source = ['@DisplayName("まとまり")', 'class A {', '  @Test', '  @DisplayName("AC-1 通る")', '  void a() {}', '}'].join('\n')
    assert.deepEqual(extractTestNames(source, 'java'), ['AC-1 通る'])
  })
})
