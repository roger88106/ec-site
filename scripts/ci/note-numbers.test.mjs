import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findNoteProblems } from './note-numbers.mjs'

describe('注の番号の検査', () => {
  test('番号が 1 から順に出れば問題なし', () => {
    const text = ['| a ※1 |', '| b ※2 |', '', '※1：理由 1', '', '※2：理由 2'].join('\n')
    assert.deepEqual(findNoteProblems(text), [])
  })

  test('同じ注を本文で何度参照してもよい', () => {
    const text = ['a ※1', 'b ※1', '※1：理由'].join('\n')
    assert.deepEqual(findNoteProblems(text), [])
  })

  test('定義の番号が重複したら失敗にする', () => {
    const text = ['a ※1', '※1：理由 1', '', 'b ※1', '※1：理由 2'].join('\n')
    const problems = findNoteProblems(text)
    assert.equal(problems.length, 1)
    assert.equal(problems[0].line, 5)
    assert.match(problems[0].message, /重複/)
  })

  test('定義が出る順に並ばなければ失敗にする', () => {
    const text = ['a ※1', 'b ※2', 'c ※3', '※1：1', '※3：3', '※2：2'].join('\n')
    const problems = findNoteProblems(text)
    assert.deepEqual(
      problems.map((problem) => problem.line),
      [6],
    )
    assert.match(problems[0].message, /順/)
  })

  test('本文の参照が出る順に並ばなければ失敗にする', () => {
    const text = ['a ※2', 'b ※1', '※1：1', '※2：2'].join('\n')
    const problems = findNoteProblems(text)
    assert.deepEqual(
      problems.map((problem) => problem.line),
      [2],
    )
  })

  test('定義の行は参照に数えず、定義の行の中の ※ は読まない', () => {
    const text = ['a ※1', '※1：理由（※9 ではない）'].join('\n')
    assert.deepEqual(findNoteProblems(text), [])
  })

  test('コードブロックの中は数えない', () => {
    const text = ['a ※1', '※1：理由', '', '```md', '※1：例', '※1：例', 'x ※5', '```'].join('\n')
    assert.deepEqual(findNoteProblems(text), [])
  })

  test('字下げした行の ※1： は定義でなく参照として読む', () => {
    const text = ['a ※1', '※1：理由', '  ※1：字下げ'].join('\n')
    assert.deepEqual(findNoteProblems(text), [])
  })

  test('他の文書の注を指す「第 N 節 ※N」は、この文書の参照に数えない', () => {
    const text = ['他の文書 第 2 節 ※3 を参照', 'a ※1', '※1：理由'].join('\n')
    assert.deepEqual(findNoteProblems(text), [])
  })

  test('字下げした囲みのコードブロックの中は数えない', () => {
    const text = ['a ※2', '- 手順', '  ```', '  ※1 の例', '  ```', 'b ※2', '※2：理由'].join('\n')
    assert.deepEqual(findNoteProblems(text), [])
  })

  test('4 つ以上のバッククォートの囲みは、同じ数以上で閉じるまで続く（中の 3 つでは閉じない）', () => {
    const text = ['a ※2', '````md', '```', '※1 の例', '```', '````', 'b ※2', '※2：理由'].join('\n')
    assert.deepEqual(findNoteProblems(text), [])
  })

  test('~~~ の囲みのコードブロックの中は数えない。バッククォートの囲みの中の ~~~ では閉じない', () => {
    const text = ['a ※2', '~~~', '※1 の例', '~~~', '```', '~~~', '※1 の例', '```', 'b ※2', '※2：理由'].join('\n')
    assert.deepEqual(findNoteProblems(text), [])
  })

  test('インラインコードの中の ※N は数えない', () => {
    const text = ['a ※3', '`※1` と ``※2`` の説明', 'b ※3', '※3：理由'].join('\n')
    assert.deepEqual(findNoteProblems(text), [])
  })

  test('枝番の注（※2a）を定義と参照として読み、重複と順序を見る', () => {
    assert.deepEqual(findNoteProblems(['a ※2', 'b ※2a', 'c ※3', '※2：x', '※2a：y', '※3：z'].join('\n')), [])
    const duplicated = findNoteProblems(['a ※1a', '※1a：x', 'b ※1a', '※1a：y'].join('\n'))
    assert.equal(duplicated.length, 1)
    assert.match(duplicated[0].message, /重複/)
    const reference = findNoteProblems(['a ※2a', 'b ※2', '※2：x', '※2a：y'].join('\n'))
    assert.deepEqual(reference.map((problem) => problem.line), [2])
    const definition = findNoteProblems(['a ※2', 'b ※2a', '※2a：y', '※2：x'].join('\n'))
    assert.deepEqual(definition.map((problem) => problem.line), [4])
  })

  test('同じ文書の「この節 ※1」は参照として数える', () => {
    const text = ['x ※3', 'この節 ※1 を見る', '※1：1', '※3：3'].join('\n')
    assert.deepEqual(findNoteProblems(text).map((problem) => problem.line), [2])
  })

  test('番号が飛んでいても、順に並んでいれば問題にしない', () => {
    const text = ['a ※1', 'b ※3', '※1：1', '※3：3'].join('\n')
    assert.deepEqual(findNoteProblems(text), [])
  })
})
