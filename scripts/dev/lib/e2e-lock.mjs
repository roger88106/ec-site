// E2E のロック（plan 104 第 5 節）の純粋な判定。ファイルの読み書きとプロセスの確認は呼ぶ側（e2e/scripts/run.ts）が行う。

/**
 * ロックの中身を書き出す。
 * @param {{ worktree: string, pid: number, startedAt: string }} owner 持ち主（作業ツリーのパス、PID、開始の時刻）
 * @returns {string}
 */
export function formatLock(owner) {
  return JSON.stringify({ worktree: owner.worktree, pid: owner.pid, startedAt: owner.startedAt })
}

/**
 * ロックの中身を読む。壊れていれば null。
 * @param {string} content
 * @returns {{ worktree: string, pid: number, startedAt: string } | null}
 */
export function parseLock(content) {
  let value
  try {
    value = JSON.parse(content)
  } catch {
    return null
  }
  if (
    value === null ||
    typeof value !== 'object' ||
    typeof value.worktree !== 'string' ||
    !Number.isInteger(value.pid) ||
    typeof value.startedAt !== 'string'
  ) {
    return null
  }
  return { worktree: value.worktree, pid: value.pid, startedAt: value.startedAt }
}

/**
 * ロックを取得できるかを判定する。
 * ファイルがない、または持ち主のプロセスがない（壊れた中身を含む）ときは取得できる（後者は古いロックの取得し直し）。
 * 持ち主のプロセスが実行中なら取得できない。
 * @param {{ content: string | null, isRunning: (pid: number) => boolean }} input content はロックのファイルの中身（なければ null）
 * @returns {{ action: 'acquire', stale?: { worktree: string, pid: number, startedAt: string } | null } | { action: 'busy', owner: { worktree: string, pid: number, startedAt: string } }}
 */
export function decideLock({ content, isRunning }) {
  if (content === null) {
    return { action: 'acquire' }
  }
  const owner = parseLock(content)
  if (owner !== null && isRunning(owner.pid)) {
    return { action: 'busy', owner }
  }
  return { action: 'acquire', stale: owner }
}
