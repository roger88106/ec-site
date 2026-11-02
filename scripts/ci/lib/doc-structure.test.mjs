import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { checkSections, checkUnclosedCodeBlocks, collectDefinitions, collectReferences, MAX_RANGE, readHeadings } from './doc-structure.mjs'

/** collectReferences の結果を、ID の文字列の一覧にする */
function ids(text) {
  return collectReferences(text).references.map((reference) => reference.id)
}

describe('見出しの読み方（DC-321-19）', () => {
  test('見出しの深さ・名前・行を返す', () => {
    assert.deepEqual(readHeadings('# 題\n\n## 節 A\n\n### 小節\n'), [
      { level: 1, title: '題', line: 1 },
      { level: 2, title: '節 A', line: 3 },
      { level: 3, title: '小節', line: 5 },
    ])
  })

  test('バッククォートとチルダのコードブロックの中の見出しは読まない', () => {
    assert.deepEqual(readHeadings('## 外\n```\n## 中 1\n```\n~~~\n## 中 2\n~~~\n## 後'), [
      { level: 2, title: '外', line: 1 },
      { level: 2, title: '後', line: 8 },
    ])
  })

  test('4 つ以上のバッククォートのコードブロックは、3 つの行で閉じず、その中の見出しも読まない', () => {
    const text = '````markdown\n```\n## 中\n```\n## まだ中\n````\n## 外'
    assert.deepEqual(readHeadings(text), [{ level: 2, title: '外', line: 7 }])
  })

  test('閉じの行に info string があるときは閉じにならず、後ろの見出しを読まない', () => {
    assert.deepEqual(readHeadings('```\n## 中\n```mermaid\n## まだ中\n'), [])
  })

  test('字下げが 4 つ以上の行は見出しにしない', () => {
    assert.deepEqual(readHeadings('    ## 字下げ\n## 見出し'), [{ level: 2, title: '見出し', line: 2 }])
  })

  test('ATX の見出しの閉じの # は、名前に含めない', () => {
    assert.deepEqual(readHeadings('## 依存 ##\n### 小節 #   \n## C#'), [
      { level: 2, title: '依存', line: 1 },
      { level: 3, title: '小節', line: 2 },
      { level: 2, title: 'C#', line: 3 },
    ])
  })

  test('HTML のコメントの中の見出しは読まない（行頭の <!-- から --> まで、閉じのない行頭の <!-- から文書の終わりまで）', () => {
    assert.deepEqual(readHeadings('## 外\n<!--\n## 中\n-->\n## 後'), [
      { level: 2, title: '外', line: 1 },
      { level: 2, title: '後', line: 5 },
    ])
    assert.deepEqual(readHeadings('## 外\n<!-- 閉じ忘れ\n## 中'), [{ level: 2, title: '外', line: 1 }])
  })

  test('段落の行の直後の --- と === の行は、その段落を Setext の見出し（## と #）として読む', () => {
    assert.deepEqual(readHeadings('前\n\n段落\n---\n\n題\n==='), [
      { level: 2, title: '段落', line: 3 },
      { level: 1, title: '題', line: 6 },
    ])
    assert.deepEqual(readHeadings('段落の 1 行目\n2 行目\n  ---  '), [{ level: 2, title: '段落の 1 行目 2 行目', line: 1 }])
  })

  test('段落の行でない行（空行・見出し・箇条・表・引用）の後ろの --- は、見出しにしない', () => {
    assert.deepEqual(readHeadings('文\n\n---\n'), [])
    assert.deepEqual(readHeadings('- 箇条\n---\n1. 番号\n---\n| 表 |\n---\n> 引用\n---'), [])
    assert.deepEqual(readHeadings('## 見出し\n---'), [{ level: 2, title: '見出し', line: 1 }])
    assert.deepEqual(readHeadings('```\n段落\n```\n---'), [])
    assert.deepEqual(readHeadings('<!-- 注 -->\n---'), [])
  })
})

describe('閉じていないコードブロックの検査（DC-321-19）', () => {
  test('閉じていないコードブロックの開きの行を返す', () => {
    assert.deepEqual(checkUnclosedCodeBlocks('文\n```\n## 中'), ['2 行：閉じていないコードブロックがある（後ろの見出しと ID を読めない）'])
  })

  test('閉じたコードブロックは問題にしない', () => {
    assert.deepEqual(checkUnclosedCodeBlocks('```\nx\n```\n~~~\ny\n~~~'), [])
  })
})

