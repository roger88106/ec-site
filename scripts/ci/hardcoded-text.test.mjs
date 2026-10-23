import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findHardcodedText, scanSources, stripComments } from './hardcoded-text.mjs'

describe('表示の文言を直接書いた所の検査', () => {
  test('JSX の文字、属性、コードの文字列の日本語を見つける', () => {
    const text = [
      '<Button>保存</Button>',
      '<Input aria-label="キーワード" />',
      "toast.success('保存しました')",
      '<Button>{t("save")}</Button>',
    ].join('\n')
    assert.deepEqual(
      findHardcodedText(text).map((hit) => hit.line),
      [1, 2, 3],
    )
  })

  test('コメントの日本語は見ない。文字列の中の // はコメントにしない', () => {
    const text = [
      '/** 保存のボタン */',
      '// 保存する',
      '{/* 確認してから送る */}',
      "const url = 'https://example.com' // 外のサイト",
      "const label = 'https://example.com/保存'",
    ].join('\n')
    assert.deepEqual(
      findHardcodedText(text).map((hit) => hit.line),
      [5],
    )
  })

  test('ログとプログラムの誤りの例外の文は見ない', () => {
    const text = [
      "await log.warn('カートを移せませんでした')",
      "throw new Error('環境変数がありません')",
      "throw new ImageUploadError('画像を送れませんでした')",
    ].join('\n')
    assert.deepEqual(
      findHardcodedText(text).map((hit) => hit.line),
      [3],
    )
  })

  test('正規表現のリテラルの中の引用符は文字列の始まりにしない', () => {
    const text = [
      "const QUOTES = /[\"'「]/g // 引用符と鍵括弧",
      'const SLASHES = /\\/\\/[a-z/]+/ // 二重の斜線',
      "if (/^'/.test(value)) return // 引用符で始まる",
      "const label = '保存'",
      '{t.rich("at", { date: () => <DateTime value={at} /> }) /* 日時 */}',
    ].join('\n')
    assert.deepEqual(
      findHardcodedText(text).map((hit) => hit.line),
      [4],
    )
  })

  test('割り算は正規表現にしない', () => {
    const text = [
      'const half = total / 2 // 半分にする',
      "const ratio = a / b; const label = '保存'",
      "const per = (sum) / count / 2; const unit = '個'",
    ].join('\n')
    assert.deepEqual(
      findHardcodedText(text).map((hit) => hit.line),
      [2, 3],
    )
  })

  test('コメントを消しても、行の数は変わらない', () => {
    const text = '/*\n * 説明\n */\nconst a = 1'
    assert.equal(stripComments(text).split('\n').length, 4)
  })

  test('ファイルごとに、文言を直接書いた行を集める（見つからないファイルは含めない）', () => {
    const files = { 'a.tsx': 'const a = "日本語"', 'b.tsx': 'const b = 1' }
    const hits = scanSources(Object.keys(files), (file) => files[file])
    assert.deepEqual([...hits.keys()], ['a.tsx'])
    assert.equal(hits.get('a.tsx')[0].line, 1)
  })
})
