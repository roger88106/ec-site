// ソースから、日本語の文を書く所（コメントとテスト名）だけを、行番号つきで取り出す。コメントの外の文字列とコードは取り出さない（画面の文言などを調べないため）。
// - Java・Gradle の設定（.kts）：// と /* */（Javadoc を含む）のコメントと、@DisplayName("…") の文字列
// - TypeScript・JavaScript（.ts、.tsx、.mjs、.cjs）：コメントと、describe・it・test（test.describe、it.each(表)('…')、test.skip などを含む）の名前の文字列
// - テンプレートで書いたテスト名：外側のバッククォートは文字列の区切りなので " に替え、埋め込み（${…}）は X に替えて取り出す
// - シェル（.sh）：# のコメント（1 行目の #! を除く）
// 走査（コメント・文字列の読み分け）は scan-code が行う。ja-text が使う
import { closingParen } from './closing-paren.mjs'
import { scanCode, templateEnd } from './scan-code.mjs'

/** Java と Kotlin の走査の選択肢。正規表現のリテラルとテンプレートがなく、テキストブロックがある */
const JAVA_SCAN = { regex: false, templates: false, textBlocks: true }

/** テストの呼び出しの始まり。前が . や語の文字のもの（/re/.test(...) など）は読まない */
const TS_TEST_CALL = /(?<![.\w$])(?:describe|it|test)(?![\w$])/g

/** 表や条件を引数に取り、続けて名前を書く修飾 */
const TS_ARGUMENT_MODIFIERS = new Set(['each', 'for', 'skipIf', 'runIf'])

/**
 * ファイルの種類を返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {'java' | 'kts' | 'ts' | 'sh' | null} 種類。取り出す対象のファイルでなければ null
 */
export function codeKind(file) {
  if (file.endsWith('.java')) return 'java'
  if (file.endsWith('.kts')) return 'kts'
  if (/\.(?:tsx?|mjs|cjs)$/.test(file)) return 'ts'
  if (file.endsWith('.sh')) return 'sh'
  // 拡張子のない hook の script（bash）。中身を読まず、置き場所で決める
  if (file.startsWith('scripts/dev/hooks/')) return 'sh'
  return null
}

/**
 * コメントの区間を返す。
 *
 * @param {string} text ソース
 * @param {Parameters<typeof scanCode>[1]} options 走査の選択肢
 * @returns {{ from: number, to: number }[]} コメントの区間
 */
function commentRanges(text, options) {
  const ranges = []
  scanCode(text, options, (type, from, to) => {
    if (type === 'comment') ranges.push({ from, to })
  })
  return ranges
}

/**
 * Java の @DisplayName("…") の文字列の区間を返す（メソッドにもクラスにも付いたもの）。
 *
 * @param {string} text ソース
 * @returns {{ from: number, to: number }[]} 文字列の区間（引用符を含む）
 */
