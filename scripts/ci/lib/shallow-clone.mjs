// commit の履歴を読む検査（migrations、related-specs）が、shallow clone では失敗にするための確認
import { execFileSync } from 'node:child_process'

/** shallow clone で履歴を読めないときの失敗の文（何をすればよいかを含む） */
export const SHALLOW_CLONE_MESSAGE = 'shallow clone では commit の履歴を読めない（CI の checkout に fetch-depth: 0 を付ける）'

/**
 * shallow clone（履歴が途中までのリポジトリ）か。
 *
 * @param {string} [cwd] 実行する場所（リポジトリの中）。なければ今のディレクトリ
 * @returns {boolean} shallow clone なら true
 */
export function isShallowRepository(cwd = process.cwd()) {
  return execFileSync('git', ['rev-parse', '--is-shallow-repository'], { cwd, encoding: 'utf8' }).trim() === 'true'
}

/** shallow clone なら、失敗の文を出して終了コード 1 で終わる。 */
export function exitIfShallow() {
  if (!isShallowRepository()) return
  console.log(SHALLOW_CLONE_MESSAGE)
  process.exit(1)
}
