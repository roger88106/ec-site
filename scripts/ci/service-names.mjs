// docs/constitution.md と docs/05-開発規則/ の下の *.md の本文に、AI のサービス固有の名前（SERVICE_NAMES）がないことを確認する。
// 理由：規則の本文はサービスに依存しない言葉で書き、サービスごとの名前は skill `sessions` の「ツールの補足」などに集めるため（サービスやモデルを替えたときに、規則を直さなくてよくする）。
// 名前の一覧（SERVICE_NAMES）はこの script の 1 か所だけに置く。大文字・小文字は区別しない。名前の境界は ASCII の英数字だけを、語を構成する文字として判定する（claudette、cursory は対象にしない。日本語の字や記号が前後にある名前は対象にする）。
// 名前を書いてよい場所（許可する形）は 3 つ。インラインのコードスパン（`...`）の中、Markdown のリンク先（](...) の括弧の中）、コードブロックの中（ディレクトリの構成の図のパスなど）。
// パスと識別子（`.claude/`、`CLAUDE.md` など）はこの形で書く。リンクの表示の文字（[...] の中）は本文として扱う。
// 判定は誤りの防止で、字を分ける、全角にするなどの意図した回避は対象にしない。
// 失敗したとき：出力の「ファイル:行」の名前を、サービスに依存しない言葉（例：「エージェント」「高性能の区分」）に替える。ツールの名前が必要なときは、skill `sessions` の「ツールの補足」に移して指す。パスや識別子ならコードスパンにする。
// 除外：なし
// 使い方：node scripts/ci/service-names.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'

/** 規則の本文に書かない、サービス固有の名前 */
export const SERVICE_NAMES = ['Claude Code', 'Claude', 'Opus', 'Sonnet', 'Haiku', 'Codex', 'Gemini', 'Copilot', 'Cursor']

/** 名前の前後が ASCII の英数字でないこと（\b は日本語の字を語の字とみなすので使わない） */
const NAME_PATTERN = new RegExp(`(?<![A-Za-z0-9])(?:${SERVICE_NAMES.join('|')})(?![A-Za-z0-9])`, 'i')

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
 * 許可する形（コードスパンの中、リンク先）を空白に替える。
 *
 * @param {string} line 1 行
 * @returns {string} 本文だけが残る行
 */
function stripAllowed(line) {
  return line.replace(/`[^`]*`/g, ' ').replace(/\]\([^)]*\)/g, '] ')
}

/**
 * 許可する形の外にある名前を探す。
 *
 * @param {{ path: string, text: string }[]} files 対象のファイルのパスと本文
 * @returns {string[]} 'パス:行番号'（行番号は 1 始まり）。ファイルの順・行の順。1 行は 1 回だけ
 */
export function findServiceNameProblems(files) {
  const problems = []
  for (const { path, text } of files) {
    // 囲みの開きの字と数。同じ字で同じ数以上の行だけで閉じる（CommonMark と同じ）
    let fence = null
    text.split('\n').forEach((line, index) => {
      const match = line.match(FENCE)
      if (fence) {
        if (match && match[1][0] === fence.char && match[1].length >= fence.length && match[2].trim() === '') fence = null
        return
      }
      if (match && !(match[1][0] === '`' && match[2].includes('`'))) {
        fence = { char: match[1][0], length: match[1].length }
        return
      }
      if (NAME_PATTERN.test(stripAllowed(line))) problems.push(`${path}:${index + 1}`)
    })
  }
  return problems
}

if (isMain(import.meta.url)) {
  const targets = listGitFiles().filter(isTargetFile)
  exitIfNoFiles(targets, '規則の本文（docs/constitution.md と docs/05-開発規則/ の *.md）')
  const files = targets.map((path) => ({ path, text: readFileSync(path, 'utf8') }))
  const problems = findServiceNameProblems(files)
  for (const problem of problems) {
    console.log(`${problem} サービス固有の名前がある（サービスに依存しない言葉に替えるか、skill sessions の「ツールの補足」に移す。パスや識別子はコードスパンにする）`)
  }
  console.log(`サービス固有の名前：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
