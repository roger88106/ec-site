// スロットのポートを使うプロセスの扱い：解除で止めてからデータを消す判断と、バックエンドの起動の前の確認。
// スロットの script（scripts/dev/slot.mjs）が、問い合わせ・停止・データの削除の実体を渡して使う。
import { portBusyMessage, queryFailedMessage, stopFailedMessage, stoppedMessage } from './port-stop.mjs'
import { checkBackendStopped } from './slot.mjs'

/**
 * スロットを解除する。ポートで待ち受けるプロセスを止め、空いたことと、バックエンドが応答しないことを確かめてからデータを消す。<br />
 * 問い合わせが失敗したとき、止めても待ち受けが残るとき、バックエンドが応答するときは、データを消さずに終了コード 1 を返す。
 * 動いているバックエンドのデータベースを消さないため。
 *
 * @param {{
 *   slot: number,
 *   ports: number[],
 *   platform: NodeJS.Platform,
 *   find: (ports: number[]) => { ok: true, listeners: import('./port-stop.mjs').Listener[] } | { ok: false, reason: string },
 *   stop: (listeners: import('./port-stop.mjs').Listener[]) => void,
 *   backendRunning: () => Promise<boolean>,
 *   drop: () => void,
 *   log: (line: string) => void,
 * }} deps スロットとポート、OS、問い合わせ・停止・応答の確認・データの削除・出力の実体
 * @returns {Promise<{ code: 0 } | { code: 1, message: string }>} 解除したか、やめた理由
 */
export async function releaseSlot({ slot, ports, platform, find, stop, backendRunning, drop, log }) {
  const queryFailed = (reason) => ({
    code: 1,
    message: `${queryFailedMessage(ports, reason, platform)}\n解除はしていない。止めてから、もう一度 node scripts/dev/slot.mjs release を流す`,
  })
  const found = find(ports)
  if (!found.ok) return queryFailed(found.reason)
  if (found.listeners.length > 0) {
    stop(found.listeners)
    log(stoppedMessage(found.listeners))
    // 止まったことを、ポートの待ち受けがなくなったかで確かめる
    const left = find(ports)
    if (!left.ok) return queryFailed(left.reason)
    if (left.listeners.length > 0) return { code: 1, message: stopFailedMessage(left.listeners, platform) }
  }
  // 問い合わせと別の経路でも、バックエンドが応答しないことを確かめてからデータを消す
  const problem = checkBackendStopped({ slot, running: await backendRunning(), action: '解く' })
  if (problem !== null) return problem
  drop()
  return { code: 0 }
}

/**
 * バックエンドを起動する前に、バックエンドと運用のポートが使われていないかを確かめる。<br />
 * 使われていれば、利用者が別に起動したバックエンドかもしれないので、自動で止めず、止め方を示して起動しない。
 * 問い合わせが失敗したときは、データは壊れず、使われているポートでは起動が失敗するだけなので、警告して起動する。
 *
 * @param {{
 *   ports: number[],
 *   platform: NodeJS.Platform,
 *   canRelease: boolean,
 *   find: (ports: number[]) => { ok: true, listeners: import('./port-stop.mjs').Listener[] } | { ok: false, reason: string },
 * }} deps ポート、OS、スロットを解除できるか（メインの作業ツリーは解除できない）、問い合わせの実体
 * @returns {{ start: true, warning: string | null } | { start: false, code: 1, message: string }} 起動するか
 */
export function checkBackendPorts({ ports, platform, canRelease, find }) {
  const found = find(ports)
  if (!found.ok) return { start: true, warning: `${queryFailedMessage(ports, found.reason, platform)}\nポートを確かめずに起動する` }
  if (found.listeners.length > 0) return { start: false, code: 1, message: portBusyMessage(found.listeners, { platform, canRelease }) }
  return { start: true, warning: null }
}