describe('節の名前と順の検査（DC-321-19）', () => {
  const required = ['A', 'B', 'C']
  const headings = (...titles) => titles.map((title, index) => ({ level: 2, title, line: index + 1 }))

  test('決めた名前を決めた順に持てば問題はない', () => {
    assert.deepEqual(checkSections(headings('A', 'B', 'C'), 2, required), [])
  })

  test('決めた節が欠けたときは、欠けた節だけを報告し、後ろの節を位置の違いにしない', () => {
    assert.deepEqual(checkSections(headings('A', 'C'), 2, required), ['節「B」がない'])
  })

  test('決めた節の入れ替えは位置の違いとして報告する', () => {
    const problems = checkSections(headings('B', 'A', 'C'), 2, required)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /2 行：「A」の位置が違う/)
  })

  test('決めた名前にない見出しは報告する。番号付きの見出しも同じ', () => {
    const problems = checkSections(headings('A', '2. B', 'C'), 2, required)
    assert.equal(problems.length, 2)
    assert.match(problems[0], /2 行：決めた節にない見出し「2\. B」/)
    assert.equal(problems[1], '節「B」がない')
  })

  test('同じ節が 2 回あれば報告する', () => {
    const problems = checkSections(headings('A', 'B', 'B', 'C'), 2, required)
    assert.deepEqual(problems, ['3 行：「B」が 2 回ある'])
  })

  test('extra が true を返す見出しは、決めた名前になくても置いてよい', () => {
    assert.deepEqual(checkSections(headings('A', '決定', 'B', 'C'), 2, required, { extra: (title) => title === '決定' }), [])
  })

  test('調べる深さと違う深さの見出しは見ない', () => {
    const mixed = [...headings('A', 'B', 'C'), { level: 3, title: '小節', line: 4 }]
    assert.deepEqual(checkSections(mixed, 2, required), [])
  })
})

describe('ID の定義の読み方（DC-321-20）', () => {
  test('箇条とタスクの行の先頭にある太字の ID を、定義として読む', () => {
    const text = ['- **AC-321-1** 文', '- **DC-321-2** 文', '- **NFR-321-3** 文', '- [ ] **T321-01-01** 内容', '- [x] **T321-01-02** 内容'].join('\n')
    assert.deepEqual(
      collectDefinitions(text).map((definition) => [definition.id, definition.kind, definition.line]),
      [
        ['AC-321-1', 'bold', 1],
        ['DC-321-2', 'bold', 2],
        ['NFR-321-3', 'bold', 3],
        ['T321-01-01', 'task', 4],
        ['T321-01-02', 'task', 5],
      ],
    )
  })

  test('US と DS は見出しの先頭の ID を定義として読む', () => {
    const text = '### US-321-1 要約（C / P1）\n\n### DS-321-2 目的（D / P1）\n\n### 他の見出し US-321-3'
    assert.deepEqual(
      collectDefinitions(text).map((definition) => [definition.id, definition.kind]),
      [
        ['US-321-1', 'heading'],
        ['DS-321-2', 'heading'],
      ],
    )
  })

  test('字下げした箇条、箇条の途中の太字、表の中の太字は定義にしない', () => {
    const text = ['  - **AC-321-1** 字下げ', '- 文の途中の **AC-321-2** は参照', '| **AC-321-3** | 表 |', '本文の **AC-321-4**'].join('\n')
    assert.deepEqual(collectDefinitions(text), [])
  })

  test('コードブロックの中と 1 行のコードの中は定義にしない', () => {
    const text = ['```markdown', '- **AC-321-1** コードブロック', '```', '- `**AC-321-2**` 1 行のコード', '~~~', '- [ ] **T321-01-01** 内容', '~~~'].join('\n')
    assert.deepEqual(collectDefinitions(text), [])
  })

  test('HTML のコメントの中は定義にしない', () => {
    const text = ['<!--', '- **AC-321-5** 取り下げた条件', '-->', '<!-- - **AC-321-6** 1 行 -->', '- [ ] **T321-01-01** <!-- 注 --> 内容'].join('\n')
    assert.deepEqual(collectDefinitions(text).map((definition) => definition.id), ['T321-01-01'])
  })

  test('ID の番号と連番は、書かれたままの文字で返す（0 で埋めた連番を見分けるため）', () => {
    const [definition] = collectDefinitions('- **DC-321-01** 文')
    assert.equal(definition.number, '321')
    assert.equal(definition.seq, '01')
  })

  test('タスクの ID は、spec の番号・段階・連番を返す', () => {
    const [definition] = collectDefinitions('- [ ] **T321-3-4** 内容')
    assert.deepEqual([definition.number, definition.stage, definition.seq], ['321', '3', '4'])
  })
})

