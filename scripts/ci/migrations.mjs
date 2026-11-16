// 適用済みのマイグレーション（バージョン付きの V*.sql）を、変えていないことを確認する（コメントの書き方の「何を書くか」）。
// Flyway は checksum を確認するので、コメントだけの変更でも、適用した DB で起動に失敗する。
// 新しい DB で動くテストと CI では見つからないので、ここで確認する。どこかの DB に適用されうる状態（基準）は resolveBaseRevisions が決める。
// 基準にあるものが今の index（commit の前の hook では次の commit）にない（削除、改名）か、中身が違えば失敗にする。
// マイグレーションが 0 件のときも失敗にする。分岐点を求めるので、shallow clone では失敗する。
//
// 前のリリースのアプリが動けなくなる変更（バックエンドの書き方の「前のリリースのアプリが動き続けられる変更だけにする」）は、レビューで見る。
//
// 失敗したとき：適用済みのマイグレーションは元に戻し、変更は新しいマイグレーションで加える。
// 除外：なし
// 使い方：node scripts/ci/migrations.mjs（リポジトリのルートで）
import { execFileSync } from 'node:child_process'
import { emptyListReason, splitNulList } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'
import { mergeBaseWith, tryGit } from './lib/merge-base.mjs'
import { exitIfShallow } from './lib/shallow-clone.mjs'

/** マイグレーションの置き場所 */
const MIGRATIONS = 'backend/ecsite/src/main/resources/db/migration/'

/**
 * バージョン付きのマイグレーションかを返す。繰り返しのもの（R__）は、変えると適用し直されるので対象外。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} バージョン付きなら true
 */
export function isVersionedMigration(file) {
  return /(^|\/)V[^/]*__[^/]*\.sql$/.test(file)
}

/**
 * 基準と今で中身が違うマイグレーションを返す。改行の違いは無視する。
 *
 * @param {string[]} files マイグレーション
 * @param {(file: string) => string | null} readBase 基準の中身。基準になければ null（新しいものなので見ない）
 * @param {(file: string) => string} readNow 今の中身
 * @returns {string[]} 変わったマイグレーション
 */
export function findChangedMigrations(files, readBase, readNow) {
  const normalize = (text) => text.replace(/\r\n/g, '\n')
  return files.filter((file) => {
    const base = readBase(file)
    return base !== null && normalize(base) !== normalize(readNow(file))
  })
}

/**
 * マイグレーションが 1 件もないときの失敗の理由を返す。文は他の検査と同じ共通の関数（emptyListReason）で作り、置き場所を添える。
 *
 * @param {string[]} files 今あるバージョン付きのマイグレーション
 * @param {string} dir マイグレーションの置き場所
 * @returns {string | null} 0 件なら失敗の理由、あれば null
 */
export function migrationsEmptyReason(files, dir) {
  return emptyListReason(files, `マイグレーション（${dir}）`)
}

/**
 * 基準にあって、今はないマイグレーション（削除と改名。改名は古い名前が消える）を返す。
 *
 * @param {Iterable<string>} baseFiles 基準にあるマイグレーション
 * @param {string[]} currentFiles 今あるマイグレーション
 * @returns {string[]} 今はないマイグレーション
 */
export function findRemovedMigrations(baseFiles, currentFiles) {
  const current = new Set(currentFiles)
  return [...baseFiles].filter((file) => !current.has(file))
}

/**
 * 守る基準（どこかの DB に適用されうる状態）の commit を決める。
 *
 * まだ develop にも main にも入っていない自分のブランチのマイグレーションは、どの DB にも適用されていないので、振り直してよい。
 * main に入ったもの（hotfix を含む）は、リリースに出るので、develop に入っていなくても守る。
 * そのため、HEAD と develop・main のそれぞれ（CI の origin/ の方を先に見る）との merge-base を基準にする。
 * 履歴の commit（追加と改名）でなく tree を比べるので、自分のブランチの追加と改名が merge commit で develop の履歴に入っても、失敗にならない。
 * merge-base が HEAD 自身（develop の上で実行する、push の CI）のときは、HEAD と、HEAD の 1 つ目の親（マージの前の develop）を基準にする。
 * どの基準も求められないとき（ref がない、履歴が繋がらない）は、HEAD の 1 つ目の親を基準にし、警告を出す。
 *
 * @param {string} [cwd] 実行する場所（リポジトリの中）。なければ今のディレクトリ
 * @returns {{ revisions: string[], bases: string[] }} 基準の commit（重複なし。初めの commit で親がなければ空）と、使った ref（なければ空）
 */
