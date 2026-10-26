// スロット（作業ツリーごとの手元の環境）の割り当てと、スロットの環境での起動。
//
// 使い方：node scripts/dev/slot.mjs <コマンド>（作業ツリーのどこから流してもよい）
//   assign                          空いている最小のスロットを割り当て、データベースとバケットを作る。割り当て済みなら番号を示すだけ
//   release                         スロットのポートで待ち受けるプロセスを停止し、データベースとバケットを削除し、割り当てを解く
//   reset                           割り当てを変えずに、データベースとバケットを作り直す（バックエンドを止めてから）
//   show                            スロットのポート・URL・名前と、全ての作業ツリーのスロットの一覧
//   env                             スロットの環境変数を `export NAME='value'` の形で出す
//   run <backend|store|admin> [番号]  スロットの環境変数を付けて起動する。番号を渡したときは、作業ツリーのスロットと同じときだけ起動する
//
// メインの作業ツリーはいつもスロット 0。assign・release・reset は何もせずに知らせ、show・env・run はスロット 0 の値で動く。
// 割り当ては `<作業ツリー>/.local/slot` に番号だけで書く。
// 終了コード：0 成功、1 実行できない（満杯、未割り当て、バックエンドが動いているなど）、2 共有の基盤が動いていない。
// 共有の基盤（compose）は、メインの作業ツリーで起動する。この script は起動しない。
import { execFileSync, spawn, spawnSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { stopOnSignals } from './lib/child-stop.mjs'
import { createLockIo, isProcessRunning } from './lib/lock-io.mjs'
import { findListeners, stopListeners } from './lib/port-stop.mjs'
import { acquireLock, busyLockMessage, ownsLock } from './lib/slot-lock.mjs'
import { checkBackendPorts, releaseSlot } from './lib/slot-ports.mjs'
import {
  assignSlot,
  checkBackendStopped,
  checkShared,
  currentSlotOf,
  dropBucketsScript,
  formatSlotRecord,
  generateApiDir,
  isBackendDown,
  isMainWorktree,
  parseSlotRecord,
  parseWorktrees,
  slotEnv,
  slotPorts,
  slotValues,
  SHARED_CONTAINERS,
  SLOT_COUNT,
} from './lib/slot.mjs'

/** 共有の PostgreSQL のコンテナ */
const POSTGRES = 'ec-site-postgres'

/** 起動できる対象 */
const TARGETS = ['backend', 'store', 'admin']

/** ロックを待つ上限（ミリ秒）。ロックの中は空きの選択と記録の書き込みだけなので、長く待つのは所有者が止まっているとき */
const LOCK_WAIT_MS = 90_000

const USAGE = `使い方：node scripts/dev/slot.mjs <コマンド>
  assign                            空いている最小のスロットを割り当て、データベースとバケットを作る
  release                           待ち受けるプロセスを停止し、データベースとバケットを削除し、割り当てを解く
  reset                             割り当てを変えずに、データベースとバケットを作り直す
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
    main: isMainWorktree(gitDir, commonDir),
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
 * 作業ツリーの記録を書く。
 *
 * @param {string} root 作業ツリーのルート
 * @param {number} slot スロットの番号
 */
function writeRecord(root, slot) {
  mkdirSync(path.dirname(recordFile(root)), { recursive: true })
  writeFileSync(recordFile(root), formatSlotRecord(slot))
}

/**
 * 今の作業ツリーのスロットを返す。メインの作業ツリーは 0。
 *
 * @param {{ root: string, main: boolean }} ctx 作業ツリーの状態
 * @returns {number | null} スロットの番号。割り当てがなければ null
 */
function currentSlot(ctx) {
  return currentSlotOf({ main: ctx.main, record: ctx.main ? null : readRecord(ctx.root) })
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
  const running = Object.fromEntries(SHARED_CONTAINERS.map((name) => [name, containerRunning(name)]))
  const problem = checkShared(running, ctx.mainPath)
  if (problem !== null) throw new Exit(problem.code, problem.message)
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
 * 一時のコンテナとして seaweedfs-init を流す。共有のコンテナは作り直さない。
 *
 * @param {string} root 作業ツリーのルート（compose の定義を読む場所）
 * @param {string[]} args `docker compose run` の引数（サービス名の前）
 * @param {string[]} [command] サービスの後ろに渡す引数
 */
function runInit(root, args, command = []) {
  execFileSync(
    'docker',
    ['compose', '-f', path.join(root, 'docker', 'compose.yaml'), 'run', '--rm', '--no-deps', '-T', ...args, 'seaweedfs-init', ...command],
    { cwd: root, stdio: ['ignore', 'ignore', 'inherit'] },
  )
}

/**
 * スロットの 2 つのバケットを削除する。ないバケット（NoSuchBucket）は無視し、他の誤りは失敗にする。
 *
 * @param {string} root 作業ツリーのルート
 * @param {number} slot スロットの番号
 */
function dropBuckets(root, slot) {
  const { incomingBucket, publicBucket } = slotValues(slot)
  runInit(root, ['--entrypoint', 'sh'], ['-c', dropBucketsScript([incomingBucket, publicBucket])])
}

/**
 * スロットの 2 つのバケットを削除してから作り直し、seed の画像を配信のバケットの assets/ に写す。
 *
 * @param {string} root 作業ツリーのルート
 * @param {number} slot スロットの番号
 */
function recreateBuckets(root, slot) {
  dropBuckets(root, slot)
  const { incomingBucket, publicBucket, adminPort } = slotValues(slot)
  runInit(root, [
    '-e',
    `SLOT_INCOMING_BUCKET=${incomingBucket}`,
    '-e',
    `SLOT_PUBLIC_BUCKET=${publicBucket}`,
    '-e',
    `SLOT_ADMIN_ORIGIN=http://localhost:${adminPort}`,
  ])
}

