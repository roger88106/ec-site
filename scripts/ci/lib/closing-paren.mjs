// 開き括弧に対応する閉じ括弧の位置を求める（文字列の中の括弧は数えない）。
// 文言のコメントの検査（message-comments）と、スキップしたテストのまとまりの検査（ac-check）が使う

/**
 * 呼び出しの閉じ括弧の位置を返す。文字列の中の括弧は数えない。
 *
 * @param {string} text  ソース
 * @param {number} open  開き括弧の位置
 * @returns {number} 閉じ括弧の位置。見つからなければソースの終わり
 */
export function closingParen(text, open) {
  let depth = 0
  let quote = null
  for (let i = open; i < text.length; i += 1) {
    const ch = text[i]
    if (quote) {
      if (ch === '\\') i += 1
      else if (ch === quote) quote = null
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