export function resolveBaseRevisions(cwd = process.cwd()) {
  const head = tryGit(['rev-parse', '--verify', '-q', 'HEAD'], cwd)
  if (!head) return { revisions: [], bases: [] }
  const parent = tryGit(['rev-parse', '--verify', '-q', 'HEAD^1'], cwd)
  const revisions = new Set()
  const bases = []
  for (const branch of ['develop', 'main']) {
    const found = mergeBaseWith(branch, cwd)
    if (!found) continue
    revisions.add(found.revision)
    if (found.revision === head && parent) revisions.add(parent)
    bases.push(found.base)
  }
  if (bases.length === 0 && parent) revisions.add(parent)
  return { revisions: [...revisions], bases }
}

/**
 * git ls-tree -r -z と git ls-files -s -z の出力から、パスごとの blob を読む。
 *
 * @param {string} output NUL 区切りの出力（各項目は「空白で区切った属性、タブ、パス」）
 * @param {number} column 属性を空白で分けたときの blob の位置
 * @returns {Map<string, string>} パス → blob
 */
export function parseBlobList(output, column) {
  const blobs = new Map()
  for (const entry of splitNulList(output)) {
    const tab = entry.indexOf('\t')
    blobs.set(entry.slice(tab + 1), entry.slice(0, tab).split(' ')[column])
  }
  return blobs
}

if (isMain(import.meta.url)) {
  exitIfShallow()
  const git = (args) => execFileSync('git', args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
  // 今の index（commit の前の hook では次の commit。ステージした削除・改名・変更を含む）
  const current = parseBlobList(git(['ls-files', '-s', '-z', '--', MIGRATIONS]), 1)
  const files = [...current.keys()].filter(isVersionedMigration)
  const emptyReason = migrationsEmptyReason(files, MIGRATIONS)
  if (emptyReason) {
    console.log(emptyReason)
    process.exit(1)
  }
  const { revisions, bases } = resolveBaseRevisions()
  if (bases.length === 0) {
    console.log('警告：develop と main（と origin/ の方）がどれもない（または履歴が繋がらない）ので、HEAD の 1 つ目の親を基準にする（初めの commit なら全てを新しいものとみなす）')
  }
  const removed = new Set()
  const changed = new Set()
  for (const revision of revisions) {
    const base = parseBlobList(git(['ls-tree', '-r', '-z', revision, '--', MIGRATIONS]), 2)
    const baseFiles = [...base.keys()].filter(isVersionedMigration)
    findRemovedMigrations(baseFiles, files).forEach((file) => removed.add(file))
    // blob が同じものは読まない。違うものだけ中身を読み、改行の違いを無視して比べる
    const differing = files.filter((file) => base.has(file) && base.get(file) !== current.get(file))
    const readBlob = (blob) => git(['cat-file', 'blob', blob])
    findChangedMigrations(differing, (file) => readBlob(base.get(file)), (file) => readBlob(current.get(file))).forEach((file) => changed.add(file))
  }
  for (const file of removed) {
    console.log(`追加した後に削除か改名された（元に戻す）：${file}`)
  }
  for (const file of changed) {
    console.log(`追加した後に変わった（元に戻し、変更は新しいマイグレーションで行う）：${file}`)
  }
  console.log(`変わったマイグレーション：${changed.size}、削除か改名：${removed.size}（${files.length} 件中）`)
  process.exit(changed.size || removed.size ? 1 : 0)
}
