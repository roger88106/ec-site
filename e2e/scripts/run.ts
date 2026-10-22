/**
 * E2E の入口。`pnpm test` が流す。<br />
 * 全ての作業ツリーで E2E を 1 つずつ流すため、最初にロックを取り、API の型の生成、準備、Playwright を順に実行して、終わりにロックを削除する。
 * E2E は DB（ec_site_e2e）・IdP・ポートを共有するので、同時に流すと互いのデータとサーバーを壊す。
 * ロックは、git の共通のディレクトリの ec-site-e2e.lock（作業ツリーのパス、PID、開始の時刻）。
 * 持ち主のプロセスが実行中なら、持ち主を示して終了コード 1 で終了する。プロセスがなければ古いロックとして取得し直す。
 *
 * 使い方：node scripts/run.ts [Playwright の引数...]（e2e/ で）。引数はそのまま Playwright に渡す。終了コードは失敗した最初の段階のもの。
 */
import { execFileSync, spawn } from 'node:child_process'
import { readFileSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { decideLock, formatLock, type LockOwner, parseLock } from '../../scripts/dev/lib/e2e-lock.mjs'

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

/** ロックを取る。取れなければ持ち主を示して終了コード 1 で終了する。ファイルは排他的に作り、同時に起動した他方との競合を防ぐ */
function acquireLock(file: string, owner: LockOwner): void {
  for (let attempt = 0; attempt < 3; attempt++) {
    const decision = decideLock({ content: readLock(file), isRunning })
    if (decision.action === 'busy') {
      console.error(
        `別の E2E が実行中のため、実行しない。作業ツリー：${decision.owner.worktree}（PID ${decision.owner.pid}、開始 ${decision.owner.startedAt}）`,
      )
      process.exit(1)
    }
    if (decision.stale !== undefined) {
      console.error(`古いロックを取得し直す（作業ツリー：${decision.stale?.worktree ?? '不明'}）`)
      rmSync(file, { force: true })
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
    child.on('error', (error) => {
      console.error(error)
      resolve(1)
    })
    child.on('close', (code) => resolve(code ?? 1))
  })
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
// Ctrl+C などで終わるときも、'exit' を経由させてロックを削除する（子のプロセスは同じ端末の信号で終わる）
for (const signal of ['SIGINT', 'SIGTERM', 'SIGHUP'] as const) {
  process.on(signal, () => process.exit(1))
}

let status = 0
for (const step of STEPS) {
  status = await runStep(step)
  if (status !== 0) {
    break
  }
}
process.exit(status)
