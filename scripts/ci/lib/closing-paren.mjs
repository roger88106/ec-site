// 開き括弧に対応する閉じ括弧の位置を求める（文字列、コメント、正規表現のリテラルの中の括弧・引用符は数えない）。
// 文言のコメントの検査（message-comments）、Java の規則の検査（java-rules）、テストの関数の読み方（lib/test-files。AC の照合と機能紹介の数え方が使う）が使う
// 走査は scan-code と同じ（正規表現かの判定は簡易の方法で、式の途中の複雑な書き方は読み違えることがある）。
// Java には正規表現のリテラルがないので、Java の呼び出し元は regex: false を渡し、/ を全て割り算と読む
import { scanCode } from './scan-code.mjs'

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
  let depth = 0
  // 見つからなければ、scanCode がソースの終わりを返す
  return scanCode(text, { start: open, regex: options.regex ?? true }, (type, from) => {
    if (type !== 'code') return false
    if (text[from] === '(') depth += 1
    else if (text[from] === ')') {
      depth -= 1
      return depth === 0
    }
    return false
  })
}
