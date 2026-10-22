/**
 * E2E の入口。`pnpm test` が流す。<br />
 * 全ての作業ツリーで E2E を 1 つずつ流すため、最初にロックを取り、API の型の生成、準備、Playwright を順に実行して、終わりにロックを削除する。
 * E2E は DB（ec_site_e2e）・IdP・ポートを共有するので、同時に流すと互いのデータとサーバーを壊す。
 * ロックは、git の共通のディレクトリの ec-site-e2e.lock（作業ツリーのパス、PID、開始の時刻）。
 * 持ち主のプロセスが実行中なら、持ち主を示して終了コード 1 で終了する。プロセスがなければ古いロックとして取得し直す。
 *
 * 使い方：node scripts/run.ts [Playwright の引数...]（e2e/ で）。引数はそのまま Playwright に渡す。終了コードは失敗した最初の段階のもの。
 */
import { type ChildProcess, execFileSync, spawn, spawnSync } from 'node:child_process'
import { linkSync, readFileSync, renameSync, rmSync, unlinkSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { decideLock, decideTakeover, formatLock, type LockOwner, parseLock } from '../../scripts/dev/lib/e2e-lock.mjs'
import { slotEnvNames } from '../../scripts/dev/lib/slot-env-names.mjs'

// スロット（scripts/dev/slot.mjs env）の環境変数を shell に export したまま流すと、バックエンドがスロットの DB とバケットにつながる。
// E2E は常にスロット 0 の固定の環境で流すので、スロットが付ける環境変数は引き継がない（DC-104-20）
const droppedEnv = slotEnvNames().filter((name) => name in process.env)
for (const name of droppedEnv) {
  delete process.env[name]
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

/** PID のプロセスが実行中か。権限がなくて信号を送れない（EPERM）プロセスも実行中とみなす */
function isRunning(pid: number): boolean {
  try {
    process.kill(pid, 0)
    return true
  } catch (error) {
    return (error as NodeJS.ErrnoException).code === 'EPERM'
  }
}

/** ロックのファイルを読む。なければ null */
function readLock(file: string): string | null {
  try {
    return readFileSync(file, 'utf8')
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === 'ENOENT') {
      return null
    }
    throw error
  }
}

/** ロックが使われていて取れないことを示して、終了コード 1 で終了する */
function exitBusy(owner: LockOwner): never {
  console.error(
    `別の E2E が実行中のため、実行しない。作業ツリー：${owner.worktree}（PID ${owner.pid}、開始 ${owner.startedAt}）`,
  )
  process.exit(1)
}

/**
 * 古いロックを退かす。成功（取り直してよい）なら true、他方が先に退かした・取り直したときは false（判定からやり直す）。
 * rmSync で消すと、同じ古いロックを読んだ他方が先に取り直した新しいロックを消しうる。そのため固有の名前へ rename で退かし、
 * 退かしたファイルが判定に使った内容と同じときだけ取り直す。違えば（新しいロックを退かした）、元に戻せるなら戻す。戻せなければ busy として終わる。
 */
function takeOverStale(file: string, judged: string): boolean {
  const moved = `${file}.stale.${process.pid}`
  try {
    renameSync(file, moved)
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === 'ENOENT') {
      return false
    }
    throw error
  }
  const movedContent = readFileSync(moved, 'utf8')
  if (decideTakeover({ judged, moved: movedContent }) === 'take') {
    rmSync(moved, { force: true })
    return true
  }
  try {
    // link は既にファイルがあれば失敗するので、他方の新しいロックを上書きしない
    linkSync(moved, file)
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== 'EEXIST') {
      throw error
    }
    rmSync(moved, { force: true })
    const owner = parseLock(movedContent)
    exitBusy(owner ?? { worktree: '不明', pid: 0, startedAt: '不明' })
  }
  unlinkSync(moved)
  return false
}

/**
 * ロックを取る。取れなければ持ち主を示して終了コード 1 で終了する。
 * 新しいロックはファイルを排他的に作る（wx）ので、同時に新規作成した他方とは競合しない。
 * 古いロックの取り直しは、固有の名前への rename と内容の照合で、他方の新しいロックを消さないようにする（takeOverStale）。
 */
function acquireLock(file: string, owner: LockOwner): void {
  for (let attempt = 0; attempt < 5; attempt++) {
    const content = readLock(file)
    const decision = decideLock({ content, isRunning })
    if (decision.action === 'busy') {
      exitBusy(decision.owner)
    }
    if (content !== null) {
      console.error(`古いロックを取得し直す（作業ツリー：${decision.stale?.worktree ?? '不明'}）`)
      if (!takeOverStale(file, content)) {
        continue
      }
    }
    try {
      writeFileSync(file, formatLock(owner), { flag: 'wx' })
      return
    } catch (error) {
      // 同時に起動した他方が先に作った。判定からやり直す
      if ((error as NodeJS.ErrnoException).code !== 'EEXIST') {
        throw error
      }
    }
  }
  console.error('ロックを取得できなかった')
  process.exit(1)
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
/** 信号を受けて終了の途中か */
let terminating = false

/** 子と配下のプロセスへ終了を送る。Windows では taskkill で配下も止める */
function killChild(child: ChildProcess, signal: NodeJS.Signals): void {
  if (child.pid === undefined) {
    return
  }
  if (process.platform === 'win32') {
    spawnSync('taskkill', ['/pid', String(child.pid), '/T', '/F'], { stdio: 'ignore' })
  } else {
    child.kill(signal)
  }
}

const lockFile = path.join(path.resolve(E2E_DIR, git('rev-parse', '--git-common-dir')), 'ec-site-e2e.lock')
acquireLock(lockFile, {
  worktree: path.resolve(git('rev-parse', '--show-toplevel')),
  pid: process.pid,
  startedAt: new Date().toISOString(),
})

/** 終了時に、自分のロックだけを削除する */
function releaseLock(): void {
  const content = readLock(lockFile)
  if (content !== null && parseLock(content)?.pid === process.pid) {
    rmSync(lockFile, { force: true })
  }
}
process.on('exit', releaseLock)
// 信号を受けたら、実行中の子へ信号を送り、子が終わってからロックを削除して終了する（子が残ったまま次の E2E がロックを取らないように）。
// 2 回目の信号では待たずに終了する（'exit' でロックを削除する）
for (const signal of ['SIGINT', 'SIGTERM', 'SIGHUP'] as const) {
  process.on(signal, () => {
    if (terminating || runningChild === null) {
      process.exit(1)
    }
    terminating = true
    killChild(runningChild, signal)
  })
}

let status = 0
for (const step of STEPS) {
  status = await runStep(step)
  if (terminating) {
    status = 1
  }
  if (status !== 0) {
    break
  }
}
process.exit(status)
