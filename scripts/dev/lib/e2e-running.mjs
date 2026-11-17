// この作業ツリーの E2E が実行中かを調べる入口。
//
// 実行中なら終了コード 0、そうでなければ 1。git で作業ツリーを調べられなければ、理由を標準エラー出力に出して 2。
import { execFileSync } from 'node:child_process'
import path from 'node:path'
import { e2eLockFile, isE2eRunningIn, isProcessRunning, readLock } from '../../shared/lock.mjs'

/**
 * git の出力（改行を除いたもの）を返す。
 *
 * @param {string[]} args git の引数
 * @returns {string} 出力
 */
function git(args) {
  return execFileSync('git', args, { cwd: import.meta.dirname, encoding: 'utf8' }).trim()
}

let worktree
let commonDir
try {
  worktree = path.resolve(git(['rev-parse', '--show-toplevel']))
  commonDir = path.resolve(import.meta.dirname, git(['rev-parse', '--git-common-dir']))
} catch (e) {
  console.error(`E2E が実行中かを調べられない（git rev-parse が失敗した）：${e instanceof Error ? e.message : String(e)}`)
  process.exit(2)
}
process.exit(isE2eRunningIn({ content: readLock(e2eLockFile(commonDir)), worktree, isRunning: isProcessRunning }) ? 0 : 1)
