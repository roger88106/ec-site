// 名前やパスを正規表現に組み込むときに、特別な文字を逃がす。`$` だけを逃がすと、`\` や `.` を含む値で一致の範囲が変わる

/**
 * 文字列を、正規表現の中でその文字のまま一致する形にする。
 *
 * @param {string} text 組み込む文字列
 * @returns {string} 正規表現の特別な文字を逃がした文字列
 */
export function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
