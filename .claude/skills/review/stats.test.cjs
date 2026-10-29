// stats.cjs のレビューの記録の表の探し方・読み方・集計のテスト
const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('fs')
const os = require('os')
const path = require('path')
const { spawnSync } = require('child_process')
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
  write('docs/08-spec/archive/1to10/301-x/tasks/04-review.md', [
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
  write('docs/08-spec/archive/mvp/302-y/spec.md', [
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
  write('docs/08-spec/archive/mvp/303-z/spec.md', '# 表なし\n')
  write('docs/08-spec/archive/mvp/3040-w/spec.md', '# 番号が似ている\n')
  write('docs/08-spec/archive/mvp/305-v/README.md', '# tasks も spec.md もない\n')
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
  assert.match(out, /\| \[リスク\] のタスクの後 \| 1 \| 10000 \|/)
  const total = out.split('\n').find((l) => l.startsWith('| 合計'))
  assert.match(total, /\| 合計 \| 5 \| 110000 \| 2 \| 3 \| 3 \| 6 \| 75\.0% \|/)
})

test('箇条の下の字下げした表（行頭に 4 文字の空白）を読む', () => {
  const text = [
    '- [x] T-01 結果',
    '',
    `    ${HEADER}`,
    `    ${SEP}`,
    '    | 段階 1 | 網羅性 | 汎用 | 1 | 50000 | 3m | 0 | 2 | 1 | 2 |',
    '    | 段階 2 | 網羅性 | 汎用 | 1 | 30000 | 2m | 1 | 0 | 1 | 2 |',
    '',
    '    表の後の字下げした文。',
  ].join('\n')
  const rows = readReviewRows(text)
  assert.equal(rows.length, 2)
  assert.equal(rows[1].high, 1)
})

test('同じファイルにある複数の表を全て読む', () => {
  const text = [
    HEADER,
    SEP,
    '| 段階 1 | 網羅性 | 汎用 | 1 | 50000 | 3m | 0 | 2 | 1 | 2 |',
    '',
    '- [x] T-02 結果',
    '',
    `    ${HEADER}`,
    `    ${SEP}`,
    '    | spec の最後 | 規則 | 高性能 | 1 | 20000 | 4m | 0 | 1 | 1 | 1 |',
    '    | spec の最後 | 安全 | 高性能 | 1 | 10000 | 1m | 1 | 0 | 0 | 1 |',
  ].join('\n')
  assert.equal(readReviewRows(text).length, 3)
})

test('見出しだけで行が 0 件の表は、ファイル名を付けて標準エラーに警告する', () => {
  const file = 'docs/08-spec/archive/x/999-y/tasks/empty.md'
  const original = console.error
  const messages = []
  console.error = (m) => messages.push(String(m))
  try {
    assert.deepEqual(readReviewRows(`${HEADER}\n${SEP}\n\n本文\n`, file), [])
  } finally {
    console.error = original
  }
  assert.equal(messages.length, 1)
  assert.ok(messages[0].includes(file))
})

test('番号のディレクトリはあるが tasks も spec.md もないときは、空の配列を返す', () => {
  assert.deepEqual(findSpecFiles(makeRoot(), '305'), [])
})

test('表を置く文書がない番号は、見つからない番号と同じく標準エラーに出して終了コード 1 になる', () => {
  const root = makeRoot()
  const script = path.join(__dirname, 'stats.cjs')
  const run = (...specs) => spawnSync(process.execPath, [script, '--round', '1', '--profile', 'p', ...specs], { cwd: root, encoding: 'utf8' })
  const noDocs = run('305')
  assert.equal(noDocs.status, 1)
  assert.match(noDocs.stderr, /305/)
  assert.match(noDocs.stderr, /tasks と spec\.md がない/)
  assert.equal(noDocs.stdout, '')
  const notFound = run('999')
  assert.equal(notFound.status, 1)
  assert.match(notFound.stderr, /999/)
  assert.equal(run('301', '302').status, 0)
})

test('時点の名前のバッククォートの有無をそろえて集計する', () => {
  const text = [
    HEADER,
    SEP,
    '| `[リスク]` のタスクの後 | 安全 | 高性能 | 1 | 10000 | 1m | 1 | 0 | 0 | 1 |',
    '| [リスク] のタスクの後 | 安全 | 高性能 | 1 | 20000 | 1m | 0 | 1 | 0 | 1 |',
  ].join('\n')
  const { byTime } = aggregate(readReviewRows(text))
  assert.equal(byTime.length, 1)
  assert.equal(byTime[0].time, '[リスク] のタスクの後')
  assert.equal(byTime[0].rows, 2)
  assert.equal(byTime[0].token, 30000)
})

test('時点は 段階・spec の最後・pull request の前・[リスク] のタスクの後 の順に並ぶ', () => {
  const row = (time) => ({ time, token: 0, tokenUnreadable: false, high: 0, must: 0, recommended: 0, adopted: 0 })
  const { byTime } = aggregate([row('`[リスク]` のタスクの後'), row('pull request の前'), row('spec の最後'), row('段階 1')])
  assert.deepEqual(byTime.map((t) => t.time), ['段階', 'spec の最後', 'pull request の前', '[リスク] のタスクの後'])
})

/** 重複を除く行の見本の表（段階 1 に 2 人のレビュー役と重複を除く行、段階 2 に 1 人、spec の最後に 2 人で重複を除く行なし）。 */
const DEDUP_TABLE = [
  HEADER,
  SEP,
  '| 段階 1 | 網羅性 | 汎用 | 1 | 50000 | 3m | 1 | 2 | 1 | 2 |',
  '| 段階 1 | 規則 | 汎用 | 1 | 30000 | 2m | 1 | 1 | 0 | 1 |',
  '| 段階 1 | 重複を除く | — | 1 | — | — | 1 | 2 | 1 | 3 |',
  '| 段階 2 | 網羅性 | 汎用 | 1 | 10000 | 1m | 0 | 1 | 0 | 1 |',
  '| spec の最後 | 規則 | 高性能 | 1 | 20000 | 4m | 0 | 1 | 1 | 1 |',
  '| spec の最後 | 安全 | 高性能 | 1 | 10000 | 1m | 0 | 1 | 0 | 1 |',
].join('\n')

test('重複を除く行がある時点は、その行の高・必須・推奨・採用を使い、他の行の値は足さない', () => {
  const stage = aggregate(readReviewRows(DEDUP_TABLE)).byTime.find((t) => t.time === '段階')
  // 段階 1 は重複を除く行の 1・2・1・3、段階 2 は行の 0・1・0・1 を合計する
  assert.equal(stage.high, 1)
  assert.equal(stage.must, 3)
  assert.equal(stage.recommended, 1)
  assert.equal(stage.adopted, 4)
  assert.equal(stage.rate, '80.0%')
})

test('重複を除く行がない時点は、今までどおり行の値を合計する', () => {
  const last = aggregate(readReviewRows(DEDUP_TABLE)).byTime.find((t) => t.time === 'spec の最後')
  assert.equal(last.must, 2)
  assert.equal(last.recommended, 1)
  assert.equal(last.adopted, 2)
})

test('重複を除く行は行の数に数えず、token も足さず、token が読めない行にも数えない', () => {
  const result = aggregate(readReviewRows(DEDUP_TABLE))
  const stage = result.byTime.find((t) => t.time === '段階')
  assert.equal(stage.rows, 3)
  assert.equal(stage.token, 90000)
  assert.equal(result.total.rows, 5)
  assert.equal(result.tokenUnreadable, 0)
})

test('段階 1 と段階 2 は別の時点として扱い、重複を除く行は同じ n の行にだけ適用される', () => {
  const text = [HEADER, SEP, '| 段階 1 | 網羅性 | 汎用 | 1 | 1 | 1m | 3 | 0 | 0 | 0 |', '| 段階 2 | 網羅性 | 汎用 | 1 | 1 | 1m | 4 | 0 | 0 | 0 |', '| 段階 2 | 重複を除く | — | 1 | — | — | 1 | 0 | 0 | 0 |'].join('\n')
  const stage = aggregate(readReviewRows(text)).byTime.find((t) => t.time === '段階')
  assert.equal(stage.high, 3 + 1)
})

test('重複を除く行がなく、行が 2 つ以上ある組の数を警告として出す（行が 1 つだけの組は数えない）', () => {
  const result = aggregate(readReviewRows(DEDUP_TABLE))
  assert.equal(result.noDedupGroups, 1)
  assert.match(formatReport({ round: '1', profile: 'p', specs: ['1'] }, result), /重複を除く行がない組（表・時点・回数）の数：1/)
})

test('回 1・回 2 とも 2 人で重複を除く行がない時点は、組を 2 と数える', () => {
  const result = aggregate(roundsTable(
    '| 段階 1 | 網羅性 | 汎用 | 1 | 100 | 1m | 0 | 1 | 0 | 1 |',
    '| 段階 1 | 規則 | 汎用 | 1 | 100 | 1m | 0 | 1 | 0 | 1 |',
    '| 段階 1 | 網羅性 | 汎用 | 2 | 100 | 1m | 0 | 1 | 0 | 1 |',
    '| 段階 1 | 規則 | 汎用 | 2 | 100 | 1m | 0 | 1 | 0 | 1 |',
  ))
  assert.equal(result.noDedupGroups, 2)
})

test('重複を除く行は、同じ表の中の同じ時点にだけ適用され、別の表の同じ時点には適用されない', () => {
  const second = [HEADER, SEP, '| 段階 1 | 網羅性 | 汎用 | 1 | 5000 | 1m | 5 | 0 | 0 | 5 |'].join('\n')
  const stage = aggregate([...readReviewRows(DEDUP_TABLE), ...readReviewRows(second)]).byTime.find((t) => t.time === '段階')
  assert.equal(stage.high, 1 + 0 + 5)
})

/** 同じ時点の回数 1・2 の行を持つ表を作る。 */
function roundsTable(...lines) {
  return readReviewRows([HEADER, SEP, ...lines].join('\n'))
}

test('1 回目だけに重複を除く行があるとき、2 回目の行の値は捨てずに合計する', () => {
  const rows = roundsTable(
    '| spec の最後 | 規則 | 高性能 | 1 | 100 | 1m | 1 | 0 | 0 | 1 |',
    '| spec の最後 | 安全 | 高性能 | 1 | 100 | 1m | 1 | 0 | 0 | 0 |',
    '| spec の最後 | 重複を除く | — | 1 | — | — | 1 | 0 | 0 | 1 |',
    '| spec の最後 | 規則 | 高性能 | 2 | 100 | 1m | 0 | 2 | 0 | 2 |',
  )
  const result = aggregate(rows)
  const last = result.byTime.find((t) => t.time === 'spec の最後')
  assert.equal(last.high, 1)
  assert.equal(last.must, 2)
  assert.equal(last.adopted, 3)
  assert.equal(last.rows, 3)
  assert.equal(result.noDedupGroups, 0)
})

test('回数 1・2 の両方に重複を除く行があるとき、後の行は前の行を上書きしない', () => {
  const rows = roundsTable(
    '| spec の最後 | 規則 | 高性能 | 1 | 100 | 1m | 1 | 0 | 0 | 1 |',
    '| spec の最後 | 安全 | 高性能 | 1 | 100 | 1m | 1 | 0 | 0 | 0 |',
    '| spec の最後 | 重複を除く | — | 1 | — | — | 1 | 0 | 0 | 1 |',
    '| spec の最後 | 規則 | 高性能 | 2 | 100 | 1m | 0 | 1 | 0 | 1 |',
    '| spec の最後 | 安全 | 高性能 | 2 | 100 | 1m | 0 | 1 | 0 | 0 |',
    '| spec の最後 | 重複を除く | — | 2 | — | — | 0 | 1 | 0 | 1 |',
  )
  const last = aggregate(rows).byTime.find((t) => t.time === 'spec の最後')
  assert.equal(last.high, 1)
  assert.equal(last.must, 1)
  assert.equal(last.adopted, 2)
})

test('レビュー役が 1 人で 2 回の時点は、重複を除く行がなくても警告に数えない', () => {
  const rows = roundsTable(
    '| 段階 1 | 網羅性 | 汎用 | 1 | 100 | 1m | 0 | 2 | 0 | 2 |',
    '| 段階 1 | 網羅性 | 汎用 | 2 | 100 | 1m | 0 | 1 | 0 | 1 |',
  )
  const result = aggregate(rows)
  assert.equal(result.noDedupGroups, 0)
  assert.equal(result.byTime[0].must, 3)
})

/** 関数の実行中に標準エラーへ出た警告を集める。 */
function captureErrors(fn) {
  const original = console.error
  const messages = []
  console.error = (m) => messages.push(String(m))
  try {
    fn()
  } finally {
    console.error = original
  }
  return messages
}

test('同じ組に重複を除く行が 2 つ以上あるとき、ファイル名を付けて警告し、値は合計する', () => {
  const text = [
    HEADER,
    SEP,
    '| 段階 1 | 網羅性 | 汎用 | 1 | 100 | 1m | 1 | 0 | 0 | 0 |',
    '| 段階 1 | 重複を除く | — | 1 | — | — | 1 | 0 | 0 | 0 |',
    '| 段階 1 | 重複を除く | — | 1 | — | — | 2 | 0 | 0 | 0 |',
    '| 段階 1 | 重複を除く | — | 2 | — | — | 5 | 0 | 0 | 0 |',
  ].join('\n')
  let rows
  const messages = captureErrors(() => { rows = readReviewRows(text, 'a/b.md') })
  assert.equal(messages.length, 1)
  assert.ok(messages[0].includes('a/b.md'))
  assert.equal(aggregate(rows).byTime[0].high, 8)
})

test('回数が数で読めない行は「?」の組にまとめ、読める回とは混ぜない', () => {
  const result = aggregate(roundsTable(
    '| 段階 1 | 網羅性 | 汎用 | 1 | 100 | 1m | 0 | 1 | 0 | 1 |',
    '| 段階 1 | 網羅性 | 汎用 | — | 100 | 1m | 0 | 1 | 0 | 1 |',
    '| 段階 1 | 規則 | 汎用 | — | 100 | 1m | 0 | 1 | 0 | 1 |',
    '| 段階 1 | 重複を除く | — | 1 | — | — | 0 | 1 | 0 | 1 |',
  ))
  // 回 1 は重複を除く行の 1、「?」の組は 2 人の行を合計して 2（警告に数える）
  assert.equal(result.byTime[0].must, 3)
  assert.equal(result.noDedupGroups, 1)
})

test('重複を除く行の回数が数で読めないとき、ファイル名を付けて警告する', () => {
  const text = [HEADER, SEP, '| 段階 1 | 重複を除く | — | — | — | — | 1 | 0 | 0 | 0 |'].join('\n')
  const messages = captureErrors(() => readReviewRows(text, 'c/d.md'))
  assert.equal(messages.length, 1)
  assert.ok(messages[0].includes('c/d.md'))
})

test('観点の列のバッククォートを除いて、重複を除く行を見分ける', () => {
  const rows = roundsTable(
    '| 段階 1 | 網羅性 | 汎用 | 1 | 100 | 1m | 1 | 0 | 0 | 0 |',
    '| 段階 1 | 規則 | 汎用 | 1 | 100 | 1m | 1 | 0 | 0 | 0 |',
    '| 段階 1 | `重複を除く` | — | 1 | — | — | 1 | 0 | 0 | 0 |',
  )
  assert.equal(aggregate(rows).byTime[0].high, 1)
})
