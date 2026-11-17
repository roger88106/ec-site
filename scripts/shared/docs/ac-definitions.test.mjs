import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { parseSpecAcDefinitions } from './ac-definitions.mjs'

describe('spec の AC の取り出し', () => {
  test('AC の定義の行（- **AC-n**）から番号を取り出す', () => {
    const spec = [
      '- **AC-1** 税区分を名称で登録できる。',
      '  - AC-1 の補足（定義ではない）',
      '- **AC-2** 税率は、率を持つ。AC-9 を参照する。',
      '- **DC-3** 整理の spec の番号は数えない。',
    ].join('\n')
    assert.deepEqual(parseSpecAcDefinitions(spec, '5-x').definitions.map((definition) => definition.ac), [1, 2])
  })

  test('定義の文を今の内容にし、下の箇条は「：」の後に読点でつなぐ。縦線は全角にする', () => {
    const spec = [
      '- **AC-5-1** 一覧を表示する。',
      '- **AC-5-2** 次を満たす。',
      '  - 名前は 10 文字まで。',
      '  - 値は a|b のどちらか',
      '- **AC-5-3** 三つ目。',
    ].join('\n')
    assert.deepEqual(parseSpecAcDefinitions(spec, '5-x'), {
      definitions: [
        { ac: '5-1', specRel: '5-x', content: '一覧を表示する。' },
        { ac: '5-2', specRel: '5-x', content: '次を満たす：名前は 10 文字まで、値は a｜b のどちらか。' },
        { ac: '5-3', specRel: '5-x', content: '三つ目。' },
      ],
      problems: [],
    })
  })

  test('コードブロック・HTML のコメント・1 行のコードにある定義は読まない', () => {
    const spec = [
      '```markdown',
      '- **AC-5-9** コードブロックの中',
      '```',
      '<!--',
      '- **AC-5-8** コメントの中',
      '-->',
      '`- **AC-5-7** 1 行のコード`',
      '- **AC-5-1** 本物。',
    ].join('\n')
    assert.deepEqual(parseSpecAcDefinitions(spec, '5-x').definitions.map((definition) => definition.ac), ['5-1'])
  })

  test('定義の行が崩れている（後ろが空、太字が閉じていない）と、問題にする', () => {
    const { problems } = parseSpecAcDefinitions(['- **AC-5-1**', '- **AC-5-2 閉じていない。', '- **AC-5-3**   '].join('\n'), '5-x')
    assert.equal(problems.length, 3)
    assert.match(problems[0], /^1 行：/)
  })

  test('「- **AC-」以外の形の定義（* + 字下げ 番号付き）を、崩れた定義の行として報告する', () => {
    const lines = ['* **AC-5-1** 星の箇条。', '+ **AC-5-2** 足す記号の箇条。', '  - **AC-5-3** 字下げした箇条。', '1. **AC-5-4** 番号付きの箇条。', '2) **AC-5-5** 括弧の番号。', '-  **AC-5-6** 空白が 2 つ。']
    const { definitions, problems } = parseSpecAcDefinitions(lines.join('\n'), '5-x')
    assert.deepEqual(definitions, [])
    assert.equal(problems.length, lines.length)
    assert.match(problems[0], /^1 行：AC の定義の行が崩れている/)
    assert.match(problems[5], /^6 行：/)
  })

  test('「- **AC-」以外の形でも、コードブロックの中と、AC でない太字の箇条は問題にしない', () => {
    const spec = ['```markdown', '* **AC-5-9** コードブロックの中', '```', '* **注意** AC でない太字', '  - AC-5-1 の補足', '- **AC-5-1** 本物。'].join('\n')
    const { definitions, problems } = parseSpecAcDefinitions(spec, '5-x')
    assert.deepEqual(problems, [])
    assert.deepEqual(definitions.map((definition) => definition.ac), ['5-1'])
  })

  test('定義の形でない書き方（太字のない箇条・箇条のない太字・チェックボックス・見出し・表の行）を、崩れた定義の行として報告する', () => {
    const lines = [
      '- AC-400 文。',
      '* AC-5-1 文。',
      '1. AC-5-2 文。',
      '  - AC-5-3 文。',
      '**AC-400** 文。',
      '**AC-5-4**',
      '- [ ] **AC-400** 文。',
      '- [x] **AC-5-5** 文。',
      '- [ ] AC-5-6 文。',
      '### AC-400 文。',
      '## **AC-5-7** 文。',
      '### AC-5-8',
      '| AC-400 | 文。 |',
      '| **AC-5-9** | 文。 |',
      '- AC-5-10 「受付」以外は文。',
      '**AC-5-11** 「受付」は文。',
      '### AC-5-12 「受付」は文。',
      '- AC-5-13 `POST /orders` は 201 を返す。',
      '**AC-5-14** `x` を返す。',
      '- AC-5-15 [注文](x.md) は文。',
      '- AC-5-16 **注文** は文。',
      '1. [ ] **AC-5-17** 文。',
      '2) [x] AC-5-18 文。',
      '### 1. AC-5-19 文。',
    ]
    for (const [index, line] of lines.entries()) {
      const { definitions, problems } = parseSpecAcDefinitions(['前の文。', line].join('\n'), '5-x')
      assert.deepEqual(definitions, [], line)
      assert.equal(problems.length, 1, line)
      assert.match(problems[0], /^2 行：AC の定義の行が崩れている/, `${index}：${line}`)
    }
  })

  test('文の中で AC の番号を書く書き方は問題にしない', () => {
    const lines = [
      '- AC-90 にテストがない。',
      '- AC-5-1 の補足。',
      '  - AC-5-1 と同じ。',
      '- AC-90、91 を変える。',
      '- AC-90（注文）を変える。',
      '- AC-90 → AC-147 に置き換える。',
      'AC-400 は文である。',
      '**AC-400** は文である。',
      '- [ ] AC-5-1 のテストを書く。',
      '- [ ] **T5-01** AC-5-1 文。',
      '### 6.3 読み上げの領域（AC-193-2）',
      '### AC-5-1 の確認方法',
      '| AC-73（カート） | 文。 |',
      '| AC-307 のテストの名前 | 文。 |',
      '| AC-94、95 | 文。 |',
      '| 項目 | AC-44 | 文。 |',
      '- AC-90',
      '- `AC-5-1` 文。',
      '### 1. AC-5-1 の確認方法',
    ]
    for (const line of lines) {
      assert.deepEqual(parseSpecAcDefinitions(line, '5-x'), { definitions: [], problems: [] }, line)
    }
  })

  test('助詞の一覧は語の先頭で比べるので、同じかなで始まる文は問題にしない（既知の制限）', () => {
    assert.deepEqual(parseSpecAcDefinitions('- AC-5-1 もし在庫がなければ拒否する。', '5-x'), { definitions: [], problems: [] })
  })

  test('定義の形でない書き方でも、コードブロック・HTML のコメント・1 行のコードの中は問題にしない', () => {
    const spec = ['```markdown', '- AC-5-1 文。', '| AC-5-2 | 文。 |', '```', '<!-- ### AC-5-3 文。 -->', '`**AC-5-4** 文。`', '| `AC-5-5` | 文。 |'].join('\n')
    assert.deepEqual(parseSpecAcDefinitions(spec, '5-x'), { definitions: [], problems: [] })
  })

  test('同じ AC を 2 回定義していても、ac-check では問題にしない（構造の検査が見る）', () => {
    const { definitions, problems } = parseSpecAcDefinitions('- **AC-5-1** 一つ目。\n- **AC-5-1** もう一度。', '5-x')
    assert.equal(definitions.length, 2)
    assert.deepEqual(problems, [])
  })

  test('spec の定義の行から、MVP の AC-n と新しい形の AC-n-m の両方を取り出す。別の AC として数える', () => {
    const spec = [
      '- **AC-12** MVP の AC。',
      '- **AC-7-1** 新しい形の AC。',
      '- **AC-7-2** 二つ目。',
      '- **DC-7-3** 整理の番号は数えない。',
    ].join('\n')
    assert.deepEqual(
      parseSpecAcDefinitions(spec, '7-dev-process').definitions.map(({ ac, content }) => ({ ac, content })),
      [
        { ac: 12, content: 'MVP の AC。' },
        { ac: '7-1', content: '新しい形の AC。' },
        { ac: '7-2', content: '二つ目。' },
      ],
    )
  })
})
