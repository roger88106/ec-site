// commit-msg の hook（scripts/dev/hooks/commit-msg）の処理。メッセージのファイルに、日本語の文の検査（scripts/ci/ja-text.mjs --message <ファイル>）を実行する。
// error があれば、hook は失敗にする。検査の出力は hook の利用者のために表示する。
// 判定は check-all と同じ judgeCheck（scripts/ci/lib/check-result.mjs）の summary で行う。終了コードが 0 でも、出力の最後の行が要約の行でなければ失敗にする（検査が何も調べずに終わったときに成功にしないため）。
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { judgeCheck } from '../../ci/lib/check-result.mjs'

/** メッセージのファイルが渡されなかったときの終了コード（検査の失敗の 1 と区別する） */
export const COMMIT_MSG_FAILED = 2

/**
 * 日本語の文の検査を実行して、hook の終了コードを返す。終了コードを返さなかった検査（シグナルでの終了など）は失敗の 1 にする。
 * 終了コードが 0 でも、出力の最後の行が要約の行でなければ、理由を表示して失敗の 1 にする。
 *
 * @param {object} deps 外部の処理（テストで差し替える）
 * @param {string | undefined} deps.file コミットメッセージのファイル
 * @param {(name: 'ja-text', args: string[]) => { status: number | null, output: string }} deps.run 検査を実行し、終了コードと標準出力を返す
 * @param {(text: string) => void} [deps.log] 失敗の理由を表示する
 * @returns {number} hook の終了コード
 */
export function runCommitMsg({ file, run, log = console.log }) {
  if (!file) return COMMIT_MSG_FAILED
  const { status, output } = run('ja-text', ['--message', file])
  if (status === null) return 1
  const result = judgeCheck({ status, output, mode: 'summary' })
  if (result.ok) return 0
  if (status !== 0) return status
  log(`日本語の文の検査（ja-text --message）を成功にしない：${result.reason}`)
  return 1
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
    run: (name, args) => {
      const result = spawnSync(process.execPath, [path.join(ci, scripts[name]), ...args], { stdio: ['inherit', 'pipe', 'inherit'], encoding: 'utf8' })
      const output = result.stdout ?? ''
      process.stdout.write(output)
      return { status: result.status, output }
    },
  })
}
