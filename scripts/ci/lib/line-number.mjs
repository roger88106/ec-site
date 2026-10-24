// 文字列の中の位置から行番号（1 始まり）を求める。一致の位置から行を出す検査が使う

/**
 * 位置から行番号を返す関数を作る。改行の位置は 1 回だけ求め、行番号は二分探索で返す。
 *
 * @param {string} text 対象の文字列
 * @returns {(index: number) => number} 位置（0 始まり）から行番号（1 始まり）を返す関数
 */
export function createLineLocator(text) {
  const newlines = []
  for (let at = text.indexOf('\n'); at !== -1; at = text.indexOf('\n', at + 1)) newlines.push(at)
  return (index) => {
    // index より前にある改行の数を求める（改行の位置そのものは、その行に含める）
    let low = 0
    let high = newlines.length
    while (low < high) {
      const middle = (low + high) >> 1
      if (newlines[middle] < index) low = middle + 1
      else high = middle
    }
    return low + 1
  }
}
