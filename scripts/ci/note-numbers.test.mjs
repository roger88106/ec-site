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

  test('リストの中で字下げした ※N： も定義として読む（重複は失敗、本文の参照には数えない）', () => {
    assert.deepEqual(findNoteProblems(['- a ※1', '- b ※2', '  ※1：理由 1', '  ※2：理由 2'].join('\n')), [])
    const problems = findNoteProblems(['a ※1', '※1：理由', '  ※1：字下げ'].join('\n'))
    assert.equal(problems.length, 1)
    assert.match(problems[0].message, /重複/)
  })

  test('他の文書の注を指す「第 N 節 ※N」は、この文書の参照に数えない', () => {
    const text = ['他の文書 第 2 節 ※3 を参照', 'a ※1', '※1：理由'].join('\n')
    assert.deepEqual(findNoteProblems(text), [])
  })

  test('「第 N 節の ※N」（の を挟む形）も他の文書の注を指す参照で、この文書の参照に数えない。「この節 ※1」「この節の ※1」は数える', () => {
    assert.deepEqual(findNoteProblems(['他の文書 第 2 節の ※3 を参照', 'a ※1', '※1：理由'].join('\n')), [])
    assert.equal(findNoteProblems(['a ※2', 'この節 ※1', '※1：1', '※2：2'].join('\n')).length, 1)
    assert.equal(findNoteProblems(['a ※2', 'この節の ※1', '※1：1', '※2：2'].join('\n')).length, 1)
  })

  test('リンクか文書の名前の後の「の「…」 ※N」「の「…」の ※N」は、他の文書の見出しの注を指すので、この文書の参照に数えない', () => {
    const names = new Map([['架空の文書', 'docs/x/fictional.md']])
    assert.deepEqual(findNoteProblems(['[開発の進め方](../../docs/05-開発規則/workflow.md)の「起票」 ※3 を参照', 'a ※1', '※1：理由'].join('\n'), names), [])
    assert.deepEqual(findNoteProblems(['架空の文書の「起票」の ※3 を参照', 'a ※1', '※1：理由'].join('\n'), names), [])
    assert.deepEqual(findNoteProblems(['架空の文書の「段階 1」の「タスク」 ※3 を参照', 'a ※1', '※1：理由'].join('\n'), names), [])
  })

  test('インラインコードで書いたパスの後と、外のリンクの後の「の「…」 ※N」も、他の文書の注として数えない', () => {
    const basenames = new Map([['setup.md', 'docs/06-手順書/setup.md']])
    assert.deepEqual(findNoteProblems(['`docs/06-手順書/setup.md` の「アカウント」 ※3 を参照', 'a ※1', '※1：理由'].join('\n'), new Map(), 'docs/a.md', basenames), [])
    assert.deepEqual(findNoteProblems(['[外](https://example.com/a)の「Y」 ※3 を参照', 'a ※1', '※1：理由'].join('\n')), [])
    // インラインコードの中の ※N は、前と同じく数えない
    assert.deepEqual(findNoteProblems(['a ※2', '`の「x」 ※1`', '※2：2'].join('\n')), [])
  })

  test('前がリンクか文書の名前でない「の「…」 ※N」（この文書の「…」、表の「…」）は、同じ文書の注として数える', () => {
    const names = new Map([['開発の進め方', 'docs/05-開発規則/workflow.md']])
    assert.equal(findNoteProblems(['a ※2', 'この文書の「起票」 ※1', '※1：1', '※2：2'].join('\n'), names).length, 1)
    assert.equal(findNoteProblems(['a ※2', '表の「許可」 ※1', '※1：1', '※2：2'].join('\n'), names).length, 1)
    assert.equal(findNoteProblems(['a ※2', '[同じ文書](#起票)の「起票」 ※1', '※1：1', '※2：2'].join('\n'), names).length, 1)
  })

  test('文書の名前を付けない「「…」 ※N」は、同じ文書にある見出しの注を指すので参照に数える', () => {
    assert.equal(findNoteProblems(['a ※2', '（「起票」 ※1）', '※1：1', '※2：2'].join('\n')).length, 1)
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