describe('ID の参照の読み方（DC-321-20）', () => {
  test('単独の ID、範囲、列挙、範囲と列挙の組み合わせを開く', () => {
    assert.deepEqual(ids('AC-321-1'), ['AC-321-1'])
    assert.deepEqual(ids('DC-321-18〜21'), ['DC-321-18', 'DC-321-19', 'DC-321-20', 'DC-321-21'])
    assert.deepEqual(ids('DC-321-1、3'), ['DC-321-1', 'DC-321-3'])
    assert.deepEqual(ids('DC-321-1〜2、5、7〜8'), ['DC-321-1', 'DC-321-2', 'DC-321-5', 'DC-321-7', 'DC-321-8'])
  })

  test('全ての接頭辞（US・DS・AC・BR・NFR・DC・DN）を読む', () => {
    assert.deepEqual(ids('US-1-1 DS-1-1 AC-1-1 BR-1-1 NFR-1-1 DC-1-1 DN-1-1'), ['US-1-1', 'DS-1-1', 'AC-1-1', 'BR-1-1', 'NFR-1-1', 'DC-1-1', 'DN-1-1'])
  })

  test('太字の ID も参照として読み、行の番号を返す', () => {
    const { references } = collectReferences('前\n**AC-321-2** 文')
    assert.deepEqual(references, [{ id: 'AC-321-2', number: '321', line: 2, text: 'AC-321-2' }])
  })

  test('区切り・「の」「と」・助詞が続かない数は、列挙にしない（件数、日付、桁など）', () => {
    assert.deepEqual(ids('AC-321-1、2 段階'), ['AC-321-1'])
    assert.deepEqual(ids('AC-321-1、30 秒'), ['AC-321-1'])
    assert.deepEqual(ids('DC-400-3、2026-11-05 に確認'), ['DC-400-3'])
    assert.deepEqual(ids('AC-321-1、2 桁の数'), ['AC-321-1'])
    assert.deepEqual(ids('AC-321-1、2 件'), ['AC-321-1'])
    assert.deepEqual(ids('AC-321-1、2つ'), ['AC-321-1'])
    assert.deepEqual(ids('AC-321-1、2〜5 件'), ['AC-321-1', 'AC-321-2', 'AC-321-3', 'AC-321-4', 'AC-321-5'])
    assert.deepEqual(ids('AC-321-1、2026-11-05'), ['AC-321-1'])
  })

  test('区切り・行末・「の」「と」・助詞が続く数は、列挙として読む', () => {
    assert.deepEqual(ids('DC-321-4、6 の前半、13'), ['DC-321-4', 'DC-321-6', 'DC-321-13'])
    assert.deepEqual(ids('AC-321-1、2 と 3'), ['AC-321-1', 'AC-321-2'])
    for (const tail of ['、', '〜3', '）', ')', '。', '：', ' 。', 'の', 'と']) {
      assert.deepEqual(ids(`AC-321-1、2${tail}`).slice(0, 2), ['AC-321-1', 'AC-321-2'], tail)
    }
    assert.deepEqual(ids('AC-321-1、2'), ['AC-321-1', 'AC-321-2'])
    assert.deepEqual(ids('AC-321-1、2 '), ['AC-321-1', 'AC-321-2'])
    for (const particle of ['を満たす', 'が成り立つ', 'は不要', 'に従う', 'で確認', 'も同じ', 'や 3']) {
      assert.deepEqual(ids(`AC-321-1、2 ${particle}`).slice(0, 2), ['AC-321-1', 'AC-321-2'], particle)
    }
  })

  test('表のセルの区切り・太字・リンク・かぎかっこ・「など」が続く数は、列挙として読む（空白を挟んでよい）', () => {
    for (const text of ['| DC-321-1、99 |', 'DC-321-1、99 など', 'DC-321-1、99など', '**DC-321-1、99**', '[DC-321-1、99](x.md)', '「DC-321-1、99」', 'DC-321-1、99 |']) {
      assert.deepEqual(ids(text), ['DC-321-1', 'DC-321-99'], text)
    }
  })

  test('範囲の記号は 〜（U+301C）、～（U+FF5E）、~ で、前後の空白を許す。列挙の要素の範囲も同じ', () => {
    for (const text of ['DC-321-1〜3', 'DC-321-1～3', 'DC-321-1~3', 'DC-321-1 〜 3', 'DC-321-1 ～3', 'DC-321-1〜 3 件']) {
      assert.deepEqual(ids(text), ['DC-321-1', 'DC-321-2', 'DC-321-3'], text)
    }
    for (const text of ['DC-321-1、5〜6', 'DC-321-1、5～6', 'DC-321-1、5 〜 6', 'DC-321-1、321-5 ～ 6']) {
      assert.deepEqual(ids(text), ['DC-321-1', 'DC-321-5', 'DC-321-6'], text)
    }
    assert.deepEqual(ids('DC-321-1 ～ 2026-11-05'), ['DC-321-1'])
  })

  test('範囲の端に同じ接頭辞と同じ spec の番号の ID を書く形（DS-321-2〜DS-321-5）は、範囲として読む', () => {
    assert.deepEqual(new Set(ids('DS-321-2〜DS-321-5')), new Set(['DS-321-2', 'DS-321-3', 'DS-321-4', 'DS-321-5']))
    assert.deepEqual(new Set(ids('DS-321-2 ～ DS-321-4')), new Set(['DS-321-2', 'DS-321-3', 'DS-321-4']))
    assert.deepEqual(ids('DS-321-2〜DC-321-8'), ['DS-321-2', 'DC-321-8'])
    assert.deepEqual(ids('DS-321-2〜DS-322-8'), ['DS-321-2', 'DS-322-8'])
  })

  test('範囲の端（`〜` の後ろの数）は、数字・`-`・`.` が続かなければ常に読む', () => {
    assert.deepEqual(ids('AC-321-1〜3 件'), ['AC-321-1', 'AC-321-2', 'AC-321-3'])
    assert.deepEqual(ids('DC-321-1〜3 を満たす'), ['DC-321-1', 'DC-321-2', 'DC-321-3'])
    assert.deepEqual(ids('DC-321-1〜2026-11-05'), ['DC-321-1'])
    assert.deepEqual(ids('DC-321-1〜3.5'), ['DC-321-1'])
    assert.deepEqual(ids('AC-321-1〜3 の範囲'), ['AC-321-1', 'AC-321-2', 'AC-321-3'])
    assert.deepEqual(ids('AC-321-1〜3、5'), ['AC-321-1', 'AC-321-2', 'AC-321-3', 'AC-321-5'])
  })

  test('`、<spec の番号>-<連番>` は、spec の番号が直前の ID と同じときだけ列挙として読む', () => {
    assert.deepEqual(ids('DC-7-4〜6、7-32、7-37'), ['DC-7-4', 'DC-7-5', 'DC-7-6', 'DC-7-32', 'DC-7-37'])
    assert.deepEqual(ids('DC-7-4、8-32'), ['DC-7-4'])
    assert.deepEqual(ids('DC-7-4、7-32〜34'), ['DC-7-4', 'DC-7-32', 'DC-7-33', 'DC-7-34'])
    assert.deepEqual(ids('DC-7-4、7-32 件'), ['DC-7-4'])
  })

  test('番号が文字の記号、語の途中の接頭辞、MVP の ID は読まない', () => {
    assert.deepEqual(ids('DC-n AC-<番号>-1 MAC-321-1 AC-82 US-5 BR-12'), [])
  })

  test('日付、commit の ID、issue の番号、TDD は読まない', () => {
    assert.deepEqual(ids('2026-11-02 に 9faca7a3 を #321 で TDD する'), [])
    assert.deepEqual(collectReferences('2026-11-02 に 9faca7a3 を #321 で TDD する'), { references: [], problems: [] })
  })

  test('1 行のコードとコードブロックの中の ID は読まない', () => {
    assert.deepEqual(ids('文 `DC-321-99` 文\n```\nAC-321-98\n```\n~~~\nBR-321-97\n~~~\n文 ``DN-321-96`` 文'), [])
  })

  test('HTML のコメントの中の ID は読まない。コメントの後ろの ID は読む', () => {
    assert.deepEqual(ids('<!--\nAC-321-5\n-->\n文 <!-- DC-321-99 --> DC-321-3'), ['DC-321-3'])
  })

  test('行の途中の、閉じのない <!-- の後ろの ID は読む（コメントにならないため）', () => {
    assert.deepEqual(ids('文 <!-- DC-321-4\nDC-321-5'), ['DC-321-4', 'DC-321-5'])
  })

  test('1 行のコードの後ろの ID は読む', () => {
    assert.deepEqual(ids('`コード` AC-321-5'), ['AC-321-5'])
  })

  test('逆順の範囲は問題にし、その範囲の ID は返さない', () => {
    const result = collectReferences('DC-321-26〜18')
    assert.deepEqual(result.references, [])
    assert.deepEqual(result.problems, ['1 行：範囲が逆順（DC-321-26〜18）'])
  })

  test(`展開して ${MAX_RANGE} 件を超える範囲は問題にし、その ID は返さない`, () => {
    const result = collectReferences('DC-321-1〜1000')
    assert.deepEqual(result.references, [])
    assert.deepEqual(result.problems, [`1 行：範囲が ${MAX_RANGE} 件を超える（DC-321-1〜1000）`])
  })

  test(`ちょうど ${MAX_RANGE} 件の範囲は開く`, () => {
    assert.equal(collectReferences(`DC-321-1〜${MAX_RANGE}`).references.length, MAX_RANGE)
  })

  test('連番を 0 で埋めた ID は問題にする。範囲と列挙の要素も同じ', () => {
    assert.deepEqual(collectReferences('DC-321-01').problems, ['1 行：連番を 0 で埋めている（DC-321-01）'])
    assert.equal(collectReferences('DC-321-1〜05').problems.length, 1)
    assert.equal(collectReferences('DC-321-1、05').problems.length, 1)
  })

  test('タスクの ID と、連番の範囲を開く', () => {
    assert.deepEqual(ids('T321-03-04'), ['T321-03-04'])
    assert.deepEqual(ids('T321-03-01〜03'), ['T321-03-01', 'T321-03-02', 'T321-03-03'])
  })

  test('タスクの ID の後ろの `、<2 桁>` と `、<2 桁>〜<2 桁>` を、spec の ID と同じ規則で列挙として読む', () => {
    assert.deepEqual(ids('依存：T321-01-01、99'), ['T321-01-01', 'T321-01-99'])
    assert.deepEqual(ids('T321-01-08、09 は'), ['T321-01-08', 'T321-01-09'])
    assert.deepEqual(ids('T321-01-01、03〜04、06'), ['T321-01-01', 'T321-01-03', 'T321-01-04', 'T321-01-06'])
    assert.deepEqual(ids('| T321-01-01、02 |'), ['T321-01-01', 'T321-01-02'])
    assert.deepEqual(ids('T321-01-01～03'), ['T321-01-01', 'T321-01-02', 'T321-01-03'])
  })

  test('タスクの ID の後ろの、列挙の規則に合わない数は、列挙にしない', () => {
    assert.deepEqual(ids('T321-01-01、02 件'), ['T321-01-01'])
    assert.deepEqual(ids('T321-01-01、2026-11-05'), ['T321-01-01'])
    assert.deepEqual(ids('T321-01-01、3 の'), ['T321-01-01'])
  })

  test('段階か連番が 2 桁でないタスクの ID は問題にする', () => {
    assert.deepEqual(collectReferences('T321-3-4').problems, ['1 行：タスクの段階と連番が 2 桁でない（T321-3-4）'])
  })

  test('古い形式のタスクの ID（T301 など）は読まない。MVP の spec の形（T011-01-01）は番号 011 の参照として読む', () => {
    assert.deepEqual(ids('T301 T3101'), [])
    assert.deepEqual(collectReferences('T011-01-01').references, [{ id: 'T011-01-01', number: '011', line: 1, text: 'T011-01-01' }])
  })
})
