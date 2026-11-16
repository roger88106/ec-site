// E2E のロック。
//
// E2E は共有のデータベースとポートを使うので、同時に 1 つだけ実行する。
// ロックの中身は所有者（作業ツリーのパス、PID、開始の時刻）。所有者の PID のプロセスがなければ古いロックとして再取得する。
import { randomUUID } from 'node:crypto'
import { linkSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs'

/** @typedef {{ worktree: string, pid: number, startedAt: string }} LockOwner */

/**
 * プロセスが実行中か。権限がなくて信号を送れない（EPERM）プロセスも実行中とみなす。
 *
 * @param {number} pid プロセスの ID
 * @returns {boolean} 実行中なら true
 */
export function isProcessRunning(pid) {
  try {
    process.kill(pid, 0)
    return true
  } catch (e) {
    return e.code === 'EPERM'
  }
}

/**
 * ロックの中身を文字列にする。
 *
 * @param {LockOwner} owner 所有者
 * @returns {string} 中身
 */
export function formatLock(owner) {
  return JSON.stringify({ worktree: owner.worktree, pid: owner.pid, startedAt: owner.startedAt })
}

/**
 * ロックの中身を読む。
 *
 * @param {string} content 中身
 * @returns {LockOwner | null} 所有者。壊れていれば null
 */
export function parseLock(content) {
  let value
  try {
    value = JSON.parse(content)
  } catch {
    return null
  }
  if (value === null || typeof value !== 'object' || typeof value.worktree !== 'string' || !Number.isInteger(value.pid) || typeof value.startedAt !== 'string') {
    return null
  }
  return { worktree: value.worktree, pid: value.pid, startedAt: value.startedAt }
}

/**
 * ロックのファイルの中身を読む。
 *
 * @param {string} file ロックのファイル
 * @returns {string | null} 中身。なければ null
 */
export function readLock(file) {
  try {
    return readFileSync(file, 'utf8')
  } catch (e) {
    if (e.code === 'ENOENT') return null
    throw e
  }
}

/**
 * 中身を書いたロックを作る。一時のファイルに書いてから link で置くので、書きかけ（空）のロックを他方に読まれない。
 *
 * @param {string} file ロックのファイル
 * @param {string} content 中身
 * @returns {boolean} 作れれば true。すでにあれば false
 */
function createLock(file, content) {
  const temp = `${file}.${process.pid}.${randomUUID()}.tmp`
  writeFileSync(temp, content, { flag: 'wx' })
  try {
    linkSync(temp, file)
    return true
  } catch (e) {
    if (e.code === 'EEXIST') return false
    throw e
  } finally {
    rmSync(temp, { force: true })
  }
}

/**
 * 古いロックを退かす。退かした中身が古いと判定した中身と違えば（他方が先に再取得した）、元に戻す。
 *
 * @param {string} file ロックのファイル
 * @param {string} judged 古いと判定した中身
 * @returns {boolean} 古いロックを取り除けば true
 */
function removeStale(file, judged) {
  const aside = `${file}.${process.pid}.${randomUUID()}.stale`
  try {
    renameSync(file, aside)
  } catch {
    // ない（他方が先に退かした）か、Windows で他方が読んでいる
    return false
  }
  const moved = readFileSync(aside, 'utf8')
  try {
    if (moved !== judged) linkSync(aside, file)
  } catch (e) {
    if (e.code !== 'EEXIST') throw e
  } finally {
    rmSync(aside, { force: true })
  }
  return moved === judged
}

/**
 * ロックを取る。待たない。所有者のプロセスが動いていれば取らず、所有者を返す。古いロック（所有者のプロセスがない、壊れた中身）は取り除いて再取得する。
 *
 * @param {{ file: string, owner: LockOwner, isRunning: (pid: number) => boolean }} input ロックのファイル、自分、プロセスの確認
 * @returns {{ acquired: true } | { acquired: false, owner: LockOwner | null }} 取れたか。取れなければ所有者（分からなければ null）
 */
export function acquireLock({ file, owner, isRunning }) {
  const content = formatLock(owner)
  if (createLock(file, content)) return { acquired: true }
  const current = readLock(file)
  if (current === null) return createLock(file, content) ? { acquired: true } : { acquired: false, owner: null }
  const holder = parseLock(current)
  if (holder !== null && isRunning(holder.pid)) return { acquired: false, owner: holder }
  if (removeStale(file, current) && createLock(file, content)) return { acquired: true }
  return { acquired: false, owner: null }
}

/**
 * ロックを取れなかったときの案内。所有者のプロセスがなければ削除してよいことを示す（PID が別のプロセスに再利用されると、古いロックが実行中に見え続ける）。
 *
 * @param {{ lock: string, owner: LockOwner | null }} input ロックのファイルと所有者
 * @returns {string} 案内
 */
export function busyLockMessage({ lock, owner }) {
  const holder = owner === null ? '' : `所有者：${owner.worktree}（PID ${owner.pid}、開始 ${owner.startedAt}）。`
  return `${holder}ロックのファイル：${lock}\n所有者のプロセスがなければ削除してよい（PID が別のプロセスに再利用されて残ることがある）`
}

/**
 * ロックが自分のもの（PID と開始の時刻が同じ）か。放すときに、他の所有者のロックを消さないために見る。
 *
 * @param {string | null} content ロックの中身。なければ null
 * @param {LockOwner} owner 自分
 * @returns {boolean} 自分のものなら true
 */
export function ownsLock(content, owner) {
  const holder = content === null ? null : parseLock(content)
  return holder !== null && holder.pid === owner.pid && holder.startedAt === owner.startedAt
}

/**
 * 指定の作業ツリーの E2E が実行中か。所有者がその作業ツリーで、所有者のプロセスが動いているときに実行中とする（PID の再利用は判定しない）。
 *
 * @param {{ content: string | null, worktree: string, isRunning: (pid: number) => boolean }} input ロックの中身（なければ null）、調べる作業ツリー、プロセスの確認
 * @returns {boolean} 実行中なら true
 */
export function isE2eRunningIn({ content, worktree, isRunning }) {
  const owner = content === null ? null : parseLock(content)
  return owner !== null && owner.worktree === worktree && isRunning(owner.pid)
}
