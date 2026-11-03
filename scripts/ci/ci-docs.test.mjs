import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { checkCommentLines, checkFirstLines, checkTableColumns, checkTableNames, compareChecks, listChecks, listCommentTargets, readTableChecks } from './ci-docs.mjs'

const DOC = [
  '# CI の検査',
  '## CI のジョブ',
  '| `not-a-check` | x |',
  '# CI の検査の script',
  '| script | 目的 |',
  '|--------|--------------|',
  '| `ac-check` | AC |',
  '| `doc-refs` | 参照 |',
  '## 1. カバレッジ',
  '| `coverage-summary.mjs` | 合計 |',
  '## 2. GitHub',
  '| `other` | y |',
].join('\n')

describe('CI の検査の文書の表の検査', () => {
  test('見出し「CI の検査の script」の節の表の 1 列目の名前を、下の見出しより前の範囲から読む', () => {
    assert.deepEqual(readTableChecks(DOC), ['ac-check', 'doc-refs'])
  })

  test('表を探す節は、見出しの番号でなく名前で決める（番号つきでも、番号がなくても読み、名前の違う節の表は読まない）', () => {
    const numbered = ['## 3. 別の節', '| `x` | y |', '## 7. CI の検査の script', '| `a` | b |', '## 8. 次の節', '| `z` | y |'].join('\n')
    assert.deepEqual(readTableChecks(numbered), ['a'])
    assert.deepEqual(readTableChecks(['# CI の検査の script', '| `a` | b |'].join('\n')), ['a'])
    assert.deepEqual(readTableChecks(['## 3. CI の検査の scripts', '| `a` | b |'].join('\n')), [])
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
    const doc = ['# CI の検査の script', '| script | 確認すること | 失敗したとき |', '|---|---|---|', '| `a` | x | y | z |', '## 1. カバレッジ', '| `b` | x | y |'].join('\n')
    const problems = checkTableColumns(doc)
    assert.equal(problems.length, 3)
    assert.match(problems[0], /ci-checks.md:2 .*3 列/)
    assert.match(problems[2], /ci-checks.md:4 .*4 列/)
  })

  test('バッククォートの中の縦線と、エスケープした縦線は、列の区切りに数えない', () => {
    const doc = ['# CI の検査の script', '| `a` | 例 `x | y` と \\| |'].join('\n')
    assert.deepEqual(checkTableColumns(doc), [])
  })

  test('2 つのバッククォートで囲んだ中にあるバッククォートを、後ろのコードスパンと組にせず、3 列の行を失敗にする', () => {
    const doc = ['# CI の検査の script', '| `a` | ``x`y`` | 説明 `z` |'].join('\n')
    const problems = checkTableColumns(doc)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /ci-checks.md:2 .*3 列/)
  })

  test('2 つのバッククォートで囲んだ中にある縦線は、列の区切りに数えない', () => {
    const doc = ['# CI の検査の script', '| `a` | 例 ``x`|`y`` |'].join('\n')
    assert.deepEqual(checkTableColumns(doc), [])
  })

  test('組にならないバッククォートが残る行は、読み飛ばさずに失敗にする', () => {
    const doc = ['# CI の検査の script', '| `a` | 例 ``x` |'].join('\n')
    const problems = checkTableColumns(doc)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /ci-checks.md:2 .*バッククォート/)
  })

  test('表の途中の、前に空白のある行と、最初の縦線のない行は、読み飛ばさずに失敗にする', () => {
    const doc = ['# CI の検査の script', '| script | 目的 |', '|---|---|', '| `a` | x |', ' | `old-check` | x |', '`old-check` | x | y |', '', '本文の行'].join('\n')
    const problems = checkTableColumns(doc)
    assert.equal(problems.length, 2)
    assert.match(problems[0], /ci-checks.md:5 .*縦線で始める/)
    assert.match(problems[1], /ci-checks.md:6 .*縦線で始める/)
  })

  test('表の後の空行より後ろの、縦線で始まらない行は、表の行とみなさない', () => {
    const doc = ['# CI の検査の script', '本文の行', '| `a` | x |', '', '本文の行', ' 字下げした本文'].join('\n')
    assert.deepEqual(checkTableColumns(doc), [])
  })

  test('末尾の縦線がない表の行は、読み飛ばさずに失敗にする', () => {
    const doc = ['# CI の検査の script', '| `a` | x'].join('\n')
    const problems = checkTableColumns(doc)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /ci-checks.md:2 /)
  })

  test('見出しの行と区切りの行を除き、1 列目が名前の形の表は、名前の検査が通る', () => {
    assert.deepEqual(checkTableNames(DOC), [])
  })

  test('1 列目が名前の形（`| `名前` |`）でない行を、行の番号つきで失敗にする', () => {
    const doc = [
      '# CI の検査の script',
      '| script | 目的 |',
      '|--------|------|',
      '| `a` | x |',
      '| old-check | x |',
      '|`old-check`| x |',
      '| `old-check`（廃止） | x |',
      '| `old-check` 廃止 | x |',
    ].join('\n')
    const problems = checkTableNames(doc)
    assert.equal(problems.length, 4)
    assert.match(problems[0], /ci-checks.md:5 /)
    assert.match(problems[1], /ci-checks.md:6 /)
    assert.match(problems[2], /ci-checks.md:7 /)
    assert.match(problems[3], /ci-checks.md:8 /)
  })

  test('表の途中で区切りの行の前に置いた行は、見出しの行とみなさず、名前の形でなければ失敗にする', () => {
    const doc = ['# CI の検査の script', '| script | 目的 |', '|---|---|', '| `a` | x |', '| old-check | x |', '|---|---|'].join('\n')
    const problems = checkTableNames(doc)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /ci-checks.md:5 /)
  })

  test('名前の後に（`引数`）を書いた行は、同じ名前の行があれば通り、名前としては数えない', () => {
    const doc = ['# CI の検査の script', '| script | 目的 |', '|---|---|', '| `ja-text` | x |', '| `ja-text`（`--changed`） | y |'].join('\n')
    assert.deepEqual(checkTableNames(doc), [])
    assert.deepEqual(readTableChecks(doc), ['ja-text'])
  })

  test('名前の後に（`引数`）を書いた行で、同じ名前の行がなければ失敗にする', () => {
    const doc = ['# CI の検査の script', '| script | 目的 |', '|---|---|', '| `a` | x |', '| `old-check`（`--x`） | y |'].join('\n')
    const problems = checkTableNames(doc)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /ci-checks.md:5 .*old-check/)
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
