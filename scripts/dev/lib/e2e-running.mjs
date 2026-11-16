// この作業ツリーの E2E が実行中かを調べる入口（scripts/dev/check-frontend.sh から呼ぶ）。
// 実行中なら終了コード 0、そうでなければ 1。ロックは E2E の入口（e2e/scripts/run.ts）が git の共通のディレクトリに置く。
import { execFileSync } from 'node:child_process'
import path from 'node:path'
import { isE2eRunningIn, isProcessRunning, readLock } from './lock.mjs'

/**
 * git の出力（改行を除いたもの）を返す。
 *
 * @param {string[]} args git の引数
 * @returns {string} 出力
 */
function git(args) {
  return execFileSync('git', args, { cwd: import.meta.dirname, encoding: 'utf8' }).trim()
}

const worktree = path.resolve(git(['rev-parse', '--show-toplevel']))
const lockFile = path.join(path.resolve(import.meta.dirname, git(['rev-parse', '--git-common-dir'])), 'ec-site-e2e.lock')
process.exit(isE2eRunningIn({ content: readLock(lockFile), worktree, isRunning: isProcessRunning }) ? 0 : 1)
