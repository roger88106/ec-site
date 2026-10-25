// スロット（作業ツリーごとの手元の環境）の値、空きの選び方、環境変数の組み立て。
// docker と git を呼ばない純粋な関数だけを置く（割り当ての手順は、ロック・git・ファイル・docker の操作を引数で受ける）。
// 呼ぶのは scripts/dev/slot.mjs。
import { readFileSync } from 'node:fs'
import path from 'node:path'

/** ローカルのポートの一覧（scripts/dev/ports.json）。他のポートの書き場所との照合は scripts/ci/local-ports.mjs */
const PORT_LIST = JSON.parse(readFileSync(new URL('../ports.json', import.meta.url), 'utf8'))

/**
 * 一覧からポートを引く。
 *
 * @param {string} key 一覧の key
 * @returns {number} ポート（スロット 0 の値）
 */
function portOf(key) {
  const entry = PORT_LIST.ports.find((item) => item.key === key)
  if (!entry) throw new Error(`ポートの一覧にない key: ${key}`)
  return entry.port
}

/** スロットの数。0 がメインの作業ツリー、1〜3 が作業ツリー */
export const SLOT_COUNT = PORT_LIST.slotCount

/** 共有の基盤（compose）のホストのポート。スロットのポートと重ならない */
export const SHARED_PORTS = PORT_LIST.ports.filter((item) => item.group === 'shared').map((item) => item.port)

/** E2E のポート、データベース、バケット。スロットの値と重ならない */
export const E2E = {
  ports: PORT_LIST.ports.filter((item) => item.group === 'e2e').map((item) => item.port),
  database: 'ec_site_e2e',
  buckets: ['ec-site-e2e-images', 'ec-site-e2e-images-incoming'],
}

/** 共有の SeaweedFS（S3 互換）の URL。配信の基準 URL の前半 */
const STORAGE_ORIGIN = `http://localhost:${portOf('seaweedfs')}`

/** 共有の PostgreSQL への JDBC URL の前半 */
const JDBC_PREFIX = `jdbc:postgresql://localhost:${portOf('postgres')}/`

/**
 * スロットの番号が 0〜3 の整数であることを確認する。
 *
 * @param {number} slot スロットの番号
 */
function assertSlot(slot) {
  if (!Number.isInteger(slot) || slot < 0 || slot >= SLOT_COUNT) {
    throw new RangeError(`スロットの番号は 0〜${SLOT_COUNT - 1} の整数: ${slot}`)
  }
}

/**
 * スロットのポートと名前を返す。<br />
 * ポートは「スロット 0 の値 + 10000 × 番号」、データベースとバケットは名前の末尾に番号を付ける（0 は付けない）。
 *
 * @param {number} slot スロットの番号（0〜3）
 * @returns {{ slot: number, backendPort: number, managementPort: number, storePort: number, adminPort: number,
 *   database: string, publicBucket: string, incomingBucket: string, cookiePrefix: string }}
 *   運用ポートは managementPort。Cookie の接頭辞はスロット 0 では空
 */
export function slotValues(slot) {
  assertSlot(slot)
  const offset = PORT_LIST.slotOffset * slot
  const suffix = slot === 0 ? '' : `-${slot}`
  return {
    slot,
    backendPort: portOf('backend') + offset,
    managementPort: portOf('management') + offset,
    storePort: portOf('store') + offset,
    adminPort: portOf('admin') + offset,
    database: slot === 0 ? 'ec_site' : `ec_site_${slot}`,
    publicBucket: `ec-site-images${suffix}`,
    incomingBucket: `ec-site-images-incoming${suffix}`,
    cookiePrefix: slot === 0 ? '' : `s${slot}-`,
  }
}

/**
 * スロットが使うポートを返す。
 *
 * @param {number} slot スロットの番号
 * @returns {number[]} バックエンド、運用ポート、店舗画面、管理画面の順
 */
export function slotPorts(slot) {
  const values = slotValues(slot)
  return [values.backendPort, values.managementPort, values.storePort, values.adminPort]
}

/**
 * `git worktree list --porcelain` の出力を読む。
 *
 * @param {string} text git の出力
 * @returns {{ path: string, main: boolean, prunable: boolean }[]}
 *   作業ツリーの一覧。最初の項目がメインの作業ツリー。prunable は作業ツリーのディレクトリが消えたもの
 */
export function parseWorktrees(text) {
  const worktrees = []
  for (const block of text.replace(/\r/g, '').split(/\n{2,}/)) {
    const lines = block.split('\n')
    const head = lines.find((line) => line.startsWith('worktree '))
    if (!head) continue
    worktrees.push({
      path: head.slice('worktree '.length),
      main: worktrees.length === 0,
      prunable: lines.some((line) => line === 'prunable' || line.startsWith('prunable ')),
    })
  }
  return worktrees
}

/**
 * 作業ツリーの記録（`.local/slot`）の内容からスロットの番号を読む。
 *
 * @param {string | null | undefined} content ファイルの内容。ファイルがなければ null か undefined
 * @returns {number | null} 1〜3 の番号。記録がない、壊れている、0 のときは null
 */
