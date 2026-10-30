import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { checkReferences, isExcluded, readHeadings, referenceName, slug, splitSectionNumbers } from './doc-refs.mjs'

/** 調べる文書の組。キーがパス、値が中身 */
const DOCS = {
  'docs/frontend/apps/store.md': '# 店舗画面（`frontend/store`）の構成\n## 4. 取得\n### 4.5 カート\n## 8. 文言\n',
  'docs/testing/writing.md': '# テストの書き方\n## 1. 名前\n## 2. 対応\n### 2.1 AC\n',
  'docs/process/workflow.md': '# 開発の進め方\n## 1. SDD\n### 1.4 spec と plan の書き方\n```\n## 9. コードの中\n```\n',
  'docs/process/tasks.md': '# 段階の一覧\n## 段階 1\n### タスク\n## 段階 2\n### タスク\n#### 確認\n',
  'docs/process/marks.md':
    '# 記号の文書\n## 1. `pnpm` の置き場所\n## 2. [OpenAPI](workflow.md) の規則\n## 3. 全角（Ａ１）の  見出し\n',
}

/**
 * 文書の組に 1 つのファイルを追加して、切れた参照を探す。
 *
 * @param {string} file 追加するファイルのパス
 * @param {string} text 追加するファイルの中身
 * @returns {string[]} 切れた参照の説明
 */
function check(file, text) {
  const files = { ...DOCS, [file]: text }
  return checkReferences(Object.keys(files), (path) => files[path], (path) => path in files)
}

