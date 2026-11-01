import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { checkFirstLines, checkTableColumns, compareChecks, listChecks, listCommentTargets, readTableChecks } from './ci-docs.mjs'

const DOC = [
  '# CI の検査',
  '## 2. CI のジョブ',
  '| `not-a-check` | x |',
  '## 3. CI の検査の script',
  '| script | 目的 |',
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

  test('表が 2 列なら列の検査が通る', () => {
    assert.deepEqual(checkTableColumns(DOC), [])
  })

  test('表が 3 列と 4 列の行を、行の番号つきで失敗にする', () => {
    const doc = ['## 3. CI の検査の script', '| script | 確認すること | 失敗したとき |', '|---|---|---|', '| `a` | x | y | z |', '### 3.1 カバレッジ', '| `b` | x | y |'].join('\n')
    const problems = checkTableColumns(doc)
    assert.equal(problems.length, 3)
    assert.match(problems[0], /ci\.md:2 .*3 列/)
    assert.match(problems[2], /ci\.md:4 .*4 列/)
  })

  test('バッククォートの中の縦線と、エスケープした縦線は、列の区切りに数えない', () => {
    const doc = ['## 3. CI の検査の script', '| `a` | 例 `x | y` と \\| |'].join('\n')
    assert.deepEqual(checkTableColumns(doc), [])
  })

  test('末尾の縦線がない表の行は、読み飛ばさずに失敗にする', () => {
    const doc = ['## 3. CI の検査の script', '| `a` | x'].join('\n')
    const problems = checkTableColumns(doc)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /ci\.md:2 /)
  })

  test('先頭のコメントの検査の対象は、直下の検査と lib の各ファイルで、テストと他の拡張子を除く', () => {
    const files = ['scripts/ci/ac-check.mjs', 'scripts/ci/ac-check.test.mjs', 'scripts/ci/lib/x.mjs', 'scripts/ci/lib/x.test.mjs', 'scripts/ci/lib/deep/y.mjs', 'scripts/ci/check-all.sh', 'scripts/ci/ja-text.json']
    assert.deepEqual(listCommentTargets(files), ['scripts/ci/ac-check.mjs', 'scripts/ci/lib/x.mjs'])
  })

  test('全ての script の 1 行目がコメントなら、先頭のコメントの検査が通る', () => {
    assert.deepEqual(checkFirstLines([{ file: 'scripts/ci/a.mjs', text: '// 説明\nimport x from "y"\n' }]), [])
  })

  test('1 行目がコメントでない script を、ファイルつきで失敗にする', () => {
    const problems = checkFirstLines([
      { file: 'scripts/ci/a.mjs', text: "import x from 'y'\n// 後ろのコメント\n" },
      { file: 'scripts/ci/lib/b.mjs', text: '\n// 空行が先\n' },
      { file: 'scripts/ci/c.mjs', text: '// 説明\n' },
    ])
    assert.equal(problems.length, 2)
    assert.match(problems[0], /scripts\/ci\/a\.mjs:1 /)
    assert.match(problems[1], /scripts\/ci\/lib\/b\.mjs:1 /)
  })
})
