import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { checkCommentLines, checkFirstLines, compareChecks, listChecks, listCommentTargets, readTableChecks } from './ci-docs.mjs'

const DOC = [
  '# CI の検査',
  '## CI のジョブ',
  '| `not-a-check` | x |',
  '## 検査の一覧',
  '| script | 目的 |',
  '|--------|--------------|',
  '| `ac-check` | AC |',
  '| `doc-refs` | 参照 |',
  '## カバレッジ',
  '| `coverage-summary.mjs` | 合計 |',
  '## GitHub',
  '| `other` | y |',
].join('\n')

describe('CI の検査の文書の表の検査', () => {
  test('見出し「検査の一覧」の節で、表の 1 列目の名前を、下の見出しより前の範囲から読む', () => {
    assert.deepEqual(readTableChecks(DOC), ['ac-check', 'doc-refs'])
  })

  test('表を探す節は、見出しの名前が「検査の一覧」と完全に一致する節だけで、名前の違う節の表は読まない', () => {
    const sections = ['## 別の節', '| `x` | y |', '## 検査の一覧', '| `a` | b |', '## 次の節', '| `z` | y |'].join('\n')
    assert.deepEqual(readTableChecks(sections), ['a'])
    assert.deepEqual(readTableChecks(['## 検査の一覧', '| `a` | b |'].join('\n')), ['a'])
    assert.deepEqual(readTableChecks(['## 検査の一覧s', '| `a` | b |'].join('\n')), [])
  })

  test('節の中の表の前にコードブロックがあり、その中に `# コメント` の行があっても、節は終わらず表を読む', () => {
    const doc = ['## 検査の一覧', '```bash', '# 手元で実行する', 'node a.mjs', '```', '| script | 目的 |', '|---|---|', '| `a` | x |'].join('\n')
    assert.deepEqual(readTableChecks(doc), ['a'])
  })

  test('節の中の h4 以下の見出し（`#### 注`）は節を区切らず、その下の表も読む', () => {
    const doc = ['## 検査の一覧', '| script | 目的 |', '|---|---|', '| `a` | x |', '#### 注', '| `b` | y |'].join('\n')
    assert.deepEqual(readTableChecks(doc), ['a', 'b'])
  })

  test('直下の検査だけを選び、テストと下のディレクトリと他の拡張子を除く', () => {
    const files = ['scripts/ci/spec/ac-check.mjs', 'scripts/ci/spec/ac-check.test.mjs', 'scripts/ci/lib/x.mjs', 'scripts/ci/check-all.sh', 'scripts/ci/ja/ja-text.json', 'scripts/ci/docs/doc-refs.mjs']
    assert.deepEqual(listChecks(files), ['ac-check', 'doc-refs'])
  })

  test('scripts/ci/ の直下、検査のフォルダの下のフォルダ、補助のフォルダの .mjs は、検査として選ばない', () => {
    const files = ['scripts/ci/x.mjs', 'scripts/ci/docs/sub/x.mjs', 'scripts/ci/mermaid/mermaid-check.mjs', 'scripts/ci/docs/doc-refs.mjs']
    assert.deepEqual(listChecks(files), ['doc-refs'])
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

  test('名前の形（| `名前` |）でない行は名前として数えない（その検査は「表にない検査」になる）', () => {
    const doc = ['## 検査の一覧', '| script | 目的 |', '|---|---|', '| `a` | x |', '| old-check | x |', '|`b`| x |'].join('\n')
    assert.deepEqual(readTableChecks(doc), ['a'])
    assert.match(compareChecks(readTableChecks(doc), ['a', 'b']).join(), /表にない検査：b/)
  })

  test('名前の後に（`引数`）を書いた行は、名前として数えない', () => {
    const doc = ['## 検査の一覧', '| script | 目的 |', '|---|---|', '| `ja-text` | x |', '| `ja-text`（`--changed`） | y |'].join('\n')
    assert.deepEqual(readTableChecks(doc), ['ja-text'])
  })

  test('先頭のコメントの検査の対象は、直下の検査と lib の各ファイルで、テストと他の拡張子を除く', () => {
    const files = ['scripts/ci/spec/ac-check.mjs', 'scripts/ci/spec/ac-check.test.mjs', 'scripts/ci/lib/x.mjs', 'scripts/ci/lib/x.test.mjs', 'scripts/ci/lib/deep/y.mjs', 'scripts/ci/check-all.sh', 'scripts/ci/ja/ja-text.json']
    assert.deepEqual(listCommentTargets(files), ['scripts/ci/lib/deep/y.mjs', 'scripts/ci/lib/x.mjs', 'scripts/ci/spec/ac-check.mjs'])
  })

  test('scripts/ci/ の直下と補助のフォルダの .mjs は、先頭のコメントの検査の対象にしない', () => {
    assert.deepEqual(listCommentTargets(['scripts/ci/x.mjs', 'scripts/ci/mermaid/mermaid-check.mjs']), [])
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

  test('直下の検査の先頭のコメントに「失敗したとき：」と「除外：」の行があれば、検査が通る（`//` の直後に書く形と箇条書きの形）', () => {
    const problems = checkCommentLines([
      { file: 'scripts/ci/a.mjs', text: '// 確認すること\n// 失敗したとき：直す\n// 除外：なし\nimport x from "y"\n' },
      { file: 'scripts/ci/b.mjs', text: '// ====\n// 確認すること\n//\n// - 失敗したとき：直す\n// - 除外：b.json に {キー: 理由}\n// ====\nimport x from "y"\n' },
    ])
    assert.deepEqual(problems, [])
  })

  test('直下の検査の先頭のコメントに「失敗したとき：」か「除外：」の行がなければ、ファイルつきで失敗にする', () => {
    const problems = checkCommentLines([
      { file: 'scripts/ci/a.mjs', text: '// 確認すること\n// 除外：なし\n' },
      { file: 'scripts/ci/b.mjs', text: '// 確認すること\n// 失敗したとき：直す\n' },
      { file: 'scripts/ci/c.mjs', text: '// 確認すること\n' },
    ])
    assert.equal(problems.length, 3)
    assert.match(problems[0], /scripts\/ci\/a\.mjs.*失敗したとき：/)
    assert.match(problems[1], /scripts\/ci\/b\.mjs.*除外：/)
    assert.match(problems[2], /scripts\/ci\/c\.mjs.*失敗したとき：.*除外：/)
  })

  test('先頭のコメントより後ろの行と、行の途中の「失敗したとき：」「除外：」は、行として数えない', () => {
    const problems = checkCommentLines([
      { file: 'scripts/ci/a.mjs', text: '// 確認すること\nimport x from "y"\n// 失敗したとき：直す\n// 除外：なし\n' },
      { file: 'scripts/ci/b.mjs', text: '// 確認すること。失敗したとき：直す。除外：なし\n' },
    ])
    assert.equal(problems.length, 2)
  })
})
