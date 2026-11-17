/**
 * E2E の入口。`pnpm test` が流す。
 *
 * 全ての作業ツリーで E2E を 1 つずつ流すため、最初にロックを取り、API の型の生成、準備、Playwright を順に実行して、終わりにロックを削除する。
 * E2E は DB（ec_site_e2e）・IdP・ポートを共有するので、同時に流すと互いのデータとサーバーを壊す。
 * ロックは、git の共通のディレクトリの ec-site-e2e.lock（作業ツリーのパス、PID、開始の時刻）。
 * 所有者のプロセスが実行中なら、所有者とロックのファイルのパスを示して終了コード 1 で終了する。プロセスがなければ古いロックとして取得し直す。
 *
 * 使い方：node scripts/run.ts [Playwright の引数...]（e2e/ で）。引数はそのまま Playwright に渡す。終了コードは失敗した最初の段階のもの。
 */
import { type ChildProcess, execFileSync, spawn } from 'node:child_process'
import { rmSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { stopOnSignals } from '../../scripts/shared/child-stop.mjs'
import {
  acquireLock,
  busyLockMessage,
  e2eLockFile,
  isProcessRunning,
  type LockOwner,
  ownsLock,
  readLock,
} from '../../scripts/shared/lock.mjs'
import { slotEnvNames } from '../../scripts/shared/slot/slot-env-names.mjs'

// スロット（scripts/dev/slot.mjs env）の環境変数を shell に export したまま流すと、バックエンドがスロットの DB とバケットにつながる。
// E2E は常にスロット 0 の固定の環境で流すので、スロットが付ける環境変数は引き継がない
const droppedEnv = slotEnvNames().filter((name) => name in process.env)
for (const envName of droppedEnv) {
  delete process.env[envName]
}
if (droppedEnv.length > 0) {
  console.error(`スロットの環境変数を除いて流す：${droppedEnv.join('、')}`)
}

/** e2e のパッケージのディレクトリ */
const E2E_DIR = path.resolve(import.meta.dirname, '..')

/** 順に実行する段階。Playwright は node で直接流し、引数を shell に通さない */
const STEPS: { command: string; args: string[]; shell?: boolean }[] = [
  // pnpm は Windows では .cmd なので shell 経由で流す（引数のない 1 つの文字列にして、引数を連結する警告を避ける）
  { command: 'pnpm generate:api', args: [], shell: true },
  { command: process.execPath, args: ['scripts/prepare.ts'] },
  {
    command: process.execPath,
    args: [fileURLToPath(import.meta.resolve('@playwright/test/cli')), 'test', ...process.argv.slice(2)],
  },
]

/** git のコマンドの出力（末尾の改行を除く） */
function git(...args: string[]): string {
  return execFileSync('git', args, { cwd: E2E_DIR, encoding: 'utf8' }).trim()
}

/**
 * ロックを取る。取れなければ、所有者とロックのファイルのパスを示して終了コード 1 で終了する。
 * 所有者のプロセスが実行中なら待たない。プロセスがなければ古いロックとして取得し直す。
 * 取得は scripts/shared/lock.mjs の手順で、中身を書いた一時のファイルを link で置くので、空のロックを他方に読まれない。
 *
 * @param file  ロックのファイルのパス
 * @param owner 自分（所有者として書く内容）
 */
function acquireLockOrExit(file: string, owner: LockOwner): void {
  const result = acquireLock({ file, owner, isRunning: isProcessRunning })
  if (!result.acquired) {
    console.error(`別の E2E が実行中のため、実行しない。${busyLockMessage({ lock: file, owner: result.owner })}`)
    process.exit(1)
  }
}

/** 段階を子のプロセスで実行し、終了コードを返す。信号で終わったときは 1 */
function runStep(step: (typeof STEPS)[number]): Promise<number> {
  return new Promise((resolve) => {
    const child = spawn(step.command, step.args, { cwd: E2E_DIR, stdio: 'inherit', shell: step.shell ?? false })
    runningChild = child
    child.on('error', (error) => {
      console.error(error)
      resolve(1)
    })
    child.on('close', (code) => {
      runningChild = null
      resolve(code ?? 1)
    })
  })
}

/** 実行中の子のプロセス */
let runningChild: ChildProcess | null = null

const lockFile = e2eLockFile(path.resolve(E2E_DIR, git('rev-parse', '--git-common-dir')))
const lockOwner: LockOwner = {
  worktree: path.resolve(git('rev-parse', '--show-toplevel')),
  pid: process.pid,
  startedAt: new Date().toISOString(),
}
acquireLockOrExit(lockFile, lockOwner)

/** 終了時に、自分のロックだけを削除する */
function releaseLock(): void {
  if (ownsLock(readLock(lockFile), lockOwner)) {
    rmSync(lockFile, { force: true })
  }
}
process.on('exit', releaseLock)
// 信号を受けたら、実行中の子を停止し、子が終わってからロックを削除して終了する（子が残ったまま次の E2E がロックを取らないように）。
// 2 回目の信号では待たずに終了する（'exit' でロックを削除する）
const stopping = stopOnSignals({ current: () => runningChild })

let exitCode = 0
for (const step of STEPS) {
  exitCode = await runStep(step)
  if (stopping.isStopping()) {
    exitCode = 1
  }
  if (exitCode !== 0) {
    break
  }
}
process.exit(exitCode)
