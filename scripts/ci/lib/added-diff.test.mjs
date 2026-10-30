import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { ADDED_DIFF_ARGS } from './added-diff.mjs'

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