export function parseSlotRecord(content) {
  const text = (content ?? '').trim()
  if (!/^\d$/.test(text)) return null
  const slot = Number(text)
  return slot >= 1 && slot < SLOT_COUNT ? slot : null
}

/**
 * 使われていない最小のスロットを選ぶ。<br />
 * 使用中は、メインの作業ツリーと prunable のものを除く作業ツリーの記録で決める。一覧にない作業ツリーは数えない。
 *
 * @param {string} porcelain `git worktree list --porcelain` の出力
 * @param {Record<string, string | null | undefined>} records 作業ツリーのパス → `.local/slot` の内容
 * @returns {{ slot: number } | { full: true, users: { slot: number, path: string }[] }}
 *   選んだ番号。全て使われていれば、使っている作業ツリー（番号の順）
 */
export function pickSlot(porcelain, records) {
  const users = []
  for (const worktree of parseWorktrees(porcelain)) {
    if (worktree.main || worktree.prunable) continue
    const slot = parseSlotRecord(records[worktree.path])
    if (slot !== null) users.push({ slot, path: worktree.path })
  }
  users.sort((a, b) => a.slot - b.slot)
  for (let slot = 1; slot < SLOT_COUNT; slot++) {
    if (!users.some((user) => user.slot === slot)) return { slot }
  }
  return { full: true, users }
}

/**
 * 起動の前に API の型（`pnpm generate:api`）を生成するディレクトリ。各アプリの `dev` の script と同じく、生成してから起動する。
 *
 * @param {'backend' | 'store' | 'admin'} target 起動する対象
 * @returns {string | null} リポジトリのルートからの相対のパス。生成が要らない対象は null
 */
export function generateApiDir(target) {
  return target === 'store' || target === 'admin' ? `frontend/${target}` : null
}

/**
 * 対象に付ける環境変数を組み立てる。スロット 0 は何も上書きしない。
 *
 * @param {number} slot スロットの番号
 * @param {'backend' | 'store' | 'admin'} target 起動する対象
 * @returns {Record<string, string>} 環境変数の名前 → 値
 */
export function slotEnv(slot, target) {
  const values = slotValues(slot)
  if (!['backend', 'store', 'admin'].includes(target)) throw new Error(`知らない対象: ${target}`)
  if (slot === 0) return {}
  const backendOrigin = `http://localhost:${values.backendPort}`
  if (target === 'backend') {
    return {
      SERVER_PORT: String(values.backendPort),
      MANAGEMENT_SERVER_PORT: String(values.managementPort),
      SPRING_DATASOURCE_URL: JDBC_PREFIX + values.database,
      ECSITE_MEDIA_STORAGE_INCOMING_BUCKET: values.incomingBucket,
      ECSITE_MEDIA_STORAGE_PUBLIC_BUCKET: values.publicBucket,
      ECSITE_MEDIA_STORAGE_PUBLIC_BASE_URL: `${STORAGE_ORIGIN}/${values.publicBucket}`,
      ECSITE_PAYMENT_RETURN_URL: `http://localhost:${values.storePort}/orders/{orderId}?payment=returned`,
      ECSITE_PAYMENT_MOCK_BASE_URL: backendOrigin,
    }
  }
  if (target === 'store') {
    return {
      STORE_API_BASE_URL: `${backendOrigin}/api/store`,
      AUTH_URL: `http://localhost:${values.storePort}`,
      STORE_COOKIE_PREFIX: values.cookiePrefix,
    }
  }
  return { ADMIN_API_PROXY_TARGET: backendOrigin }
}

/** 共有の基盤のうち、動いていることを確かめるコンテナ */
export const SHARED_CONTAINERS = ['ec-site-postgres', 'ec-site-seaweedfs']

/**
 * メインの作業ツリーかを判定する。git-dir と git-common-dir が同じならメイン（追加の作業ツリーの git-dir は worktrees/ の下）。
 *
 * @param {string} gitDir `git rev-parse --path-format=absolute --git-dir` の出力
 * @param {string} commonDir `git rev-parse --path-format=absolute --git-common-dir` の出力
 * @returns {boolean}
 */
export function isMainWorktree(gitDir, commonDir) {
  return path.resolve(gitDir) === path.resolve(commonDir)
}

/**
 * 作業ツリーのスロットを返す。メインの作業ツリーは記録によらず 0。
 *
 * @param {{ main: boolean, record: string | null | undefined }} input record は `.local/slot` の内容
 * @returns {number | null} 番号。割り当てがなければ null
 */
export function currentSlotOf({ main, record }) {
  return main ? 0 : parseSlotRecord(record)
}

/**
 * 作業ツリーの記録（`.local/slot`）に書く内容。
 *
 * @param {number} slot スロットの番号（1〜3）
 * @returns {string} 番号と改行
 */
export function formatSlotRecord(slot) {
  if (!Number.isInteger(slot) || slot < 1 || slot >= SLOT_COUNT) {
    throw new RangeError(`記録するスロットの番号は 1〜${SLOT_COUNT - 1} の整数: ${slot}`)
  }
  return `${slot}\n`
}

