// CI の検査の script の文書（docs/06-手順書/ci-checks.md）の「検査の一覧」の表と、scripts/ci/ の検査を確認する。次のどれかに当てはまると失敗にする。
// 1. 表の名前と直下の検査が 1 対 1 に対応しない（表にない検査、検査のない表の行、同じ名前の行）
//    - 直下の検査：*.test.mjs 以外の *.mjs（check-all.sh が引数なしで実行するもの）
//    - 表：見出し（h2・h3）が「検査の一覧」の節にある表の、1 列目が | `名前` | の行。名前の後に（`引数`）を書いた行（ja-text の --changed）は数えない
//      名前の形でない行は数えないので、その検査は「表にない検査」として失敗になる
// 2. 直下の検査と lib/ の各ファイル（テストを除く）の 1 行目が `//` で始まらない。先頭のコメントのない script を残さないため
// 3. 直下の検査の先頭のコメント（1 行目から続く `//` の行）に、「失敗したとき：」の行と「除外：」の行がない。書き漏らしを防ぐため
//    - 行は、`//` の後に空白と箇条書きの `-` を置いてよい。lib/ のファイルは、役割だけを書くので対象にしない
//
// 表の列の数と書き方は確認しない（レビューで見る）。
//
// 失敗したとき：表に行を加えるか、不要な行を削除する。先頭のコメントのない script には、先頭のコメントを書く。
//   「失敗したとき：」か「除外：」の行がない検査には、先頭のコメントにその行を書く（除外がなければ「除外：なし」）。
//
// 除外：なし
//
// 使い方：node scripts/ci/docs/ci-docs.mjs（リポジトリのルートで）
import { existsSync, readFileSync } from 'node:fs'
import { exitIfNoFiles, listGitFiles } from '../../shared/git/git-files.mjs'
import { describeUnreadableLine } from '../lib/source/unreadable-line.mjs'

/** CI の検査の script の文書 */
const DOC = 'docs/06-手順書/ci-checks.md'

/** 表を探す節の見出しの名前 */
const TABLE_SECTION = '検査の一覧'

/** 見出しの行（h2・h3） */
const HEADING = /^#{2,3} (.*)$/

/** 検査を置くディレクトリ */
const CHECK_DIR = 'scripts/ci/'

/** 検査のフォルダ（CHECK_DIR の下）。正規表現の選択肢の形 */
const CHECK_FOLDERS = ['docs', 'spec', 'ja', 'java', 'config', 'frontend'].join('|')

/** 検査のファイル（CHECK_DIR からの相対パス）。検査のフォルダの直下の .mjs（テストを除くのは呼ぶ側） */
const CHECK_FILE = new RegExp(`^(?:${CHECK_FOLDERS})/[^/]+\\.mjs$`)

/** 先頭のコメントを確認するファイル：検査と、lib の下の各ファイル */
const COMMENT_FILE = new RegExp(`^(?:(?:${CHECK_FOLDERS})/[^/]+|lib/(?:.+/)?[^/]+)\\.mjs$`)

/** 1 列目が名前の行（名前と、名前の後の（`引数`）） */
const NAME_ROW = /^\| `([^`\s]+)`(（`[^`]+`）)? \|/

/**
 * 「検査の一覧」の節（次の h2・h3 の見出しまで）の表の、1 列目の名前を読む。名前の後に（`引数`）を書いた行は数えない。
 *
 * @param {string} markdown 文書の中身
 * @returns {string[]} 表の順の名前
 */
export function readTableChecks(markdown) {
  const names = []
  let inSection = false
  for (const line of markdown.split(/\r?\n/)) {
    const heading = line.match(HEADING)
    if (heading) inSection = heading[1] === TABLE_SECTION
    const match = inSection ? line.match(NAME_ROW) : null
    if (match && !match[2]) names.push(match[1])
  }
  return names
}