/**
 * スロットのデータ（データベースとバケット）を作り直す。assign と reset が使う。
 *
 * @param {string} root 作業ツリーのルート
 * @param {number} slot スロットの番号
 */
function recreateData(root, slot) {
  recreateDatabase(slot)
  recreateBuckets(root, slot)
}

/**
 * スロットのデータ（データベースとバケット）を消す。release が使う。
 *
 * @param {string} root 作業ツリーのルート
 * @param {number} slot スロットの番号
 */
function dropData(root, slot) {
  dropDatabase(slot)
  dropBuckets(root, slot)
}

/**
 * 全ての作業ツリーで共有するロックを持って処理を流す。<br />
 * ロックは git の共通のディレクトリの ec-site-slot-assign.lock（作業ツリーのパス、PID、開始の時刻）。
 * 所有者の PID のプロセスがなければ古いロックとして取り除く（lib/slot-lock.mjs）。放すときは自分のロックだけを消す。
 * 2 つのセッションが同時に assign しても、空きの選択から記録の書き込みまでが重ならない。
 *
 * @template T
 * @param {{ root: string, commonDir: string }} ctx 作業ツリーの状態
 * @param {() => T} action ロックを持って流す処理（短いものだけ）
 * @returns {Promise<T>} action の結果
 */
async function withLock(ctx, action) {
  const lock = path.join(ctx.commonDir, 'ec-site-slot-assign.lock')
  const io = createLockIo(lock)
  const owner = { worktree: ctx.root, pid: process.pid, startedAt: new Date().toISOString() }
  const result = await acquireLock({ io, owner, isRunning: isProcessRunning, waitMs: LOCK_WAIT_MS })
  if (!result.acquired) {
    throw new Exit(1, `スロットのロックを取れなかった。${busyLockMessage({ lock, owner: result.owner })}`)
  }
  try {
    return action()
  } finally {
    if (ownsLock(io.read(), owner)) rmSync(lock, { force: true })
  }
}

/**
 * assign：スロットを割り当てる。記録を書いてロックを放してから、データベースとバケットを作り直す。
 *
 * @param {ReturnType<typeof context>} ctx 作業ツリーの状態
 */
async function assign(ctx) {
  if (!ctx.main) requireShared(ctx)
  const result = await assignSlot({
    main: ctx.main,
    readOwnRecord: () => readRecord(ctx.root),
    listWorktrees: () => git(['worktree', 'list', '--porcelain']),
    readRecord,
    withLock: (action) => withLock(ctx, action),
    writeRecord: (slot) => writeRecord(ctx.root, slot),
    recreate: (slot) => recreateData(ctx.root, slot),
  })
  if (result.kind === 'main') return console.log('メインの作業ツリーはスロット 0。割り当ては要らない')
  if (result.kind === 'already') return console.log(`すでにスロット ${result.slot} が割り当て済み`)
  if (result.kind === 'full') {
    const lines = result.users.map((user) => `  スロット ${user.slot}：${user.path}`)
    throw new Exit(
      1,
      `空いているスロットがない（1〜${SLOT_COUNT - 1} が使用中）。使っている作業ツリー：\n${lines.join('\n')}\n` +
        '使い終えた作業ツリーで release するか、消えた作業ツリーなら git worktree prune を流す',
    )
  }
  const values = slotValues(result.slot)
  if (result.kind === 'recreateFailed') {
    throw new Exit(
      1,
      `スロット ${result.slot} を割り当てたが、データベースとバケットを作れなかった（${result.error?.message ?? result.error}）。\n` +
        '割り当ては残している。原因を直してから node scripts/dev/slot.mjs reset で作り直す',
    )
  }
  console.log(`スロット ${result.slot} を割り当てた（データベース ${values.database}、バケット ${values.publicBucket} と ${values.incomingBucket}）`)
}

