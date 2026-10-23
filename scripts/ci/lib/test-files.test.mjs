// lib/test-files.mjs のテスト（node --test scripts/ci/lib/test-files.test.mjs）
import assert from 'node:assert/strict'
import path from 'node:path'
import { describe, test } from 'node:test'
import { extractTestNames, findTestFiles, readTsTests, testFileKind } from './test-files.mjs'

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

  test('extractTestNames の java は、メソッドの @DisplayName を読み、クラスの名前を読まない', () => {
    const source = ['@DisplayName("まとまり")', 'class A {', '  @Test', '  @DisplayName("AC-1 通る")', '  void a() {}', '}'].join('\n')
    assert.deepEqual(extractTestNames(source, 'java'), ['AC-1 通る'])
  })
})
