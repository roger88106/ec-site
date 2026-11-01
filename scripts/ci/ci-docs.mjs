// CI の検査の文書（docs/06-手順書/ci.md）の「CI の検査の script」の表と、scripts/ci/ の検査を確認する。次の 4 つのどれかに当てはまると失敗にする。
// 1. 表の名前と直下の検査が 1 対 1 に対応しない（表にない検査、検査のない表の行、同じ名前の行）
//    - 直下の検査：*.test.mjs 以外の *.mjs（check-all.sh が引数なしで実行するもの）
//    - 表：見出し `## 3.` の節の、最初の下の見出し（`### 3.1`）より前の表の 1 列目の名前（`ac-check` など）
// 2. 表が 2 列（script と目的）でない。詳しい説明を表に戻さず、script の先頭のコメントに書くため。読めない書き方の行も失敗にする
//    - 列は縦線で数える。コードスパン（開きと同じ数のバッククォートで閉じる。lib/code-span.mjs）の中の縦線と、エスケープした縦線（\|）は数えない
//    - 組にならないバッククォートが残る行は、列を数えられないので失敗にする
//    - 表の行の後から空行までの、縦線で始まらない行（前に空白のある行、最初の縦線のない行）は、表の行として表示されるのに検査から漏れるので失敗にする
//    - 見出しの行と区切りの行を除き、1 列目は名前の形（| `名前` |）にする。名前を読めない行を数え漏らさないため
//    - 例外として、名前の後に（`引数`）だけを書いた行（ja-text の --changed の行）は、同じ名前の行の実行の仕方として許す。名前には数えず、同じ名前の行がなければ失敗にする
// 3. 直下の検査と lib/ の各ファイル（テストを除く）の 1 行目が `//` で始まらない。先頭のコメントのない script を残さないため
// 4. 直下の検査の先頭のコメント（1 行目から続く `//` の行）に、「失敗したとき：」の行と「除外：」の行がない。書き漏らしを防ぐため
//    - 行は、`//` の後に空白と箇条書きの `-` を置いてよい（// 失敗したとき：、// - 失敗したとき：）。行の途中の文字列は数えない
//    - lib/ のファイルは、役割だけを書くので対象にしない
// 失敗したとき：表に行を加えるか、不要な行を削除する。先頭のコメントのない script には、先頭のコメントを書く。
//   「失敗したとき：」か「除外：」の行がない検査には、先頭のコメントにその行を書く（除外がなければ「除外：なし」）。
// 除外：なし
// 使い方：node scripts/ci/ci-docs.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'
import { readCodeSpans, replaceCodeSpans } from './lib/code-span.mjs'
import { describeUnreadableLine } from './lib/unreadable-line.mjs'

/** CI の検査の文書 */
const DOC = 'docs/06-手順書/ci.md'

/** 検査を置くディレクトリ */
const CHECK_DIR = 'scripts/ci/'

/** 表の列の数 */
const TABLE_COLUMNS = 2

/** 1 列目が名前の行（名前と、名前の後の（`引数`）） */
const NAME_ROW = /^\| `([^`\s]+)`(（`[^`]+`）)? \|/

/** 表の区切りの行 */
const SEPARATOR_ROW = /^\|(?:\s*:?-+:?\s*\|)+$/

/**
 * 文書の見出し `## 3.` の節（最初の下の見出し `### 3.1` より前）の表の行を読む。見出しの行（表の最初の行で、次が区切りの行）と区切りの行は除く。
 *
 * @param {string} markdown 文書の中身
 * @returns {{ line: number, text: string }[]} 表の行（line は 1 始まり、text は行の最後の空白を除く）
 */
