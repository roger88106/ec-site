// ============================================================================
// カバレッジの合計（行・分岐）と、行のカバレッジが低い所を、GitHub Actions の job の summary に書く
// （テストの書き方の「カバレッジと mutation score の基準」）
//
//   使い方：node scripts/ci/report/coverage-summary.mjs <名前> <報告のファイル>
//     報告のファイル：JaCoCo の XML（*.xml）か、Vitest の json-summary（coverage-summary.json）
//
// 低い所は、JaCoCo はパッケージ、Vitest はファイル（流した場所からのパス）の単位で、低い順に 10 件を出す。
// この script は報告を書くだけで、失敗にしない。基準の検査は coverage-baseline.mjs が行う。報告がなければ、そのことを書いて終わる。
// GITHUB_STEP_SUMMARY がなければ（ローカル）、標準出力に書く。
// ============================================================================
import { appendFileSync, existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { isMain } from '../lib/is-main.mjs'

/** 低い所を出す数 */
const LOWEST_LIMIT = 10

/** 割合の表示（小数第 1 位） */
const percent = (covered, total) => (total === 0 ? '-' : `${((covered / total) * 100).toFixed(1)}%`)

/**
 * JaCoCo の XML の一部から、counter を読む。
 *
 * @param {string} text XML の一部（同じ種類の counter が複数あれば、最後のもの）
 * @param {string} type counter の種類（LINE、BRANCH）
 * @returns {{ covered: number, total: number }} 実行した数と全体の数
 */
function counter(text, type) {
  const match = [...text.matchAll(new RegExp(`<counter type="${type}" missed="([0-9]+)" covered="([0-9]+)"/>`, 'g'))].at(-1)
  const [missed, covered] = match ? [Number(match[1]), Number(match[2])] : [0, 0]
  return { covered, total: missed + covered }
}

/**
 * 報告から、行と分岐の合計を読む。
 *
 * @param {string} text 報告の中身
 * @param {'xml' | 'json'} kind 報告の種類（JaCoCo の XML、Vitest の json-summary）
 * @returns 行と分岐の、実行した数と全体の数
 */
export function readTotals(text, kind) {
  if (kind === 'xml') {
    // 報告の全体の counter は、最後の package の後（</report> の直前）に並ぶ
    const tail = text.slice(text.lastIndexOf('</package>'))
    return { lines: counter(tail, 'LINE'), branches: counter(tail, 'BRANCH') }
  }
  const { total } = JSON.parse(text)
  return { lines: total.lines, branches: total.branches }
}

/**
 * 行のカバレッジが低い順に、パッケージ（JaCoCo）かファイル（Vitest）を返す。行のないものは除く。
 *
 * @param {string} text 報告の中身
 * @param {'xml' | 'json'} kind 報告の種類
 * @param {{ limit?: number, root?: string }} options 出す数と、Vitest のファイルのパスの基準
 * @returns {{ name: string, covered: number, total: number }[]} 低い順。同じ割合なら行の多い順
 */
export function lowest(text, kind, { limit = LOWEST_LIMIT, root = '' } = {}) {
  const rows =
    kind === 'xml'
      ? [...text.matchAll(/<package name="([^"]+)">([\s\S]*?)<\/package>/g)].map(([, name, body]) => {
          // パッケージの counter は、最後の sourcefile の後に並ぶ
          const tail = body.slice(body.lastIndexOf('</sourcefile>') + 1)
          return { name, ...(body.includes('</sourcefile>') ? counter(tail, 'LINE') : { covered: 0, total: 0 }) }
        })
      : Object.entries(JSON.parse(text))
          .filter(([file]) => file !== 'total')
          .map(([file, summary]) => ({
            name: (root ? path.relative(root, file) : file).replaceAll('\\', '/'),
            covered: summary.lines.covered,
            total: summary.lines.total,
          }))
  return rows
    .filter((row) => row.total > 0)
    .sort((a, b) => a.covered / a.total - b.covered / b.total || b.total - a.total)
    .slice(0, limit)
}

const output = (markdown) => {
  if (process.env.GITHUB_STEP_SUMMARY) {
    appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${markdown}\n`)
  } else {
    console.log(markdown)
  }
}

/** 報告を読んで、summary に書く */
function main() {
  const [name, file] = process.argv.slice(2)
  if (!name || !file) {
    console.error('使い方：node scripts/ci/report/coverage-summary.mjs <名前> <報告のファイル>')
    process.exit(2)
  }
  if (!existsSync(file)) {
    output(`### カバレッジ（${name}）\n\n報告がありません（${file}）。\n`)
    return
  }

  const text = readFileSync(file, 'utf8')
  const kind = file.endsWith('.xml') ? 'xml' : 'json'
  const { lines, branches } = readTotals(text, kind)
  const unit = kind === 'xml' ? 'パッケージ' : 'ファイル'
  output(
    [
      `### カバレッジ（${name}）`,
      '',
      '| 種類 | 割合 | 実行した数 / 全体 |',
      '|------|------|-------------------|',
      `| 行 | ${percent(lines.covered, lines.total)} | ${lines.covered} / ${lines.total} |`,
      `| 分岐 | ${percent(branches.covered, branches.total)} | ${branches.covered} / ${branches.total} |`,
      '',
      `行のカバレッジが低い${unit}（低い順に ${LOWEST_LIMIT} 件）：`,
      '',
      `| ${unit} | 行 | 実行した数 / 全体 |`,
      '|------|------|-------------------|',
      ...lowest(text, kind, { root: process.cwd() }).map(
        (row) => `| \`${row.name}\` | ${percent(row.covered, row.total)} | ${row.covered} / ${row.total} |`,
      ),
      '',
    ].join('\n'),
  )
}

if (isMain(import.meta.url)) {
  main()
}
