import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { keepJavaComments } from './lib/strip-comments.mjs'
import { findSpecNumbers, isBackendTestFile, isProductionFile } from './spec-numbers.mjs'

describe('本番のコードの spec の番号の検査', () => {
  test('AC・DC などの番号、タスクの番号、spec・plan の番号を見つける', () => {
    const text = [
      '// 配信する画像は WebP にする（AC-68）',
      '/** 共有の基準（DC-49、T6305） */',
      '// plan 001 第 7 節、spec 003',
      '// 配信する画像は、すべて WebP にする',
    ].join('\n')
    assert.deepEqual(
      findSpecNumbers(text).map((hit) => `${hit.line} ${hit.number}`),
      ['1 AC-68', '2 DC-49', '2 T6305', '3 plan 001', '3 spec 003'],
    )
  })

  test('新しい形式と古い形式のタスクの番号を見つける', () => {
    const text = ['// 割引の上限（T011-01-01）', '// 例（T1000-01-01、T11-02-03）', '// 古い形式（T3101）'].join('\n')
    assert.deepEqual(
      findSpecNumbers(text).map((hit) => `${hit.line} ${hit.number}`),
      ['1 T011-01-01', '2 T1000-01-01', '2 T11-02-03', '3 T3101'],
    )
  })

  test('1→10 の spec の番号（AC-7-1 など）、タスクの番号（T7-01-01）、spec 7・plan 7 を見つける', () => {
    const text = [
      '// 共有の基準（AC-7-1、DC-7-14、US-12-3）',
      '// 規則（BR-7-2、NFR-7-1、DS-7-1、DN-7-4）',
      '// 段階（T7-01-01、T12-02-03）',
      '// spec 7 と plan 12 第 2 節',
    ].join('\n')
    assert.deepEqual(
      findSpecNumbers(text).map((hit) => `${hit.line} ${hit.number}`),
      ['1 AC-7-1', '1 DC-7-14', '1 US-12-3', '2 BR-7-2', '2 NFR-7-1', '2 DS-7-1', '2 DN-7-4', '3 T7-01-01', '3 T12-02-03', '4 spec 7', '4 plan 12'],
    )
  })

  test('バージョンの番号（数字の後に . が続くもの）は spec の番号として見つけない', () => {
    assert.deepEqual(findSpecNumbers('OpenAPI spec 3.1 と spec 3.x に従う'), [])
    assert.deepEqual(
      findSpecNumbers('spec 12 の話。plan 001 と spec 7').map((hit) => hit.number),
      ['spec 12', 'plan 001', 'spec 7'],
    )
  })

  test('番号に似た識別子は見つけない', () => {
    assert.deepEqual(findSpecNumbers('const ACTION = "X-12"; const T10 = 1; const UTF8 = true'), [])
  })

  test('画面のテスト、生成物、マイグレーションは見ない', () => {
    assert.equal(isProductionFile('backend/ecsite/src/main/java/com/ecsite/Foo.java'), true)
    assert.equal(isProductionFile('frontend/store/src/lib/price.ts'), true)
    assert.equal(isProductionFile('frontend/store/src/lib/price.test.ts'), false)
    assert.equal(isProductionFile('frontend/admin/src/test/server.ts'), false)
    assert.equal(isProductionFile('frontend/admin/src/api/generated/schema.ts'), false)
    assert.equal(isProductionFile('backend/ecsite/src/main/resources/db/migration/V1__init.sql'), false)
  })

  test('バックエンドのテストと、テストの補助の .java は、コメントだけを見る対象にする', () => {
    assert.equal(isBackendTestFile('backend/ecsite/src/test/java/com/ecsite/FooTest.java'), true)
    assert.equal(isBackendTestFile('backend/ecsite/src/integrationTest/java/com/ecsite/FooIT.java'), true)
    assert.equal(isBackendTestFile('backend/ecsite/src/testFixtures/java/com/ecsite/FooFixture.java'), true)
    assert.equal(isBackendTestFile('backend/ecsite/src/main/java/com/ecsite/Foo.java'), false)
    assert.equal(isBackendTestFile('backend/ecsite/src/test/resources/application.yml'), false)
    assert.equal(isProductionFile('backend/ecsite/src/test/java/com/ecsite/FooTest.java'), false)
    assert.equal(isProductionFile('frontend/store/src/lib/price.test.ts'), false)
    assert.equal(isBackendTestFile('frontend/store/src/lib/price.test.ts'), false)
  })

  test('テストの Javadoc と行のコメントの番号を見つける', () => {
    const text = [
      '/** 注文の合計を確かめる（AC-12） */',
      'class OrderTest {',
      '  // 在庫が足りない場合（T186-04-05）',
      '  void test() {}',
      '}',
    ].join('\n')
    assert.deepEqual(
      findSpecNumbers(keepJavaComments(text)).map((hit) => `${hit.line} ${hit.number}`),
      ['1 AC-12', '3 T186-04-05'],
    )
  })

  test('@DisplayName の文字列の番号は見つけない', () => {
    const text = ['class OrderTest {', '  @DisplayName("AC-12 注文の合計を返す")', '  void test() {}', '}'].join('\n')
    assert.deepEqual(findSpecNumbers(keepJavaComments(text)), [])
  })

  test('テストの補助（testFixtures）のコメントの番号を見つける', () => {
    const text = ['public final class OrderFixture {', '  // spec 12 のデータ', '  static int id = 1;', '}'].join('\n')
    assert.deepEqual(
      findSpecNumbers(keepJavaComments(text)).map((hit) => `${hit.line} ${hit.number}`),
      ['2 spec 12'],
    )
  })
})
