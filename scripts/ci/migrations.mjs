// 適用済みのマイグレーション（バージョン付きの V*.sql）を、追加した後に変えていないことを確認する。
// Flyway は適用済みのマイグレーションの checksum を確認するので、コメントだけの変更でも、
// 既に適用した DB で起動に失敗する（バックエンドの書き方、コメントの書き方 第 1 節）。
// 新しい DB で動くテストと CI では見つからないので、ここで確認する。commit の履歴を読むので、shallow clone では失敗する。
// 使い方：node scripts/ci/migrations.mjs（リポジトリのルートで）
import { execFileSync, execSync } from 'node:child_process'
import { splitNulList } from './lib/git-files.mjs'
import { readFileSync } from 'node:fs'
import { isMain } from './lib/is-main.mjs'
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
 * 追加した時と今で中身が違うマイグレーションを返す。改行の違いは無視する。
 *
 * @param {string[]} files マイグレーション
 * @param {(file: string) => string | null} readAdded 追加した commit の中身。まだ commit していなければ null（変わりようがないので見ない）
 * @param {(file: string) => string} readNow 今の中身
 * @returns {string[]} 変わったマイグレーション
 */
export function findChangedMigrations(files, readAdded, readNow) {
  const normalize = (text) => text.replace(/\r\n/g, '\n')
  return files.filter((file) => {
    const added = readAdded(file)
    return added !== null && normalize(added) !== normalize(readNow(file))
  })
}

/**
 * マイグレーションが 1 件もないときの失敗の理由を返す。置き場所の変更で、検査が空振りするのを防ぐ。
 *
 * @param {string[]} files 今あるバージョン付きのマイグレーション
 * @param {string} dir マイグレーションの置き場所
 * @returns {string | null} 0 件なら失敗の理由、あれば null
 */
export function findEmptyReason(files, dir) {
  return files.length === 0 ? `マイグレーションが 1 件も見つからない（検査の対象の置き場所が変わっていないか確認する）：${dir}` : null
}

/**
 * 各ファイルを追加した commit を読む git log の引数を返す。<br />
 * --no-renames がないと、改名したファイルが追加として現れず、見落とす。
 *
 * @param {string} dir マイグレーションの置き場所
 * @param {string} [revision] 履歴を読む起点。なければ HEAD
 * @returns {string[]} git に渡す引数
 */
export function gitLogArgs(dir, revision = 'HEAD') {
  return ['log', '--no-renames', '--diff-filter=A', '--name-only', '--format=%H', revision, '--', dir]
}

/**
 * 改名・削除と中身の変更を判定する履歴の起点を決める。<br />
 * まだ develop にも main にも入っていない自分のブランチのマイグレーションは、どの DB にも適用されていないので、振り直してよい。
 * main に入ったもの（hotfix を含む）は、リリースに出るので、develop に入っていなくても守る。
 * そのため、develop と main のそれぞれ（手元になければ CI の origin/ の方）との merge-base までの履歴で判定する。
 * CI は fetch-depth: 0 で checkout し、origin/develop と origin/main も取得する。
 * 片方の ref しかないときは、ある方で判定する。どちらもない、または merge-base を求められないとき（取得していない、履歴が繋がらない）は、
 * 判定を弱めないために全ての履歴（HEAD）で判定する。
 *
 * @param {string} [cwd] 実行する場所（リポジトリの中）。なければ今のディレクトリ
 * @returns {{ revisions: string[], bases: string[] }} 履歴の起点（commit。なければ HEAD だけ）と、使った ref（なければ空）
 */
export function resolveHistoryRevisions(cwd = process.cwd()) {
  const revisions = []
  const bases = []
  for (const branch of ['develop', 'main']) {
    for (const base of [`origin/${branch}`, branch]) {
      try {
        const ref = (base.includes('/') ? 'refs/remotes/' : 'refs/heads/') + base
        execFileSync('git', ['rev-parse', '--verify', '-q', ref], { cwd, stdio: 'ignore' })
        const revision = execFileSync('git', ['merge-base', 'HEAD', ref], { cwd, encoding: 'utf8' }).trim()
        if (revision) {
          revisions.push(revision)
          bases.push(base)
          break
        }
      } catch {
        // この ref がない、または merge-base がない。次の候補を試す
      }
    }
  }
  return revisions.length === 0 ? { revisions: ['HEAD'], bases } : { revisions, bases }
}

/**
 * 追加した後に、今はないマイグレーション（削除と改名。改名は古い名前が消える）を返す。
 *
 * @param {Iterable<string>} addedFiles 履歴で追加したことのあるマイグレーション
 * @param {string[]} currentFiles 今あるマイグレーション
 * @returns {string[]} 今はないマイグレーション
 */
export function findRemovedMigrations(addedFiles, currentFiles) {
  const current = new Set(currentFiles)
  return [...addedFiles].filter((file) => !current.has(file))
}

/**
 * git log --diff-filter=A --name-only --format=%H の出力から、ファイルごとの最初に追加した commit を読む。
 * 出力は新しい順なので、同じファイルが複数回出たときは最後のものが最初の追加。
 *
 * @param {string} log git log の出力
 * @returns {Map<string, string>} ファイル → 最初に追加した commit
 */
export function parseAddedLog(log) {
  const addedIn = new Map()
  let commit = null
  for (const line of log.split('\n')) {
    if (/^[0-9a-f]{40}$/.test(line)) commit = line
    else if (line) addedIn.set(line, commit)
  }
  return addedIn
}

if (isMain(import.meta.url)) {
  exitIfShallow()
  const files = splitNulList(execFileSync('git', ['ls-files', '-z', '--', MIGRATIONS], { encoding: 'utf8' })).filter(isVersionedMigration)
  const emptyReason = findEmptyReason(files, MIGRATIONS)
  if (emptyReason) {
    console.log(emptyReason)
    process.exit(1)
  }
  // 各ファイルを追加した commit（1 回の git log で読む）
  const history = resolveHistoryRevisions()
  if (history.bases.length === 0) {
    console.log('警告：develop と main（と origin/ の方）がどれもない（または履歴が繋がらない）ので、全ての履歴で判定する。自分のブランチで追加したマイグレーションの振り直しも失敗になる')
  }
  // どちらの履歴にもあるファイルは、先に読んだ方の commit を使う
  const addedIn = new Map()
  for (const revision of history.revisions) {
    const log = execFileSync('git', gitLogArgs(MIGRATIONS, revision), { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
    for (const [file, commit] of parseAddedLog(log)) {
      if (!addedIn.has(file)) addedIn.set(file, commit)
    }
  }
  // 履歴（develop と main との分岐点まで）にあって今ないバージョン付きのマイグレーション。ステージした削除と改名は、hook でも今の index に無いので見つかる
  const removed = findRemovedMigrations([...addedIn.keys()].filter(isVersionedMigration), files)
  for (const file of removed) {
    console.log(`追加した後に削除か改名された（元に戻す）：${file}`)
  }
  // 分岐の後に追加したもの（commit の前の hook でステージしたものを含む）は、履歴にないので中身を見ない
  const readAdded = (file) =>
    !addedIn.has(file) ? null :
    execSync(`git show ${addedIn.get(file)}:"${file}"`, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
  const changed = findChangedMigrations(files, readAdded, (file) => readFileSync(file, 'utf8'))
  for (const file of changed) {
    console.log(`追加した後に変わった（元に戻し、変更は新しいマイグレーションで行う）：${file}`)
  }
  console.log(`変わったマイグレーション：${changed.length}、削除か改名：${removed.length}（${files.length} 件中）`)
  process.exit(changed.length || removed.length ? 1 : 0)
}
