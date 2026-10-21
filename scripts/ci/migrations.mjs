// 適用済みのマイグレーション（バージョン付きの V*.sql）を、追加した後に変えていないことを確認する。
// Flyway は適用済みのマイグレーションの checksum を確認するので、コメントだけの変更でも、
// 既に適用した DB で起動に失敗する（バックエンドの書き方、コメントの書き方 第 1 節）。
// 新しい DB で動くテストと CI では見つからないので、ここで確認する。commit の履歴を読むので、shallow clone では失敗する。
// 使い方：node scripts/ci/migrations.mjs（リポジトリのルートで）
import { execSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

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
 * @param {(file: string) => string} readAdded 追加した commit の中身
 * @param {(file: string) => string} readNow 今の中身
 * @returns {string[]} 変わったマイグレーション
 */
export function findChangedMigrations(files, readAdded, readNow) {
  const normalize = (text) => text.replace(/\r\n/g, '\n')
  return files.filter((file) => normalize(readAdded(file)) !== normalize(readNow(file)))
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  if (execSync('git rev-parse --is-shallow-repository', { encoding: 'utf8' }).trim() === 'true') {
    console.log('shallow clone では commit の履歴を読めない（CI の checkout に fetch-depth: 0 を付ける）')
    process.exit(1)
  }
  const files = execSync(`git ls-files "${MIGRATIONS}"`, { encoding: 'utf8' }).split('\n').filter(isVersionedMigration)
  // 各ファイルを追加した commit（1 回の git log で、新しい順に読む。最後に出たものが最初の追加）
  const addedIn = new Map()
  let commit = null
  for (const line of execSync(`git log --diff-filter=A --name-only --format=%H -- "${MIGRATIONS}"`, {
    encoding: 'utf8',
    maxBuffer: 64 * 1024 * 1024,
  }).split('\n')) {
    if (/^[0-9a-f]{40}$/.test(line)) commit = line
    else if (line) addedIn.set(line, commit)
  }
  const readAdded = (file) =>
    execSync(`git show ${addedIn.get(file)}:"${file}"`, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
  const changed = findChangedMigrations(files, readAdded, (file) => readFileSync(file, 'utf8'))
  for (const file of changed) {
    console.log(`追加した後に変わった（元に戻し、変更は新しいマイグレーションで行う）：${file}`)
  }
  console.log(`変わったマイグレーション：${changed.length}（${files.length} 件中）`)
  process.exit(changed.length ? 1 : 0)
}
