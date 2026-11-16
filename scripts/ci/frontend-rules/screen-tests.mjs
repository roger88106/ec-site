// 画面のテストの規則：複数の結果を並びのインデックスで選ばない（E2E にも当てる）、要素のプロパティ・属性を toBe・toEqual で比べない。
// 変数の範囲と式の終わりを追うので、Biome の plugin では書けない
import { closingParen } from '../lib/closing-paren.mjs'
import { createLineLocator } from '../lib/line-number.mjs'
import { scanCode } from '../lib/scan-code.mjs'
import { stripComments } from '../lib/strip-comments.mjs'
import { statementEnd, toHits } from './source-text.mjs'

/** getAllBy…・findAllBy…・queryAllBy… の呼び出しの始まり */
const MULTIPLE_QUERY = /\b(?:getAll|findAll|queryAll)By\w+\(/g

/** 呼び出しの後の、結果をそのまま持つ書き方（空白、閉じ括弧、!、;、as の型の変換）。`)` は await を囲む括弧とスプレッド・Array.from の閉じ */
const WRAPPER_TAIL = /^(?:\s|[)\]!;]|\bas\s+(?:readonly\s+)?[\w$.]+(?:<[^<>]*>)?(?:\[\])*)*/

/** 呼び出しの直後（WRAPPER_TAIL の後）の、並びのインデックス（[数字]、.at(数字)。?. を許す） */
const INDEX_ACCESS = /^(?:\?\.)?(?:\[\s*-?\d+\s*\]|\.at\(\s*-?\d+\s*\))/

/** 呼び出しの前の、結果をそのまま持つ書き方（await、括弧、スプレッド、Array.from(、screen.・within(…).・rtl.screen. などの受け手） */
const CALL_PREFIX = String.raw`(?:\s|\(|await\b|\[\s*\.\.\.|Array\s*\.\s*from\s*\(|within\s*\((?:[^()]|\((?:[^()]|\([^()]*\))*\))*\)\s*\.|[\w$]+\s*\.)*`

/**
 * 変数に入れる文（const items = await screen.getAllByRole(…)、const items: T[] = …、let の後の items = … など）の、呼び出しの前の部分。
 * 1 つ目が const・let・var の名前、2 つ目が配列の分割代入、3 つ目がオブジェクトの分割代入、4 つ目が宣言のない代入の名前
 */
const ASSIGNMENT_BEFORE = new RegExp(
  String.raw`(?:\b(?:const|let|var)\s+(?:([\w$]+)|(\[[^\]]*\])|(\{[^}]*\}))(?:\s*:[^=;]+?)?|(?<![.\w$])([\w$]+))\s*=(?![=>])${CALL_PREFIX}$`,
)

/** 呼び出しの前を遡って調べる字数 */
const ASSIGNMENT_LOOKBEHIND = 600

