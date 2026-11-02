// 1 行の中のインラインのコードスパン（`...`、``...``）を読む。ci-docs（表の列の数え方）、service-names（名前を書いてよい形）、lib/code-block.mjs、lib/doc-structure.mjs が使う。
// CommonMark と同じく、開きのバッククォートの並びと同じ数の並びで閉じる（中の短い並び・長い並びでは閉じない）。
// 閉じる並びのない開きは文字として残し、組にならないバッククォートがあったことを unmatched で返す（読めない行として失敗にするかは呼ぶ側が決める）。

/**
 * 行の中のコードスパンを、左から順に読む。
 *
 * @param {string} line 1 行
 * @returns {{ spans: { start: number, end: number, content: string }[], unmatched: boolean }}
 *   spans は開きの位置（start）、閉じの次の位置（end）、囲みの中身（content。前後の空白は残す）。unmatched は組にならないバッククォートがあれば true
 */
export function readCodeSpans(line) {
  const spans = []
  let unmatched = false
  const run = /`+/g
  let open
  while ((open = run.exec(line)) !== null) {
    const length = open[0].length
    const contentStart = open.index + length
    let close
    let found = null
    const search = /`+/g
    search.lastIndex = contentStart
    while ((close = search.exec(line)) !== null) {
      if (close[0].length === length) {
        found = close
        break
      }
    }
    if (!found) {
      unmatched = true
      continue
    }
    const end = found.index + length
    spans.push({ start: open.index, end, content: line.slice(contentStart, found.index) })
    run.lastIndex = end
  }
  return { spans, unmatched }
}

/**
 * 行の中のコードスパンを、中身を受け取る関数の戻り値に替える。組にならないバッククォートはそのまま残す。
 *
 * @param {string} line 1 行
 * @param {(content: string) => string} replacer コードスパンの中身（囲みのバッククォートを除く）から、置き換える文字列を返す
 * @returns {string} 置き換えた行
 */
export function replaceCodeSpans(line, replacer) {
  let result = ''
  let last = 0
  for (const { start, end, content } of readCodeSpans(line).spans) {
    result += line.slice(last, start) + replacer(content)
    last = end
  }
  return result + line.slice(last)
}
