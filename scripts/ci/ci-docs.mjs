// CI の検査の文書（docs/06-手順書/ci.md）の「CI の検査の script」の表と、scripts/ci/ の検査を確認する。次の 3 つのどれかに当てはまると失敗にする。
// 1. 表の名前と直下の検査が 1 対 1 に対応しない（表にない検査、検査のない表の行、同じ名前の行）
//    - 直下の検査：*.test.mjs 以外の *.mjs（check-all.sh が引数なしで実行するもの）
//    - 表：見出し `## 3.` の節の、最初の下の見出し（`### 3.1`）より前の表の 1 列目の名前（`ac-check` など）
// 2. 表が 2 列（script と目的）でない。詳しい説明を表に戻さず、script の先頭のコメントに書くため。読めない書き方の行も失敗にする
// 3. 直下の検査と lib/ の各ファイル（テストを除く）の 1 行目が `//` で始まらない。先頭のコメントのない script を残さないため
// 失敗したとき：表に行を加えるか、不要な行を削除する。先頭のコメントのない script には、先頭のコメントを書く。
// 除外：なし
// 使い方：node scripts/ci/ci-docs.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'
import { describeUnreadableLine } from './lib/unreadable-line.mjs'

/** CI の検査の文書 */
const DOC = 'docs/06-手順書/ci.md'

/** 検査を置くディレクトリ */
const CHECK_DIR = 'scripts/ci/'

/** 表の列の数 */
const TABLE_COLUMNS = 2

/**
 * 文書の見出し `## 3.` の節の表（最初の下の見出し `### 3.1` より前）の 1 列目の名前を読む。
 *
 * @param {string} markdown 文書の中身
 * @returns {string[]} 表の順の名前
 */
export function readTableChecks(markdown) {
  const names = []
  let inSection = false
  for (const line of markdown.split('\n')) {
    if (/^#{2,3} /.test(line)) inSection = /^## 3\. /.test(line)
    if (!inSection) continue
    const match = line.match(/^\| `([^`]+)` \|/)
    if (match) names.push(match[1])
  }
  return names
}

/**
 * ファイルの一覧から、scripts/ci/ の直下の検査の名前を選ぶ。
 *
 * @param {string[]} files リポジトリのルートからのパス
 * @returns {string[]} 拡張子を除いた名前。名前の順
 */
export function listChecks(files) {
  return files
    .filter((file) => file.startsWith(CHECK_DIR) && /^[^/]+\.mjs$/.test(file.slice(CHECK_DIR.length)) && !file.endsWith('.test.mjs'))
    .map((file) => file.slice(CHECK_DIR.length, -'.mjs'.length))
    .sort()
}

/**
 * 表の名前と検査の名前を比べる。
 *
 * @param {string[]} tableNames 表の名前
 * @param {string[]} checkNames 検査の名前
 * @returns {string[]} 食い違いの説明
 */
export function compareChecks(tableNames, checkNames) {
  const problems = []
  const table = new Set(tableNames)
  const checks = new Set(checkNames)
  for (const name of checkNames) {
    if (!table.has(name)) problems.push(`表にない検査：${name}（${DOC} の「CI の検査の script」の表に行を加える）`)
  }
  for (const name of table) {
    if (!checks.has(name)) problems.push(`検査のない表の行：${name}（${CHECK_DIR} の直下に ${name}.mjs がない）`)
  }
  if (table.size !== tableNames.length) problems.push('表に同じ名前の行が 2 つ以上ある')
  return problems
}

/**
 * 文書の表（`## 3.` の節の、最初の `### 3.1` より前）の各行が 2 列であることを確認する。
 * 縦線で始まり縦線で終わらない行は、列を数えられないので読み飛ばさずに失敗にする。
 *
 * @param {string} markdown 文書の中身
 * @returns {string[]} 2 列でない行と読めない行の説明
 */
export function checkTableColumns(markdown) {
  const problems = []
  let inSection = false
  markdown.split('\n').forEach((raw, index) => {
    const line = raw.trimEnd()
    if (/^#{2,3} /.test(line)) inSection = /^## 3\. /.test(line)
    if (!inSection || !line.startsWith('|')) return
    const item = { file: DOC, line: index + 1, text: line }
    if (!/^\|.*\|$/.test(line) || line === '|') {
      problems.push(describeUnreadableLine({ ...item, reason: '表の行の読み方が分からない。行の最後を縦線で閉じる' }))
      return
    }
    // バッククォートの中の縦線と、エスケープした縦線は、列の区切りではない
    const cells = line.replaceAll('\\|', '').replace(/`[^`]*`/g, '').slice(1, -1).split('|').length
    if (cells !== TABLE_COLUMNS) problems.push(describeUnreadableLine({ ...item, reason: `${cells} 列ある。表は ${TABLE_COLUMNS} 列（script と目的）にし、詳しい説明は script の先頭のコメントに書く` }))
  })
  return problems
}

/**
 * ファイルの一覧から、先頭のコメントを確認する script（scripts/ci/ の直下の検査と lib/ の各ファイル。テストを除く）を選ぶ。
 *
 * @param {string[]} files リポジトリのルートからのパス
 * @returns {string[]} 対象のパス。名前の順
 */
export function listCommentTargets(files) {
  return files
    .filter((file) => file.startsWith(CHECK_DIR) && /^(lib\/)?[^/]+\.mjs$/.test(file.slice(CHECK_DIR.length)) && !file.endsWith('.test.mjs'))
    .sort()
}

/**
 * 1 行目が `//` で始まらない script を見つける。
 *
 * @param {{ file: string, text: string }[]} sources script のパスと中身
 * @returns {string[]} 1 行目がコメントでない script の説明
 */
export function checkFirstLines(sources) {
  return sources
    .filter(({ text }) => !text.split('\n')[0].startsWith('//'))
    .map(({ file, text }) => describeUnreadableLine({ file, line: 1, text: text.split('\n')[0], reason: '1 行目がコメントでない。script の先頭に、何を確認するかを `//` のコメントで書く' }))
}

if (isMain(import.meta.url)) {
  const checkFiles = listGitFiles([CHECK_DIR])
  exitIfNoFiles(checkFiles, `検査（${CHECK_DIR}）`)
  const doc = readFileSync(DOC, 'utf8')
  const sources = listCommentTargets(checkFiles).map((file) => ({ file, text: readFileSync(file, 'utf8') }))
  const problems = [...compareChecks(readTableChecks(doc), listChecks(checkFiles)), ...checkTableColumns(doc), ...checkFirstLines(sources)]
  for (const problem of problems) console.log(problem)
  console.log(`食い違い：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
