import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findLicenseProblems, readAllowedRow } from './license-policy.mjs'

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
