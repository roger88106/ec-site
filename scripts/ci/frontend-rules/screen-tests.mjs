// 画面のテストの規則：複数の結果を並びのインデックスで選ばない。
// 変数の範囲を追うので、Biome の plugin では書けない
import { closingParen } from '../lib/closing-paren.mjs'
import { createLineLocator } from '../lib/line-number.mjs'
import { scanCode } from '../lib/scan-code.mjs'
import { stripComments } from '../lib/strip-comments.mjs'

/**
 * 行の番号と表示する文字（元のソースの行）にする。
 *
 * @param {string} text 元のソース
 * @param {number[]} lineNumbers 行の番号（1 始まり）
 * @returns {{ line: number, text: string }[]} 行の順、重複なし
 */
function toHits(text, lineNumbers) {
  const original = text.split('\n')
  return [...new Set(lineNumbers)].sort((a, b) => a - b).map((line) => ({ line, text: original[line - 1].trim() }))
}

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
  const id = `(?<![\\w$])${RegExp.escape(name)}(?![\\w$])`
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
 * 見つけない形：変数に入れる前に map・filter などをつなぐもの、関数の引数や戻り値で渡して選ぶもの
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
    const use = new RegExp(`(?<![.\\w$])${RegExp.escape(name)}(?:\\?\\.|!)?(?:\\[\\s*-?\\d+\\s*\\]|\\.at\\(\\s*-?\\d+\\s*\\))`, 'g')
    for (const usage of range.matchAll(use)) lineNumbers.push(lineOf(close + 1 + usage.index))
  }
  return toHits(text, lineNumbers)
}
