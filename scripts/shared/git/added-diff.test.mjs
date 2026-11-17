import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { ADDED_DIFF_ARGS, parseAddedLines } from './added-diff.mjs'

describe('追加行を読む差分の引数', () => {
  /** diff のサブコマンドより後ろ（diff の引数） */
  const diffArgs = ADDED_DIFF_ARGS.slice(ADDED_DIFF_ARGS.indexOf('diff') + 1)

  test('git の diff を実行し、パスを引用符で囲まない設定を diff の前に置く', () => {
    assert.ok(ADDED_DIFF_ARGS.includes('diff'))
    const before = ADDED_DIFF_ARGS.slice(0, ADDED_DIFF_ARGS.indexOf('diff'))
    assert.deepEqual(before, ['-c', 'core.quotePath=false'])
  })

  test('名前の変更を見分け（-M）、改行コードだけの違いを追加行にしない', () => {
    assert.ok(diffArgs.includes('-M'))
    assert.ok(diffArgs.includes('--ignore-cr-at-eol'))
  })

  test('前後の行を含めず（-U0）、接頭辞を利用者の設定によらず a/ と b/ に固定する', () => {
    assert.ok(diffArgs.includes('-U0'))
    assert.ok(diffArgs.includes('--src-prefix=a/'))
    assert.ok(diffArgs.includes('--dst-prefix=b/'))
  })

  test('追加・変更・名前の変更のファイルだけを見る（削除したファイルは見ない）', () => {
    assert.ok(diffArgs.includes('--diff-filter=AMR'))
  })

  test('利用者の色・外部の diff・変換の設定を使わない', () => {
    for (const option of ['--no-color', '--no-ext-diff', '--no-textconv']) assert.ok(diffArgs.includes(option), option)
  })
})

describe('差分の追加行の読み方', () => {
  /** 差分を読み、[パス, 追加行の番号] の並びにする */
  const read = (lines) => [...parseAddedLines(lines.join('\n'))].map(([file, added]) => [file, [...added]])

  test('差分の本文の「+++」で始まる追加行を、ファイルの見出しと取り違えない', () => {
    const diff = ['+++ b/docs/a.md', '@@ -1,0 +2,2 @@', '+++ b/docs/x.md', '+次の行']
    assert.deepEqual(read(diff), [['docs/a.md', [2, 3]]])
  })

  test('行数を省いた hunk の見出し（@@ -5 +7 @@）は、1 行と読む', () => {
    const diff = ['+++ b/docs/a.md', '@@ -5 +7 @@', '-古い行', '+新しい行', '@@ -9 +9,0 @@', '-消した行']
    assert.deepEqual(read(diff), [['docs/a.md', [7]]])
  })

  test('引用符で囲んだパスの、逃がした字（\\"、\\t、\\\\、8 進数のバイト）を戻す', () => {
    const diff = ['+++ "b/docs/q\\"\\346\\227\\245.md"', '@@ -0,0 +1 @@', '+あ', '+++ "b/docs/t\\tb\\\\s.md"', '@@ -0,0 +1 @@', '+い']
    assert.deepEqual(read(diff), [
      ['docs/q"日.md', [1]],
      ['docs/t\tb\\s.md', [1]],
    ])
  })

  test('空白のあるパスの後ろのタブを除く', () => {
    const diff = ['+++ b/docs/a b.md\t', '@@ -3,2 +3,0 @@', '-消した行', '-消した行']
    assert.deepEqual(read(diff), [['docs/a b.md', []]])
  })

  test('「\\ No newline at end of file」の行は、hunk の行に数えない', () => {
    const diff = ['+++ b/docs/a.md', '@@ -0,0 +1 @@', '+あ', '\\ No newline at end of file', '+++ b/docs/b.md', '@@ -0,0 +1 @@', '+い']
    assert.deepEqual(read(diff), [
      ['docs/a.md', [1]],
      ['docs/b.md', [1]],
    ])
  })
})
