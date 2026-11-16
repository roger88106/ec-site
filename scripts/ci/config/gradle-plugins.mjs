// Gradle のプラグインの id が、ライセンスの検査の一覧（scripts/ci/licenses/policy.json の gradlePlugins）と過不足なく一致することを確認する。
// Gradle のプラグインは依存の構成（licenseReport が読む構成）に出ないので、ライセンスは policy.json に手動で書いている。
// プラグインを追加・削除して一覧を修正し忘れると、ライセンスを確認しないプラグインが入る、または使わないものが残るため、過不足を失敗にする。
//
// プラグインの id は、`gradle/libs.versions.toml` の `[plugins]` から読む。java、jacoco のように `.` のない id は Gradle の組み込みで、別の成果物として入らないので対象にしない。
// カタログを通さない書き方（Gradle のファイルの `id("…")`、`kotlin("…")`、`apply(plugin = "…")`）と、カタログの id のない項目は、照合から漏れるので失敗にする。
//
// 失敗したとき：プラグインを加える・外すときは、gradlePlugins にライセンスを確認して加える・削除する。
//
// 除外：なし
//
// 使い方：node scripts/ci/config/gradle-plugins.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { parse } from 'smol-toml'
import { exitIfNoFiles, listGitFiles } from '../lib/git/git-files.mjs'
import { describeUnreadableLine } from '../lib/source/unreadable-line.mjs'

/** カタログを通さない書き方の理由 */
const DIRECT_REASON = '外部のプラグインは、libs.versions.toml の [plugins] に書き、plugins {} で alias(libs.plugins.…) にする'

/** バージョンカタログ */
const CATALOG_FILE = 'backend/ecsite/gradle/libs.versions.toml'

/** ライセンスの検査の方針 */
const POLICY_FILE = 'scripts/ci/licenses/policy.json'

/** プラグインの `id("…")` を探す Gradle のファイルの置き場所 */
const BUILD_PATHS = ['backend/ecsite']

/**
 * 照合の対象にするプラグインの id か。
 *
 * `java`、`jacoco` のように `.` のない id は Gradle に組み込みで、別の成果物として入らず、ライセンスの確認の対象にならないので外す。
 *
 * @param {string} id プラグインの id
 * @returns {boolean} 対象なら true
 */
const isExternal = (id) => id.includes('.')

/**
 * バージョンカタログの `[plugins]` の id を読む。
 *
 * 値は、表の `id` か、文字列（`"id:バージョン"`）の `:` の前。id を読めない項目は、読み飛ばすとライセンスを確認しないプラグインが通るので、`unknown` に集める。
 *
 * @param {string} text libs.versions.toml の内容
 * @returns {{ ids: string[], unknown: string[] }} id（書いた順）と、id を読めない項目の名前
 */
export function readCatalogPlugins(text) {
  const ids = []
  const unknown = []
  for (const [name, value] of Object.entries(parse(text).plugins ?? {})) {
    const id = typeof value === 'string' ? value.split(':')[0] : value?.id
    if (typeof id === 'string' && id !== '') {
      ids.push(id)
    } else {
      unknown.push(name)
    }
  }
  return { ids, unknown }
}

/** カタログを通さずにプラグインを入れる書き方（`id("…")`、`kotlin("…")`、`apply(plugin = "…")`、`apply plugin:`） */
const DIRECT_PLUGIN = /\b(?:id|kotlin)\s*\(\s*"|\bapply\s*\(\s*plugin\b|\bapply\s+plugin\b/

/**
 * Gradle のファイルで、カタログを通さずにプラグインを入れる行を探す。行コメントの中は読まない。
 *
 * 外部のプラグインは `plugins {}` に `alias(libs.plugins.…)` で書く。カタログの `[plugins]` だけを照合するので、他の書き方はライセンスの確認から漏れる。
 *
 * @param {string} text Gradle のファイルの内容
 * @returns {{ line: number, text: string }[]} 見つけた行（1 始まりの行番号と内容）
 */
export function findDirectPlugins(text) {
  return text
    .split('\n')
    .map((raw, index) => ({ line: index + 1, text: raw.replace(/\r$/, '').replace(/\/\/.*$/, '').trim() }))
    .filter((item) => DIRECT_PLUGIN.test(item.text))
}

/**
 * ライセンスの検査の方針の `gradlePlugins` のキーを読む。
 *
 * @param {{ gradlePlugins?: Record<string, string> }} policy policy.json の内容
 * @returns {string[]} id（`$comment` を除く）
 */
export function readPolicyPluginIds(policy) {
  if (!policy.gradlePlugins || typeof policy.gradlePlugins !== 'object') {
    throw new Error(`${POLICY_FILE} に gradlePlugins がない`)
  }
  return Object.keys(policy.gradlePlugins).filter((key) => key !== '$comment')
}

/**
 * 使っているプラグインと、ライセンスの一覧を照合する。
 *
 * @param {string[]} used 使っているプラグインの id
 * @param {string[]} listed ライセンスの一覧のプラグインの id
 * @returns {string[]} 過不足の説明
 */
export function findPluginProblems(used, listed) {
  const usedSet = new Set(used.filter(isExternal))
  const listedSet = new Set(listed.filter(isExternal))
  const problems = []
  for (const id of usedSet) {
    if (!listedSet.has(id)) {
      problems.push(`${POLICY_FILE} の gradlePlugins にない（ライセンスを確認して加える）：${id}`)
    }
  }
  for (const id of listedSet) {
    if (!usedSet.has(id)) {
      problems.push(`${POLICY_FILE} の gradlePlugins にあるが使っていない（削除する）：${id}`)
    }
  }
  return problems
}

if (import.meta.main) {
  const buildFiles = listGitFiles(BUILD_PATHS).filter((path) => /\.gradle\.kts$/.test(path))
  exitIfNoFiles(buildFiles, 'Gradle のファイル')
  /** 読むファイルがなければ、理由を出して終了コード 2 で終わる */
  const readRequired = (file) => {
    try {
      return readFileSync(file, 'utf8')
    } catch (e) {
      console.log(`${file} を読めない：${e.code ?? e.message}`)
      process.exit(2)
    }
  }
  const used = []
  const unknownLines = []
  const catalog = readCatalogPlugins(readRequired(CATALOG_FILE))
  used.push(...catalog.ids)
  unknownLines.push(...catalog.unknown.map((name) => `${CATALOG_FILE} の plugins.${name}（id を読めない。id を書く）`))
  for (const file of buildFiles) {
    const direct = findDirectPlugins(readRequired(file))
    unknownLines.push(...direct.map((item) => describeUnreadableLine({ file, line: item.line, text: item.text, reason: DIRECT_REASON })))
  }
  let listed
  try {
    listed = readPolicyPluginIds(JSON.parse(readFileSync(POLICY_FILE, 'utf8')))
  } catch (e) {
    console.log(e.message)
    process.exit(2)
  }
  const problems = [...unknownLines, ...findPluginProblems(used, listed)]
  if (used.filter(isExternal).length === 0) {
    problems.push('Gradle のプラグインが 1 件も見つからない（読む場所が変わっていないか確認する）')
  }
  for (const problem of problems) {
    console.log(problem)
  }
  console.log(`Gradle のプラグイン：${new Set(used.filter(isExternal)).size} 件、読んだファイル：${buildFiles.length} 件、問題：${problems.length} 件`)
  process.exit(problems.length ? 1 : 0)
}