/** 結果の後に式が続く（変数に入れる前に変換する）ことを表す、後ろの始まり */
const CONTINUES = /^(?:\?\.|\.|\(|\[|&&|\|\||\?|:|\+|\*|\/|%|\||&|==|!=)/

/**
 * 名前を、同じ名前の別の宣言（const・let・var、分割代入、関数の引数、アロー関数の引数）で上書きする書き方。
 *
 * @param {string} name 変数の名前
 * @returns {RegExp} 上書きの宣言を探す正規表現
 */
function shadowingPattern(name) {
  const id = `(?<![\\w$])${name.replace(/\$/g, '\\$')}(?![\\w$])`
  return new RegExp(
    `\\b(?:const|let|var)\\s+(?:${id}|\\{[^}]*${id}[^}]*\\}|\\[[^\\]]*${id}[^\\]]*\\])|\\bfunction\\b[^(]*\\([^)]*${id}|\\([^()]*${id}[^()]*\\)\\s*(?::[^=()]*)?=>|${id}\\s*=>`,
  )
}

/**
 * 位置から後ろで、その位置を含むブロックが閉じる位置を返す。
 *
 * @param {string} code ソース（コメントと文字列を空白にしたもの）
 * @param {number} from 始まりの位置
 * @returns {number} 閉じる } の位置。なければソースの終わり
 */
function blockEnd(code, from) {
  let depth = 0
  return scanCode(code, { start: from }, (type, at) => {
    if (type !== 'code') return false
    if (code[at] === '{') depth += 1
    else if (code[at] === '}') {
      depth -= 1
      return depth < 0
    }
    return false
  })
}

/**
 * getAllBy…・findAllBy…・queryAllBy… の結果を、並びのインデックスで選ぶ所を探す。
 * 規則は、フロントのテストの書き方の「画面に見えるもので探す」。並びの 1 つ目は使わない。
 * 呼び出しの直後の [数字]・.at(数字) と、変数に入れて（配列の分割代入を含む）から [数字]・.at(数字) で選ぶ所を見つける。
 * 変数に入れる形：const・let・var の宣言と、宣言のない代入。= の後の改行、await、括弧、as の型の変換、型の注釈、
 * 受け手（screen.、within(…).）、スプレッド（[...x]）、Array.from(x) をはさんでもよい。
 * 変数の使用は、宣言した所より後で、同じ名前が別の宣言（const・let・var、分割代入、引数）で上書きされるまで、
 * const・let・var の宣言ならそのブロックが閉じるまでの範囲だけ見る（別のテストの同じ名前の普通の配列は見ない）。
 * 見つけない形：変数に入れる前に map・filter などをつなぐもの、関数の引数や戻り値で渡して選ぶもの。
 * Playwright の .first()・.nth() は対象にしない
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findIndexedMultiples(text) {
  const code = stripComments(text, { blankStrings: true })
  const lineOf = createLineLocator(code)
  const lineNumbers = []
  for (const match of code.matchAll(MULTIPLE_QUERY)) {
    const close = closingParen(code, match.index + match[0].length - 1)
    if (close >= code.length) continue
    const after = code.slice(close + 1)
    const wrapped = after.slice(WRAPPER_TAIL.exec(after)[0].length)
    if (INDEX_ACCESS.test(wrapped)) {
      lineNumbers.push(lineOf(match.index))
      continue
    }
    if (CONTINUES.test(wrapped)) continue
    const windowStart = Math.max(0, match.index - ASSIGNMENT_LOOKBEHIND)
    const assignment = ASSIGNMENT_BEFORE.exec(code.slice(windowStart, match.index))
    if (!assignment) continue
    if (assignment[2] || (assignment[3] && /^\{\s*\d+\s*:/.test(assignment[3]))) {
      lineNumbers.push(lineOf(match.index))
      continue
    }
    const name = assignment[1] ?? assignment[4]
    if (name === undefined) continue
    const limit = assignment[1] === undefined ? code.length : blockEnd(code, close + 1)
    const scope = code.slice(close + 1, limit)
    const shadow = shadowingPattern(name).exec(scope)
    const range = shadow ? scope.slice(0, shadow.index) : scope
    const use = new RegExp(`(?<![.\\w$])${name.replace(/\$/g, '\\$')}(?:\\?\\.|!)?(?:\\[\\s*-?\\d+\\s*\\]|\\.at\\(\\s*-?\\d+\\s*\\))`, 'g')
    for (const usage of range.matchAll(use)) lineNumbers.push(lineOf(close + 1 + usage.index))
  }
  return toHits(text, lineNumbers)
}

/** expect( の始まり */
const EXPECT_CALL = /(?<![.\w$])expect\(/g

/** expect の後の、toBe・toEqual（.not などをはさんでもよい） */
const BE_OR_EQUAL = /^\s*(?:\.(?:not|resolves|rejects)\s*)*\.(?:toBe|toEqual)\(/

/** expect の引数の最後が、要素のプロパティか属性（.disabled、.checked、.value、.getAttribute(…)。?. も）。その前が受け手 */
const PROPERTY_TAIL = /\??\.(?:disabled|checked|value)$|\??\.getAttribute\((?:[^()]|\((?:[^()]|\([^()]*\))*\))*\)$/

/** 要素を返す式（getBy…・queryBy…・findBy…、screen.、within(、document.・container. からたどる形） */
const ELEMENT_SOURCE = /\b(?:get|query|find)(?:All)?By\w+\(|(?<![.\w$])screen\s*\.|\bwithin\s*\(|(?<![.\w$])(?:document|container)\s*\./

/** 要素の型（HTMLInputElement など） */
const ELEMENT_TYPE = /\bHTML\w*Element\b/

/**
 * 変数が要素かを、その変数の直前の宣言から判定する。
 * const・let・var の宣言は、型の注釈が要素の型か、初期値が要素を返す式か要素の型への変換であるもの。引数などの `名前: HTMLInputElement` の型の注釈も要素とする
 *
 * @param {string} code ソース（コメントと文字列を空白にしたもの）
 * @param {string} name 変数の名前
 * @param {number} before この位置より前の宣言を見る
 * @returns {boolean} 要素なら true
 */
function isElementVariable(code, name, before) {
  const id = name.replace(/\$/g, '\\$')
  const declaration = new RegExp(`\\b(?:const|let|var)\\s+${id}(?![\\w$])|(?<![\\w$.])${id}\\s*\\??:\\s*(?:Readonly<)?HTML\\w*Element\\b`, 'g')
  let last = null
  for (const match of code.slice(0, before).matchAll(declaration)) last = match
  if (!last) return false
  if (!/^(?:const|let|var)/.test(last[0])) return true
  const rest = code.slice(last.index + last[0].length)
  const assign = /^\s*(?::[^=;]*?)?=(?![=>])/.exec(rest)
  if (!assign) return false
  if (ELEMENT_TYPE.test(assign[0])) return true
  const init = code.slice(last.index + last[0].length + assign[0].length, statementEnd(code, last.index + last[0].length + assign[0].length))
  return ELEMENT_SOURCE.test(init) || ELEMENT_TYPE.test(init)
}

/**
 * 要素のプロパティ・属性（.disabled、.checked、.value、getAttribute(…)）を toBe・toEqual で比べる所を探す。
 * 規則は、フロントのテストの書き方の「要素のプロパティ・属性・状態を jest-dom で確認する」。
 * expect の引数の最後がそのプロパティか属性であるもので、受け手が要素のものだけを見つける。受け手が要素なのは、
 * getBy…・queryBy…・findBy…・screen.・within( の結果か、それを入れた変数（要素の型の注釈や変換も）、document.・container. からたどる形。
 * `parts.map((part) => part.value)` のように途中に出てくるものと、`result.current.value` のように要素でない値は見つけない。
 * .textContent は対象にしない
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findPropertyComparisons(text) {
  const code = stripComments(text, { blankStrings: true })
  const lineOf = createLineLocator(code)
  const lineNumbers = []
  for (const match of code.matchAll(EXPECT_CALL)) {
    const open = match.index + match[0].length - 1
    const close = closingParen(code, open)
    if (close >= code.length || !BE_OR_EQUAL.test(code.slice(close + 1))) continue
    const argument = code.slice(open + 1, close).trim().replace(/,$/, '').trim()
    const tail = PROPERTY_TAIL.exec(argument)
    if (!tail) continue
    const receiver = argument.slice(0, tail.index)
    const simple = receiver
      .replace(/\bas\s+[\w$.<>[\]]+/g, '')
      .replace(/\.at\([^)]*\)|\[[^\]]*\]/g, '')
      .replace(/[()!?\s]/g, '')
    const isElement = /^[\w$]+$/.test(simple) ? ELEMENT_TYPE.test(receiver) || isElementVariable(code, simple, match.index) : ELEMENT_SOURCE.test(receiver) || ELEMENT_TYPE.test(receiver)
    if (isElement) lineNumbers.push(lineOf(match.index))
  }
  return toHits(text, lineNumbers)
}
