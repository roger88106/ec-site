// 文書の Mermaid の図（```mermaid、~~~mermaid のコードブロック）が、mermaid-cli で描けることを確認する（判定の区分：誤りの防止。構文の誤りの検査）。
// 目的：GitHub は構文の誤りのある図を「Unable to render」と表示するだけで、push して画面を見るまで気づけないため。
// 見る文書：docs/ の下の .md。アーカイブ（docs/08-spec/archive/）は見ない。
// 図の読み取り：コードブロックの読み方は lib/code-block を使う（CommonMark と同じ。HTML のコメントの中、他のコードブロックの中は読まない）。
//   閉じていない mermaid のコードブロックは失敗にする。箇条書き・引用・HTML のブロック・front matter の中の図は対象にしない（使っていないため）。
// 描画：ファイルごとに 1 回、そのファイルの図だけを並べた一時の Markdown を mermaid-cli に渡す。失敗したファイルだけ、図ごとに再描画して、失敗した図の行（元のファイルの行）を示す。
//   描画のプロセスが失敗したら、エラーの出力が空でも失敗にする。
// mermaid-cli は依存に入れず、バージョンを固定して npx で取得する。ブラウザーは取得せず（PUPPETEER_SKIP_DOWNLOAD）、手元の Chrome を使う。
// Chrome の場所は環境変数 MERMAID_CHROME。なければ既定の場所を順に探す（Windows の Chrome、google-chrome、chromium）。
//   見つからなければ、作業のディレクトリを作る前に、理由を出して終了コード 2 で終わる。
// script を scripts/ci/ の直下に置かない理由：直下の script は check-all.sh と commit の前の hook が全て実行するため。描画に Chrome とネットワークが必要で遅い。実行するのは CI の文書のワークフローだけ。
// 失敗したとき：示された文書と行の図の構文を修正する（mermaid-cli の誤りの文に、崩れた所の前後が表示される）。閉じていないコードブロックは、閉じの行を追加する。
// 除外：なし
// 使い方：node scripts/ci/mermaid/mermaid-check.mjs（リポジトリのルートで）
import { execFileSync, execSync } from 'node:child_process'
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { readCodeBlocks } from '../lib/code-block.mjs'
import { exitIfNoFiles, listGitFiles } from '../lib/git-files.mjs'
import { isMain } from '../lib/is-main.mjs'

/** 使う mermaid-cli のバージョン（上げるときは、GitHub での表示と食い違わないかを確認する） */
const MERMAID_CLI = '@mermaid-js/mermaid-cli@12.0.0'

/** Chrome を探す既定の場所（環境変数 MERMAID_CHROME がないとき、この順に探す） */
const CHROME_CANDIDATES = [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
]

/**
 * 見る文書かを返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} docs/ の下の .md で、アーカイブでなければ true
 */
export function isMermaidTargetDoc(file) {
  return /^docs\/.*\.md$/.test(file) && !file.startsWith('docs/08-spec/archive/')
}

/**
 * 文書の中の Mermaid の図を拾う。
 *
 * @param {string} text 文書の内容
 * @returns {{ diagrams: { line: number, source: string }[], unclosed: number[] }}
 *   diagrams は閉じた図の開きの行（1 から）と本文、unclosed は閉じていない図の開きの行
 */
export function extractMermaidBlocks(text) {
  const diagrams = []
  const unclosed = []
  for (const block of readCodeBlocks(text)) {
    if (block.info.split(/\s+/)[0].toLowerCase() !== 'mermaid') continue
    if (block.closed) diagrams.push({ line: block.line, source: block.body.join('\n') })
    else unclosed.push(block.line)
  }
  return { diagrams, unclosed }
}

/**
 * 図の本文を並べた、描画の入力の Markdown を作る。
 *
 * @param {string[]} sources 図の本文
 * @returns {string} 図ごとに mermaid のコードブロックにした Markdown。本文の中のバッククォートの並びより長い囲みを使う
 */
