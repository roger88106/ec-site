import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findNoteProblems, isTargetDoc } from './note-numbers.mjs'

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

  test('番号が飛んでいても、順に並んでいれば問題にしない', () => {
    const text = ['a ※1', 'b ※3', '※1：1', '※3：3'].join('\n')
    assert.deepEqual(findNoteProblems(text), [])
  })

  test('対象の文書：docs、README、AGENTS、skill の Markdown。アーカイブは除く', () => {
    assert.equal(isTargetDoc('docs/design/modules/order.md'), true)
    assert.equal(isTargetDoc('docs/specs/185-new-checks/spec.md'), true)
    assert.equal(isTargetDoc('README.md'), true)
    assert.equal(isTargetDoc('AGENTS.md'), true)
    assert.equal(isTargetDoc('.claude/skills/docs/SKILL.md'), true)
    assert.equal(isTargetDoc('docs/specs/archive/1to10/x/spec.md'), false)
    assert.equal(isTargetDoc('scripts/ci/note-numbers.mjs'), false)
  })
})
