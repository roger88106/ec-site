// スロットのポートで待ち受けるプロセスを探し、停止する。
// スロットの script（scripts/dev/slot.mjs）の release と run backend が使う。
// script の node を強制終了すると停止の処理が動かず、Gradle のデーモンの子のバックエンドがポートで待ち受け続けるため。
import { spawnSync } from 'node:child_process'
import { killTreeOnWindows } from './child-stop.mjs'

/** 停止したプロセスの終了を待つ上限（ミリ秒）。Windows の Wait-Process と、それ以外の生存の問い合わせで共有する */
export const STOP_WAIT_MS = 30_000

/** 生存の問い合わせの間隔（ミリ秒） */
const POLL_INTERVAL_MS = 100

/**
 * ポートで待ち受けるプロセス。
 *
 * @typedef {{ port: number, pid: number }} Listener
 */

/**
 * 問い合わせの指令の出力から、待ち受けるプロセスを読む。<br />
 * Windows は「ポート プロセスの ID 状態」の行（findListeners の PowerShell が出す形）で、状態が Listen の行だけを読む。
 * 状態は .NET の列挙の名前なので、OS の言語で変わらない。
 * それ以外は lsof の -F pn の出力（p の行がプロセス、n の行がアドレス）で、接続（-> を含むアドレス）を読まない。
 * どちらも、渡したポート以外、自分のプロセス、ID 0（どのプロセスにも属さない接続）を読まず、同じ組（IPv4 と IPv6）は 1 つにする。
 *
 * @param {string} text 指令の出力
 * @param {{ platform: NodeJS.Platform, ports: number[], ownPid: number }} options OS、対象のポート、自分のプロセスの ID
 * @returns {Listener[]} 待ち受けるプロセス
 */
export function parseListeners(text, { platform, ports, ownPid }) {
  const found = []
  const add = (port, pid) => {
    if (!ports.includes(port) || pid === 0 || pid === ownPid) return
    if (found.some((item) => item.port === port && item.pid === pid)) return
    found.push({ port, pid })
  }
  const lines = text.split(/\r?\n/)
  if (platform === 'win32') {
    for (const line of lines) {
      const match = /^(\d+) (\d+) Listen$/.exec(line.trim())
      if (match) add(Number(match[1]), Number(match[2]))
    }
    return byPortOrder(found, ports)
  }
  let pid = null
  for (const line of lines) {
    if (line.startsWith('p')) pid = Number(line.slice(1))
    else if (line.startsWith('n') && pid !== null && !line.includes('->')) {
      const port = Number(line.slice(line.lastIndexOf(':') + 1))
      if (Number.isInteger(pid)) add(port, pid)
    }
  }
  return byPortOrder(found, ports)
}

/**
 * 待ち受けるプロセスを、渡したポートの順に並べる（示す文のポートの順をそろえるため）。
 *
 * @param {Listener[]} listeners 待ち受けるプロセス
 * @param {number[]} ports 対象のポート
 * @returns {Listener[]} 並べた待ち受けるプロセス
 */
function byPortOrder(listeners, ports) {
  return [...listeners].sort((a, b) => ports.indexOf(a.port) - ports.indexOf(b.port))
}

/**
 * ポートで待ち受けるプロセスを探す。<br />
 * Windows は PowerShell の Get-NetTCPConnection、それ以外は lsof で問い合わせる。netstat を使わないのは、状態の列が OS の言語で変わるため。
 * 待ち受けがないことと、問い合わせの失敗（指令を流せない、誤りで終わった）を分けて返す。
 * 失敗を待ち受けなしとみなすと、動いているバックエンドのデータベースを解除で消すため。
 * 一致なしとみなすのは、Get-NetTCPConnection の ObjectNotFound（どのポートにも接続がない）と、
 * lsof が誤りの出力なしに終了コード 1 で終わったときだけ。lsof は誤りでも 1 で終わるので、警告を -w で消し、出力の有無で分ける。
 *
 * @param {number[]} ports 対象のポート
 * @param {{ platform: NodeJS.Platform, spawnSync: typeof spawnSync, ownPid: number }} [deps] OS、指令の実行、自分のプロセスの ID。テストで差し替える
 * @returns {{ ok: true, listeners: Listener[] } | { ok: false, reason: string }} 待ち受けるプロセスか、失敗の理由
 */
