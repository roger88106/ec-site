// ============================================================================
// カバレッジの基準の検査：今回の値が、基準の値（から許容の幅を引いた値）を下回ったら失敗にする
// （テストの書き方 第 1.2 節）。カバレッジが基準の値（許容の幅を含む）を下回らないことを、CI で守る。
// 基準は、リポジトリのファイルに置く。正当な理由で下げるときは、--write で書き換え、pull request に理由を書く
//
//   使い方：node scripts/ci/report/coverage-baseline.mjs <対象> <報告のファイル> [--write] [--baseline <基準のファイル>]
//     対象：基準のファイルの targets の名前（backend、admin、store、shared、backend-domain）
//     報告のファイル：JaCoCo の XML か、Vitest の json-summary（coverage-summary.json）か、PIT の XML（mutations.xml）。
//       JaCoCo と PIT の XML は、中身（PIT は <mutations>）で見分ける。対象の基準の指標（line と branch、または mutation）と合わない報告は失敗にする
//     指標：line（行）と branch（分岐）はカバレッジ、mutation は mutation score（検出した変異 ÷ 全変異。mutation-report.mjs）
//     --write：基準のファイルの、その対象の値を、報告の値で書き換える（上げるときも下げるときも）。許容の幅は変えない
//     --baseline：基準のファイル（既定は quality-baseline.json）。CI と手元は同じ引数で流せる
//
// 結果：
//   - 下回る：対象、基準、今回、差、直し方を出して、終了コード 1
//   - 同じ：成功
//   - 上回る：成功にして、基準の更新を促す警告（「警告：」で始まる行）を出す
//   - 報告がない、基準のファイルの誤り（対象がない、値の形が違う）：失敗（終了コード 1）
//   - 引数の誤り（対象か報告のファイルがない、余分な引数、--baseline の値がない）：使い方を出して、終了コード 2
// 結果は標準出力・標準エラーに出す。GITHUB_STEP_SUMMARY があれば、summary にも書く。
// ============================================================================
import { appendFileSync, existsSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { readTotals } from './coverage-summary.mjs'
import { isPitReport, readMutations, SURVIVORS_LIMIT } from './mutation-report.mjs'
import { isMain } from '../lib/is-main.mjs'

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url))

/** リポジトリのルート（この script の 3 つ上）。直し方のコマンドのパスの基準 */
const ROOT = path.resolve(SCRIPT_DIR, '../../..')

/** 基準のファイルの既定の場所（この script と同じディレクトリ） */
const DEFAULT_BASELINE = path.join(SCRIPT_DIR, 'quality-baseline.json')

/** 指標の表示名 */
const METRIC_LABELS = { line: '行', branch: '分岐', mutation: 'mutation score' }

/** 基準の対象の指標。mutation を持つ対象は mutation だけ、ほかは行と分岐 */
const metricsOf = (entry) => (entry && 'mutation' in entry ? ['mutation'] : ['line', 'branch'])

/** 指標の値の名前（メッセージの「カバレッジ」「mutation score」） */
const subjectOf = (metrics) => (metrics[0] === 'mutation' ? 'mutation score' : 'カバレッジ')

/**
 * 割合を、小数第 2 位に丸めて返す。全体が 0 のときは 100（足りないものがない）。
 *
 * @param {number} covered 実行した数
 * @param {number} total 全体の数
 * @returns {number} 割合（%）
 */
export function percent2(covered, total) {
  return total === 0 ? 100 : Math.round((covered / total) * 10000) / 100
}

/** 小数第 2 位までの値を、浮動小数点の誤差なしで比べるために、100 倍の整数にする */
const hundredths = (value) => Math.round(value * 100)

/**
 * 基準のファイルを読み、形を確認する。
 *
 * @param {string} text 基準のファイルの中身
 * @returns {{ targets: Record<string, Record<string, { value: number, tolerance: number }>> }} 基準
 * @throws {Error} JSON でない、targets がない、値か許容の幅の形が違うとき
 */
export function parseBaseline(text) {
  let baseline
  try {
    baseline = JSON.parse(text)
  } catch (error) {
    throw new Error(`基準のファイルが JSON ではありません（${error.message}）`)
  }
  if (typeof baseline?.targets !== 'object' || baseline.targets === null) {
    throw new Error('基準のファイルに targets がありません')
  }
  for (const [name, entry] of Object.entries(baseline.targets)) {
    for (const metric of metricsOf(entry)) {
      const cell = entry?.[metric]
      const valid = (n) => typeof n === 'number' && Number.isFinite(n)
      if (!valid(cell?.value) || !valid(cell?.tolerance) || cell.value < 0 || cell.value > 100 || cell.tolerance < 0) {
        throw new Error(`基準のファイルの ${name}.${metric} は、{ "value": 0〜100 の数, "tolerance": 0 以上の数 } の形ではありません`)
      }
    }
  }
  return baseline
}

