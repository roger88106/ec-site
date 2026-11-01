// docs/constitution.md と docs/05-開発規則/ の下の *.md の本文に、AI のサービス固有の名前（SERVICE_NAMES）がないことを確認する。
// 理由：規則の本文はサービスに依存しない言葉で書き、サービスごとの名前は skill `sessions` の「ツールの補足」などに集めるため（サービスやモデルを替えたときに、規則を直さなくてよくする）。
// 名前の一覧（SERVICE_NAMES）はこの script の 1 か所だけに置く。大文字・小文字は区別しない。名前の境界は ASCII の英数字だけを、語を構成する文字として判定する（claudette、cursory は対象にしない。日本語の字や記号が前後にある名前は対象にする）。
// 名前を書いてよい場所（許可する形）は 3 つ。
//   - パスと識別子のインラインのコードスパン（`.claude/`、`CLAUDE.md` など）の中。コードスパンは、開きと同じ数のバッククォートの並びで閉じる形で読む（lib/code-span.mjs）。
//     パスと識別子のコードスパンは、パスや識別子の字（/ . _ - \ @ $ =）を 1 つ以上含むもの。含まないもの（`Claude Code`、`Claude Code の設定`、`Opus 4` など）は、中身を本文として扱う
//   - Markdown のリンク先（](...) の括弧の中の、最初の空白より前か、<...> の中）。リンクのタイトル（"..."）とリンクの表示の文字（[...] の中）は本文として扱う
//   - コードブロックの中（ディレクトリの構成の図のパスなど）。囲みを開いたままファイルが終わったら、その後の本文を読めないので、開きの行を失敗にする（開きより短い閉じ、違う字の閉じも閉じとみなさない）
// 判定は誤りの防止で、字を分ける、全角にするなどの意図した回避は対象にしない。
// 既知の制限：パスや識別子の字を含むコードスパンは、中身を問わず許す。そのため、バージョンを付けた名前（`Opus 4.5` など）をコードスパンで書くと通る。規則の本文でバージョンの名前をコードスパンに書く誤りは起きにくいので、許可する形を増やさない（利用者の決定、2026-11-01）。
// 失敗したとき：出力の「ファイル:行」の名前を、サービスに依存しない言葉（例：「エージェント」「高性能の区分」）に替える。ツールの名前が必要なときは、skill `sessions` の「ツールの補足」に移して指す。パスや識別子ならコードスパンにする。
// 除外：なし
// 使い方：node scripts/ci/service-names.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { replaceCodeSpans } from './lib/code-span.mjs'
import { isMain } from './lib/is-main.mjs'
import { describeUnreadableLine } from './lib/unreadable-line.mjs'

/** 規則の本文に書かない、サービス固有の名前 */
export const SERVICE_NAMES = ['Claude Code', 'Claude', 'Opus', 'Sonnet', 'Haiku', 'Codex', 'Gemini', 'Copilot', 'Cursor']

/** 名前の前後が ASCII の英数字でないこと（\b は日本語の字を語の字とみなすので使わない） */
const NAME_PATTERN = new RegExp(`(?<![A-Za-z0-9])(?:${SERVICE_NAMES.join('|')})(?![A-Za-z0-9])`, 'i')

/** パスや識別子の字。コードスパンの中身がこれを含めば、パスか識別子として許す */
const IDENTIFIER_CHARS = /[/._\-\\@$=]/

/** リンク先（`](` の後の、<...> か、最初の空白か `)` より前） */
const LINK_TARGET = /\]\((?:<[^>]*>|[^\s)]*)/g

