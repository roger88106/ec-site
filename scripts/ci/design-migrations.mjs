// モジュールの設計書（docs/design/modules/<モジュール>.md）の「データモデル」の節に、そのモジュールの全てのバージョン付きのマイグレーションを挙げていることを確認する。
// モジュールは、マイグレーションのファイル名 V<番号>__<接頭辞>_<内容>.sql の接頭辞で決める。接頭辞が設計書の名前と違うものだけ MODULE_OF_PREFIX に置く（ordering は order の設計書）。
// 接頭辞 common は、どのモジュールにも属さない共通の設定（スキーマ、イベント、ロックなど）なので、照合しない。
// 設計書が挙げたマイグレーションが実在しないとき（ファイル名の誤り）と、他のモジュールのマイグレーションを挙げたときも失敗にする。
// マイグレーションの接頭辞に当たる設計書がない、または設計書にデータモデルの節がないときも失敗にする。
// データモデルの節がない設計書（storefront など。マイグレーションを持たない）は、マイグレーションがなければ読み飛ばす。
// 使い方：node scripts/ci/design-migrations.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'
import { isVersionedMigration } from './migrations.mjs'

/** マイグレーションの置き場所 */
const MIGRATIONS = 'backend/ecsite/src/main/resources/db/migration/'

/** モジュールの設計書の置き場所 */
const DESIGNS = 'docs/design/modules/'

/** どのモジュールにも属さないマイグレーションの接頭辞 */
const COMMON_PREFIX = 'common'

/** 接頭辞と設計書の名前が違うモジュール（接頭辞：設計書の名前） */
const MODULE_OF_PREFIX = { ordering: 'order' }

/**
 * マイグレーションが属するモジュール（設計書の名前）を返す。
 *
 * @param {string} name マイグレーションのファイル名
 * @returns {string | null | undefined} モジュール。共通のものは null、接頭辞を読めなければ undefined
 */
export function moduleOfMigration(name) {
  const match = name.match(/^V\d+__([a-z0-9]+)_/)
  if (!match) return undefined
  if (match[1] === COMMON_PREFIX) return null
  return MODULE_OF_PREFIX[match[1]] ?? match[1]
}

/**
 * 設計書の「データモデル」の節に挙げたマイグレーションのファイル名を読む。
 *
 * @param {string} markdown 設計書
 * @returns {string[] | null} ファイル名（書いた順、重ねない）。節がなければ null
 */
export function parseDataModelMigrations(markdown) {
  const lines = markdown.split(/\r?\n/)
  const start = lines.findIndex((line) => /^## データモデル\s*$/.test(line))
  if (start < 0) return null
  const end = lines.findIndex((line, index) => index > start && /^## /.test(line))
  const section = lines.slice(start + 1, end < 0 ? undefined : end).join('\n')
  return [...new Set(section.match(/V\d+__[A-Za-z0-9_]+\.sql/g) ?? [])]
}

/**
 * 設計書とマイグレーションの食い違いを返す。
 *
 * @param {{ migrations: string[], designs: Record<string, string> }} input バージョン付きのマイグレーションのファイル名と、モジュールの名前から設計書の中身への対応
 * @returns {string[]} 失敗の文
 */
export function checkDesignMigrations({ migrations, designs }) {
  const problems = []
  const listed = {}
  for (const [module, markdown] of Object.entries(designs)) listed[module] = parseDataModelMigrations(markdown)
  const existing = new Set(migrations)
  for (const name of migrations) {
    const module = moduleOfMigration(name)
    if (module === null) continue
    if (module === undefined) {
      problems.push(`${MIGRATIONS}${name} のモジュールを、ファイル名（V<番号>__<モジュール>_<内容>.sql）から読めない`)
    } else if (!(module in designs)) {
      problems.push(`${MIGRATIONS}${name} のモジュール ${module} の設計書（${DESIGNS}${module}.md）がない`)
    } else if (listed[module] === null) {
      problems.push(`${DESIGNS}${module}.md に「データモデル」の節がない（${name} を挙げる）`)
    } else if (!listed[module].includes(name)) {
      problems.push(`${DESIGNS}${module}.md の「データモデル」に ${name} がない`)
    }
  }
  for (const [module, names] of Object.entries(listed)) {
    for (const name of names ?? []) {
      if (!existing.has(name)) {
        problems.push(`${DESIGNS}${module}.md の「データモデル」の ${name} は実在しない（ファイル名の誤りか、削除したマイグレーション）`)
      } else if (moduleOfMigration(name) !== module) {
        problems.push(`${DESIGNS}${module}.md の「データモデル」の ${name} は、他のモジュール（${moduleOfMigration(name) ?? '共通'}）のマイグレーション`)
      }
    }
  }
  return problems
}

if (isMain(import.meta.url)) {
  const migrations = listGitFiles([MIGRATIONS]).filter(isVersionedMigration)
  exitIfNoFiles(migrations, `マイグレーション（${MIGRATIONS}）`)
  const designFiles = listGitFiles([DESIGNS]).filter((file) => file.endsWith('.md') && path.dirname(file) === DESIGNS.slice(0, -1))
  exitIfNoFiles(designFiles, `モジュールの設計書（${DESIGNS}）`)
  const designs = {}
  for (const file of designFiles) designs[path.basename(file, '.md')] = readFileSync(file, 'utf8')
  const problems = checkDesignMigrations({ migrations: migrations.map((file) => path.basename(file)), designs })
  for (const problem of problems) console.log(problem)
  console.log(`設計書のマイグレーションの不一致：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
