// 参照の検査と日本語の文の検査が、同じく使う見ないファイルの判定。
//
// 当時の記録、直せないもの、この 2 つの検査のテストの例（切れた参照や禁止の語をわざと書く）は見ない。

/**
 * 見ないファイル（当時の記録、直せないもの、この検査と ja-text のテストの例）。
 *
 * アーカイブの下の mvp.md は spec の記録でなく一覧の文書で、spec を移すたびにリンクが切れるので見る
 */
const EXCLUDED = [
  /^docs\/08-spec\/archive\/(?!mvp\/mvp\.md$)/,
  /\/db\/migration\//,
  /^scripts\/ci\/(docs\/doc-refs|ja\/ja-text)\.test\.mjs$/,
]

/**
 * 見ないファイルか。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} 見ないなら true
 */
export function isExcluded(file) {
  return EXCLUDED.some((pattern) => pattern.test(file))
}