export function toMarkdown(sources) {
  return sources
    .map((source) => {
      const longest = Math.max(0, ...(source.match(/`+/g) ?? []).map((run) => run.length))
      const fence = '`'.repeat(Math.max(3, longest + 1))
      return `${fence}mermaid\n${source}\n${fence}\n`
    })
    .join('\n')
}

/**
 * 描画の失敗を、空でない文字列にする（エラーの出力が空でも、失敗を成功にしない）。
 *
 * @param {{ stderr?: unknown, message?: unknown }} error 描画のプロセスの失敗
 * @returns {string} 誤りの文（スタックの行を除いた先頭の 4 行）。出力が空なら代わりの文
 */
export function describeFailure(error) {
  const lines = `${error?.stderr ?? ''}`
    .split(/\r?\n/)
    .filter((line) => line.trim() && !line.trim().startsWith('at '))
    .slice(0, 4)
  return lines.join('\n') || `${error?.message ?? ''}`.trim() || '原因の分からない失敗（エラーの出力がない）'
}

/**
 * ファイルごとに図を描画して、描けない図を集める。
 *
 * @param {{ file: string, text: string }[]} docs 文書
 * @param {(markdown: string) => string | null} render 描画する関数。描けたら null、失敗したら空でない誤りの文を返す
 * @returns {{ problems: { file: string, line: number, message: string }[], count: number }}
 *   problems は描けない図と閉じていない図（ファイル・開きの行・理由）、count は図の数（閉じていない図を含む）
 */
export function checkDocs(docs, render) {
  const problems = []
  let count = 0
  for (const { file, text } of docs) {
    const { diagrams, unclosed } = extractMermaidBlocks(text)
    count += diagrams.length + unclosed.length
    const found = unclosed.map((line) => ({ file, line, message: '閉じていないコードブロックがある（閉じの行が足りない）' }))
    if (diagrams.length > 0) {
      const whole = render(toMarkdown(diagrams.map((diagram) => diagram.source)))
      if (whole !== null) {
        const each = diagrams.flatMap((diagram) => {
          const message = render(toMarkdown([diagram.source]))
          return message === null ? [] : [{ file, line: diagram.line, message }]
        })
        found.push(...(each.length > 0 ? each : [{ file, line: diagrams[0].line, message: whole }]))
      }
    }
    problems.push(...found.sort((a, b) => a.line - b.line))
  }
  return { problems, count }
}

/**
 * 最後の行の要約を返す。
 *
 * @param {number} failed 描けない図の数
 * @param {number} count 図の数
 * @returns {string} 要約の行
 */
export function summaryLine(failed, count) {
  return `描画できない Mermaid の図：${failed}（図の数 ${count}）`
}

/**
 * 使う Chrome の場所を返す。
 *
 * @param {Record<string, string | undefined>} env 環境変数
 * @param {(candidate: string) => boolean} exists ファイルがあるかを返す関数
 * @returns {string | null} 実行ファイルのパス。見つからなければ null（環境変数の場所がないときは、既定の場所に替えない）
 */
export function findChrome(env, exists) {
  if (env.MERMAID_CHROME) return exists(env.MERMAID_CHROME) ? env.MERMAID_CHROME : null
  return CHROME_CANDIDATES.find((candidate) => exists(candidate)) ?? null
}

/**
 * 引数を引用符で囲む（shell を通す起動で、空白のあるパスが 1 つの引数になるように）。
 *
 * @param {string} arg 引数
 * @returns {string} 引用符で囲んだ引数
 */
export function quoteArg(arg) {
  return `"${arg}"`
}

/**
 * shell を通して起動する命令の文字列を作る。
 *
 * @param {string} command 命令
 * @param {string[]} args 引数
 * @returns {string} 全ての引数を引用符で囲んだ命令
 */
export function buildCommand(command, args) {
  return [command, ...args.map(quoteArg)].join(' ')
}

/**
 * mermaid-cli で Markdown の図を描画する関数を作る。
 *
 * @param {string} work 作業のディレクトリ
 * @param {string} config puppeteer の設定のファイル
 * @returns {(markdown: string) => string | null} 描けたら null、失敗したら空でない誤りの文を返す
 */
function createRenderer(work, config) {
  let serial = 0
  return (markdown) => {
    serial++
    const input = path.join(work, `in-${serial}.md`)
    const output = path.join(work, `out-${serial}.md`)
    writeFileSync(input, markdown)
    const args = ['--yes', MERMAID_CLI, '-q', '-p', config, '-i', input, '-o', output]
    const options = { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], env: { ...process.env, PUPPETEER_SKIP_DOWNLOAD: '1' } }
    try {
      // Windows の npx は npx.cmd で、shell を通さないと起動できない。引用符で囲んだ 1 つの命令にして、空白のあるパスを守る
      if (process.platform === 'win32') execSync(buildCommand('npx', args), options)
      else execFileSync('npx', args, options)
      return null
    } catch (error) {
      return describeFailure(error)
    }
  }
}

function main() {
  const chrome = findChrome(process.env, existsSync)
  if (chrome === null) {
    console.error('Chrome が見つからない。環境変数 MERMAID_CHROME に実行ファイルのパスを入れる（既定の場所にもない）。')
    process.exit(2)
  }
  const files = listGitFiles(['docs']).filter(isMermaidTargetDoc)
  exitIfNoFiles(files, 'docs/ の文書')
  const docs = files.map((file) => ({ file, text: readFileSync(file, 'utf8') }))
  const work = mkdtempSync(path.join(tmpdir(), 'mermaid-check-'))
  let result
  try {
    const config = path.join(work, 'puppeteer.json')
    // CI（Linux）では、サンドボックスを使えないことがある
    writeFileSync(config, JSON.stringify({ executablePath: chrome, args: ['--no-sandbox'] }))
    result = checkDocs(docs, createRenderer(work, config))
  } finally {
    rmSync(work, { recursive: true, force: true })
  }
  for (const problem of result.problems) console.error(`${problem.file}:${problem.line}\n${problem.message}`)
  console.log(summaryLine(result.problems.length, result.count))
  if (result.problems.length > 0) process.exit(1)
}

if (isMain(import.meta.url)) main()
