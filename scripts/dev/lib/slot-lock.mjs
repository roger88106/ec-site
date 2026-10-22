// スロットの割り当てのロック（plan 104 第 2 節）の判定と手順。
// ロックの中身と古さの判定は E2E のロック（e2e-lock.mjs）と同じ：持ち主の PID のプロセスがなければ古い。
// ファイルの操作とプロセスの確認は、呼ぶ側（scripts/dev/slot.mjs）が io と isRunning で渡す。
import { decideLock, formatLock, parseLock } from './e2e-lock.mjs'

/**
 * @typedef {{ worktree: string, pid: number, startedAt: string }} LockOwner
 *
 * @typedef {object} LockIo ロックのファイルの操作
 * @property {(content: string) => boolean} create 中身を書いたロックを原子的に作る。すでにあれば false
 * @property {() => string | null} read ロックの中身。なければ null
 * @property {() => string | null} moveAside ロックを固有の名前へ rename で退かし、退かした中身を返す。なければ null
 * @property {() => void} restore 退かしたロックを元に戻す（ロックがまだないときだけ）
 * @property {() => void} discard 退かしたロックを捨てる
 */

/**
 * ロックを 1 回だけ取りにいく。<br />
 * 古いロックは rename で退かす（同時に退かせるのは 1 つだけ）。退かした中身が判定に使った中身と違えば、
 * 他方が先に取り直したロックを退かしたので、元に戻して判定からやり直す。
 *
 * @param {{ io: LockIo, owner: LockOwner, isRunning: (pid: number) => boolean }} input
 * @returns {{ action: 'acquired' } | { action: 'busy', owner: LockOwner } | { action: 'retry' }}
 *   retry は、古いロックを取り除いた、または状態が変わったので、すぐに判定からやり直す
 */
export function acquireOnce({ io, owner, isRunning }) {
  if (io.create(formatLock(owner))) return { action: 'acquired' }
  const content = io.read()
  // 調べる間に持ち主が放した
  if (content === null) return { action: 'retry' }
  const decision = decideLock({ content, isRunning })
  if (decision.action === 'busy') return { action: 'busy', owner: decision.owner }
  const moved = io.moveAside()
  if (moved === null) return { action: 'retry' }
  if (moved === content) io.discard()
  else io.restore()
  return { action: 'retry' }
}

/**
 * ロックを取るまで待つ。持ち主が動いている間は待ち、上限を過ぎたら持ち主を返す。
 *
 * @param {{ io: LockIo, owner: LockOwner, isRunning: (pid: number) => boolean, waitMs: number,
 *   now?: () => number, sleep: (ms: number) => Promise<void>, intervalMs?: number }} input
 * @returns {Promise<{ acquired: true } | { acquired: false, owner: LockOwner }>}
 */
export async function acquireLock({ io, owner, isRunning, waitMs, now = Date.now, sleep, intervalMs = 200 }) {
  const deadline = now() + waitMs
  for (;;) {
    const result = acquireOnce({ io, owner, isRunning })
    if (result.action === 'acquired') return { acquired: true }
    if (result.action === 'retry') continue
    if (now() > deadline) return { acquired: false, owner: result.owner }
    await sleep(intervalMs)
  }
}

/**
 * ロックが自分のもの（PID と開始の時刻が同じ）か。放すときに、他の持ち主のロックを消さないために見る。
 *
 * @param {string | null} content ロックの中身。なければ null
 * @param {LockOwner} owner 自分
 * @returns {boolean}
 */
export function ownsLock(content, owner) {
  const holder = content === null ? null : parseLock(content)
  return holder !== null && holder.pid === owner.pid && holder.startedAt === owner.startedAt
}
