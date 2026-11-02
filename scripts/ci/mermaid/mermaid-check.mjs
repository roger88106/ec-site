// 文書の Mermaid の図（```mermaid、~~~mermaid のコードブロック）が、mermaid-cli で描画できることを確認する（判定の区分：誤りの防止。構文の誤りの検査）。
// 目的：GitHub は構文の誤りのある図を「Unable to render」と表示するだけで、push して画面を見るまで気づけないため。
// 対象の文書：docs/ の下の .md。アーカイブ（docs/08-spec/archive/）は対象にしない。
// 図の取り出し：コードブロックは lib/code-block で読む（CommonMark と同じ。HTML のコメントの中、他のコードブロックの中は読まない）。
//   閉じていない mermaid のコードブロックは失敗にする。箇条書き・引用・HTML のブロック・front matter の中の図は対象にしない（使っていないため）。
//   取り出した図が 0 件のときも失敗にする（取り出しの不具合で、何も検査せずに成功するのを防ぐ）。
// 描画：ファイルごとに 1 回、そのファイルの図だけを並べた一時の Markdown を mermaid-cli に渡す。出力の SVG が図の数だけできたかを確認する。足りなければ失敗にする
//   （mermaid-cli は、行頭の記号がちょうど 3 個のコードブロックだけを取り出し、図が 1 つも取り出されなくても終了コード 0 で終わるため）。
//   本文に行末の ``` か ::: がある図は、まとめた Markdown では途中で切れる（SVG はできるので数では検出できない）ため、そのファイルはまとめて描画せず、図ごとに描画する。
//   失敗したファイルだけ、図ごとに、図の本文だけの .mmd を入力にして再描画し、失敗した図の行（元のファイルの行）を示す。
//   描画のプロセスが失敗したら、エラーの出力が空でも失敗にする。
// mermaid-cli は依存に入れず、バージョンを固定して npx で取得する。ブラウザーは取得せず（PUPPETEER_SKIP_DOWNLOAD）、手元の Chrome を使う。
// Chrome の場所は環境変数 MERMAID_CHROME。指定しないときは、既定の場所を順に探す（Windows の Chrome、google-chrome、chromium）。
//   見つからなければ、作業のディレクトリを作る前に、理由を出して終了コード 2 で終わる。
// script を scripts/ci/ の直下に置かない理由：直下の script は check-all.sh と commit の前の hook が全て実行する。この検査は Chrome とネットワークが必要で遅いので、実行するのは CI の文書のワークフローだけにする。
// 失敗したとき：出力された文書と行の図の構文を修正する（mermaid-cli のエラーメッセージに、構文が崩れた箇所の前後が表示される）。閉じていないコードブロックは、閉じの行を追加する。
// 除外：なし
// 使い方：node scripts/ci/mermaid/mermaid-check.mjs（リポジトリのルートで）
import { execFileSync, execSync } from 'node:child_process'
import { existsSync, mkdtempSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { readCodeBlocks } from '../lib/code-block.mjs'
import { emptyListReason, listGitFiles } from '../lib/git-files.mjs'
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

/** mermaid-cli が図の終わりとみなす行（行末の ``` か :::）。本文にあると、まとめた Markdown では図がそこで切れる */
const CUTTING_LINE = /(`{3}|:{3})[^\S\n]*$/m

/**
 * 対象の文書かを返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} docs/ の下の .md で、アーカイブでなければ true
 */
export function isMermaidTargetDoc(file) {
  return /^docs\/.*\.md$/.test(file) && !file.startsWith('docs/08-spec/archive/')
}

/**
 * 文書の中の Mermaid の図を取り出す。
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
 * @returns {string} 図ごとに 3 個のバッククォートのコードブロックにした Markdown
 *   （mermaid-cli は、行頭の記号がちょうど 3 個のコードブロックだけを読み取る）
 */
export function toMarkdown(sources) {
  return sources.map((source) => `\`\`\`mermaid\n${source}\n\`\`\`\n`).join('\n')
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
 * 描画の結果から、失敗の理由を返す。
 *
 * @param {{ error: string | null, outputs: number }} result 描画の結果。outputs は、できた SVG の数
 * @param {number} expected 渡した図の数
 * @returns {string | null} 全ての図が描画されたら null、そうでなければ空でない理由
 */
export function renderFailure(result, expected) {
  if (result.error !== null) return result.error
  if (result.outputs !== expected) {
    return `描画された図が足りない（${expected} 個のうち ${result.outputs} 個。図の本文に、行末の \`\`\` か ::: がないか確認する）`
  }
  return null
}

/**
 * ファイルごとに図を描画して、描画できない図を集める。
 *
 * @param {{ file: string, text: string }[]} docs 文書
 * @param {(input: string, kind: 'markdown' | 'diagram') => { error: string | null, outputs: number }} render
 *   描画する関数。kind が markdown なら入力は Markdown、diagram なら図の本文だけ。outputs はできた SVG の数
 * @returns {{ problems: { file: string, line: number, message: string }[], count: number }}
 *   problems は描画できない図と閉じていない図（ファイル・開きの行・理由）、count は図の数（閉じていない図を含む）
 */
export function checkDocs(docs, render) {
  const problems = []
  let count = 0
  for (const { file, text } of docs) {
    const { diagrams, unclosed } = extractMermaidBlocks(text)
    count += diagrams.length + unclosed.length
    const found = unclosed.map((line) => ({ file, line, message: '閉じていないコードブロックがある（閉じの行が足りない）' }))
    if (diagrams.length > 0) {
      // 本文に行末の ``` か ::: がある図は、まとめた Markdown では途中で切れる（切れても SVG はできるので数では検出できない）。まとめて描画せず、図ごとに描画する
      const cutting = diagrams.some((diagram) => CUTTING_LINE.test(diagram.source))
      const whole = cutting ? '' : renderFailure(render(toMarkdown(diagrams.map((diagram) => diagram.source)), 'markdown'), diagrams.length)
      if (whole !== null) {
        const each = diagrams.flatMap((diagram) => {
          const message = renderFailure(render(diagram.source, 'diagram'), 1)
          return message === null ? [] : [{ file, line: diagram.line, message }]
        })
        found.push(...(each.length > 0 || cutting ? each : [{ file, line: diagrams[0].line, message: whole }]))
      }
    }
    problems.push(...found.sort((a, b) => a.line - b.line))
  }
  return { problems, count }
}

/**
 * 最後の行の要約を返す。
 *
 * @param {number} failed 描画できない図の数
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
 * shell を通して起動するコマンドの文字列を作る。
 *
 * @param {string} command コマンド
 * @param {string[]} args 引数
 * @returns {string} 全ての引数を引用符で囲んだコマンド
 */
export function buildCommand(command, args) {
  return [command, ...args.map(quoteArg)].join(' ')
}

/**
 * できた SVG の数を数える。
 * mermaid-cli 12.0.0 は、Markdown の入力では `<出力の名前>-<n>.svg`（n は 1 から）、図の本文だけの入力では `<出力の名前>.svg` を作る。
 *
 * @param {string} work 作業のディレクトリ
 * @param {string} stem 出力の名前（拡張子を除く）
 * @param {'markdown' | 'diagram'} kind 入力の種類
 * @returns {number} 中身のある SVG の数
 */
function countOutputs(work, stem, kind) {
  const names = kind === 'markdown'
    ? readdirSync(work).filter((name) => name.startsWith(stem) && /^-\d+\.svg$/.test(name.slice(stem.length)))
    : [`${stem}.svg`].filter((name) => existsSync(path.join(work, name)))
  return names.filter((name) => statSync(path.join(work, name)).size > 0).length
}

/**
 * mermaid-cli で図を描画する関数を作る。
 *
 * @param {string} work 作業のディレクトリ
 * @param {string} config puppeteer の設定のファイル
 * @returns {(input: string, kind: 'markdown' | 'diagram') => { error: string | null, outputs: number }}
 *   描画する関数。プロセスが失敗したら error に空でない誤りの文を入れる
 */
export function createRenderer(work, config) {
  let serial = 0
  return (content, kind) => {
    serial++
    const markdown = kind === 'markdown'
    const input = path.join(work, markdown ? `in-${serial}.md` : `in-${serial}.mmd`)
    const stem = `out-${serial}`
    const output = path.join(work, markdown ? `${stem}.md` : `${stem}.svg`)
    writeFileSync(input, content)
    const args = ['--yes', MERMAID_CLI, '-q', '-p', config, '-i', input, '-o', output]
    const options = { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], env: { ...process.env, PUPPETEER_SKIP_DOWNLOAD: '1' } }
    try {
      // Windows の npx は npx.cmd で、shell を通さないと起動できない。引用符で囲んだ 1 つのコマンドにして、空白のあるパスが 1 つの引数になるようにする
      if (process.platform === 'win32') execSync(buildCommand('npx', args), options)
      else execFileSync('npx', args, options)
    } catch (error) {
      return { error: describeFailure(error), outputs: 0 }
    }
    return { error: null, outputs: countOutputs(work, stem, kind) }
  }
}

