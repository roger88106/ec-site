// commit-msg の hook（scripts/dev/hooks/commit-msg）の処理。メッセージのファイルに、日本語の文の検査（scripts/ci/ja-text.mjs --message <ファイル>）を実行する。
// error があれば、hook は失敗にする。
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

/** メッセージのファイルが渡されなかったときの終了コード（検査の失敗の 1 と区別する） */
export const COMMIT_MSG_FAILED = 2

/**
 * 日本語の文の検査を実行して、hook の終了コードを返す。終了コードを返さなかった検査（シグナルでの終了など）は失敗の 1 にする。
 *
 * @param {object} deps 外部の処理（テストで差し替える）
 * @param {string | undefined} deps.file コミットメッセージのファイル
 * @param {(name: 'ja-text', args: string[]) => number | null} deps.run 検査を実行し、終了コードを返す
 * @returns {number} hook の終了コード
 */
export function runCommitMsg({ file, run }) {
  if (!file) return COMMIT_MSG_FAILED
  return run('ja-text', ['--message', file]) ?? 1
}

/**
 * 実際の検査の script で runCommitMsg を実行する。
 * 直接実行の判定に頼らず、入口（commit-msg-main.mjs）が必ず呼ぶ。判定が外れて hook が何もせず成功するのを防ぐため。
 *
 * @param {string | undefined} file コミットメッセージのファイル
 * @returns {number} hook の終了コード
 */
export function main(file) {
  const ci = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../ci')
  const scripts = { 'ja-text': 'ja-text.mjs' }
  return runCommitMsg({
    file,
    run: (name, args) => spawnSync(process.execPath, [path.join(ci, scripts[name]), ...args], { stdio: 'inherit' }).status,
  })
}
