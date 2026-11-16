// E2E の規則：ボタンの名前を完全一致で探す、タイムアウトを数字のリテラルで書かない
import { closingParen } from '../lib/closing-paren.mjs'
import { createLineLocator } from '../lib/line-number.mjs'
import { stripComments } from '../lib/strip-comments.mjs'
import { toHits } from './source-text.mjs'

/**
 * 括弧の中の引数や、オブジェクトの項目を、深さ 0 のカンマで分ける。
 *
 * @param {string} code 文字列を空白にしたソース（括弧とカンマが文字列の中にない）
 * @param {string} plain code と同じ位置で、文字列を残したソース（空の部分かの判定に使う）
 * @param {number} from 始まりの位置
 * @param {number} to 終わりの位置（含まない）
 * @returns {[number, number][]} 部分の範囲（始まりと終わりの次の位置。末尾のカンマの後の空の部分は除く）
 */
function splitTopLevel(code, plain, from, to) {
  const parts = []
  let depth = 0
  let start = from
  for (let i = from; i < to; i += 1) {
    if ('([{'.includes(code[i])) depth += 1
    else if (')]}'.includes(code[i])) depth -= 1
    else if (code[i] === ',' && depth === 0) {
      parts.push([start, i])
      start = i + 1
    }
  }
  parts.push([start, to])
  // 末尾のカンマの後の、空の部分を除く（文字列は code では空白なので、plain で判定する）
  const [last] = parts.slice(-1)
  return parts.length > 1 && plain.slice(...last).trim() === '' ? parts.slice(0, -1) : parts
}

