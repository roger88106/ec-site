// スロットのポートを使うプロセスの扱い：解除で止めてからデータを消す判断と、停止（stop）と、バックエンドの起動の前の確認。
// スロットの script（scripts/dev/slot.mjs）が、問い合わせ・停止・データの削除の実体を渡して使う。
import { portBusyMessage, queryFailedMessage, stopFailedMessage, stopLeftMessage, stoppedMessage } from './port-stop.mjs'
import { checkBackendStopped, slotPorts } from './slot.mjs'

/**
 * stop コマンドの対象から、停止するポートを決める。
 *
 * @param {number} slot スロットの番号
 * @param {string[]} args stop の引数。backend、store、admin のどれか 1 つ。空は 4 つ全て
 * @returns {{ ports: number[] } | { error: string }} 停止するポート。2 つ以上の対象と知らない対象は、理由
 */
export function stopTargetPorts(slot, args) {
  const [backend, management, store, admin] = slotPorts(slot)
  if (args.length > 1) return { error: `停止する対象は 1 つだけ指定する（省略は全て）：${args.join(' ')}` }
  const [target] = args
  if (target === undefined) return { ports: [backend, management, store, admin] }
  if (target === 'backend') return { ports: [backend, management] }
  if (target === 'store') return { ports: [store] }
  if (target === 'admin') return { ports: [admin] }
  return { error: `停止する対象は backend、store、admin のどれか：${target}` }
}

/**
 * ポートで待ち受けるプロセスを停止する。割り当てとデータは変えない。<br />
 * 待ち受けがなければ、ないことを示して 0。あれば停止し、停止したことを示し、もう一度問い合わせて残りがないことを確認する。
 * 問い合わせが失敗したときと、停止の後に残ったときは、手で止めるコマンドを含む理由を付けて 1 を返す。
 *
 * @param {{
 *   ports: number[],
 *   platform: NodeJS.Platform,
 *   find: (ports: number[]) => { ok: true, listeners: import('./port-stop.mjs').Listener[] } | { ok: false, reason: string },
 *   stop: (listeners: import('./port-stop.mjs').Listener[]) => void,
 *   log: (line: string) => void,
 * }} deps ポート、OS、問い合わせ・停止・出力の実体
 * @returns {{ code: 0 } | { code: 1, message: string }} 停止したか、失敗の理由
 */
export function stopSlot({ ports, platform, find, stop, log }) {
  const queryFailed = (reason) => ({ code: 1, message: queryFailedMessage(ports, reason, platform) })
  const found = find(ports)
  if (!found.ok) return queryFailed(found.reason)
  if (found.listeners.length === 0) {
    log('スロットのポートで待ち受けるプロセスはない')
    return { code: 0 }
  }
  stop(found.listeners)
  log(stoppedMessage(found.listeners))
  const left = find(ports)
  if (!left.ok) return queryFailed(left.reason)
  if (left.listeners.length > 0) return { code: 1, message: stopLeftMessage(left.listeners, platform) }
  return { code: 0 }
}

/**
 * スロットを解除する。ポートで待ち受けるプロセスを止め、空いたことと、バックエンドが応答しないことを確認してからデータを消す。<br />
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
    // 止まったことを、ポートの待ち受けがなくなったかで確認する
    const left = find(ports)
    if (!left.ok) return queryFailed(left.reason)
    if (left.listeners.length > 0) return { code: 1, message: stopFailedMessage(left.listeners, platform) }
  }
  // 問い合わせと別の経路でも、バックエンドが応答しないことを確認してからデータを消す
  const problem = checkBackendStopped({ slot, running: await backendRunning(), action: '解く' })
  if (problem !== null) return problem
  drop()
  return { code: 0 }
}

/**
 * バックエンドを起動する前に、バックエンドと運用のポートが使われていないかを確認する。<br />
 * 使われていれば、利用者が別に起動したバックエンドかもしれないので、自動で止めず、止め方を示して起動しない。
 * 問い合わせが失敗したときは、データは壊れず、使われているポートでは起動が失敗するだけなので、警告して起動する。
 *
 * @param {{
 *   ports: number[],
 *   platform: NodeJS.Platform,
 *   find: (ports: number[]) => { ok: true, listeners: import('./port-stop.mjs').Listener[] } | { ok: false, reason: string },
 * }} deps ポート、OS、問い合わせの実体
 * @returns {{ start: true, warning: string | null } | { start: false, code: 1, message: string }} 起動するか
 */
export function checkBackendPorts({ ports, platform, find }) {
  const found = find(ports)
  if (!found.ok) return { start: true, warning: `${queryFailedMessage(ports, found.reason, platform)}\nポートを確かめずに起動する` }
  if (found.listeners.length > 0) return { start: false, code: 1, message: portBusyMessage(found.listeners, { platform }) }
  return { start: true, warning: null }
}
