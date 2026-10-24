// 開き括弧に対応する閉じ括弧の位置を求める（文字列、コメント、正規表現のリテラルの中の括弧・引用符は数えない）。
// 文言のコメントの検査（message-comments）、Java の規則の検査（java-rules）、テストの関数の読み方（lib/test-files。AC の照合と機能紹介の数え方が使う）が使う
// 正規表現かの判定は strip-comments と同じ簡易の方法（直前の意味のある字と語で決める）。式の途中の複雑な書き方は読み違えることがある。
// Java には正規表現のリテラルがないので、Java の呼び出し元は regex: false を渡し、/ を全て割り算と読む
import { regexEnd, startsRegex } from './strip-comments.mjs'

/**
 * 呼び出しの閉じ括弧の位置を返す。文字列とコメントの中の括弧は数えない。
 * テンプレートの `${...}` の中はコードとして読む（入れ子のテンプレートも数える）。
 *
 * @param {string} text  ソース
 * @param {number} open  開き括弧の位置
 * @param {{ regex?: boolean }} [options] regex が false なら、正規表現のリテラルを読まない（Java 用。既定は読む）
 * @returns {number} 閉じ括弧の位置。見つからなければソースの終わり
 */
export function closingParen(text, open, options = {}) {
  const readRegex = options.regex ?? true
  let depth = 0
  let quote = null
  // テンプレートの `${...}` の中はコードとして読む。入れ子に備えて、`${` を開いたときの { の深さを積む
  let braces = 0
  const templates = []
  for (let i = open; i < text.length; i += 1) {
    const ch = text[i]
    if (quote) {
      if (ch === '\\') i += 1
      else if (ch === quote) quote = null
      // ' と " の文字列は改行で終える（JSX の it's などの単独の引用符で、後ろを全て文字列と読まない）
      else if (ch === '\n' && quote !== '`') quote = null
      else if (quote === '`' && ch === '$' && text[i + 1] === '{') {
        templates.push(braces)
        quote = null
        i += 1
      }
    } else if (ch === '{') {
      braces += 1
    } else if (ch === '}') {
      if (templates.length > 0 && templates.at(-1) === braces) {
        templates.pop()
        quote = '`'
      } else {
        braces -= 1
      }
    } else if (ch === '/' && text[i + 1] === '/') {
      // 行コメントは行の終わりまで飛ばす（中の引用符や括弧を数えない）
      const end = text.indexOf('\n', i)
      if (end === -1) return text.length
      i = end
    } else if (ch === '/' && text[i + 1] === '*') {
      const end = text.indexOf('*/', i + 2)
      if (end === -1) return text.length
      i = end + 1
    } else if (readRegex && ch === '/' && text[i + 1] !== '>' && startsRegex(text.slice(Math.max(open, i - 200), i))) {
      // 正規表現のリテラルは閉じまで飛ばす（中の括弧と引用符を数えない）
      i = regexEnd(text, i) - 1
    } else if (ch === "'" || ch === '"' || ch === '`') {
      quote = ch
    } else if (ch === '(') {
      depth += 1
    } else if (ch === ')') {
      depth -= 1
      if (depth === 0) return i
    }
  }
  return text.length
}
