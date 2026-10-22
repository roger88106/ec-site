// スロット（作業ツリーごとの手元の環境）の割り当てと、スロットの環境での起動。
//
// 使い方：node scripts/dev/slot.mjs <コマンド>（作業ツリーのどこから流してもよい）
//   assign                          空いている最小のスロットを割り当て、データベースを作る。割り当て済みなら番号を示すだけ
//   release                         データベースを削除し、割り当てを解く
//   reset                           割り当てを変えずに、データベースを作り直す（バックエンドを止めてから）
//   show                            スロットのポート・URL・名前と、全ての作業ツリーのスロットの一覧
//   env                             スロットの環境変数を `export NAME='value'` の形で出す
//   run <backend|store|admin> [番号]  スロットの環境変数を付けて起動する。番号を渡したときは、作業ツリーのスロットと同じときだけ起動する
//
// メインの作業ツリーはいつもスロット 0。assign・release・reset は何もせずに知らせ、show・env・run はスロット 0 の値で動く。
// 割り当ては `<作業ツリー>/.local/slot` に番号だけで書く。
// 終了コード：0 成功、1 実行できない（満杯、未割り当て、バックエンドが動いているなど）、2 共有の基盤が動いていない。
// 共有の基盤（compose）は、メインの作業ツリーで起動する。この script は起動しない。
import { execFileSync, spawn } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, rmSync, rmdirSync, statSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { parseSlotRecord, parseWorktrees, pickSlot, slotEnv, slotValues, SLOT_COUNT } from './lib/slot.mjs'

/** 共有の基盤のうち、確認するコンテナ */
const SHARED_CONTAINERS = ['ec-site-postgres', 'ec-site-seaweedfs']

/** 共有の PostgreSQL のコンテナ */
const POSTGRES = 'ec-site-postgres'

/** 起動できる対象 */
const TARGETS = ['backend', 'store', 'admin']

/** ロックを待つ上限（ミリ秒） */
const LOCK_WAIT_MS = 90_000

/** これより古いロックは、持ち主が死んだものとして消す（ミリ秒） */
const LOCK_STALE_MS = 60_000

const USAGE = `使い方：node scripts/dev/slot.mjs <コマンド>
  assign                            空いている最小のスロットを割り当て、データベースを作る
  release                           データベースを削除し、割り当てを解く
  reset                             割り当てを変えずに、データベースを作り直す
  show                              スロットのポート・URL・名前と、作業ツリーの一覧を示す
  env                               スロットの環境変数を export の形で出す
  run <backend|store|admin> [番号]  スロットの環境変数を付けて起動する`

/**
 * 終了コードを付けて終わるための例外。
 */
class Exit extends Error {
  /**
   * @param {number} code 終了コード
   * @param {string} [message] 示す文（標準エラー出力へ）
   */
  constructor(code, message) {
    super(message ?? '')
    this.code = code
  }
}

/**
 * git を流して、標準出力を返す。
 *
 * @param {string[]} args git の引数
 * @returns {string} 標準出力（末尾の改行なし）
 */
