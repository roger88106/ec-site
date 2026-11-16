// 画面のテスト・テストの補助・E2E の規則：テストの間の空行と、組み込みの名前のシャドーイング
import { createLineLocator } from '../lib/line-number.mjs'
import { stripComments } from '../lib/strip-comments.mjs'
import { statementEnd } from './source-text.mjs'

/** テストの呼び出しを閉じる行（`})`、`}, 30_000)`、`});`）。コメントと文字列を除いた行に当てる */
const CALL_CLOSE_LINE = /^\}.*\)[;,]?$/

/**
 * テスト・グループの呼び出しの始まりの行（test、it、describe と、test.each(…)、describe.skip(…)、test.describe(… など）。
 * `.` の後はテスト・グループの形に限る。test.beforeEach・test.use・test.step・test.info・test.setTimeout などは含めない（await 付きの test.step も含めない）
 */
const TEST_CALL_START = /^(?:test|it|describe)(?:\.(?:each|skip|only|fixme|fail|todo|concurrent|describe|serial|parallel))*\s*[(`]/

/** 1 行で閉じるテストの呼び出しの終わり（`)`、`);`） */
const CALL_ONE_LINE_END = /\)[;,]?$/

/**
 * 1 行で閉じる、テスト・グループの呼び出しかを返す（test('a', () => {}) のように、始まりの行で括弧が閉じるもの）。
 *
 * @param {string} line コメントと文字列を除いた行（前後の空白を除く）
 * @returns {boolean} 1 行で閉じていれば true
 */
function isOneLineTestCall(line) {
  if (!TEST_CALL_START.test(line) || !CALL_ONE_LINE_END.test(line)) return false
  let depth = 0
  for (const char of line) {
    if (char === '(') depth += 1
    else if (char === ')') depth -= 1
  }
  return depth === 0
}

/**
 * テストの間に空行がない箇所を探す（フロントのテストの書き方の「テストとテストの間に空行を置く」）。
 * テストを閉じる行（`})` と、1 行で閉じるテストの行）の直後に、空行なしで test・it・describe が続く箇所。間にコメントの行があっても、空行がなければ見つける。
 * test.beforeEach・test.use・test.step などはテスト・グループでないので、続く側として見ない。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行（続く側の test・it・describe の行）
 */
export function findMissingTestSpacing(text) {
  const original = text.split('\n')
  const code = stripComments(text, { blankStrings: true }).split('\n')
  const hits = []
  code.forEach((line, index) => {
    if (!CALL_CLOSE_LINE.test(line.trim()) && !isOneLineTestCall(line.trim())) return
    let next = index + 1
    while (next < code.length && code[next].trim() === '' && original[next].trim() !== '') next += 1
    if (next < code.length && TEST_CALL_START.test(code[next].trim())) hits.push({ line: next + 1, text: original[next].trim() })
  })
  return hits
}

/** 組み込みの名前の一覧（TypeScript の lib から生成する。scripts/ci/globals/lib-globals.mjs） */
export const BUILTIN_NAMES_FILE = 'scripts/ci/globals/lib-globals.json'

/** const・let の宣言の始まり（as const の const と、プロパティの const は除く） */
const DECLARATION_KEYWORD = /(?<![\w$.])(?:const|let)\s+/g

/** 宣言の名前の後ろに続いてよいもの（初期値、型の注釈、次の宣言、文の終わり、for…of・for…in、行の終わり） */
const BINDING_FOLLOWER = /^[ \t]*(?:[=,:;)}]|of\b|in\b|\r?\n|$)/

/** 変数の名前 */
const IDENTIFIER_ONLY = /^[\w$]+$/

/**
 * 深さ 0 にある、初めての文字の位置を返す（括弧の中は数えない）。= は、=>・==・<=・>=・!= の一部でないものだけを数える。
 *
 * @param {string} code 文字列を空白にしたソース
 * @param {string} char 探す文字
 * @returns {number} 位置。なければ -1
 */
function topLevelIndexOf(code, char) {
  let depth = 0
  for (let i = 0; i < code.length; i += 1) {
    if ('([{'.includes(code[i])) depth += 1
    else if (')]}'.includes(code[i])) depth -= 1
    else if (depth === 0 && code[i] === char) {
      if (char === '=' && (/[=>]/.test(code[i + 1] ?? '') || /[=!<>]/.test(code[i - 1] ?? ''))) continue
      return i
    }
  }
  return -1
}

/**
 * 分割代入の対象（名前か入れ子の分割代入。後ろに既定値があってもよい）が宣言する名前を返す。
 *
 * @param {string} target 対象
 * @returns {string[]} 名前
 */
function targetNames(target) {
  const equals = topLevelIndexOf(target, '=')
  const value = (equals >= 0 ? target.slice(0, equals) : target).trim()
  if (value.startsWith('{') || value.startsWith('[')) return patternNames(value)
  return IDENTIFIER_ONLY.test(value) ? [value] : []
}

/**
 * 分割代入のパターン（{ … } か [ … ]）が宣言する名前を返す。
 * オブジェクトの { a: b } は右側の b が宣言される名前で、{ a } は a、{ ...rest } は rest。配列は全ての要素を見る。
 *
 * @param {string} pattern 括弧を含むパターン
 * @returns {string[]} 名前
 */
function patternNames(pattern) {
  const isObject = pattern.startsWith('{')
  const inner = pattern.slice(1, -1)
  const parts = []
  let depth = 0
  let start = 0
  for (let i = 0; i <= inner.length; i += 1) {
    if (i === inner.length || (inner[i] === ',' && depth === 0)) {
      parts.push(inner.slice(start, i).trim())
      start = i + 1
    } else if ('([{'.includes(inner[i])) depth += 1
    else if (')]}'.includes(inner[i])) depth -= 1
  }
  return parts.flatMap((part) => {
    if (part === '') return []
    if (part.startsWith('...')) return targetNames(part.slice(3))
    if (!isObject) return targetNames(part)
    const colon = topLevelIndexOf(part, ':')
    const equals = topLevelIndexOf(part, '=')
    return colon >= 0 && (equals < 0 || colon < equals) ? targetNames(part.slice(colon + 1)) : targetNames(part)
  })
}

/**
 * 宣言の名前（名前か分割代入のパターン）を、位置から読む。
 *
 * @param {string} code 文字列を空白にしたソース
 * @param {number} from 始まりの位置
 * @returns {{ names: string[], start: number, end: number } | null} 名前、始まりと終わり（含まない）の位置。名前の形でなければ null
 */
function readBinding(code, from) {
  const start = from + /^\s*/.exec(code.slice(from))[0].length
  let end = start
  if (code[start] === '{' || code[start] === '[') {
    let depth = 0
    for (end = start; end < code.length; end += 1) {
      if ('([{'.includes(code[end])) depth += 1
      else if (')]}'.includes(code[end])) {
        depth -= 1
        if (depth === 0) break
      }
    }
    if (end >= code.length) return null
    end += 1
    return BINDING_FOLLOWER.test(code.slice(end)) ? { names: patternNames(code.slice(start, end)), start, end } : null
  }
  end = start + (/^[\w$]*/.exec(code.slice(start))[0].length)
  if (end === start || !BINDING_FOLLOWER.test(code.slice(end))) return null
  return { names: [code.slice(start, end)], start, end }
}

/**
 * 名前の後ろの型の注釈（: の後ろ）を読み飛ばし、初期値の = か、次の宣言の , か、文の終わりの位置を返す。
 *
 * @param {string} code 文字列を空白にしたソース
 * @param {number} from 名前の終わりの位置
 * @returns {number} 位置
 */
function skipAnnotation(code, from) {
  let at = from + /^[ \t]*/.exec(code.slice(from))[0].length
  if (code[at] !== ':') return at
  let depth = 0
  for (at += 1; at < code.length; at += 1) {
    const char = code[at]
    const arrow = char === '>' && code[at - 1] === '='
    if ('([{<'.includes(char)) depth += 1
    else if (')]}>'.includes(char) && !arrow) depth -= 1
    else if (depth <= 0 && (char === ',' || char === ';' || char === '\n' || (char === '=' && code[at + 1] !== '>'))) break
  }
  return at
}

/**
 * ジェネリクスの型引数 `<…>` の閉じの位置を返す。呼び出し側は、`<` が識別子の直後にあるものだけを渡す
 * （比較の `a < b` は演算子の前後に空白を置くので渡されない）。`=>` の `>` は数えない。対応する `>` が文の終わりまでになければ、比較とみなす。
 *
 * @param {string} code 文字列を空白にしたソース
 * @param {number} open `<` の位置
 * @param {number} end 文の終わりの位置
 * @returns {number} 対応する `>` の位置。なければ -1
 */
function genericsEnd(code, open, end) {
  let depth = 0
  for (let i = open; i < end; i += 1) {
    if (code[i] === '<') depth += 1
    else if (code[i] === '>' && code[i - 1] !== '=') {
      depth -= 1
      if (depth === 0) return i
    }
  }
  return -1
}

/**
 * `<` がジェネリクスの型引数の始まりかを返す。識別子の直後（new Map<string, number>）か、
 * `=` か `(` の直後（const f = <T,>(a: T) => a の型引数を持つ矢印関数）にあるものを、型引数とみなす。
 *
 * @param {string} code 文字列を空白にしたソース
 * @param {number} open `<` の位置
 * @returns {boolean} 型引数の始まりなら true
 */
function opensGenerics(code, open) {
  if (/[\w$]/.test(code[open - 1] ?? '')) return true
  const before = code.slice(0, open).trimEnd().at(-1)
  return before === '=' || before === '('
}

/**
 * 初期値の後ろの、次の宣言の始まり（深さ 0 の , の次の位置）を返す。
 *
 * @param {string} code 文字列を空白にしたソース
 * @param {number} from 初期値の始まりの位置
 * @returns {number} 次の宣言の始まりの位置。なければ -1
 */
function nextDeclarator(code, from) {
  const end = statementEnd(code, from)
  let depth = 0
  for (let i = from; i < end; i += 1) {
    if (code[i] === '<' && opensGenerics(code, i)) {
      const close = genericsEnd(code, i, end)
      if (close > i) i = close
    } else if ('([{'.includes(code[i])) depth += 1
    else if (')]}'.includes(code[i])) depth -= 1
    else if (depth === 0 && code[i] === ',') return i + 1
  }
  return -1
}

/**
 * フロントのテストと E2E で、組み込みの名前と同じ名前の const・let の宣言を探す。組み込みの名前は、TypeScript の lib が宣言するグローバルの名前である。
 * 規則は、フロントのテストの書き方の「組み込みの名前をシャドーイングしない」。エラーにならずにグローバル変数の値が使われるのを防ぐ。
 * 対象は、const・let の宣言の名前（1 つの宣言に並べたもの、分割代入、for…of・for…in の宣言を含む）。
 * 関数の引数、catch の変数、関数・クラスの宣言、import は対象にしない。
 * 分割代入の { a: name } は右側の name が宣言される名前で、{ name: x } は x。
 *
 * @param {string} text ソース
 * @param {Set<string>} names 組み込みの名前
 * @returns {{ line: number, text: string, name: string }[]} 見つけた宣言（名前ごと。行は名前か、分割代入の始まりの行）
 */
export function findBuiltinShadowing(text, names) {
  const code = stripComments(text, { blankStrings: true })
  const lineOf = createLineLocator(code)
  const original = text.split('\n')
  const hits = []
  for (const match of code.matchAll(DECLARATION_KEYWORD)) {
    if (/\bas\s+$/.test(code.slice(Math.max(0, match.index - 12), match.index))) continue
    let from = match.index + match[0].length
    while (from >= 0) {
      const binding = readBinding(code, from)
      if (!binding) break
      for (const name of binding.names) {
        if (names.has(name)) hits.push({ line: lineOf(binding.start), text: original[lineOf(binding.start) - 1].trim(), name })
      }
      const at = skipAnnotation(code, binding.end)
      if (code[at] === '=') from = nextDeclarator(code, at + 1)
      else from = code[at] === ',' ? at + 1 : -1
    }
  }
  return hits
}

/** 共有の名前と同じ名前の定義の除外のキー（<画面>:<名前>）。画面は store か admin */
