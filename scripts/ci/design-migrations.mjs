// モジュールの設計書（docs/design/modules/<モジュール>.md）の「データモデル」の節に、そのモジュールの全てのバージョン付きのマイグレーションを挙げていることを確認する。
// モジュールは、マイグレーションのファイル名 V<番号>__<接頭辞>_<内容>.sql の接頭辞で決める。接頭辞が設計書の名前と違うものだけ MODULE_OF_PREFIX に置く（ordering は order の設計書）。
// マイグレーションが設計書に挙がるべきモジュールは、ファイル名の接頭辞のモジュール（common を除く）と、SQL の中のスキーマ付きの表の名前（<スキーマ>.<表>）のスキーマに当たるモジュールの和集合。
// スキーマ common と CREATE SCHEMA の文は、どのモジュールにも当てない。SQL のコメント（-- と /* */）と文字列の中は見ない（読み飛ばす処理は lib/sql-code.mjs）。
// 理由：設計書のデータモデルは、本文の制約を定義するマイグレーションが分かるように、そのモジュールの表に触る全てのマイグレーションを挙げる（common のマイグレーションが他のモジュールの表に制約を加えることがある）。
// 設計書が挙げたマイグレーションが実在しないとき（ファイル名の誤り）と、上の和集合に入らないモジュールのマイグレーションを挙げたときも失敗にする。
// マイグレーションの接頭辞に当たる設計書がない、または設計書にデータモデルの節がないときも失敗にする。
// データモデルの節がない設計書（storefront など。マイグレーションを持たない）は、マイグレーションがなければ読み飛ばす。
// 使い方：node scripts/ci/design-migrations.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'
import { blankSqlNonCode } from './lib/sql-code.mjs'
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
 * SQL の中のスキーマ付きの表の名前（<スキーマ>.<表>）から、スキーマを読む。コメントと文字列の中と、ドットの後ろの名前（ファイル名など）は見ない。
 * 大文字は小文字にそろえ、引用符付きの名前（"payment"."payment"）も読む（引用符の中は blankSqlNonCode が _ にするので、同じ位置の元の文字を読む）。
 *
 * @param {string} sql マイグレーションの SQL
 * @returns {string[]} スキーマの名前（重ねない）
 */
export function schemasOfSql(sql) {
  const { code } = blankSqlNonCode(sql)
  const names = []
  for (const match of code.matchAll(/(?<![\w./"])(?:([A-Za-z][A-Za-z0-9_]*)|"(_+)")\.(?:[A-Za-z_]|"_)/g)) {
    const name = match[1] ?? sql.slice(match.index + 1, match.index + 1 + match[2].length)
    if (/^[A-Za-z][A-Za-z0-9_]*$/.test(name)) names.push(name.toLowerCase())
  }
  return [...new Set(names)]
}

/**
 * マイグレーションを設計書に挙げるべきモジュールを返す。
 *
 * @param {string} name マイグレーションのファイル名
 * @param {string} sql マイグレーションの SQL
 * @param {string[]} knownModules 設計書があるモジュール
 * @returns {Set<string>} モジュール（設計書の名前）。ファイル名の接頭辞のモジュール（common を除く）と、SQL のスキーマに当たるモジュール
 */
export function expectedModules(name, sql, knownModules) {
  const modules = new Set()
  const prefix = moduleOfMigration(name)
  if (prefix) modules.add(prefix)
  for (const schema of schemasOfSql(sql)) {
    if (schema === COMMON_PREFIX) continue
    const module = MODULE_OF_PREFIX[schema] ?? schema
    if (knownModules.includes(module)) modules.add(module)
  }
  return modules
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
 * @param {{ migrations: string[], sqls?: Record<string, string>, designs: Record<string, string> }} input バージョン付きのマイグレーションのファイル名と、ファイル名から SQL への対応（ない SQL は空として読む）と、モジュールの名前から設計書の中身への対応
 * @returns {string[]} 失敗の文
 */
export function checkDesignMigrations({ migrations, sqls = {}, designs }) {
  const problems = []
  const listed = {}
  for (const [module, markdown] of Object.entries(designs)) listed[module] = parseDataModelMigrations(markdown)
  const existing = new Set(migrations)
  const expected = {}
  for (const name of migrations) {
    if (moduleOfMigration(name) === undefined) {
      problems.push(`${MIGRATIONS}${name} のモジュールを、ファイル名（V<番号>__<モジュール>_<内容>.sql）から読めない`)
      continue
    }
    expected[name] = expectedModules(name, sqls[name] ?? '', Object.keys(designs))
    const prefix = moduleOfMigration(name)
    if (prefix && !(prefix in designs)) problems.push(`${MIGRATIONS}${name} のモジュール ${prefix} の設計書（${DESIGNS}${prefix}.md）がない`)
    for (const module of expected[name]) {
      if (!(module in designs)) continue
      if (listed[module] === null) {
        problems.push(`${DESIGNS}${module}.md に「データモデル」の節がない（${name} を挙げる）`)
      } else if (!listed[module].includes(name)) {
        problems.push(`${DESIGNS}${module}.md の「データモデル」に ${name} がない`)
      }
    }
  }
  for (const [module, names] of Object.entries(listed)) {
    for (const name of names ?? []) {
      if (!existing.has(name)) {
        problems.push(`${DESIGNS}${module}.md の「データモデル」の ${name} は実在しない（ファイル名の誤りか、削除したマイグレーション）`)
      } else if (expected[name] && !expected[name].has(module)) {
        problems.push(`${DESIGNS}${module}.md の「データモデル」の ${name} は、他のモジュール（${moduleOfMigration(name) ?? '共通'}）のマイグレーションで、SQL もこのモジュールの表に触らない`)
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
  const sqls = {}
  for (const file of migrations) sqls[path.basename(file)] = readFileSync(file, 'utf8')
  const problems = checkDesignMigrations({ migrations: migrations.map((file) => path.basename(file)), sqls, designs })
  for (const problem of problems) console.log(problem)
  console.log(`設計書のマイグレーションの不一致：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
