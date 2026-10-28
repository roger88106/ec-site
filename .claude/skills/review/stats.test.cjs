// stats.cjs のレビューの記録の表の探し方・読み方・集計のテスト
const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('fs')
const os = require('os')
const path = require('path')
const { findSpecFiles, readReviewRows, aggregate, formatReport } = require('./stats.cjs')

const HEADER = '| 時点 | 観点 | モデル | 回数 | token | 時間 | 高 | 必須 | 推奨 | 採用 |'
const SEP = '|------|------|--------|------|-------|------|----|------|------|------|'

/** 見本の根のディレクトリを作る。規模 L の 301 と、規模 M の 302、表のない 303 を置く。 */
function makeRoot() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'stats-test-'))
  const write = (rel, text) => {
    const file = path.join(root, rel)
    fs.mkdirSync(path.dirname(file), { recursive: true })
    fs.writeFileSync(file, text)
  }
  write('docs/specs/archive/1to10/301-x/tasks/04-review.md', [
    '# レビュー',
    '',
    HEADER,
    SEP,
    '| 段階 1 | 網羅性 | 汎用 | 1 | 50000 | 3m | 0 | 2 | 1 | 2 |',
    '| 段階 2 | 網羅性 | 汎用 | 1 | 30000 | 2m | 1 | 0 | 1 | 2 |',
    '| spec の最後 | 規則 | 高性能 | 1 | — | 5m | 0 | 0 | 0 | 0 |',
    '',
    'この文の後の | 段階 9 | x | 汎用 | 1 | 99999 | 1m | 9 | 9 | 9 | 9 | は読まない。',
  ].join('\n'))
  write('docs/specs/archive/mvp/302-y/spec.md', [
    '# spec',
    '',
    HEADER,
    SEP,
    '| pull request の前 | 全体 | 高性能 | 1 | 20000 | 4m | 0 | 1 | 1 | 1 |',
    '| `[リスク]` のタスクの後 | 安全 | 高性能 | 1 | 10000 | 1m | 1 | 0 | 0 | 1 |',
    '',
    '| 表 | 別の表 |',
    '|----|--------|',
    '| 段階 5 | 読まない |',
  ].join('\n'))
  write('docs/specs/archive/mvp/303-z/spec.md', '# 表なし\n')
  write('docs/specs/archive/mvp/3040-w/spec.md', '# 番号が似ている\n')
  return root
}

/** 見本の spec の表の行を読む。 */
function rowsOf(root, number) {
  return findSpecFiles(root, number).flatMap((f) => readReviewRows(fs.readFileSync(f, 'utf8')))
}

test('規模 L の tasks と規模 M の spec.md の両方を探し、番号が前方一致の別の spec は含めない', () => {
  const root = makeRoot()
  const l = findSpecFiles(root, '301')
  const m = findSpecFiles(root, '302')
  assert.equal(l.length, 1)
  assert.ok(l[0].endsWith('04-review.md'))
  assert.equal(m.length, 1)
  assert.ok(m[0].endsWith('spec.md'))
  assert.equal(findSpecFiles(root, '304'), null)
})

test('見つからない番号は null を返す', () => {
  assert.equal(findSpecFiles(makeRoot(), '999'), null)
})

test('見出しの行の後の行を読み、表の後の文と別の表は読まない', () => {
  const root = makeRoot()
  const rows = rowsOf(root, '301')
  assert.equal(rows.length, 3)
  assert.ok(!rows.some((r) => r.high === 9))
  assert.equal(rowsOf(root, '302').length, 2)
  assert.deepEqual(readReviewRows('# 表なし'), [])
})

test('数でない token の列は 0 として数え、読めない行の数を数える', () => {
  const rows = rowsOf(makeRoot(), '301')
  const dash = rows.find((r) => r.time === 'spec の最後')
  assert.equal(dash.token, 0)
  assert.equal(dash.tokenUnreadable, true)
  assert.equal(aggregate(rows).tokenUnreadable, 1)
})

test('段階 n の行は「段階」にまとまり、時点ごとの合計と採用の割合が出る', () => {
  const { byTime } = aggregate(rowsOf(makeRoot(), '301'))
  const stage = byTime.find((t) => t.time === '段階')
  assert.equal(stage.rows, 2)
  assert.equal(stage.token, 80000)
  assert.equal(stage.high, 1)
  assert.equal(stage.must, 2)
  assert.equal(stage.recommended, 2)
  assert.equal(stage.adopted, 4)
  assert.equal(stage.rate, '80.0%')
})

test('指摘が 0 の時点の採用の割合は「—」', () => {
  const last = aggregate(rowsOf(makeRoot(), '301')).byTime.find((t) => t.time === 'spec の最後')
  assert.equal(last.rate, '—')
})

test('出力は弾・profile・spec の番号、時点ごとの行、合計の行を持つ', () => {
  const root = makeRoot()
  const rows = [...rowsOf(root, '301'), ...rowsOf(root, '302')]
  const out = formatReport({ round: '6', profile: '厚め', specs: ['301', '302'] }, aggregate(rows))
  assert.match(out, /弾：6/)
  assert.match(out, /profile：厚め/)
  assert.match(out, /spec：301, 302/)
  assert.match(out, /\| 段階 \| 2 \| 80000 \|/)
  assert.match(out, /\| `\[リスク\]` のタスクの後 \| 1 \| 10000 \|/)
  const total = out.split('\n').find((l) => l.startsWith('| 合計'))
  assert.match(total, /\| 合計 \| 5 \| 110000 \| 2 \| 3 \| 3 \| 6 \| 75\.0% \|/)
})