/**
 * 共有の基盤のコンテナの状態から、止まっているものと案内を決める。
 *
 * @param {Record<string, boolean>} running コンテナの名前 → 動いているか。ないものは止まっているとする
 * @param {string} mainPath メインの作業ツリーのパス
 * @returns {{ code: 2, stopped: string[], message: string } | null} 全て動いていれば null
 */
export function checkShared(running, mainPath) {
  const stopped = SHARED_CONTAINERS.filter((name) => running[name] !== true)
  if (stopped.length === 0) return null
  return {
    code: 2,
    stopped,
    message:
      `共有の基盤が動いていない（${stopped.join('、')}）。メインの作業ツリーで起動する：\n` +
      `  cd ${mainPath}\n  docker compose up -d\nこの script は docker compose up を流さない`,
  }
}

/**
 * 死活監視の fetch の誤りから、バックエンドが止まっているかを判定する。<br />
 * 待ち受けがない（接続が拒まれた：ECONNREFUSED）ときだけ止まっているとし、タイムアウトなどは動いているとみなす（安全な側）。
 *
 * @param {unknown} error fetch が投げた誤り（Node の undici では `cause.code` に原因が入る）
 * @returns {boolean} 止まっていれば true
 */
export function isBackendDown(error) {
  const cause = error instanceof Error ? error.cause : null
  return typeof cause === 'object' && cause !== null && cause.code === 'ECONNREFUSED'
}

/**
 * バックエンドが動いているときの案内を決める。動いている間にデータベースを削除すると、起動中の処理が失敗する。
 *
 * @param {{ slot: number, running: boolean, action: string }} input action は止めた後にする操作（「流し直す」「解く」）
 * @returns {{ code: 1, message: string } | null} 動いていなければ null
 */
export function checkBackendStopped({ slot, running, action }) {
  if (!running) return null
  return {
    code: 1,
    message: `スロット ${slot} のバックエンド（:${slotValues(slot).backendPort}）が動いている。止めてから${action}（接続を持ったまま削除すると、起動中の処理が失敗する）`,
  }
}

/**
 * バケットを削除する sh の script（SeaweedFS の aws cli のコンテナで流す）。<br />
 * バケットがない誤り（NoSuchBucket）だけを無視し、権限や接続の誤りは出力して失敗にする。
 *
 * @param {string[]} buckets バケットの名前
 * @returns {string}
 */
export function dropBucketsScript(buckets) {
  return buckets
    .map(
      (bucket) =>
        `out=$(aws --endpoint-url http://seaweedfs:8333 s3 rb --force s3://${bucket} 2>&1) || case "$out" in *NoSuchBucket*) ;; *) echo "$out" >&2; exit 1 ;; esac`,
    )
    .join('\n')
}

/**
 * スロットを割り当てる手順。<br />
 * ロックの中では、作業ツリーの一覧を読み、空きを選び、記録を書くだけにする。
 * データベースとバケットの作り直しは長くかかるので、ロックを放した後に流す。
 * 記録を先に書くので、作り直しの間に他の作業ツリーが同じ番号を選ばない。作り直しに失敗しても記録は残す（reset で作り直す）。
 *
 * @param {{ main: boolean, readOwnRecord: () => string | null, listWorktrees: () => string,
 *   readRecord: (worktreePath: string) => string | null, withLock: <T>(action: () => T) => Promise<T>,
 *   writeRecord: (slot: number) => void, recreate: (slot: number) => void }} deps
 *   listWorktrees は `git worktree list --porcelain` の出力、readRecord は作業ツリーの `.local/slot` の内容
 * @returns {Promise<{ kind: 'main' } | { kind: 'already', slot: number } | { kind: 'assigned', slot: number }
 *   | { kind: 'full', users: { slot: number, path: string }[] } | { kind: 'recreateFailed', slot: number, error: unknown }>}
 */
export async function assignSlot(deps) {
  if (deps.main) return { kind: 'main' }
  const existing = parseSlotRecord(deps.readOwnRecord())
  if (existing !== null) return { kind: 'already', slot: existing }
  const picked = await deps.withLock(() => {
    // ロックを待つ間に、同じ作業ツリーの別のセッションが割り当てていないかを見直す
    const again = parseSlotRecord(deps.readOwnRecord())
    if (again !== null) return { kind: 'already', slot: again }
    // 一覧もロックの中で読む。待つ間に作られた作業ツリーの割り当ても数えるため
    const porcelain = deps.listWorktrees()
    const records = {}
    for (const worktree of parseWorktrees(porcelain)) {
      if (!worktree.main) records[worktree.path] = deps.readRecord(worktree.path)
    }
    const choice = pickSlot(porcelain, records)
    if ('full' in choice) return { kind: 'full', users: choice.users }
    deps.writeRecord(choice.slot)
    return { kind: 'picked', slot: choice.slot }
  })
  if (picked.kind !== 'picked') return picked
  try {
    deps.recreate(picked.slot)
  } catch (error) {
    return { kind: 'recreateFailed', slot: picked.slot, error }
  }
  return { kind: 'assigned', slot: picked.slot }
}
