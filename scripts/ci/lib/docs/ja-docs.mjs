// 日本語の文と注の番号を調べる文書。

/** 調べる文書 */
const DOCS = /^((docs\/.*|README|AGENTS|\.claude\/(skills|agents)\/.*)\.md|\.github\/.*\.(md|ya?ml))$/

/**
 * 見ない文書：アーカイブした spec と、評価の一覧の置き場所。
 *
 * 前者は当時の記録で、後者は使わない語を例として書くため。
 * note-numbers も ja-text の判定を使うので、評価の一覧は注の番号の検査からも外れる（例文に ※1 を含む。意図した影響）
 */
const EXCLUDED = /^docs\/08-spec\/archive\/|^\.claude\/skills\/review\/evals\//

/**
 * 調べる文書かを返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} 調べるなら true
 */
export function isTargetDoc(file) {
  return DOCS.test(file) && !EXCLUDED.test(file)
}
