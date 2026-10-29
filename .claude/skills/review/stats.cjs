// アーカイブした spec の tasks（規模 L）と spec.md（規模 M）のレビューの記録の表を、時点ごとに集計する
// 使い方：node .claude/skills/review/stats.cjs --round <弾の名前> --profile <profile の名前> <spec の番号> [<spec の番号> ...]
// 弾と profile は表にないので引数で受ける。結果は標準出力に Markdown の表で出す
// 同じ時点に複数のレビュー役が動いたときは、観点を「重複を除く」とした行の値を使う（なければ行の値を合計し、そのような時点の数を出す）。同じ時点の 2 回目以降は、回数ごとに分ける
const fs = require('fs'), path = require('path')

const COLUMNS = ['時点', '観点', 'モデル', '回数', 'token', '時間', '高', '必須', '推奨', '採用']
const TIME_ORDER = ['段階', 'spec の最後', 'pull request の前', '[リスク] のタスクの後']
// 同じ時点に複数のレビュー役が動いたとき、重複を除いた数を書く行の観点
const DEDUP_VIEWPOINT = '重複を除く'
// 表ごとの番号。同じ表の行だけを同じ時点としてまとめるために、読んだ表に 1 つずつ付ける
let nextTableId = 0

/**
 * アーカイブの下から、番号の spec の表を持ちうる文書を探す。
 * ディレクトリの名前が「<番号>-」で始まるものを使い、規模 L は tasks/*.md、規模 M は spec.md を返す。
 * @param {string} root リポジトリのルート（docs/specs/archive を含むディレクトリ）
 * @param {string} number spec の番号
 * @returns {string[] | null} 文書のパス。番号のディレクトリがなければ null、あっても tasks も spec.md もなければ空の配列
 */
function findSpecFiles(root, number) {
  const dirs = []
  const walk = (dir) => {
    if (!fs.existsSync(dir)) return
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      if (!entry.isDirectory()) continue
      const full = path.join(dir, entry.name)
      if (entry.name.startsWith(`${number}-`)) dirs.push(full)
      else walk(full)
    }
  }
  walk(path.join(root, 'docs', 'specs', 'archive'))
  if (!dirs.length) return null
  const files = []
  for (const dir of dirs) {
    const tasks = path.join(dir, 'tasks')
    if (fs.existsSync(tasks)) {
      for (const f of fs.readdirSync(tasks).sort()) if (f.endsWith('.md')) files.push(path.join(tasks, f))
    }
    const spec = path.join(dir, 'spec.md')
    if (fs.existsSync(spec)) files.push(spec)
  }
  return files
}

/**
 * 数の列を読む。数でなければ 0 とし、読めなかったことを返す。
 * @param {string} cell 列の文字列
 * @returns {{value: number, ok: boolean}} 読んだ値と、数として読めたか
 */
function toNumber(cell) {
  const ok = /^\d+$/.test(cell)
  return { value: ok ? Number(cell) : 0, ok }
}

/**
 * 文書の中の、見出しの行が決まった列の名前と順序の表を読む。
 * 見出しの行に一致する全ての表を読む。行頭の空白（箇条の下の字下げ）は除いて判定する。
 * 区切りの行の後から、「|」で始まらない行までを読む。見出しの行がなければ空の配列を返す。
 * 見出しがあって行が 0 件の表は、ファイル名を付けて標準エラーに警告する。
 * 各行に、読んだ表の番号（table）、観点が「重複を除く」の行か（dedup。バッククォートは除いて比べる）、回数（round。数として読めなければ「?」）を付ける。
 * @param {string} text 文書の全文
 * @param {string} [file] 警告に付けるファイル名
 * @returns {{table: number, dedup: boolean, round: number | string, time: string, token: number, tokenUnreadable: boolean, high: number, must: number, recommended: number, adopted: number}[]} 表の行
 */
function readReviewRows(text, file = '(不明)') {
  const lines = text.split(/\r?\n/).map((l) => l.trimStart())
  const cellsOf = (line) => line.split('|').slice(1, -1).map((c) => c.trim())
  const rows = []
  for (let start = 0; start < lines.length; start++) {
    const header = cellsOf(lines[start])
    if (!lines[start].startsWith('|') || header.length !== COLUMNS.length || !header.every((c, i) => c === COLUMNS[i])) continue
    const tableId = nextTableId++
    let count = 0
    let i = start + 2
    for (; i < lines.length; i++) {
      const line = lines[i]
      if (!line.startsWith('|')) break
      const cells = cellsOf(line)
      if (cells.length < COLUMNS.length) continue
      const token = toNumber(cells[4])
      rows.push({
        table: tableId,
        dedup: cells[1].replaceAll('`', '') === DEDUP_VIEWPOINT,
        round: /^\d+$/.test(cells[3]) ? Number(cells[3]) : '?',
        time: cells[0],
        token: token.value,
        tokenUnreadable: !token.ok,
        high: toNumber(cells[6]).value,
        must: toNumber(cells[7]).value,
        recommended: toNumber(cells[8]).value,
        adopted: toNumber(cells[9]).value,
      })
      count += 1
    }
    if (count === 0) console.error(`警告：${file} に見出しだけで行が 0 件の表がある`)
    start = i - 1
  }
  return rows
}