/**
 * ファイルの一覧から、検査の名前を選ぶ。検査は、検査のフォルダ（CHECK_FILE）の直下にある。
 *
 * @param {string[]} files リポジトリのルートからのパス
 * @returns {string[]} 拡張子を除いた名前。名前の順
 */
export function listChecks(files) {
  return files
    .filter((file) => file.startsWith(CHECK_DIR) && CHECK_FILE.test(file.slice(CHECK_DIR.length)) && !file.endsWith('.test.mjs'))
    .map((file) => file.slice(file.lastIndexOf('/') + 1, -'.mjs'.length))
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
    if (!table.has(name)) problems.push(`表にない検査：${name}（${DOC} の「${TABLE_SECTION}」の表に行を加える）`)
  }
  for (const name of table) {
    if (!checks.has(name)) problems.push(`検査のない表の行：${name}（${CHECK_DIR} の検査のフォルダの直下に ${name}.mjs がない）`)
  }
  const duplicates = [...new Set(tableNames.filter((name, index) => tableNames.indexOf(name) !== index))]
  for (const name of duplicates) problems.push(`表に同じ名前の行が 2 つ以上ある：${name}`)
  return problems
}

/**
 * 先頭のコメントを確認する script を選ぶ。対象は、検査と lib/ の下のファイルで、テストを除く。
 *
 * @param {string[]} files リポジトリのルートからのパス
 * @returns {string[]} 対象のパス。名前の順
 */
export function listCommentTargets(files) {
  return files
    .filter((file) => file.startsWith(CHECK_DIR) && COMMENT_FILE.test(file.slice(CHECK_DIR.length)) && !file.endsWith('.test.mjs'))
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

/** 直下の検査が先頭のコメントに書く、必須の行の接頭辞 */
const REQUIRED_COMMENT_LINES = ['失敗したとき：', '除外：']

/** 必須の行の接頭辞ごとの、その行の形（`//` の後の空白と箇条書きの `-` を許す） */
const REQUIRED_LINE_PATTERNS = REQUIRED_COMMENT_LINES.map((label) => ({ label, pattern: new RegExp(`^//\\s*(?:-\\s*)?${label}`) }))

/**
 * 直下の検査の先頭のコメント（1 行目から続く `//` の行）に、「失敗したとき：」か「除外：」の行がない script を見つける。
 *
 * 行は `//` の後に空白と箇条書きの `-` を置いてよい。
 *
 * @param {{ file: string, text: string }[]} sources 直下の検査のパスと中身
 * @returns {string[]} 行のない script の説明
 */
export function checkCommentLines(sources) {
  const problems = []
  for (const { file, text } of sources) {
    const lines = text.split('\n')
    const end = lines.findIndex((line) => !line.startsWith('//'))
    const comment = end === -1 ? lines : lines.slice(0, end)
    const missing = REQUIRED_LINE_PATTERNS.filter(({ pattern }) => !comment.some((line) => pattern.test(line))).map(({ label }) => label)
    if (!missing.length) continue
    const hint = missing.includes('除外：') ? '。除外がなければ「除外：なし」' : ''
    problems.push(`先頭のコメントに行がない：${file}（${missing.map((label) => `「${label}」`).join('、')}の行を書く${hint}）`)
  }
  return problems
}

if (import.meta.main) {
  const checkFiles = listGitFiles([CHECK_DIR])
  exitIfNoFiles(checkFiles, `検査（${CHECK_DIR}）`)
  if (!existsSync(DOC)) {
    console.log(`${DOC} がない（DOC を、CI の検査の文書の今の名前に合わせる）`)
    process.exit(1)
  }
  const doc = readFileSync(DOC, 'utf8')
  const sources = listCommentTargets(checkFiles).map((file) => ({ file, text: readFileSync(file, 'utf8') }))
  const problems = [...compareChecks(readTableChecks(doc), listChecks(checkFiles)), ...checkFirstLines(sources), ...checkCommentLines(sources.filter(({ file }) => !file.startsWith(`${CHECK_DIR}lib/`)))]
  for (const problem of problems) console.log(problem)
  console.log(`食い違い：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
