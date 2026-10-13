import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { checkReferences, readHeadings, referenceName, slug, splitSectionNumbers } from './doc-refs.mjs'

/** 調べる文書の組。キーがパス、値が中身 */
const DOCS = {
  'docs/frontend/apps/store.md': '# 店舗画面（`frontend/store`）の構成\n## 4. 取得\n### 4.5 カート\n## 8. 文言\n',
  'docs/testing/writing.md': '# テストの書き方\n## 1. 名前\n## 2. 対応\n### 2.1 AC\n',
  'docs/process/workflow.md': '# 開発の進め方\n## 1. SDD\n### 1.4 spec と plan の書き方\n```\n## 9. コードの中\n```\n',
}

/**
 * 文書の組に 1 つのファイルを足して、切れた参照を探す。
 *
 * @param {string} file 足すファイルのパス
 * @param {string} text 足すファイルの中身
 * @returns {string[]} 切れた参照の説明
 */
function check(file, text) {
  const files = { ...DOCS, [file]: text }
  return checkReferences(Object.keys(files), (path) => files[path], (path) => path in files)
}

describe('文書の参照の検査', () => {
  test('見出しの slug は GitHub と同じ作り方（記号を除き、空白をハイフンに）', () => {
    assert.equal(slug('1.4 spec と plan の書き方'), '14-spec-と-plan-の書き方')
    assert.equal(slug('`frontend/store` の構成'), 'frontendstore-の構成')
  })

  test('節の番号は見出しの先頭から取り、コードブロックの中は見ない', () => {
    const { title, sections } = readHeadings(DOCS['docs/process/workflow.md'])
    assert.equal(title, '開発の進め方')
    assert.deepEqual([...sections], ['1', '1.4'])
  })

  test('文書の名前は括弧の中を除く', () => {
    assert.equal(referenceName('店舗画面（`frontend/store`）の構成'), '店舗画面の構成')
  })

  test('範囲と並びは、両端と各番号を確かめる', () => {
    assert.deepEqual(splitSectionNumbers('4〜6・8'), ['4', '6', '8'])
    assert.deepEqual(splitSectionNumbers('4、13'), ['4', '13'])
  })

  test('文書の名前で書いた節を確かめる（コードのコメントも）', () => {
    const problems = check('backend/Foo.java', '// （店舗画面の構成 第 4.5 節）\n// （店舗画面の構成 第 4.2 節）')
    assert.equal(problems.length, 1)
    assert.match(problems[0], /第 4\.2 節/)
  })

  test('リンク、パス、ファイル名で書いた節を確かめる', () => {
    const text = [
      '[開発の進め方](../process/workflow.md) 第 1.4 節',
      '[開発の進め方](../process/workflow.md) 第 3 節',
      '`docs/testing/writing.md` 第 2.1 節',
      'store.md 第 9 節',
    ].join('\n')
    const problems = check('docs/dev/setup.md', text)
    assert.deepEqual(
      problems.map((problem) => problem.match(/:(\d+) /)[1]),
      ['2', '4'],
    )
  })

  test('名前のない節は、Markdown ではその文書自身を指し、コードでは見ない', () => {
    assert.equal(check('docs/dev/setup.md', '# 開発環境\n## 1. 起動\n第 1 節と第 2 節').length, 1)
    assert.deepEqual(check('backend/Foo.java', '// 第 99 節'), [])
  })

  test('spec・plan の節は見ない（当時の記録で番号が変わらない）', () => {
    assert.deepEqual(check('backend/Foo.java', '// plan 001 第 7 節、spec 003 の第 9 節'), [])
  })

  test('リンクの先のファイルと見出しを確かめる', () => {
    const text = [
      '[a](../process/workflow.md#14-spec-と-plan-の書き方)',
      '[b](../process/workflow.md#2-tdd)',
      '[c](../process/none.md)',
      '[d](https://example.com/none.md)',
    ].join('\n')
    const problems = check('docs/dev/setup.md', text)
    assert.equal(problems.length, 2)
    assert.match(problems[0], /^見出しがない/)
    assert.match(problems[1], /^ファイルがない/)
  })
})