/**
 * release：スロットのポートで待ち受けるプロセスを停止してから、割り当てを解く。
 *
 * @param {ReturnType<typeof context>} ctx 作業ツリーの状態
 */
async function release(ctx) {
  if (ctx.main) return console.log('メインの作業ツリーはスロット 0。解くものはない')
  requireShared(ctx)
  const slot = parseSlotRecord(readRecord(ctx.root))
  if (slot === null) return console.log('スロットは割り当てられていない')
  // スロットのポートは他のスロットと重ならないので、待ち受けるものはこのスロットで起動したもの（強制終了した script の残りを含む）
  const result = await releaseSlot({
    slot,
    ports: slotPorts(slot),
    platform: process.platform,
    find: (ports) => findListeners(ports),
    stop: (listeners) => stopListeners(listeners),
    backendRunning: () => backendRunning(slot),
    drop: () => {
      dropData(ctx.root, slot)
      rmSync(recordFile(ctx.root), { force: true })
    },
    log: (line) => console.log(line),
  })
  if (result.code !== 0) throw new Exit(result.code, result.message)
  console.log(`スロット ${slot} を解いた`)
}

/**
 * スロットのバックエンドが動いているか。運用ポートの死活監視に応答があるかで見る。
 *
 * @param {number} slot スロットの番号
 * @returns {Promise<boolean>} 接続が拒まれた（待ち受けがない）とき以外は true（タイムアウトも動いているとみなす）
 */
async function backendRunning(slot) {
  try {
    await fetch(`http://localhost:${slotValues(slot).managementPort}/actuator/health`, {
      signal: AbortSignal.timeout(2000),
    })
    return true
  } catch (error) {
    return !isBackendDown(error)
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
  const problem = checkBackendStopped({ slot, running: await backendRunning(slot), action: '流し直す' })
  if (problem !== null) throw new Exit(problem.code, problem.message)
  recreateData(ctx.root, slot)
  console.log(`スロット ${slot} のデータベース ${values.database} とバケットを作り直した。次のバックエンドの起動で seed が入る`)
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
    const check = checkBackendPorts({
      ports: [values.backendPort, values.managementPort],
      platform: process.platform,
      canRelease: !ctx.main,
      find: (ports) => findListeners(ports),
    })
    if (!check.start) throw new Exit(check.code, check.message)
    if (check.warning !== null) console.error(check.warning)
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
  // 画面は、起動の前に API の型を生成する（新しい作業ツリーには src/api/generated がない）。失敗したら起動せず、その終了コードで終わる
  const generateDir = generateApiDir(target)
  if (generateDir !== null) {
    // pnpm は Windows では .cmd なので shell 経由で流す
    const generated = spawnSync('pnpm generate:api', { ...options, cwd: path.join(ctx.root, generateDir), shell: true })
    if (generated.status !== 0) {
      console.error(`API の型の生成に失敗したので起動しない（${generateDir}）`)
      return generated.status ?? 1
    }
  }
  // Windows の .bat は shell を通さないと起動できない（引数のない 1 つの文字列にして、引数を連結する警告を避ける）
  const child =
    process.platform === 'win32' && command.endsWith('.bat')
      ? spawn([`"${command}"`, ...commandArgs].join(' '), { ...options, shell: true })
      : spawn(command, commandArgs, options)
  /** 実行中の子。終わったら null */
  let running = child
  // シグナルを受けたら子を停止し、子が終わってから終了する。2 回目は待たずに終了する
  stopOnSignals({ current: () => running })
  return new Promise((resolve) => {
    child.on('error', (e) => {
      running = null
      console.error(`起動できなかった：${e.message}`)
      resolve(1)
    })
    child.on('exit', (code, signal) => {
      running = null
      resolve(code ?? (signal ? 1 : 0))
    })
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
  else if (command === 'release') await release(ctx)
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
