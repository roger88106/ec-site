import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { readHeadingIndex, referenceName, slug } from './doc-names.mjs'

describe('文書の名前と見出しの読み取り', () => {
  test('見出しの slug は GitHub と同じ作り方（記号を除き、空白をハイフンに）', () => {
    assert.equal(slug('1.4 spec と plan の書き方'), '14-spec-と-plan-の書き方')
    assert.equal(slug('`frontend/store` の構成'), 'frontendstore-の構成')
  })

  test('節の番号は見出しの先頭から取り、コードブロックの中は見ない', () => {
    const { title, sections } = readHeadingIndex('# 開発の進め方\n## 1. SDD\n### 1.4 spec と plan の書き方\n```\n## 9. コードの中\n```\n')
    assert.equal(title, '開発の進め方')
    assert.deepEqual([...sections.keys()], ['1', '1.4'])
  })

  test('Setext の見出しと、閉じの # のある見出しも読み、HTML のコメントの中は読まない', () => {
    const { title, anchors } = readHeadingIndex(['題', '===', '', '節', '---', '', '## 閉じ ##', '', '<!--', '## 隠れた節', '-->'].join('\n'))
    assert.equal(title, '題')
    assert.deepEqual([...anchors], ['題', '節', '閉じ'])
  })

  test('見出しは、閉じの # を除き、Setext と字下げした見出しも読む（spec-structure・rule-sections と同じ読み方）', () => {
    const { title, anchors } = readHeadingIndex('題\n===\n\n## 依存 ##\n\n   ## 字下げ\n\n段落\n---\n')
    assert.equal(title, '題')
    assert.deepEqual([...anchors], ['題', '依存', '字下げ', '段落'])
  })

  test('同じ slug の見出しは、2 回目から -1、-2 を付ける（slug が Object の持つ名前（constructor など）でも同じ）', () => {
    const { anchors } = readHeadingIndex('# 題\n## a\n## a\n## a\n## constructor\n## constructor\n## valueOf\n')
    assert.deepEqual([...anchors], ['題', 'a', 'a-1', 'a-2', 'constructor', 'constructor-1', 'valueof'])
  })

  test('文書の名前は括弧の中を除く', () => {
    assert.equal(referenceName('店舗画面（`frontend/store`）の構成'), '店舗画面の構成')
  })
})
