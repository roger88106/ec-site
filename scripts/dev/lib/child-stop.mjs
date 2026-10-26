// 子のプロセスの停止と、停止のシグナルを受けたときの止め方。
// スロットの script（scripts/dev/slot.mjs）と E2E の実行（e2e/scripts/run.ts）が共有する。
import { spawnSync } from 'node:child_process'

/** 受けたら子を停止するシグナル */
export const STOP_SIGNALS = ['SIGINT', 'SIGTERM', 'SIGHUP']

/**
 * 子のプロセスとその子孫を停止する。<br />
 * Windows ではシグナルが子孫に届かないので、taskkill でツリーごと強制終了する。それ以外では受けたシグナルを子に送る。
 * pid がない（起動できなかった）子には何もしない。
 *
 * @param {import('node:child_process').ChildProcess} child 子のプロセス
 * @param {NodeJS.Signals} signal Windows 以外で子に送るシグナル
 * @param {{ platform: NodeJS.Platform, spawnSync: typeof spawnSync }} [deps] OS と指令の実行。テストで差し替える
 */
export function stopChild(child, signal, deps = { platform: process.platform, spawnSync }) {
  if (child.pid === undefined) return
  if (deps.platform === 'win32') {
    killTreeOnWindows(child.pid, deps.spawnSync)
  } else {
    child.kill(signal)
  }
}

/**
 * Windows で、プロセスとその子孫を taskkill で強制終了する。<br />
 * spawnSync は終了コードが 0 以外でも例外を投げないので、プロセスがすでに終わっていて taskkill が失敗しても止まらない。
 *
 * @param {number} pid プロセスの ID
 * @param {typeof spawnSync} [run] 指令の実行。テストで差し替える
 */
export function killTreeOnWindows(pid, run = spawnSync) {
  run('taskkill', ['/pid', String(pid), '/T', '/F'], { stdio: 'ignore' })
}

/**
 * 停止のシグナルを受けたときの止め方を登録する。<br />
 * 1 回目は実行中の子を停止し、終了せずに子が終わるのを待つ（呼び出し側が子の終わりを受けて終了する）。
 * 2 回目か、実行中の子がないときは、終了コード 1 で終了する。
 *
 * @param {{ current: () => import('node:child_process').ChildProcess | null, stop?: typeof stopChild,
 *   exit?: (code: number) => void }} options 実行中の子を返す関数。停止と終了はテストで差し替える
 * @param {Pick<NodeJS.EventEmitter, 'on'>} [proc] シグナルを受けるもの。テストで差し替える
 * @returns {{ isStopping: () => boolean }} シグナルを受けて停止の途中か
 */
export function stopOnSignals({ current, stop = stopChild, exit = (code) => process.exit(code) }, proc = process) {
  let stopping = false
  for (const signal of STOP_SIGNALS) {
    proc.on(signal, () => {
      const child = current()
      if (stopping || child === null) {
        exit(1)
        return
      }
      stopping = true
      stop(child, signal)
    })
  }
  return { isStopping: () => stopping }
}
