import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findLicenseProblems, findPolicyProblems, readAllowedRow } from './license-policy.mjs'

const doc = (row) =>
  ['## 2. 依存のライセンスの方針', '', '| 区分 | ライセンス | 扱い |', '|------|------------|------|', row, '| 条件付き | EPL-1.0・2.0 | 条件 |', '', '## 3. 次'].join('\n')

describe('許可の行の読み取り', () => {
  test('2 列目を「、」で分けて読む', () => {
    assert.deepEqual(readAllowedRow(doc('| 許可 | MIT、Apache-2.0、BSD-2-Clause | そのまま使える |')), {
      licenses: ['MIT', 'Apache-2.0', 'BSD-2-Clause'],
      unreadable: [],
    })
  })

  test('「。他に、」から後ろの文は読まない', () => {
    const result = readAllowedRow(doc('| 許可 | MIT、ISC。他に、改変を禁じるが無料で商用に使えるもの | 扱い |'))
    assert.deepEqual(result.licenses, ['MIT', 'ISC'])
    assert.deepEqual(result.unreadable, [])
  })

  test('許可の行がなければ、licenses を null にする', () => {
    assert.equal(readAllowedRow(doc('| 禁止 | GPL | 使わない |')).licenses, null)
  })

  test('区切りが読めない（空の項目、空白を含む項目）ときは、行を出す', () => {
    const empty = readAllowedRow(doc('| 許可 | MIT、、ISC | 扱い |'))
    assert.equal(empty.unreadable[0].line, 5)
    const spaced = readAllowedRow(doc('| 許可 | MIT, ISC | 扱い |'))
    assert.equal(spaced.unreadable[0].line, 5)
  })

  test('第 2 節に許可の行が 2 つ以上あるときは、2 つ目以降の行を理由つきで出し、ライセンスは最初の行だけ読む', () => {
    const text = ['## 2. 依存のライセンスの方針', '| 許可 | MIT | a |', '| 許可 | ISC | b |', '| 許可 | Zlib | c |', '## 3. 次'].join('\n')
    const result = readAllowedRow(text)
    assert.deepEqual(result.licenses, ['MIT'])
    assert.deepEqual(
      result.unreadable.map((item) => item.line),
      [3, 4],
    )
    assert.match(result.unreadable[0].reason, /複数/)
  })

  test('第 2 節の外の許可の行は読まない', () => {
    const text = ['## 1. 前', '| 許可 | MIT | x |', '## 2. 依存のライセンスの方針', '| 区分 | a | b |'].join('\n')
    assert.equal(readAllowedRow(text).licenses, null)
  })
})

describe('一覧の照合', () => {
  test('順序が違っても一致していれば問題はない', () => {
    assert.deepEqual(findLicenseProblems(['MIT', 'ISC'], ['ISC', 'MIT']), [])
  })

  test('文書にだけあるライセンスを見つける', () => {
    const problems = findLicenseProblems(['MIT', 'ISC'], ['MIT'])
    assert.equal(problems.length, 1)
    assert.match(problems[0], /ISC/)
  })

  test('policy.json にだけあるライセンスを見つける', () => {
    const problems = findLicenseProblems(['MIT'], ['MIT', 'Zlib'])
    assert.equal(problems.length, 1)
    assert.match(problems[0], /Zlib/)
  })
})

describe('問題の集め方', () => {
  const row = (names) => doc(`| 許可 | ${names} | 扱い |`)
  const problemsOf = (text, policy) => findPolicyProblems(text, policy).problems

  test('全て一致していれば問題はない', () => {
    assert.deepEqual(problemsOf(row('MIT、ISC'), { allowed: ['ISC', 'MIT'] }), [])
  })

  test('文書の「許可」の行の件数も返す（行がなければ 0）', () => {
    assert.equal(findPolicyProblems(row('MIT、ISC'), { allowed: ['MIT'] }).documentedCount, 2)
    assert.equal(findPolicyProblems(doc('| 禁止 | GPL | 使わない |'), { allowed: ['MIT'] }).documentedCount, 0)
  })

  test('「許可」の行がなければ、その問題を出す', () => {
    const problems = problemsOf(doc('| 禁止 | GPL | 使わない |'), { allowed: ['MIT'] })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /「許可」の行がない/)
  })

  test('allowed がない、または配列でないときは、その問題を出し、過不足は照合しない', () => {
    for (const policy of [{}, { allowed: 'MIT' }, { allowed: null }]) {
      const problems = problemsOf(row('MIT'), policy)
      assert.equal(problems.length, 1)
      assert.match(problems[0], /allowed がない/)
    }
  })

  test('「許可」の行がなく allowed もないときは、両方の問題を出す', () => {
    assert.equal(problemsOf(doc('| 禁止 | GPL | 使わない |'), {}).length, 2)
  })

  test('許可の行が複数あるときは、2 つ目以降の行を問題にする', () => {
    const text = ['## 2. 依存', '| 許可 | MIT | a |', '| 許可 | ISC | b |', '## 3. 次'].join('\n')
    const problems = problemsOf(text, { allowed: ['MIT'] })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /複数/)
    assert.match(problems[0], /licenses\.md:3/)
  })

  test('区切りが読めないときは、読めない行を問題にし、読めた分は照合する', () => {
    const problems = problemsOf(row('MIT、、ISC'), { allowed: ['MIT'] })
    assert.equal(problems.length, 2)
    assert.match(problems[0], /licenses\.md:5/)
    assert.match(problems[1], /ISC/)
  })

  test('過不足があれば、その説明を出す', () => {
    const problems = problemsOf(row('MIT、ISC'), { allowed: ['MIT', 'Zlib'] })
    assert.equal(problems.length, 2)
    assert.match(problems[0], /ISC/)
    assert.match(problems[1], /Zlib/)
  })
})