function git(args) {
  return execFileSync('git', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim()
}

/**
 * 今の作業ツリーの状態を調べる。
 *
 * @returns {{ root: string, commonDir: string, main: boolean, porcelain: string, mainPath: string }}
 *   root は作業ツリーのルート、commonDir は git の共通のディレクトリ、mainPath はメインの作業ツリー
 */
function context() {
  const root = git(['rev-parse', '--show-toplevel'])
  const gitDir = git(['rev-parse', '--path-format=absolute', '--git-dir'])
  const commonDir = git(['rev-parse', '--path-format=absolute', '--git-common-dir'])
  const porcelain = git(['worktree', 'list', '--porcelain'])
  return {
    root,
    commonDir,
    main: path.resolve(gitDir) === path.resolve(commonDir),
    porcelain,
    mainPath: parseWorktrees(porcelain)[0].path,
  }
}

/**
 * 作業ツリーの記録のファイルのパス。
 *
 * @param {string} root 作業ツリーのルート
 * @returns {string} `.local/slot` のパス
 */
function recordFile(root) {
  return path.join(root, '.local', 'slot')
}

/**
 * 作業ツリーの記録を読む。
 *
 * @param {string} root 作業ツリーのルート
 * @returns {string | null} ファイルの内容。なければ null
 */
function readRecord(root) {
  const file = recordFile(root)
  return existsSync(file) ? readFileSync(file, 'utf8') : null
}

/**
 * 今の作業ツリーのスロットを返す。メインの作業ツリーは 0。
 *
 * @param {{ root: string, main: boolean }} ctx 作業ツリーの状態
 * @returns {number | null} スロットの番号。割り当てがなければ null
 */
function currentSlot(ctx) {
  return ctx.main ? 0 : parseSlotRecord(readRecord(ctx.root))
}

/**
 * コンテナが動いているか。
 *
 * @param {string} name コンテナの名前
 * @returns {boolean} 動いていれば true
 */
function containerRunning(name) {
  try {
    return (
      execFileSync('docker', ['inspect', '-f', '{{.State.Running}}', name], {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
      }).trim() === 'true'
    )
  } catch {
    return false
  }
}

/**
 * 共有の基盤が動いていることを確かめる。止まっていれば、メインの作業ツリーで起動するよう示して終わる。
 *
 * @param {{ mainPath: string }} ctx 作業ツリーの状態
 */
function requireShared(ctx) {
  const stopped = SHARED_CONTAINERS.filter((name) => !containerRunning(name))
  if (stopped.length === 0) return
  throw new Exit(
    2,
    `共有の基盤が動いていない（${stopped.join('、')}）。メインの作業ツリーで起動する：\n` +
      `  cd ${ctx.mainPath}\n  docker compose up -d\nこの script は docker compose up を流さない`,
  )
}

/**
 * 共有の PostgreSQL で SQL を流す。
 *
 * @param {string[]} statements 流す SQL
 */
function psql(statements) {
  const args = ['exec', '-e', 'PGOPTIONS=-c client_min_messages=warning', POSTGRES, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'ec_site', '-d', 'postgres']
  for (const statement of statements) args.push('-c', statement)
  execFileSync('docker', args, { stdio: ['ignore', 'ignore', 'inherit'] })
}

/**
 * スロットのデータベースを消す。
 *
 * @param {number} slot スロットの番号
 */
function dropDatabase(slot) {
  psql([`drop database if exists ${slotValues(slot).database} with (force)`])
}

/**
 * スロットのデータベースを作り直す。マイグレーションと seed は、次のバックエンドの起動で入る。
 *
 * @param {number} slot スロットの番号
 */
function recreateDatabase(slot) {
  const { database } = slotValues(slot)
  psql([`drop database if exists ${database} with (force)`, `create database ${database} owner ec_site`])
}

/**
 * スロットのデータを作り直す。assign と reset が使う。
 *
 * @param {number} slot スロットの番号
 */
function recreateData(slot) {
  recreateDatabase(slot)
}

/**
 * スロットのデータを消す。release が使う。
 *
 * @param {number} slot スロットの番号
 */
function dropData(slot) {
  dropDatabase(slot)
}

/**
 * 指定の時間だけ待つ。
 *
 * @param {number} ms ミリ秒
 * @returns {Promise<void>}
 */
function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/**
 * 全ての作業ツリーで共有するロックを持って処理を流す。<br />
 * mkdir が、ディレクトリがあれば原子的に失敗することを使う。
 * 2 つのセッションが同時に assign しても、空きの選択から記録の書き込みまでが重ならない。
 *
 * @template T
 * @param {string} commonDir git の共通のディレクトリ
 * @param {() => T} action ロックを持って流す処理
 * @returns {Promise<T>} action の結果
 */
async function withLock(commonDir, action) {
  const lock = path.join(commonDir, 'ec-site-slot.lock')
  const deadline = Date.now() + LOCK_WAIT_MS
  for (;;) {
    try {
      mkdirSync(lock)
      break
    } catch (e) {
      if (e.code !== 'EEXIST') throw e
      try {
        if (Date.now() - statSync(lock).mtimeMs > LOCK_STALE_MS) {
          rmSync(lock, { recursive: true, force: true })
          continue
        }
      } catch {
        // 調べている間に持ち主が消した。取り直す
        continue
      }
      if (Date.now() > deadline) throw new Exit(1, `スロットのロック（${lock}）を取れなかった`)
      await sleep(200)
    }
  }
  try {
    return action()
  } finally {
    try {
      rmdirSync(lock)
    } catch {
      // すでに消えていればよい
    }
  }
}

/**
 * assign：スロットを割り当てる。
 *
 * @param {ReturnType<typeof context>} ctx 作業ツリーの状態
 */
async function assign(ctx) {
  if (ctx.main) return console.log('メインの作業ツリーはスロット 0。割り当ては要らない')
  requireShared(ctx)
  const existing = parseSlotRecord(readRecord(ctx.root))
  if (existing !== null) return console.log(`すでにスロット ${existing} が割り当て済み`)
  await withLock(ctx.commonDir, () => {
    // ロックを取る間に別のセッションが同じ作業ツリーへ割り当てていないかを見直す
    const again = parseSlotRecord(readRecord(ctx.root))
    if (again !== null) return console.log(`すでにスロット ${again} が割り当て済み`)
    const records = {}
    for (const worktree of parseWorktrees(ctx.porcelain)) {
      if (!worktree.main) records[worktree.path] = readRecord(worktree.path)
    }
    const picked = pickSlot(ctx.porcelain, records)
    if ('full' in picked) {
      const lines = picked.users.map((user) => `  スロット ${user.slot}：${user.path}`)
      throw new Exit(
        1,
        `空いているスロットがない（1〜${SLOT_COUNT - 1} が使用中）。使っている作業ツリー：\n${lines.join('\n')}\n` +
          '使い終えた作業ツリーで release するか、消えた作業ツリーなら git worktree prune を流す',
      )
    }
    recreateData(picked.slot)
    mkdirSync(path.dirname(recordFile(ctx.root)), { recursive: true })
    writeFileSync(recordFile(ctx.root), `${picked.slot}\n`)
    console.log(`スロット ${picked.slot} を割り当てた（データベース ${slotValues(picked.slot).database}）`)
  })
}

/**
 * release：割り当てを解く。
 *
 * @param {ReturnType<typeof context>} ctx 作業ツリーの状態
 */
function release(ctx) {
  if (ctx.main) return console.log('メインの作業ツリーはスロット 0。解くものはない')
  requireShared(ctx)
  const slot = parseSlotRecord(readRecord(ctx.root))
  if (slot === null) return console.log('スロットは割り当てられていない')
  dropData(slot)
  rmSync(recordFile(ctx.root), { force: true })
  console.log(`スロット ${slot} を解いた`)
}

/**
 * スロットのバックエンドが動いているか。運用ポートの死活監視に応答があるかで見る。
 *
 * @param {number} slot スロットの番号
 * @returns {Promise<boolean>} 応答があれば true
 */
async function backendRunning(slot) {
  try {
    await fetch(`http://localhost:${slotValues(slot).managementPort}/actuator/health`, {
      signal: AbortSignal.timeout(2000),
    })
    return true
  } catch {
    return false
  }
}

/**
 * reset：割り当てを変えずに、データベースを作り直す。
 *
 * @param {ReturnType<typeof context>} ctx 作業ツリーの状態
 */
async function reset(ctx) {
  if (ctx.main) return console.log('メインの作業ツリーはスロット 0。scripts/dev/reset.sh を使う')
  requireShared(ctx)
  const slot = parseSlotRecord(readRecord(ctx.root))
  if (slot === null) throw new Exit(1, 'スロットが割り当てられていない。先に node scripts/dev/slot.mjs assign を流す')
  const values = slotValues(slot)
  if (await backendRunning(slot)) {
    throw new Exit(
      1,
      `スロット ${slot} のバックエンド（:${values.backendPort}）が動いている。止めてから流し直す（接続を持ったまま消すと、起動中の処理が失敗する）`,
    )
  }
  recreateData(slot)
  console.log(`スロット ${slot} のデータベース ${values.database} を作り直した。次のバックエンドの起動で seed が入る`)
}

/**
 * show：スロットの値と、作業ツリーの一覧を示す。
 *
 * @param {ReturnType<typeof context>} ctx 作業ツリーの状態
 */
function show(ctx) {
  const slot = currentSlot(ctx)
  if (slot === null) {
    console.log('この作業ツリーにスロットは割り当てられていない（node scripts/dev/slot.mjs assign）')
  } else {
    const v = slotValues(slot)
    console.log(`この作業ツリー：${ctx.root}`)
    console.log(`スロット：${slot}${ctx.main ? '（メインの作業ツリー）' : ''}`)
    console.log(`バックエンド：http://localhost:${v.backendPort}`)
    console.log(`運用：http://localhost:${v.managementPort}`)
    console.log(`店舗画面：http://localhost:${v.storePort}`)
    console.log(`管理画面：http://localhost:${v.adminPort}`)
    console.log(`データベース：${v.database}`)
    console.log(`バケット：${v.publicBucket}、${v.incomingBucket}`)
  }
  console.log('\n作業ツリーの一覧：')
  for (const worktree of parseWorktrees(ctx.porcelain)) {
    const n = worktree.main ? 0 : parseSlotRecord(readRecord(worktree.path))
    const label = n === null ? '未割り当て' : `スロット ${n}`
    console.log(`  ${label}：${worktree.path}${worktree.prunable ? '（消えている）' : ''}`)
  }
}

/**
 * Bash の単一引用符で囲む。
 *
 * @param {string} value 値
 * @returns {string} 引用した値
 */
function shellQuote(value) {
  return `'${value.replace(/'/g, `'\\''`)}'`
}

/**
 * env：スロットの環境変数を export の形で出す。
 *
 * @param {ReturnType<typeof context>} ctx 作業ツリーの状態
 */
function env(ctx) {
  const slot = currentSlot(ctx)
  if (slot === null) throw new Exit(1, 'スロットが割り当てられていない。先に node scripts/dev/slot.mjs assign を流す')
  const merged = {}
  for (const target of TARGETS) Object.assign(merged, slotEnv(slot, target))
  for (const [name, value] of Object.entries(merged)) console.log(`export ${name}=${shellQuote(value)}`)
}

/**
 * 子のプロセスを終了させる。Windows では、cmd の下の子も含めて止める。
 *
 * @param {import('node:child_process').ChildProcess} child 子のプロセス
 * @param {NodeJS.Signals} signal 渡すシグナル
 */
function killChild(child, signal) {
  if (process.platform === 'win32' && child.pid) {
    try {
      execFileSync('taskkill', ['/pid', String(child.pid), '/T', '/F'], { stdio: 'ignore' })
    } catch {
      // すでに終わっていればよい
    }
  } else {
    child.kill(signal)
  }
}

/**
 * run：スロットの環境変数を付けて対象を起動する。
 *
 * @param {ReturnType<typeof context>} ctx 作業ツリーの状態
 * @param {string[]} args 対象と、あれば番号
 * @returns {Promise<number>} 子の終了コード
 */
async function run(ctx, args) {
  const [target, number] = args
  if (!TARGETS.includes(target)) throw new Exit(1, `起動する対象は backend、store、admin のどれか：${target ?? ''}`)
  const slot = currentSlot(ctx)
  if (slot === null) throw new Exit(1, 'スロットが割り当てられていない。先に node scripts/dev/slot.mjs assign を流す')
  if (number !== undefined && Number(number) !== slot) {
    throw new Exit(1, `この作業ツリーのスロットは ${slot} で、指定の ${number} と違う。起動しない`)
  }
  const values = slotValues(slot)
  let command
  let commandArgs
  let cwd
  if (target === 'backend') {
    requireShared(ctx)
    cwd = path.join(ctx.root, 'backend', 'ecsite')
    command = path.join(cwd, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew')
    commandArgs = ['bootRun', '--args=--spring.profiles.active=local']
  } else if (target === 'store') {
    cwd = path.join(ctx.root, 'frontend', 'store')
    command = process.execPath
    commandArgs = ['node_modules/next/dist/bin/next', 'dev', '--port', String(values.storePort)]
  } else {
    cwd = ctx.root
    command = process.execPath
    commandArgs = ['frontend/admin/node_modules/vite/bin/vite.js', 'frontend/admin', '--port', String(values.adminPort), '--strictPort']
  }
  const options = { cwd, stdio: 'inherit', env: { ...process.env, ...slotEnv(slot, target) } }
  // Windows の .bat は shell を通さないと起動できない
  const child =
    process.platform === 'win32' && command.endsWith('.bat')
      ? spawn(`"${command}"`, commandArgs, { ...options, shell: true })
      : spawn(command, commandArgs, options)
  for (const signal of ['SIGINT', 'SIGTERM', 'SIGHUP']) process.on(signal, () => killChild(child, signal))
  return new Promise((resolve) => {
    child.on('error', (e) => {
      console.error(`起動できなかった：${e.message}`)
      resolve(1)
    })
    child.on('exit', (code, signal) => resolve(code ?? (signal ? 1 : 0)))
  })
}

/**
 * コマンドを振り分ける。
 *
 * @param {string[]} argv コマンドの引数
 * @returns {Promise<number>} 終了コード
 */
async function main(argv) {
  const [command, ...rest] = argv
  const known = ['assign', 'release', 'reset', 'show', 'env', 'run']
  if (!known.includes(command)) {
    console.error(USAGE)
    return command === undefined ? 0 : 1
  }
  let ctx
  try {
    ctx = context()
  } catch {
    throw new Exit(1, 'git の作業ツリーの中で流す')
  }
  if (command === 'assign') await assign(ctx)
  else if (command === 'release') release(ctx)
  else if (command === 'reset') await reset(ctx)
  else if (command === 'show') show(ctx)
  else if (command === 'env') env(ctx)
  else return run(ctx, rest)
  return 0
}

try {
  process.exitCode = await main(process.argv.slice(2))
} catch (e) {
  if (e instanceof Exit) {
    if (e.message) console.error(e.message)
    process.exitCode = e.code
  } else {
    throw e
  }
}
