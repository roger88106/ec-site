// ロックの取得の手順。スロットの割り当てのロック（scripts/dev/slot.mjs）と E2E のロック（e2e/scripts/run.ts）が使う。
// ロックの中身と古さの判定は E2E のロック（e2e-lock.mjs）と同じ：所有者の PID のプロセスがなければ古い。
// ファイルの操作とプロセスの確認は、呼ぶ側（scripts/dev/slot.mjs）が io と isRunning で渡す。
import { decideLock, decideTakeover, formatLock, parseLock } from './e2e-lock.mjs'

/** 判定のやり直しが続くときに、waitMs が短くても待つ最低の時間（ミリ秒） */
const RETRY_MIN_MS = 5000

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
  // 調べる間に所有者が放した
  if (content === null) return { action: 'retry' }
  const decision = decideLock({ content, isRunning })
  if (decision.action === 'busy') return { action: 'busy', owner: decision.owner }
  const moved = io.moveAside()
  if (moved === null) return { action: 'retry' }
  if (decideTakeover({ judged: content, moved }) === 'take') io.discard()
  else io.restore()
  return { action: 'retry' }
}

/**
 * ロックを取るまで待つ。所有者が動いている間は待ち、上限を過ぎたら所有者を返す。
 * 古いロックを取り除けない状態（判定のやり直しが続く）も、間を置いて待ち、上限を過ぎたら終わる（そのときの所有者は null）。
 * 古いロックを取り除いた直後の取り直しは、waitMs が 0 でも行えるよう、やり直しには waitMs とは別に最低 RETRY_MIN_MS の上限を持つ。
 *
 * @param {{ io: LockIo, owner: LockOwner, isRunning: (pid: number) => boolean, waitMs: number,
 *   now?: () => number, sleep: (ms: number) => Promise<void>, intervalMs?: number }} input
 * @returns {Promise<{ acquired: true } | { acquired: false, owner: LockOwner | null }>}
 */
export async function acquireLock({ io, owner, isRunning, waitMs, now = Date.now, sleep, intervalMs = 200 }) {
  const start = now()
  const busyDeadline = start + waitMs
  const retryDeadline = start + Math.max(waitMs, RETRY_MIN_MS)
  let retries = 0
  for (;;) {
    const result = acquireOnce({ io, owner, isRunning })
    if (result.action === 'acquired') return { acquired: true }
    if (result.action === 'busy') {
      if (now() >= busyDeadline) return { acquired: false, owner: result.owner }
      retries = 0
    } else {
      if (now() >= retryDeadline) return { acquired: false, owner: null }
      // 1 回目のやり直し（古いロックを取り除いた直後など）はすぐに行い、続くときは間を置く
      retries++
      if (retries === 1) continue
    }
    await sleep(intervalMs)
  }
}

/**
 * ロックを取れなかったときの案内。ロックのファイルのパスと、所有者のプロセスがなければ削除してよいことを示す
 * （PID が別のプロセスに再利用されると、古いロックが実行中に見え続ける）。
 *
 * @param {{ lock: string, owner: LockOwner | null }} input lock はロックのファイルのパス
 * @returns {string}
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
 * @returns {boolean}
 */
export function ownsLock(content, owner) {
  const holder = content === null ? null : parseLock(content)
  return holder !== null && holder.pid === owner.pid && holder.startedAt === owner.startedAt
}