/**
 * 今回の値を、基準と比べる。
 *
 * @param {Record<string, { value: number, tolerance: number }>} entry 対象の基準（指標ごとの値と許容の幅）
 * @param {Record<string, number>} actual 今回の値（指標ごとの割合）
 * @returns {{ failures: { metric: string, baseline: number, actual: number, diff: number }[], warnings: { metric: string, baseline: number, actual: number, diff: number }[] }}
 *   failures：基準から許容の幅を引いた値より低いもの。warnings：基準より高いもの。diff は今回 − 基準
 */
export function compare(entry, actual) {
  const failures = []
  const warnings = []
  for (const metric of Object.keys(actual)) {
    const { value, tolerance } = entry[metric]
    const row = { metric, baseline: value, actual: actual[metric], diff: Math.round((actual[metric] - value) * 100) / 100 }
    if (hundredths(actual[metric]) < hundredths(value) - hundredths(tolerance)) failures.push(row)
    else if (hundredths(actual[metric]) > hundredths(value)) warnings.push(row)
  }
  return { failures, warnings }
}

/** 差を、符号付きで小数第 2 位まで表示する（上回るときは +） */
const signed = (diff) => `${diff > 0 ? '+' : ''}${diff.toFixed(2)}`

/**
 * 基準を書き換えるコマンドを返す。どこで流してもよいように、script と報告をリポジトリのルートからのパスにする。
 *
 * @param {string} name 対象
 * @param {string} file 報告のファイル（流した場所からのパス）
 * @returns {string} ルートで流すコマンド
 */
function writeCommand(name, file) {
  const report = path.relative(ROOT, path.resolve(file)).replaceAll('\\', '/')
  return `node scripts/ci/report/coverage-baseline.mjs ${name} ${report} --write`
}

/** 結果を出す。標準出力・標準エラーに出し、GITHUB_STEP_SUMMARY があれば summary にも書く */
function report({ ok, lines, markdown }) {
  for (const line of lines) (ok ? console.log : console.error)(line)
  if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${markdown}\n`)
}

/** 失敗したことを出して、終了する */
function fail(title, message) {
  report({ ok: false, lines: [`${title}：${message}`], markdown: `### 基準の検査（${title}）\n\n失敗：${message}\n` })
  process.exit(1)
}

/** 引数を読む */
function parseArgs(argv) {
  const positional = []
  let write = false
  let baselineFile = DEFAULT_BASELINE
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--write') write = true
    else if (argv[i] === '--baseline') baselineFile = argv[++i]
    else positional.push(argv[i])
  }
  return { name: positional[0], file: positional[1], extra: positional.length > 2, write, baselineFile }
}

