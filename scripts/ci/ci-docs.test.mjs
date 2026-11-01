import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { compareChecks, listChecks, readTableChecks } from './ci-docs.mjs'

const DOC = [
  '# CI の検査',
  '## 2. CI のジョブ',
  '| `not-a-check` | x |',
  '## 3. CI の検査の script',
  '| script | 確認すること |',
  '|--------|--------------|',
  '| `ac-check` | AC |',
  '| `doc-refs` | 参照 |',
  '### 3.1 カバレッジ',
  '| `coverage-summary.mjs` | 合計 |',
  '## 4. GitHub',
  '| `other` | y |',
].join('\n')

describe('CI の検査の文書の表の検査', () => {
  test('見出し `## 3.` の節の表の 1 列目の名前を、`### 3.1` より前の範囲から読む', () => {
    assert.deepEqual(readTableChecks(DOC), ['ac-check', 'doc-refs'])
  })

  test('直下の検査だけを選び、テストと下のディレクトリと他の拡張子を除く', () => {
    const files = ['scripts/ci/ac-check.mjs', 'scripts/ci/ac-check.test.mjs', 'scripts/ci/lib/x.mjs', 'scripts/ci/check-all.sh', 'scripts/ci/ja-text.json', 'scripts/ci/doc-refs.mjs']
    assert.deepEqual(listChecks(files), ['ac-check', 'doc-refs'])
  })

  test('一致していれば問題がない', () => {
    assert.deepEqual(compareChecks(['a', 'b'], ['b', 'a']), [])
  })

  test('表にない script と、script のない表の行を失敗にする', () => {
    const problems = compareChecks(['a', 'ghost'], ['a', 'new'])
    assert.equal(problems.length, 2)
    assert.match(problems[0], /new/)
    assert.match(problems[1], /ghost/)
  })

  test('表に同じ名前が 2 回あれば失敗にする', () => {
    assert.equal(compareChecks(['a', 'a'], ['a']).length, 1)
  })
})
