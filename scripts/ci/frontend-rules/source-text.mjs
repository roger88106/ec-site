// frontend-rules の規則が共通に使う、ソースの読み方（報告の行、式の文の終わり）
import { scanCode } from '../lib/scan-code.mjs'

/**
 * 行の番号と表示する文字（元のソースの行）にする。
 *
 * @param {string} text 元のソース
 * @param {number[]} lineNumbers 行の番号（1 始まり）
 * @returns {{ line: number, text: string }[]} 行の順、重複なし
 */
export function toHits(text, lineNumbers) {
  const original = text.split('\n')
  return [...new Set(lineNumbers)].sort((a, b) => a - b).map((line) => ({ line, text: original[line - 1].trim() }))
}

/**
 * 式の文の終わりの位置を返す（深さ 0 の ; か、続きのない改行。続きは、行末の演算子や = か、次の行の始まりの . ? : && || + as）。
 *
 * @param {string} code ソース（コメントと文字列を空白にしたもの）
 * @param {number} from 式の始まりの位置
 * @returns {number} 文の終わりの位置。なければソースの終わり
 */
export function statementEnd(code, from) {
  const start = from + /^\s*/.exec(code.slice(from))[0].length
  let depth = 0
  let end = code.length
  scanCode(code, { start }, (type, at) => {
    if (type !== 'code') return false
    const char = code[at]
    if ('([{'.includes(char)) depth += 1
    else if (')]}'.includes(char)) {
      depth -= 1
      if (depth < 0) {
        end = at
        return true
      }
    } else if (depth === 0 && char === ';') {
      end = at
      return true
    } else if (depth === 0 && char === '\n') {
      const before = code.slice(start, at).trimEnd()
      const next = code.slice(at).trimStart()
      if (!/[=+\-*/&|?:,.(]$/.test(before) && !/^(?:\?\.|\.|\?|:|&&|\|\||\+|as\b)/.test(next)) {
        end = at
        return true
      }
    }
    return false
  })
  return end
}