export function findListeners(ports, deps = { platform: process.platform, spawnSync, ownPid: process.pid }) {
  const options = { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }
  const windows = deps.platform === 'win32'
  const result = windows
    ? deps.spawnSync(
        'powershell',
        [
          '-NoProfile',
          '-NonInteractive',
          '-Command',
          // 二重引用符は引数の受け渡しで崩れるので、単一引用符の連結で 1 行を作る
          `try { Get-NetTCPConnection -LocalPort ${ports.join(',')} -ErrorAction Stop | ForEach-Object { [string]$_.LocalPort + ' ' + $_.OwningProcess + ' ' + $_.State } } ` +
            `catch { if ($_.CategoryInfo.Category -eq 'ObjectNotFound') { exit 0 }; [Console]::Error.WriteLine($_.Exception.Message); exit 1 }`,
        ],
        options,
      )
    : deps.spawnSync('lsof', ['-nP', '-w', '-sTCP:LISTEN', '-Fpn', ...ports.map((port) => `-iTCP:${port}`)], options)
  if (result.error) return { ok: false, reason: result.error.message }
  const detail = typeof result.stderr === 'string' ? result.stderr.trim() : ''
  const succeeded = result.status === 0 || (!windows && result.status === 1 && detail === '')
  if (!succeeded || typeof result.stdout !== 'string') {
    return { ok: false, reason: `${windows ? 'Get-NetTCPConnection' : 'lsof'} が終了コード ${result.status} で終わった${detail ? `：${detail}` : ''}` }
  }
  return { ok: true, listeners: parseListeners(result.stdout, { platform: deps.platform, ports, ownPid: deps.ownPid }) }
}

/**
 * 待ち受けるプロセスを強制終了する。プロセスごとに 1 回だけ止める。<br />
 * Windows は taskkill でツリーごと止め、終わるまで Wait-Process で待つ。
 * それ以外は SIGKILL を送り、プロセスの生存の問い合わせ（シグナル 0）を、終わるまで繰り返して待つ。
 * どちらも上限は {@link STOP_WAIT_MS}。上限を過ぎても例外を投げず、先へ進む（残ったかどうかは、呼ぶ側の再度の問い合わせで判定する）。
 * 終わるのを待つのは、止めた直後に問い合わせると、まだ待ち受けが残って見えることがあるため。
 * 親が終了状態を回収しないゾンビのプロセスは、シグナル 0 が成功し続けて生きていると判定されるので、上限まで待つ（失敗はしない）。
 * すでに終わっていて kill が失敗しても止まらない。
 *
 * @param {Listener[]} listeners 待ち受けるプロセス
 * @param {Partial<StopDeps>} [deps] OS、指令の実行、シグナルの送り方、時計と待ち。テストで差し替える。省いた項目は実際のものを使う
 */
export function stopListeners(listeners, deps = {}) {
  const { platform, spawnSync: run, kill, now, sleep } = { ...defaultStopDeps(), ...deps }
  const pids = uniquePids(listeners)
  if (pids.length === 0) return
  if (platform === 'win32') {
    for (const pid of pids) killTreeOnWindows(pid, run)
    run(
      'powershell',
      ['-NoProfile', '-NonInteractive', '-Command', `Wait-Process -Id ${pids.join(',')} -Timeout ${STOP_WAIT_MS / 1000} -ErrorAction SilentlyContinue`],
      { stdio: 'ignore' },
    )
    return
  }
  for (const pid of pids) {
    try {
      kill(pid, 'SIGKILL')
    } catch {
      // すでに終わっている
    }
  }
  const deadline = now() + STOP_WAIT_MS
  let alive = pids.filter((pid) => isAlive(pid, kill))
  while (alive.length > 0 && now() < deadline) {
    sleep(POLL_INTERVAL_MS)
    alive = alive.filter((pid) => isAlive(pid, kill))
  }
}

/**
 * stopListeners が使う外部の機能。
 *
 * @typedef {object} StopDeps
 * @property {NodeJS.Platform} platform OS
 * @property {typeof spawnSync} spawnSync 指令の実行
 * @property {(pid: number, signal: NodeJS.Signals | 0) => void} kill シグナルの送り方（シグナル 0 は生存の問い合わせ）
 * @property {() => number} now 時計（ミリ秒）
 * @property {(ms: number) => void} sleep 待ち
 */

/**
 * 実際の OS・指令・時計・待ち。
 *
 * @returns {StopDeps} 実際の機能
 */