/** 報告を読み、基準と比べる（--write なら書き換える） */
function main() {
  const { name, file, extra, write, baselineFile } = parseArgs(process.argv.slice(2))
  if (!name || !file || extra || !baselineFile) {
    console.error('使い方：node scripts/ci/report/coverage-baseline.mjs <対象> <報告のファイル> [--write] [--baseline <基準のファイル>]')
    process.exit(2)
  }
  if (!existsSync(file)) fail(name, `報告がありません（${file}）。報告を作るテストが失敗していないか確認する。`)
  if (!existsSync(baselineFile)) fail(name, `基準のファイルがありません（${baselineFile}）。`)

  let baseline
  try {
    baseline = parseBaseline(readFileSync(baselineFile, 'utf8'))
  } catch (error) {
    fail(name, error.message)
  }
  const entry = baseline.targets[name]
  if (!entry) fail(name, `基準のファイルに対象「${name}」がありません（あるもの：${Object.keys(baseline.targets).join('、')}）。`)

  const text = readFileSync(file, 'utf8')
  const pit = isPitReport(text)
  const metrics = metricsOf(entry)
  const subject = subjectOf(metrics)
  if (pit && metrics[0] !== 'mutation') {
    fail(name, `対象「${name}」の基準は行と分岐のカバレッジだが、報告は PIT の XML（mutation score。${file}）。対象か報告を確認する。`)
  }
  if (!pit && metrics[0] === 'mutation') {
    fail(name, `対象「${name}」の基準は mutation score だが、報告は PIT の XML ではない（JaCoCo か Vitest の報告。${file}）。対象か報告を確認する。`)
  }

  let actual
  let detail = ''
  /** 生き残った変異が多いクラス（PIT のとき）*/
  let survivors = []
  if (pit) {
    let result
    try {
      result = readMutations(text)
    } catch (error) {
      fail(name, `${error.message}（${file}）。`)
    }
    // 変異が 0 の報告は、中身がない・壊れているので、100% とみなさず失敗にする
    if (result.total === 0) fail(name, `報告に変異がない（${file}）。PIT が対象のクラスを見つけているか確認する。`)
    actual = { mutation: percent2(result.detected, result.total) }
    survivors = result.survivors
    detail = `検出 ${result.detected} / 変異 ${result.total}`
  } else {
    const totals = readTotals(text, file.endsWith('.xml') ? 'xml' : 'json')
    // 行の全体が 0 の報告は、中身がない・壊れている（全体の counter がない XML を含む）ので、100% とみなさず失敗にする
    if (totals.lines.total === 0) fail(name, `報告に行がない（${file}）。報告の作り方を確認する。`)
    actual = {
      line: percent2(totals.lines.covered, totals.lines.total),
      branch: percent2(totals.branches.covered, totals.branches.total),
    }
  }
  const values = metrics.map((metric) => `${METRIC_LABELS[metric]} ${actual[metric].toFixed(2)}%`).join('、')

  if (write) {
    for (const metric of metrics) entry[metric].value = actual[metric]
    writeFileSync(baselineFile, `${JSON.stringify(baseline, null, 2)}\n`)
    const done = `基準（${name}）を書き換えました：${values}。基準のファイルを同じ pull request に含める。`
    report({ ok: true, lines: [done], markdown: `### 基準の検査（${name}）\n\n${done}\n` })
    return
  }

  // 生き残った変異が多いクラス。成功のときも失敗のときも出す（テストを追加する所の手がかり）
  const survivorLines = survivors.length === 0 ? [] : [
    `生き残った変異（テストが検出できなかった変異）が多いクラス（上位 ${SURVIVORS_LIMIT} 件）：`,
    ...survivors.map((row) => `  ${row.name}  ${row.survived} / ${row.total}`),
  ]
  const survivorMarkdown = survivors.length === 0 ? [] : [
    `生き残った変異（テストが検出できなかった変異）が多いクラス（上位 ${SURVIVORS_LIMIT} 件）：`,
    '',
    '| クラス | 生き残った変異 | 変異 |',
    '|--------|----------------|------|',
    ...survivors.map((row) => `| \`${row.name}\` | ${row.survived} | ${row.total} |`),
    '',
  ]

  const { failures, warnings } = compare(entry, actual)
  const label = (row) => METRIC_LABELS[row.metric]
  if (failures.length > 0) {
    const remedy = pit
      ? '直し方：生き残った変異を検出できるように、テストの検証を追加する。'
      : '直し方：テストを追加して、カバレッジを基準まで戻す。'
    const lines = [
      `${subject}${pit ? ' ' : ''}が基準を下回りました（${name}）。`,
      ...failures.map(
        (row) =>
          `  ${label(row)}：基準 ${row.baseline.toFixed(2)}%（許容の幅 ${entry[row.metric].tolerance}）、今回 ${row.actual.toFixed(2)}%、差 ${signed(row.diff)} ポイント`,
      ),
      remedy,
      `正当な理由で下げるときは、${writeCommand(name, file)} で基準のファイルを書き換え、pull request に理由を書く。`,
      ...survivorLines,
    ]
    const markdown = [
      `### 基準の検査（${name}）：失敗`,
      '',
      '| 種類 | 基準 | 許容の幅 | 今回 | 差 |',
      '|------|------|----------|------|----|',
      ...failures.map(
        (row) =>
          `| ${label(row)} | ${row.baseline.toFixed(2)}% | ${entry[row.metric].tolerance} | ${row.actual.toFixed(2)}% | ${signed(row.diff)} |`,
      ),
      '',
      remedy,
      '',
      `正当な理由で下げるときは、\`${writeCommand(name, file)}\` で基準のファイルを書き換え、pull request に理由を書く。`,
      '',
      ...survivorMarkdown,
    ].join('\n')
    report({ ok: false, lines, markdown })
    process.exit(1)
  }

  const lines = [`${subject}${pit ? ' ' : ''}は基準以上です（${name}）：${values}${detail ? `（${detail}）` : ''}。`]
  const markdown = [`### 基準の検査（${name}）：成功`, '', ...lines, '']
  for (const row of warnings) {
    const warning = `警告：${name} の${pit ? ' ' : ''}${label(row)}${pit ? '' : 'のカバレッジ'}${pit ? ' ' : ''}が基準より高い（基準 ${row.baseline.toFixed(2)}%、今回 ${row.actual.toFixed(2)}%、差 ${signed(row.diff)} ポイント）。--write で基準のファイルを更新して、pull request に含める。`
    lines.push(warning)
    markdown.push(warning, '')
  }
  lines.push(...survivorLines)
  markdown.push(...survivorMarkdown)
  report({ ok: true, lines, markdown: markdown.join('\n') })
}

if (isMain(import.meta.url)) {
  main()
}