function javaTestNameRanges(text) {
  const strings = new Map()
  const code = new Uint8Array(text.length)
  scanCode(text, JAVA_SCAN, (type, from, to) => {
    if (type === 'string') strings.set(from, to)
    if (type === 'code') code.fill(1, from, to)
  })
  const ranges = []
  for (const match of text.matchAll(/@DisplayName\(\s*(?=")/g)) {
    if (!code[match.index]) continue
    const from = match.index + match[0].length
    if (strings.has(from)) ranges.push({ from, to: strings.get(from) })
  }
  return ranges
}

/**
 * TypeScript・JavaScript の describe・it・test の名前の文字列の区間を返す。
 * 修飾（.only、.skip、.each(表) など）は読み飛ばし、名前が文字列でもテンプレートでもない呼び出しは取り出さない。
 *
 * @param {string} text ソース
 * @returns {{ from: number, to: number, template?: boolean }[]} 名前の区間（引用符を含む）。template はテンプレートで書いた名前
 */
function tsTestNameRanges(text) {
  const strings = new Map()
  const code = new Uint8Array(text.length)
  scanCode(text, {}, (type, from, to) => {
    if (type === 'string') strings.set(from, to)
    if (type === 'code') code.fill(1, from, to)
  })
  const ranges = []
  for (const match of text.matchAll(TS_TEST_CALL)) {
    if (!code[match.index]) continue
    let pos = match.index + match[0].length
    while (text[pos] === '.') {
      const modifier = /^\.(\w+)/.exec(text.slice(pos, pos + 20))
      if (!modifier) break
      pos += modifier[0].length
      if (!TS_ARGUMENT_MODIFIERS.has(modifier[1])) continue
      if (text[pos] === '(') {
        pos = closingParen(text, pos) + 1
      } else if (text[pos] === '`') {
        pos = templateEnd(text, pos)
      }
    }
    if (text[pos] !== '(') continue
    const from = pos + 1 + /^\s*/.exec(text.slice(pos + 1))[0].length
    if (text[from] === '`') ranges.push({ from, to: templateEnd(text, from), template: true })
    else if (strings.has(from)) ranges.push({ from, to: strings.get(from) })
  }
  return ranges
}

/**
 * テンプレートで書いたテスト名を、文字列の名前と同じ形にする。外側のバッククォートは文字列の区切りなので " に替え
 * （ja-text が名前の全体をインラインコードと読まないように）、`\``（エスケープしたバッククォート）は ` に戻す（名前の中のインラインコード）。
 * 埋め込み（`${…}`）は、中の式がコードなので X の 1 字に替える（インラインコードと同じ数え方）。式の中の改行は残し、行の数を変えない。
 *
 * @param {string} text ソース
 * @param {number} from 始まりの ` の位置
 * @param {number} to 終わりの次の位置
 * @returns {string} 名前（行の数は元の区間と同じ）
 */
function templateName(text, from, to) {
  let name = '"'
  let i = from + 1
  while (i < to) {
    if (text[i] === '\\') {
      name += text[i + 1] === '`' ? '`' : text.slice(i, i + 2)
      i += 2
    } else if (text[i] === '`') {
      name += '"'
      i += 1
    } else if (text[i] === '$' && text[i + 1] === '{') {
      const end = embeddedEnd(text, i + 2)
      name += `X${text.slice(i, end).replace(/[^\n]/g, '')}`
      i = end
    } else {
      name += text[i]
      i += 1
    }
  }
  return name
}

/**
 * テンプレートの埋め込み（`${…}`）を閉じる } の次の位置を返す。中の文字列・入れ子のテンプレート・波括弧は読み飛ばす。
 *
 * @param {string} text ソース
 * @param {number} start `${` の次の位置
 * @returns {number} 閉じる } の次の位置。閉じなければソースの終わり
 */
function embeddedEnd(text, start) {
  let braces = 0
  const at = scanCode(text, { start }, (type, from, to, depth) => {
    if (type !== 'code' || depth !== 0) return false
    if (text[from] === '{') braces += 1
    if (text[from] === '}') {
      if (braces === 0) return true
      braces -= 1
    }
    return false
  })
  return Math.min(at + 1, text.length)
}

/**
 * シェルの # のコメントの区間を返す。引用符の中、$# や ${#x}、1 行目の #! は含めない。
 * 限界：ヒアドキュメントの中は読み分けない（# で始まる行はコメントと読む）。
 *
 * @param {string} text ソース
 * @returns {{ from: number, to: number }[]} コメントの区間
 */
function shellCommentRanges(text) {
  const ranges = []
  let quote = ''
  for (let i = 0; i < text.length; i += 1) {
    const char = text[i]
    if (quote) {
      if (char === '\\' && quote === '"') i += 1
      else if (char === quote) quote = ''
    } else if (char === '\\') {
      i += 1
    } else if (char === "'" || char === '"') {
      quote = char
    } else if (char === '#' && (i === 0 || /[\s;&|(]/.test(text[i - 1]))) {
      if (i === 0 && text[1] === '!') {
        i = lineEnd(text, i)
        continue
      }
      const to = lineEnd(text, i)
      ranges.push({ from: i, to })
      i = to
    }
  }
  return ranges
}

/**
 * 行の終わり（改行の位置。最後の行ならソースの終わり）を返す。
 *
 * @param {string} text ソース
 * @param {number} from 探し始める位置
 * @returns {number} 行の終わりの位置
 */
function lineEnd(text, from) {
  const end = text.indexOf('\n', from)
  return end === -1 ? text.length : end
}

/**
 * コメントとテスト名の区間を返す。
 *
 * @param {string} text ソース
 * @param {'java' | 'kts' | 'ts' | 'sh'} kind ファイルの種類
 * @returns {{ from: number, to: number }[]} 区間
 */
function textRanges(text, kind) {
  if (kind === 'java') return [...commentRanges(text, JAVA_SCAN), ...javaTestNameRanges(text)]
  if (kind === 'kts') return commentRanges(text, JAVA_SCAN)
  if (kind === 'ts') return [...commentRanges(text, {}), ...tsTestNameRanges(text)]
  return shellCommentRanges(text)
}

/**
 * ソースから、コメントとテスト名を、元のファイルの行番号つきで取り出す。複数行のコメントは行ごとに分ける。
 *
 * @param {string} text ソース
 * @param {'java' | 'kts' | 'ts' | 'sh'} kind ファイルの種類（codeKind で求める）
 * @returns {{ line: number, text: string }[]} 行番号（1 から）と、その行にあるコメントとテスト名の文字列（行の順）
 */
export function extractCommentLines(text, kind) {
  const ranges = textRanges(text, kind).sort((a, b) => a.from - b.from)
  const lineStarts = [0]
  for (let i = text.indexOf('\n'); i !== -1; i = text.indexOf('\n', i + 1)) lineStarts.push(i + 1)
  const byLine = new Map()
  for (const { from, to, template } of ranges) {
    const line = lineStarts.findLastIndex((start) => start <= from)
    const segment = template ? templateName(text, from, to) : text.slice(from, to)
    segment.split('\n').forEach((raw, offset) => {
      const part = raw.replace(/\r$/, '')
      if (part.trim()) byLine.set(line + offset + 1, [...(byLine.get(line + offset + 1) ?? []), part])
    })
  }
  return [...byLine].map(([line, parts]) => ({ line, text: parts.join(' ') }))
}

/**
 * 行末のコメント（同じ行のコメントの前にコードがあるもの）が始まる行を返す。
 * commentText はコメントの位置を残さないので、行頭のコメントと見分けるのに使う（ja-text が、前後の行とつながないために使う）。
 * テスト名と、ブロックコメントの 2 行目からの行は含めない。
 *
 * @param {string} text ソース
 * @param {'java' | 'kts' | 'ts' | 'sh'} kind ファイルの種類
 * @returns {Set<number>} 行番号（1 から）
 */
export function trailingCommentLines(text, kind) {
  const ranges = kind === 'sh' ? shellCommentRanges(text) : commentRanges(text, kind === 'ts' ? {} : JAVA_SCAN)
  const lines = new Set()
  for (const { from } of ranges) {
    const start = text.lastIndexOf('\n', from - 1) + 1
    if (text.slice(start, from).trim()) lines.add(text.slice(0, start).split('\n').length)
  }
  return lines
}

/**
 * ソースから、コメントとテスト名だけを残した文書を返す。行の数と、残した文字の行は元のソースと同じなので、行番号がそのまま使える。
 *
 * @param {string} text ソース
 * @param {'java' | 'kts' | 'ts' | 'sh'} kind ファイルの種類
 * @returns {string} 行番号が元のソースと同じ文書
 */
export function commentText(text, kind) {
  const lines = new Array(text.split('\n').length).fill('')
  for (const { line, text: part } of extractCommentLines(text, kind)) lines[line - 1] = part
  return lines.join('\n')
}