function defaultStopDeps() {
  return {
    platform: process.platform,
    spawnSync,
    kill: process.kill.bind(process),
    now: Date.now,
    sleep: (ms) => Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms),
  }
}

/**
 * プロセスが生きているか。権限がなく問い合わせられない（EPERM）プロセスは、存在するので生きているとみなす。
 *
 * @param {number} pid プロセスの ID
 * @param {StopDeps['kill']} kill シグナルの送り方
 * @returns {boolean} 生きていれば true
 */
function isAlive(pid, kill) {
  try {
    kill(pid, 0)
    return true
  } catch (error) {
    return error?.code === 'EPERM'
  }
}

/**
 * プロセスの ID を、重ねずに出てきた順で返す。
 *
 * @param {Listener[]} listeners 待ち受けるプロセス
 * @returns {number[]} プロセスの ID
 */
function uniquePids(listeners) {
  return [...new Set(listeners.map((item) => item.pid))]
}

/**
 * プロセスごとに、待ち受けるポートを並べる（`1234（ポート 18080、19080）`）。
 *
 * @param {Listener[]} listeners 待ち受けるプロセス
 * @returns {string} 示す文
 */
function describeListeners(listeners) {
  return uniquePids(listeners)
    .map((pid) => {
      const ports = listeners.filter((item) => item.pid === pid).map((item) => item.port)
      return `${pid}（ポート ${ports.join('、')}）`
    })
    .join('、')
}

/**
 * 停止したときに示す 1 行。
 *
 * @param {Listener[]} listeners 停止したプロセス
 * @returns {string} 示す文
 */
export function stoppedMessage(listeners) {
  return `スロットのポートで待ち受けるプロセスを停止した：${describeListeners(listeners)}`
}

/**
 * ポートが使われていて起動しないときに示す文。停止の指令（解除できるときは release も）を示す。
 *
 * @param {Listener[]} listeners 待ち受けるプロセス
 * @param {{ platform: NodeJS.Platform, canRelease: boolean }} options OS と、スロットを解除できるか（メインの作業ツリーは解除できない）
 * @returns {string} 示す文
 */
export function portBusyMessage(listeners, { platform, canRelease }) {
  const release = canRelease ? 'node scripts/dev/slot.mjs release（スロットのデータベースとバケットも消す）か、' : ''
  return (
    `バックエンドのポートが使われているので起動しない。待ち受けるプロセス：${describeListeners(listeners)}\n` +
    `止めるには、${release}${manualStopCommand(listeners, platform)} を流す`
  )
}

/**
 * 停止しても待ち受けが残り、解除しないときに示す文。
 *
 * @param {Listener[]} listeners 残った待ち受けるプロセス
 * @param {NodeJS.Platform} platform OS
 * @returns {string} 示す文
 */
export function stopFailedMessage(listeners, platform) {
  return (
    `スロットのポートで待ち受けるプロセスが残ったので、解かない。待ち受けるプロセス：${describeListeners(listeners)}\n` +
    `${manualStopCommand(listeners, platform)} で止めてから、もう一度 node scripts/dev/slot.mjs release を流す`
  )
}

/**
 * ポートの問い合わせが失敗したときに示す文。プロセスの ID が分からないので、ポートで探して止める指令を示す。
 *
 * @param {number[]} ports 問い合わせたポート
 * @param {string} reason 失敗の理由
 * @param {NodeJS.Platform} platform OS
 * @returns {string} 示す文
 */
export function queryFailedMessage(ports, reason, platform) {
  const manual =
    platform === 'win32'
      ? `PowerShell で Stop-Process -Id (Get-NetTCPConnection -LocalPort ${ports.join(',')} -State Listen).OwningProcess -Force`
      : `kill $(lsof -t -sTCP:LISTEN ${ports.map((port) => `-iTCP:${port}`).join(' ')})`
  return `ポート ${ports.join('、')} で待ち受けるプロセスを問い合わせられなかった（${reason}）。\n待ち受けるプロセスがあれば、${manual} で止める`
}

/**
 * 手動でプロセスを止める指令。
 *
 * @param {Listener[]} listeners 待ち受けるプロセス
 * @param {NodeJS.Platform} platform OS
 * @returns {string} 指令
 */
function manualStopCommand(listeners, platform) {
  const pids = uniquePids(listeners)
  return platform === 'win32' ? `PowerShell で Stop-Process -Id ${pids.join(',')} -Force` : `kill ${pids.join(' ')}`
}