/** getByRole の呼び出しの始まり */
const GET_BY_ROLE = /\bgetByRole\s*\(/g

/** 先頭の ^ と末尾の $ を固定した正規表現のリテラル（フラグを許す。最上位の選択 | は isAnchoredRegex が別に見る） */
const ANCHORED_REGEX = /^\/\^([\s\S]*)\$\/[a-z]*$/

/** オプションの name のキー（name、'name'、"name"、['name']）と、後ろの : */
const NAME_KEY = /^(?:name|(['"`])name\1|\[\s*(['"`])name\2\s*\])\s*:/

/**
 * 正規表現のリテラルが、先頭の ^ と末尾の $ で固定されているかを返す。
 * 末尾の $ がエスケープされていると（/^合計\$/）、文字の $ に一致するだけで行末を固定していないので固定とみなさない（\\$ の $ は固定）。
 * 最上位に | があると（/^a|b$/）、^a と b$ の選択で部分一致になるので固定とみなさない。グループ・文字クラス・エスケープの中の | は選択でない。
 *
 * @param {string} literal 正規表現のリテラル
 * @returns {boolean} 固定していれば true
 */
function isAnchoredRegex(literal) {
  const match = ANCHORED_REGEX.exec(literal)
  if (!match) return false
  const body = match[1]
  // 末尾の $ の前の \ が奇数個なら、その $ はエスケープされた文字そのもので、行末を固定していない
  if ((/\\*$/.exec(body)[0].length & 1) === 1) return false
  let depth = 0
  let inClass = false
  for (let i = 0; i < body.length; i += 1) {
    const char = body[i]
    if (char === '\\') i += 1
    else if (inClass) inClass = char !== ']'
    else if (char === '[') inClass = true
    else if (char === '(') depth += 1
    else if (char === ')') depth -= 1
    else if (char === '|' && depth === 0) return false
  }
  return true
}

/**
 * E2E で、ボタンを名前で探すのに、完全一致でない箇所を探す（フロントのテストの書き方の「画面に見えるもので探す」）。
 * name が正規表現のリテラルなら、先頭の ^ と末尾の $ を固定していること。それ以外（文字列、変数、テンプレート文字列）なら exact: true が付いていること。
 * 役割が button の getByRole だけを見る（row.getByRole(…) のような連鎖の途中、複数の行にまたがる呼び出しも読む）。
 * name のない呼び出しは名前で探さないので対象にしない。オプションがオブジェクトのリテラルでないとき、
 * または展開（...）を含み name を読めないときは、名前を読めないので見つける。
 * name に正規表現を入れた変数を渡す書き方は追わない（変数の名前は文字列にし、exact: true を付ける。フロントのテストの書き方の「画面に見えるもので探す」）
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行（getByRole の行）
 */
export function findButtonNameChecks(text) {
  // 構造を読むための、文字列と正規表現を空白にしたソースと、値を読むための、文字列と正規表現を残したソース（位置は同じ）
  const code = stripComments(text, { blankStrings: true })
  const plain = stripComments(text, { keepRegex: true })
  const lineOf = createLineLocator(code)
  const lineNumbers = []
  for (const match of code.matchAll(GET_BY_ROLE)) {
    const open = match.index + match[0].length - 1
    const close = closingParen(code, open)
    if (close >= code.length) continue
    const [role, options] = splitTopLevel(code, plain, open + 1, close)
    if (!/^(['"`])button\1$/.test(plain.slice(...role).trim()) || !options) continue
    const optionsCode = code.slice(...options).trim()
    if (!optionsCode.startsWith('{') || !optionsCode.endsWith('}')) {
      lineNumbers.push(lineOf(match.index))
      continue
    }
    const objectFrom = options[0] + code.slice(...options).indexOf('{')
    const objectTo = options[0] + code.slice(...options).trimEnd().length - 1
    let nameKind = null
    let exact = false
    let hasSpread = false
    for (const item of splitTopLevel(code, plain, objectFrom + 1, objectTo)) {
      const masked = code.slice(...item).trim()
      const value = plain.slice(...item).trim()
      const nameKey = NAME_KEY.exec(value)
      if (nameKey || value === 'name') {
        const nameValue = nameKey ? value.slice(nameKey[0].length).trim() : null
        if (nameValue?.startsWith('/')) nameKind = isAnchoredRegex(nameValue) ? 'anchored' : 'regex'
        else nameKind = 'other'
      } else if (/^exact\s*:\s*true$/.test(masked)) exact = true
      else if (masked.startsWith('...')) hasSpread = true
    }
    if (nameKind === null && hasSpread) lineNumbers.push(lineOf(match.index))
    if (nameKind === null || nameKind === 'anchored') continue
    if (nameKind === 'regex' || !exact) lineNumbers.push(lineOf(match.index))
  }
  return toHits(text, lineNumbers)
}

/** タイムアウトの定数を定義するファイル。数字のリテラルを書いてよい */
const TIMEOUT_CONSTANTS_FILE = 'e2e/src/waits.ts'

/**
 * タイムアウトの数字の検査を当てるファイルに絞る（タイムアウトの定数を定義するファイルを除く）。
 *
 * @param {string[]} files E2E のファイル
 * @returns {string[]} 検査するファイル（順は保つ）
 */
export function timeoutTargets(files) {
  return files.filter((file) => file !== TIMEOUT_CONSTANTS_FILE)
}

/** timeout を含む項目（timeout、actionTimeout、timeoutMs、navigationTimeoutMs など）と intervals の項目 */
const TIMEOUT_PROPERTY = /\b(?:[\w$]*timeout[\w$]*|intervals)\s*:(?!:)/gi

/** 引用符で囲んだ timeout を含む項目と intervals の項目（文字列を残したソースに当てる） */
const QUOTED_TIMEOUT_PROPERTY = /(['"`])(?:[\w$]*timeout[\w$]*|intervals)\1\s*:(?!:)/gi

/** setTimeout・test.setTimeout・setDefaultTimeout・setDefaultNavigationTimeout の呼び出しの始まり */
const SET_TIMEOUT = /\b(?:setTimeout|setDefaultTimeout|setDefaultNavigationTimeout)\s*\(/g

/** 型の注釈つきのデフォルト引数の値（timeout: number = 5000 の `number = 5000`）。項目の値でないので見ない */
const TYPE_ANNOTATED_DEFAULT = /^\s*[\w$.<>[\]|& ]+?(?<![<>!=])=(?![=>])/

/** 数字のリテラルの始まり（識別子の一部の数字は除く） */
const NUMBER_LITERAL = /(?<![\w$.])\d/

/**
 * 式の終わりの位置を返す（深さ 0 のカンマか、囲んでいる括弧の閉じ）。
 *
 * @param {string} code 文字列を空白にしたソース
 * @param {number} from 式の始まりの位置
 * @returns {number} 式の終わりの位置。なければソースの終わり
 */
function expressionEnd(code, from) {
  let depth = 0
  for (let i = from; i < code.length; i += 1) {
    if ('([{'.includes(code[i])) depth += 1
    else if (')]}'.includes(code[i])) {
      if (depth === 0) return i
      depth -= 1
    } else if ((code[i] === ',' || code[i] === ';') && depth === 0) return i
  }
  return code.length
}

/**
 * E2E で、タイムアウトを数字のリテラルで書く行を探す（テストの書き方の「待ち方」。e2e/src/waits.ts の定数を使う）。
 * timeout を含む項目（timeout、actionTimeout、navigationTimeout など。引用符で囲んだキーを含む）と intervals の項目の値、
 * setTimeout・test.setTimeout・setDefaultTimeout・setDefaultNavigationTimeout の引数（引数が複数なら最後の引数。global の setTimeout は最後が待つ時間）に、
 * 数字のリテラルを含む式（10_000、5 * 1000、ASYNC_TIMEOUT * 2 など）を見つける。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findTimeoutLiterals(text) {
  const code = stripComments(text, { blankStrings: true })
  const plain = stripComments(text)
  const lineOf = createLineLocator(code)
  const lineNumbers = []
  for (const match of code.matchAll(TIMEOUT_PROPERTY)) {
    const from = match.index + match[0].length
    const value = code.slice(from, expressionEnd(code, from))
    if (code.slice(0, match.index).trimEnd().endsWith('?') || TYPE_ANNOTATED_DEFAULT.test(value)) continue
    if (NUMBER_LITERAL.test(value)) lineNumbers.push(lineOf(match.index))
  }
  for (const match of plain.matchAll(QUOTED_TIMEOUT_PROPERTY)) {
    // 文字列の中の文字（"a 'timeout': 5" など）は、直前が { か , のときだけキーとみなす
    const before = plain.slice(0, match.index).trimEnd().at(-1)
    if (code[match.index] !== ' ' || (before !== '{' && before !== ',')) continue
    const from = match.index + match[0].length
    if (NUMBER_LITERAL.test(code.slice(from, expressionEnd(code, from)))) lineNumbers.push(lineOf(match.index))
  }
  for (const match of code.matchAll(SET_TIMEOUT)) {
    const open = match.index + match[0].length - 1
    const close = closingParen(code, open)
    if (close >= code.length) continue
    const target = splitTopLevel(code, plain, open + 1, close).at(-1)
    if (target && NUMBER_LITERAL.test(code.slice(...target))) lineNumbers.push(lineOf(match.index))
  }
  return toHits(text, lineNumbers)
}
