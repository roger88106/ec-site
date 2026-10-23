// 画面のコードに、表示の文言（日本語の文字）を直接書いた所がないことを確認する（フロントの書き方 第 7.1 節）。
// 文言は各画面の messages/ja.json（共通は frontend/shared/src/messages/ja.json）に置き、キーで引く。
// コメントと、開発者だけが見る文（ログ、プログラムの誤りの例外）は見ない。テスト、生成物、shadcn/ui の部品、除外の一覧（hardcoded-text-exclusions.json。リポジトリのルートからのパスと理由）のファイルも見ない。
// 使い方：node scripts/ci/hardcoded-text.mjs [--count]（リポジトリのルートで。--count は数だけを出し、失敗にしない）
import { readdirSync, readFileSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import { checkExclusions, readExclusions } from './lib/exclusions.mjs'

/** 調べる画面のソースの置き場所 */
const SOURCE_DIRS = ['frontend/admin/src', 'frontend/store/src', 'frontend/shared/src']

/** 調べない置き場所（生成物、shadcn/ui の部品、テストの補助） */
const EXCLUDED = [/[\\/]generated[\\/]/, /[\\/]components[\\/]ui[\\/]/, /[\\/]src[\\/]ui[\\/]/, /[\\/]src[\\/]test[\\/]/]

/** 開発者だけが見る文（ログ、プログラムの誤りの例外）の行。利用者に見せる文ではないので見ない */
const DEVELOPER_ONLY = /\b(log|console)\.(debug|info|warn|error)\(|throw new Error\(/

/** 日本語の文字（ひらがな、カタカナ、漢字、全角の記号と句読点） */
const JAPANESE = /[　-ヿ㐀-鿿＀-￯]/

/**
 * コメントを空白に置き換える（行の数は変えない）。文字列の中の // は、URL などなので消さない。
 *
 * @param {string} text ソース
 * @returns {string} コメントを除いたソース
 */
export function stripComments(text) {
  let result = ''
  let index = 0
  let quote = null
  while (index < text.length) {
    const char = text[index]
    const next = text[index + 1]
    if (quote) {
      result += char
      if (char === '\\') {
        result += next ?? ''
        index += 2
        continue
      }
      if (char === quote) quote = null
      index += 1
      continue
    }
    if (char === '/' && next === '*') {
      const end = text.indexOf('*/', index + 2)
      const stop = end < 0 ? text.length : end + 2
      result += text.slice(index, stop).replace(/[^\n]/g, ' ')
      index = stop
      continue
    }
    if (char === '/' && next === '/') {
      const end = text.indexOf('\n', index)
      const stop = end < 0 ? text.length : end
      result += ' '.repeat(stop - index)
      index = stop
      continue
    }
    if (char === '/' && next !== '>' && startsRegex(result)) {
      const stop = regexEnd(text, index)
      result += ' '.repeat(stop - index)
      index = stop
      continue
    }
    if (char === "'" || char === '"' || char === '`') quote = char
    result += char
    index += 1
  }
  return result
}

/**
 * 正規表現のリテラルの前に来る記号（この後の / は割り算でなく正規表現の始まり）。
 * } は JSX の属性の後の /> と見分けられないので入れない（ブロックの後の正規表現は、たいてい行の始まりにある）。
 * JSX の文字の中の記号の後の /（`<p>(/日本語)</p>` など）も正規表現と読み、行の終わりまでを見ない。まれなので、見逃しとして許す
 */
const BEFORE_REGEX_CHARS = '(,=:[!&|?{;'

/** 正規表現のリテラルの前に来る語 */
const BEFORE_REGEX_WORD = /\b(?:return|typeof|case|in|of|delete|void|throw|new|instanceof|yield|await)$/

/**
 * ここまでのコードの後の / が、正規表現のリテラルの始まりかを返す。
 *
 * @param {string} before ここまでのコード
 * @returns {boolean} 行の始まりか、式の始まりになる記号・語の後なら true（それ以外は割り算）
 */
function startsRegex(before) {
  const trimmed = before.replace(/[ \t]+$/, '')
  const last = trimmed.at(-1)
  return last === undefined || last === '\n' || BEFORE_REGEX_CHARS.includes(last) || BEFORE_REGEX_WORD.test(trimmed)
}

/**
 * 正規表現のリテラルの終わりの位置を返す。
 *
 * @param {string} text ファイルの中身
 * @param {number} start 始まりの / の位置
 * @returns {number} フラグの次の位置（行の終わりまでに閉じなければ、行の終わり）
 */
function regexEnd(text, start) {
  let index = start + 1
  let inClass = false
  while (index < text.length && text[index] !== '\n') {
    const char = text[index]
    if (char === '\\') {
      index += 2
      continue
    }
    if (char === '[') inClass = true
    else if (char === ']') inClass = false
    else if (char === '/' && !inClass) {
      index += 1
      while (index < text.length && /[a-z]/.test(text[index])) index += 1
      return index
    }
    index += 1
  }
  return index
}

/**
 * 表示の文言を直接書いた行を探す。
 *
 * @param {string} text ファイルの中身
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findHardcodedText(text) {
  const original = text.split('\n')
  return stripComments(text)
    .split('\n')
    .flatMap((line, index) =>
      JAPANESE.test(line) && !DEVELOPER_ONLY.test(line) ? [{ line: index + 1, text: original[index].trim() }] : [],
    )
}

/** 調べるファイル（テストを除く .ts と .tsx） */
function sourceFiles(dir) {
  return readdirSync(dir, { recursive: true, withFileTypes: true })
    .filter((entry) => entry.isFile() && /\.tsx?$/.test(entry.name) && !entry.name.includes('.test.'))
    .map((entry) => join(entry.parentPath, entry.name))
    .filter((file) => !EXCLUDED.some((pattern) => pattern.test(file)))
}

/**
 * ファイルごとに、文言を直接書いた行を集める。
 *
 * @param {string[]} files 調べるファイル
 * @param {(file: string) => string} read ファイルの中身を読む関数
 * @returns {Map<string, { line: number, text: string }[]>} 文言を直接書いた行があるファイルと、その行（ない場合はファイルを含めない）
 */
export function scanSources(files, read) {
  const hitsByFile = new Map()
  for (const file of files) {
    const hits = findHardcodedText(read(file))
    if (hits.length > 0) hitsByFile.set(file, hits)
  }
  return hitsByFile
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const exclusions = readExclusions(fileURLToPath(new URL('./hardcoded-text-exclusions.json', import.meta.url)))
  const files = SOURCE_DIRS.flatMap((dir) => sourceFiles(dir)).map((file) => relative('.', file).replaceAll('\\', '/'))
  // 除外のファイルも読む。除外が要るのは、文言を直接書いた行があるファイルだけ
  const hitsByFile = scanSources(files, (file) => readFileSync(file, 'utf8'))
  const found = [...hitsByFile].filter(([file]) => !(file in exclusions)).flatMap(([file, hits]) => hits.map((hit) => ({ file, ...hit })))
  const problems = checkExclusions('hardcoded-text-exclusions.json', exclusions, (file) => hitsByFile.has(file))
  if (problems.length > 0) {
    for (const problem of problems) console.error(problem)
    process.exit(1)
  }
  if (process.argv.includes('--count')) {
    const byFile = Object.groupBy(found, (hit) => hit.file)
    for (const [file, hits] of Object.entries(byFile)) console.log(`${hits.length}\t${file}`)
    console.log(`合計 ${found.length} 行、${Object.keys(byFile).length} ファイル`)
  } else if (found.length > 0) {
    console.error('表示の文言を直接書いた所がある。messages/ja.json に置き、キーで引く：')
    for (const hit of found) console.error(`  ${hit.file}:${hit.line}  ${hit.text}`)
    process.exit(1)
  } else {
    console.log('表示の文言を直接書いた所はない')
  }
}
