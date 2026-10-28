import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { codeKind, commentText, extractCommentLines } from './comment-text.mjs'

/** 取り出した文字列を、行番号つきの 1 つの文字列の配列にする */
function lines(text, kind) {
  return extractCommentLines(text, kind).map((entry) => `${entry.line}:${entry.text}`)
}

describe('ファイルの種類', () => {
  test('取り出す対象の拡張子を種類に分ける', () => {
    assert.equal(codeKind('a/B.java'), 'java')
    assert.equal(codeKind('build.gradle.kts'), 'kts')
    for (const file of ['a.ts', 'a.tsx', 'a.mjs', 'a.cjs']) assert.equal(codeKind(file), 'ts', file)
    assert.equal(codeKind('scripts/a.sh'), 'sh')
  })

  test('拡張子のない hook の script（scripts/dev/hooks の下）は sh にする', () => {
    assert.equal(codeKind('scripts/dev/hooks/pre-commit'), 'sh')
    assert.equal(codeKind('scripts/dev/hooks/commit-msg'), 'sh')
    assert.equal(codeKind('scripts/dev/other/pre-commit'), null)
  })

  test('対象でない拡張子は null にする', () => {
    for (const file of ['a.md', 'a.json', 'a.js', 'a.sql', 'a.yml']) assert.equal(codeKind(file), null, file)
  })
})

describe('Java のコメントとテスト名', () => {
  const source = [
    '/**',
    ' * 注文を確定する。',
    ' */',
    'class A {',
    '  @DisplayName("在庫が足りないと失敗する")',
    '  void test() {',
    '    String s = "画面の文言 // これはコメントでない";', // 文字列の中の // はコメントでない
    '    int x = 1; // 行の後ろのコメント',
    '    /* ブロック */ int y = 2;',
    '  }',
    '}',
  ].join('\n')

  test('Javadoc、行コメント、ブロックコメント、@DisplayName の文字列を、元の行番号で取り出す', () => {
    assert.deepEqual(lines(source, 'java'), [
      '1:/**',
      '2: * 注文を確定する。',
      '3: */',
      '5:"在庫が足りないと失敗する"',
      '8:// 行の後ろのコメント',
      '9:/* ブロック */',
    ])
  })

  test('コメントの外の文字列とコードは取り出さない', () => {
    const text = commentText(source, 'java')
    assert.ok(!text.includes('画面の文言'))
    assert.ok(!text.includes('int x'))
  })

  test('取り出した文書の行の数は、元のソースと同じである', () => {
    assert.equal(commentText(source, 'java').split('\n').length, source.split('\n').length)
    assert.equal(commentText(source, 'java').split('\n')[4], '"在庫が足りないと失敗する"')
  })

  test('@DisplayName が付いていない文字列は取り出さない', () => {
    assert.deepEqual(lines('class A { String s = "道具"; void f() { g("道具"); } }', 'java'), [])
  })

  test('クラスに付いた @DisplayName も取り出す', () => {
    assert.deepEqual(lines('@DisplayName("注文の集約")\nclass A {}', 'java'), ['1:"注文の集約"'])
  })
})

describe('Gradle の設定（.kts）のコメント', () => {
  test('行コメントとブロックコメントを取り出し、文字列は取り出さない', () => {
    const source = ['plugins {', '  id("x") // 道具の設定', '}', '/* まとめ', ' 続き */', 'val name = "文字列 // 文字列"'].join('\n')
    assert.deepEqual(lines(source, 'kts'), ['2:// 道具の設定', '4:/* まとめ', '5: 続き */'])
  })
})

describe('TypeScript・JavaScript のコメントとテスト名', () => {
  test('コメントを元の行番号で取り出す', () => {
    const source = ['// 先頭', 'const a = 1 /* 途中 */', '/**', ' * 関数の説明', ' */', 'export function f() {}'].join('\n')
    assert.deepEqual(lines(source, 'ts'), ['1:// 先頭', '2:/* 途中 */', '3:/**', '4: * 関数の説明', '5: */'])
  })

  test('describe・it・test の名前の文字列を取り出す', () => {
    const source = [
      "describe('注文の画面', () => {",
      '  it("確定できる", () => {})',
      '  test(`在庫がない`, () => {})',
      "  test.skip('飛ばす', () => {})",
      "  test.describe('入れ子', () => {})",
      "  it.each([[1], [2]])('表の名前 %i', () => {})",
      "  test.skipIf(process.env.CI)('条件つき', () => {})",
      '})',
    ].join('\n')
    assert.deepEqual(lines(source, 'ts'), [
      "1:'注文の画面'",
      '2:"確定できる"',
      '3:`在庫がない`',
      "4:'飛ばす'",
      "5:'入れ子'",
      "6:'表の名前 %i'",
      "7:'条件つき'",
    ])
  })

  test('画面の文言の文字列リテラルとコードは取り出さない', () => {
    const source = ['const label = "道具の一覧"', '<p>{t("道具")}</p>', "expect(page.getByText('道具')).toBeVisible()"].join('\n')
    assert.deepEqual(lines(source, 'ts'), [])
  })

  test('名前でない文字列（describe の外の関数、メソッド呼び出し）は取り出さない', () => {
    const source = ["foo.test('道具')", "const re = /x/.test('道具')", "mytest('道具')"].join('\n')
    assert.deepEqual(lines(source, 'ts'), [])
  })

  test('文字列の中の // はコメントでなく、コメントの中の describe は名前にしない', () => {
    const source = ["const url = 'http://example.com'", "// describe('コメントの中')"].join('\n')
    assert.deepEqual(lines(source, 'ts'), ["2:// describe('コメントの中')"])
  })

  test('名前が文字列でない呼び出しは読み飛ばす', () => {
    assert.deepEqual(lines("it(name, () => {})\ntest(`${a}の名前`, () => {})", 'ts'), ['2:`${a}の名前`'])
  })
})

describe('シェルのコメント', () => {
  test('# のコメントを取り出し、1 行目の #! は除く', () => {
    const source = ['#!/usr/bin/env bash', '# 先頭の説明', 'echo "$#個" # 行の後ろ', 'echo "# 文字列の中"', "echo '#' ${#x}"].join('\n')
    assert.deepEqual(lines(source, 'sh'), ['2:# 先頭の説明', '3:# 行の後ろ'])
  })
})

describe('取り出した文書', () => {
  test('取り出す所がなければ、空の行だけの文書にする', () => {
    assert.equal(commentText('a\nb', 'ts'), '\n')
  })

  test('同じ行のコメントとテスト名は 1 行にまとめる', () => {
    assert.equal(commentText("it('名前', () => {}) // 補足", 'ts'), "'名前' // 補足")
  })
})