/**
 * 採用の割合を百分率で書く。
 * @param {number} adopted 採用の数
 * @param {number} found 指摘の数（高＋必須＋推奨）
 * @returns {string} 小数 1 桁の百分率。指摘が 0 なら「—」
 */
function rate(adopted, found) {
  return found === 0 ? '—' : `${((adopted / found) * 100).toFixed(1)}%`
}

/** 集計の入れ物を作る。 */
function emptySum(time) {
  return { time, rows: 0, token: 0, high: 0, must: 0, recommended: 0, adopted: 0, rate: '—' }
}

/**
 * 行を時点ごとに集計する。「段階 n」は、n ごとに分けて集計したあとで「段階」にまとめる。
 * 表・時点・回数の組ごとにまとめ、その組に観点「重複を除く」の行があれば、高・必須・推奨・採用は、その行の値を使い、他の行の値は合計しない。
 * 回数が数として読めない行は、回数「?」の組にまとめる（読める回とは混ぜない）。
 * 行の数と token は、「重複を除く」の行を数えず、合計しない。
 * 「重複を除く」の行がなく、行が 2 つ以上ある組は、重複して数えているおそれがあるので、そのような時点の数を返す。
 * @param {ReturnType<typeof readReviewRows>} rows 表の行
 * @returns {{byTime: ReturnType<typeof emptySum>[], total: ReturnType<typeof emptySum>, tokenUnreadable: number, noDedupTimes: number}} 時点ごとの合計、全体の合計、token が読めない行の数、重複を除く行がない時点の数
 */
function aggregate(rows) {
  // 表・時点（段階 n は n ごと）・回数の組ごとに行を集める
  const groups = new Map()
  for (const r of rows) {
    const name = r.time.replaceAll('`', '')
    const key = `${r.table}|${name}|${r.round}`
    if (!groups.has(key)) groups.set(key, { name, rows: [], dedups: [] })
    const group = groups.get(key)
    if (r.dedup) group.dedups.push(r)
    else group.rows.push(r)
  }
  const map = new Map()
  const total = emptySum('合計')
  let tokenUnreadable = 0
  let noDedupTimes = 0
  for (const { name, rows: reviewers, dedups } of groups.values()) {
    const time = /^段階 \d+$/.test(name) ? '段階' : name
    if (!map.has(time)) map.set(time, emptySum(time))
    if (!dedups.length && reviewers.length > 1) noDedupTimes += 1
    const counted = dedups.length ? dedups : reviewers
    for (const sum of [map.get(time), total]) {
      sum.rows += reviewers.length
      for (const r of reviewers) sum.token += r.token
      for (const r of counted) {
        sum.high += r.high
        sum.must += r.must
        sum.recommended += r.recommended
        sum.adopted += r.adopted
      }
    }
    for (const r of reviewers) if (r.tokenUnreadable) tokenUnreadable += 1
  }
  const rank = (t) => (TIME_ORDER.includes(t) ? TIME_ORDER.indexOf(t) : TIME_ORDER.length)
  const byTime = [...map.values()].sort((a, b) => rank(a.time) - rank(b.time))
  for (const sum of [...byTime, total]) sum.rate = rate(sum.adopted, sum.high + sum.must + sum.recommended)
  return { byTime, total, tokenUnreadable, noDedupTimes }
}

/**
 * 集計を Markdown にする。
 * @param {{round: string, profile: string, specs: string[]}} meta 弾、profile、対象の spec の番号
 * @param {ReturnType<typeof aggregate>} result 集計の結果
 * @returns {string} Markdown
 */
function formatReport(meta, result) {
  const line = (s) => `| ${[s.time, s.rows, s.token, s.high, s.must, s.recommended, s.adopted, s.rate].join(' | ')} |`
  return [
    `弾：${meta.round}`,
    `profile：${meta.profile}`,
    `spec：${meta.specs.join(', ')}`,
    '',
    '| 時点 | 行の数 | token | 高 | 必須 | 推奨 | 採用 | 採用の割合 |',
    '|------|--------|-------|----|------|------|------|------------|',
    ...result.byTime.map(line),
    line(result.total),
    '',
    `token が読めない行の数：${result.tokenUnreadable}`,
    `重複を除く行がない時点の数：${result.noDedupTimes}`,
  ].join('\n')
}

/** 引数を読み、spec を集計して標準出力に出す。見つからない番号があれば標準エラーに出して異常終了する。 */
function main() {
  const args = process.argv.slice(2)
  let round, profile
  const specs = []
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--round') round = args[++i]
    else if (args[i] === '--profile') profile = args[++i]
    else specs.push(args[i])
  }
  if (!round || !profile || !specs.length) {
    console.error('使い方：node .claude/skills/review/stats.cjs --round <弾の名前> --profile <profile の名前> <spec の番号> [...]')
    process.exit(1)
  }
  const root = process.cwd()
  const rows = []
  const missing = []
  for (const number of specs) {
    const files = findSpecFiles(root, number)
    if (!files || !files.length) missing.push(number)
    else for (const f of files) rows.push(...readReviewRows(fs.readFileSync(f, 'utf8'), f))
  }
  if (missing.length) {
    console.error(`アーカイブに見つからない、または tasks と spec.md がない spec の番号：${missing.join(', ')}`)
    process.exit(1)
  }
  console.log(formatReport({ round, profile, specs }, aggregate(rows)))
}

module.exports = { findSpecFiles, readReviewRows, aggregate, formatReport }

if (require.main === module) main()