/** コードブロックの囲みの行（開きの字の並びと、その後の文字列） */
const FENCE = /^\s*(`{3,}|~{3,})(.*)$/

/**
 * 検査の対象のファイルか。
 *
 * @param {string} path リポジトリのルートからのパス（/ 区切り）
 * @returns {boolean} docs/constitution.md か、docs/05-開発規則/ の下の *.md ならば true
 */
export function isTargetFile(path) {
  return path === 'docs/constitution.md' || (path.startsWith('docs/05-開発規則/') && path.endsWith('.md'))
}

/**
 * 許可する形（パスと識別子のコードスパンの中、リンク先）を空白に替える。パスや識別子の字を含まないコードスパンは、中身を本文として残す。
 *
 * @param {string} line 1 行
 * @returns {string} 本文だけが残る行
 */
function stripAllowed(line) {
  return replaceCodeSpans(line, (content) => (IDENTIFIER_CHARS.test(content) ? ' ' : ` ${content} `)).replace(LINK_TARGET, '] (')
}

/**
 * ファイルの行を、コードブロックの外の行と、閉じていない囲みの開きの行に分ける。
 * 囲みは、開きと同じ字で同じ数以上の、後ろに文字のない行だけで閉じる（CommonMark と同じ）。
 *
 * @param {string} text ファイルの本文
 * @returns {{ body: { line: number, text: string }[], unclosed: { line: number, text: string } | null }} 外の行と、閉じていない囲みの開きの行（line は 1 始まり）
 */
function splitFences(text) {
  const body = []
  let fence = null
  text.split('\n').forEach((line, index) => {
    const match = line.match(FENCE)
    if (fence) {
      if (match && match[1][0] === fence.char && match[1].length >= fence.length && match[2].trim() === '') fence = null
      return
    }
    if (match && !(match[1][0] === '`' && match[2].includes('`'))) {
      fence = { char: match[1][0], length: match[1].length, line: index + 1, text: line }
      return
    }
    body.push({ line: index + 1, text: line })
  })
  return { body, unclosed: fence && { line: fence.line, text: fence.text } }
}

/**
 * 許可する形の外にある名前を探す。
 *
 * @param {{ path: string, text: string }[]} files 対象のファイルのパスと本文
 * @returns {string[]} 'パス:行番号'（行番号は 1 始まり）。ファイルの順・行の順。1 行は 1 回だけ
 */
export function findServiceNameProblems(files) {
  return files.flatMap(({ path, text }) =>
    splitFences(text)
      .body.filter((row) => NAME_PATTERN.test(stripAllowed(row.text)))
      .map((row) => `${path}:${row.line}`),
  )
}

/**
 * 囲みを開いたままファイルが終わったファイルを探す。その後の本文を全てコードブロックの中として読み飛ばさないため。
 *
 * @param {{ path: string, text: string }[]} files 対象のファイルのパスと本文
 * @returns {string[]} 開きの行の説明（`ファイル:行 内容（理由）`）。ファイルの順
 */
export function findUnclosedFences(files) {
  return files.flatMap(({ path, text }) => {
    const { unclosed } = splitFences(text)
    if (!unclosed) return []
    return [describeUnreadableLine({ file: path, ...unclosed, reason: 'コードブロックが閉じていない。開きと同じ字で、同じ数以上の行で閉じる' })]
  })
}

if (isMain(import.meta.url)) {
  const targets = listGitFiles().filter(isTargetFile)
  exitIfNoFiles(targets, '規則の本文（docs/constitution.md と docs/05-開発規則/ の *.md）')
  const files = targets.map((path) => ({ path, text: readFileSync(path, 'utf8') }))
  const problems = findServiceNameProblems(files)
  const unclosed = findUnclosedFences(files)
  for (const problem of problems) {
    console.log(`${problem} サービス固有の名前がある（サービスに依存しない言葉に替えるか、skill sessions の「ツールの補足」に移す。パスや識別子はコードスパンにする）`)
  }
  for (const problem of unclosed) console.log(problem)
  console.log(`サービス固有の名前と閉じていないコードブロック：${problems.length + unclosed.length}`)
  process.exit(problems.length + unclosed.length ? 1 : 0)
}