describe('文書の参照の検査', () => {
  test('進行中の spec は見て、アーカイブした spec は見ない', () => {
    assert.equal(isExcluded('docs/08-spec/019-mvp-completion/plan.md'), false)
    assert.equal(isExcluded('docs/08-spec/mvp.md'), false)
    assert.equal(isExcluded('docs/08-spec/archive/mvp/002-cart-and-order/spec.md'), true)
    assert.equal(isExcluded('docs/08-spec/archive/mvp/mvp.md'), false)
    assert.equal(isExcluded('docs/08-spec/archive/1to10/mvp.md'), true)
    assert.equal(isExcluded('backend/ecsite/src/main/resources/db/migration/V1__a.sql'), true)
  })

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

  test('範囲と並びは、両端と各番号を確認する', () => {
    assert.deepEqual(splitSectionNumbers('4〜6・8'), ['4', '6', '8'])
    assert.deepEqual(splitSectionNumbers('4、13'), ['4', '13'])
  })

  test('文書の名前で書いた節を確認する（コードのコメントも）', () => {
    const problems = check('backend/Foo.java', '// （店舗画面の構成 第 4.5 節）\n// （店舗画面の構成 第 4.2 節）')
    assert.equal(problems.length, 1)
    assert.match(problems[0], /第 4\.2 節/)
  })

  test('リンク、パス、ファイル名で書いた節を確認する', () => {
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

  test('リンクの先のファイルと見出しを確認する', () => {
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

/**
 * 切れた参照の説明から、行の番号を取り出す。
 *
 * @param {string[]} problems 切れた参照の説明
 * @returns {string[]} 行の番号
 */
function lines(problems) {
  return problems.map((problem) => problem.match(/:(\d+) /)[1])
}

describe('見出しの参照の検査', () => {
  test('リンクの直後の「」を見出しとして確認する（番号を除いて比べる）', () => {
    const text = [
      '[開発の進め方](../process/workflow.md)の「spec と plan の書き方」',
      '[開発の進め方](../process/workflow.md)の「TDD」',
      '[店舗画面](../frontend/apps/store.md)の「カート」',
      '[店舗画面](../frontend/apps/store.md)の「4.5 カート」',
    ].join('\n')
    const problems = check('docs/dev/setup.md', text)
    assert.deepEqual(lines(problems), ['2', '4'])
    assert.match(problems[0], /^見出しがない（docs\/process\/workflow\.md の「TDD」）/)
  })

  test('文書の名前の直後の「」を見出しとして確認する（コードのコメントも）', () => {
    const text = ['開発の進め方の「SDD」', '開発の進め方の「ない」', '店舗画面の構成の「文言」'].join('\n')
    assert.deepEqual(lines(check('docs/dev/setup.md', text)), ['2'])
    const code = ['// 開発の進め方の「SDD」', '// テストの書き方の「ない」'].join('\n')
    assert.deepEqual(lines(check('backend/Foo.java', code)), ['2'])
  })

  test('コードブロックの中の見出しは、先の見出しにしない', () => {
    assert.equal(check('docs/dev/setup.md', '開発の進め方の「コードの中」').length, 1)
  })

  test('同じ見出しが 2 つ以上あり、親の見出しがないときは失敗にする', () => {
    const problems = check('docs/dev/setup.md', '[段階の一覧](../process/tasks.md)の「タスク」')
    assert.equal(problems.length, 1)
    assert.match(problems[0], /^見出しが 1 つに決まらない（docs\/process\/tasks\.md の「タスク」）/)
  })

  test('親の見出しを付ければ、その下（子孫）の見出しを確認する', () => {
    const text = [
      '[段階の一覧](../process/tasks.md)の「段階 1」の「タスク」',
      '[段階の一覧](../process/tasks.md)の「段階 2」の「確認」',
      '[段階の一覧](../process/tasks.md)の「段階 1」の「確認」',
      '段階の一覧の「段階 2」の「タスク」の「確認」',
      '段階の一覧の「タスク」の「段階 2」',
    ].join('\n')
    assert.deepEqual(lines(check('docs/dev/setup.md', text)), ['3', '5'])
  })

  test('文書を指さない「」は見ない', () => {
    const text = [
      '「書き言葉」と書く。',
      '語の「書き言葉」は、',
      '「段階 1」の「タスク」',
      '<文書の名前>の「<見出し>」',
      '[外](https://example.com/a.md)の「ない」',
      '```',
      '開発の進め方の「ない」',
      '```',
    ].join('\n')
    assert.deepEqual(check('docs/dev/setup.md', text), [])
  })

  test('インラインコードの中のリンクも、リンクと同じく見る', () => {
    assert.equal(check('docs/dev/setup.md', '`[開発の進め方](../process/workflow.md)の「ない」`').length, 1)
  })

  test('見出しは、前後と連続の空白、全角と半角、コードとリンクの記号の違いを除いて比べる', () => {
    const text = [
      '[記号](../process/marks.md)の「pnpm の置き場所」',
      '[記号](../process/marks.md)の「`pnpm` の置き場所」',
      '[記号](../process/marks.md)の「 OpenAPI の規則 」',
      '[記号](../process/marks.md)の「全角(A1)の 見出し」',
      '[記号](../process/marks.md)の「pnpmの置き場所」',
    ].join('\n')
    assert.deepEqual(lines(check('docs/dev/setup.md', text)), ['5'])
  })

  test('見出しの末尾の全角の括弧の補足は、除いても全て書いても通る', () => {
    const docs = {
      'docs/process/plan.md':
        '# 計画\n## 5. 文体の検査（DS-283-5：DC-283-21〜25）\n### 5.1 段落（下書き）\n## 6. 用語（DS-1）\n## 7. 用語（DS-2）\n## 8. 中の（括弧）の後\n',
      'docs/dev/setup.md': [
        '[計画](../process/plan.md)の「文体の検査」',
        '[計画](../process/plan.md)の「文体の検査（DS-283-5：DC-283-21〜25）」',
        '[計画](../process/plan.md)の「文体の検査」の「段落」',
        '[計画](../process/plan.md)の「用語」',
        '[計画](../process/plan.md)の「用語（DS-1）」',
        '[計画](../process/plan.md)の「文体の検査（DS-283-5）」',
        '[計画](../process/plan.md)の「中のの後」',
        '[計画](../process/plan.md)の「中の」',
      ].join('\n'),
    }
    const problems = checkReferences(Object.keys(docs), (path) => docs[path], (path) => path in docs)
    assert.deepEqual(lines(problems), ['4', '6', '7', '8'])
    assert.match(problems[0], /^見出しが 1 つに決まらない/)
  })

  test('アンカー付きのリンクの後の「」も、リンクの先の文書で確認する', () => {
    const text = '[開発の進め方](../process/workflow.md#1-sdd)の「ない」'
    assert.equal(check('docs/dev/setup.md', text).length, 1)
  })
})