/**
 * 検査の本体。外の環境に触る処理は引数で受け取る（テストで偽の関数に替えるため）。
 *
 * @param {object} deps 外の環境に触る処理
 * @param {() => string | null} deps.findChrome Chrome の場所を返す。なければ null
 * @param {() => { file: string, text: string }[]} deps.listDocs 対象の文書を読んで返す
 * @param {() => string} deps.makeWork 作業のディレクトリを作って、そのパスを返す
 * @param {(work: string) => void} deps.removeWork 作業のディレクトリを削除する
 * @param {(work: string, chrome: string) => (input: string, kind: 'markdown' | 'diagram') => { error: string | null, outputs: number }} deps.makeRender
 *   描画する関数を作る
 * @param {(line: string) => void} deps.out 標準出力に書く
 * @param {(line: string) => void} deps.err 標準エラー出力に書く
 * @returns {number} 終了コード（0：成功、1：描画できない図などがある、2：Chrome がない）
 */
export function runCheck(deps) {
  const chrome = deps.findChrome()
  if (chrome === null) {
    deps.err('Chrome が見つからない。環境変数 MERMAID_CHROME に実行ファイルのパスを入れる（既定の場所にもない）。')
    return 2
  }
  const docs = deps.listDocs()
  const noDocs = emptyListReason(docs, 'docs/ の文書')
  if (noDocs) {
    deps.out(noDocs)
    return 1
  }
  const work = deps.makeWork()
  let result
  try {
    result = checkDocs(docs, deps.makeRender(work, chrome))
  } finally {
    deps.removeWork(work)
  }
  for (const problem of result.problems) deps.err(`${problem.file}:${problem.line}\n${problem.message}`)
  if (result.count === 0) {
    deps.out(emptyListReason([], 'Mermaid の図'))
    return 1
  }
  deps.out(summaryLine(result.problems.length, result.count))
  return result.problems.length > 0 ? 1 : 0
}

function main() {
  const code = runCheck({
    findChrome: () => findChrome(process.env, existsSync),
    listDocs: () => listGitFiles(['docs']).filter(isMermaidTargetDoc).map((file) => ({ file, text: readFileSync(file, 'utf8') })),
    makeWork: () => mkdtempSync(path.join(tmpdir(), 'mermaid-check-')),
    removeWork: (work) => rmSync(work, { recursive: true, force: true }),
    makeRender: (work, chrome) => {
      const config = path.join(work, 'puppeteer.json')
      // CI（Linux）では、サンドボックスを使えないことがある
      writeFileSync(config, JSON.stringify({ executablePath: chrome, args: ['--no-sandbox'] }))
      return createRenderer(work, config)
    },
    out: (line) => console.log(line),
    err: (line) => console.error(line),
  })
  if (code !== 0) process.exit(code)
}

if (isMain(import.meta.url)) main()