function readTableRows(markdown) {
  const lines = markdown.split('\n').map((line) => line.trimEnd())
  const rows = []
  let inSection = false
  lines.forEach((line, index) => {
    if (/^#{2,3} /.test(line)) inSection = /^## 3\. /.test(line)
    if (!inSection || !line.startsWith('|') || SEPARATOR_ROW.test(line)) return
    // 見出しの行は、表の最初の行で、次の行が区切りの行
    if (!(lines[index - 1] ?? '').startsWith('|') && SEPARATOR_ROW.test(lines[index + 1] ?? '')) return
    rows.push({ line: index + 1, text: line })
  })
  return rows
}

/**
 * 文書の見出し `## 3.` の節の表（最初の下の見出し `### 3.1` より前）の 1 列目の名前を読む。
 * 名前の後に（`引数`）を書いた行は数えない（同じ名前の検査を、引数を付けて実行することを表す）。名前の形でない行は checkTableNames が失敗にする。
 *
 * @param {string} markdown 文書の中身
 * @returns {string[]} 表の順の名前
 */
export function readTableChecks(markdown) {
  return readTableRows(markdown)
    .map(({ text }) => text.match(NAME_ROW))
    .filter((match) => match && !match[2])
    .map((match) => match[1])
}

/**
 * 表の行（見出しの行と区切りの行を除く）の 1 列目が、名前の形（| `名前` |）か、名前の後に（`引数`）を書いた形であることを確認する。
 * 名前の後に（`引数`）を書いた行は、同じ名前の行がなければ失敗にする。
 *
 * @param {string} markdown 文書の中身
 * @returns {string[]} 名前を読めない行の説明
 */
export function checkTableNames(markdown) {
  const names = new Set(readTableChecks(markdown))
  const problems = []
  for (const { line, text } of readTableRows(markdown)) {
    const item = { file: DOC, line, text }
    const match = text.match(NAME_ROW)
    if (!match) {
      problems.push(describeUnreadableLine({ ...item, reason: '1 列目が名前の形でない。名前をバッククォートで囲み、前後に空白を 1 つ置く（名前の後に書いてよいのは（`引数`）だけ）' }))
    } else if (match[2] && !names.has(match[1])) {
      problems.push(describeUnreadableLine({ ...item, reason: `名前の後に引数を書いた行に、同じ名前（${match[1]}）の行がない` }))
    }
  }
  return problems
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
 * 表の行の後から空行までの、縦線で始まらない行（前に空白のある行、最初の縦線のない行）も、名前と列の検査から漏れるので失敗にする。
 *
 * @param {string} markdown 文書の中身
 * @returns {string[]} 2 列でない行と読めない行の説明
 */
export function checkTableColumns(markdown) {
  const problems = []
  let inSection = false
  // 表を読んでいる途中か（空行で終わる）。GFM では、前に空白がある行や、最初の縦線がない行も、表の行になる
  let inTable = false
  markdown.split('\n').forEach((raw, index) => {
    const line = raw.trimEnd()
    if (/^#{2,3} /.test(line)) inSection = /^## 3\. /.test(line)
    if (!inSection || line.trim() === '') {
      inTable = false
      return
    }
    const item = { file: DOC, line: index + 1, text: line }
    if (!line.startsWith('|')) {
      if (inTable) problems.push(describeUnreadableLine({ ...item, reason: '表の行は縦線で始める。前に空白を置かず、最初の縦線を省かない' }))
      return
    }
    inTable = true
    if (!/^\|.*\|$/.test(line) || line === '|') {
      problems.push(describeUnreadableLine({ ...item, reason: '表の行の読み方が分からない。行の最後を縦線で閉じる' }))
      return
    }
    // コードスパンの中の縦線と、エスケープした縦線は、列の区切りではない
    const unescaped = line.replaceAll('\\|', '')
    if (readCodeSpans(unescaped).unmatched) {
      problems.push(describeUnreadableLine({ ...item, reason: '組にならないバッククォートがあり、列を数えられない。コードスパンは開きと同じ数のバッククォートで閉じる' }))
      return
    }
    const cells = replaceCodeSpans(unescaped, () => '').slice(1, -1).split('|').length
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

/** 直下の検査が先頭のコメントに書く、必須の行の接頭辞 */
const REQUIRED_COMMENT_LINES = ['失敗したとき：', '除外：']

/**
 * 直下の検査の先頭のコメント（1 行目から続く `//` の行）に、「失敗したとき：」か「除外：」の行がない script を見つける。
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
    const missing = REQUIRED_COMMENT_LINES.filter((label) => !comment.some((line) => new RegExp(`^//\\s*(?:-\\s*)?${label}`).test(line)))
    if (!missing.length) continue
    const hint = missing.includes('除外：') ? '。除外がなければ「除外：なし」' : ''
    problems.push(`先頭のコメントに行がない：${file}（${missing.map((label) => `「${label}」`).join('、')}の行を書く${hint}）`)
  }
  return problems
}

if (isMain(import.meta.url)) {
  const checkFiles = listGitFiles([CHECK_DIR])
  exitIfNoFiles(checkFiles, `検査（${CHECK_DIR}）`)
  const doc = readFileSync(DOC, 'utf8')
  const sources = listCommentTargets(checkFiles).map((file) => ({ file, text: readFileSync(file, 'utf8') }))
  const problems = [...compareChecks(readTableChecks(doc), listChecks(checkFiles)), ...checkTableColumns(doc), ...checkTableNames(doc), ...checkFirstLines(sources), ...checkCommentLines(sources.filter(({ file }) => !file.startsWith(`${CHECK_DIR}lib/`)))]
  for (const problem of problems) console.log(problem)
  console.log(`食い違い：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
