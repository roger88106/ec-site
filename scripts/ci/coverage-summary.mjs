// ============================================================================
// カバレッジの合計（行・分岐）を、GitHub Actions の job の summary に書く（constitution 12.1）
//
//   使い方：node scripts/ci/coverage-summary.mjs <名前> <報告のファイル>
//     報告のファイル：JaCoCo の XML（*.xml）か、Vitest の json-summary（coverage-summary.json）
//
// 報告だけで、基準の値では失敗させない。報告がなければ、そのことを書いて終わる。
// GITHUB_STEP_SUMMARY がなければ（ローカル）、標準出力に書く。
// ============================================================================
import { appendFileSync, existsSync, readFileSync } from 'node:fs'

const [name, file] = process.argv.slice(2)
if (!name || !file) {
  console.error('使い方：node scripts/ci/coverage-summary.mjs <名前> <報告のファイル>')
  process.exit(2)
}

/** 割合の表示（小数第 1 位） */
const percent = (covered, total) => (total === 0 ? '-' : `${((covered / total) * 100).toFixed(1)}%`)

/**
 * 報告から、行と分岐の合計を読む。
 *
 * @param path 報告のファイル
 * @returns 行と分岐の、実行した数と全体の数
 */
function readTotals(path) {
  const text = readFileSync(path, 'utf8')
  if (path.endsWith('.xml')) {
    // 報告の全体の counter は、最後の package の後（</report> の直前）に並ぶ
    const tail = text.slice(text.lastIndexOf('</package>'))
    const counter = (type) => {
      const match = tail.match(new RegExp(`<counter type="${type}" missed="([0-9]+)" covered="([0-9]+)"/>`))
      const [missed, covered] = match ? [Number(match[1]), Number(match[2])] : [0, 0]
      return { covered, total: missed + covered }
    }
    return { lines: counter('LINE'), branches: counter('BRANCH') }
  }
  const { total } = JSON.parse(text)
  return { lines: total.lines, branches: total.branches }
}

const output = (markdown) => {
  if (process.env.GITHUB_STEP_SUMMARY) {
    appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${markdown}\n`)
  } else {
    console.log(markdown)
  }
}

if (!existsSync(file)) {
  output(`### カバレッジ（${name}）\n\n報告がありません（${file}）。\n`)
  process.exit(0)
}

const { lines, branches } = readTotals(file)
output(
  [
    `### カバレッジ（${name}）`,
    '',
    '| 種類 | 割合 | 実行した数 / 全体 |',
    '|------|------|-------------------|',
    `| 行 | ${percent(lines.covered, lines.total)} | ${lines.covered} / ${lines.total} |`,
    `| 分岐 | ${percent(branches.covered, branches.total)} | ${branches.covered} / ${branches.total} |`,
    '',
  ].join('\n'),
)
