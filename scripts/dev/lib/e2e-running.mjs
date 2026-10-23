// この作業ツリーの E2E が実行中かを調べる入口（scripts/dev/check-frontend.sh から呼ぶ）。
// 実行中なら終了コード 0、そうでなければ 1。ロックは E2E の入口（e2e/scripts/run.ts）が git の共通のディレクトリに置く。
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { isE2eRunningIn } from './e2e-lock.mjs'

/**
 * git の出力（改行を除いたもの）を返す。
 * @param {string[]} args
 * @returns {string}
 */
function git(args) {
  return execFileSync('git', args, { cwd: import.meta.dirname, encoding: 'utf8' }).trim()
}

/**
 * プロセスが動いているか。
 * @param {number} pid
 * @returns {boolean}
 */
function isRunning(pid) {
  try {
    process.kill(pid, 0)
    return true
  } catch (e) {
    return e.code === 'EPERM'
  }
}

/**
 * ロックのファイルの中身を返す。なければ null。
 * @param {string} file
 * @returns {string | null}
 */
function readLock(file) {
  try {
    return readFileSync(file, 'utf8')
  } catch (e) {
    if (e.code === 'ENOENT') return null
    throw e
  }
}

const worktree = path.resolve(git(['rev-parse', '--show-toplevel']))
const lockFile = path.join(path.resolve(import.meta.dirname, git(['rev-parse', '--git-common-dir'])), 'ec-site-e2e.lock')
process.exit(isE2eRunningIn({ content: readLock(lockFile), worktree, isRunning }) ? 0 : 1)
