// Gradle のプラグインの id が、ライセンスの検査の一覧（scripts/ci/licenses/policy.json の gradlePlugins）と一致することを確認する。
// Gradle のプラグインは依存の構成（licenseReport が読む構成）に出ないので、ライセンスは policy.json に手で書いている。
// プラグインを追加・削除して一覧を直し忘れると、ライセンスを確認しないプラグインが入る、または使わないものが残るため、過不足を失敗にする。
// プラグインの id は、`gradle/libs.versions.toml` の `[plugins]`（1 行の表と文字列）と、`build.gradle.kts` の `plugins {}` に直接書いた `id("…")`・`kotlin("…")` から読む。
// 読めない書き方（`[plugins.名前]` の表、複数行の表、`plugins {}` の中の知らない行、`apply(plugin = "…")`）は、読み飛ばすと誤って通るので、ファイル:行を出して失敗にする。
// 使い方：node scripts/ci/gradle-plugins.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'
import { describeUnreadableLine } from './lib/unreadable-line.mjs'

/** 読めない行の理由 */
const UNREADABLE_REASON = '読めない書き方。検査の読み方を追加するか、今の書き方にそろえる'

/** バージョンカタログ */
const CATALOG_FILE = 'backend/ecsite/gradle/libs.versions.toml'

/** ライセンスの検査の方針 */
const POLICY_FILE = 'scripts/ci/licenses/policy.json'

/** プラグインの `id("…")` を探す Gradle のファイルの置き場所 */
const BUILD_PATHS = ['backend/ecsite']

/**
 * 照合の対象にするプラグインの id か。<br />
 * `java`、`jacoco` のように `.` のない id は Gradle に組み込みで、別の成果物として入らず、ライセンスの確認の対象にならないので外す。
 *
 * @param {string} id プラグインの id
 * @returns {boolean} 対象なら true
 */
const isExternal = (id) => id.includes('.')

/**
 * バージョンカタログの `[plugins]` の id を読む。<br />
 * 読める書き方は、1 行の表（`foo = { id = "…", version.ref = "…" }`）と、文字列（`foo = "id:バージョン"`）。
 * それ以外の行（`[plugins.名前]` の表、複数行の表、id のない表）は読めないので、`unknown` に集める（読み飛ばすと、ライセンスを確認しないプラグインが通る）。
 *
 * @param {string} text libs.versions.toml の内容
 * @returns {{ ids: string[], unknown: { line: number, text: string }[] }} id（書いた順）と、読めない行（1 始まりの行番号と内容）
 */
export function readCatalogPlugins(text) {
  const ids = []
  const unknown = []
  let inPlugins = false
  text.split('\n').forEach((raw, index) => {
    const line = raw.replace(/\r$/, '').replace(/(^|\s)#.*$/, '').trim()
    if (line === '') {
      return
    }
    const section = /^\[([^\]]+)\]/.exec(line)
    if (section) {
      inPlugins = section[1].trim() === 'plugins'
      if (section[1].trim().startsWith('plugins.')) {
        unknown.push({ line: index + 1, text: line })
      }
      return
    }
    if (!inPlugins) {
      return
    }
    const id =
      /^[\w.-]+\s*=\s*\{[^}]*\bid\s*=\s*"([^"]+)"[^}]*\}$/.exec(line)?.[1] ?? /^[\w.-]+\s*=\s*"([^":]+)(?::[^"]*)?"$/.exec(line)?.[1]
    if (id) {
      ids.push(id)
    } else {
      unknown.push({ line: index + 1, text: line })
    }
  })
  return { ids, unknown }
}

/** `plugins {}` の中の 1 つの文（`;` で区切る）が読める形か。id("…")、kotlin("…")、alias(…)、組み込みの名前（`java`、`` `java-library` ``）の後に、`version …`、`apply …` が続いてよい */
const PLUGIN_STATEMENT = /^(?:id\s*\(\s*"([^"]+)"\s*\)|kotlin\s*\(\s*"([^"]+)"\s*\)|alias\s*\([^)]*\)|`?[\w-]+`?)(?:\s+(?:version|apply)\b.*)?$/

/**
 * `build.gradle.kts` の `plugins {}` の中に直接書いた `id("…")` と `kotlin("…")`（`org.jetbrains.kotlin.…`）を読む。<br />
 * `alias(libs.plugins.…)` はバージョンカタログ側で読むので読まない。行コメントの中は読まない。`plugins {` と同じ行の書き方も読む。
 * `plugins {}` の中の読めない行と、`apply(plugin = "…")` は、`unknown` に集める。
 *
 * @param {string} text Gradle のファイルの内容
 * @returns {{ ids: string[], unknown: { line: number, text: string }[] }} id（書いた順）と、読めない行（1 始まりの行番号と内容）
 */
export function readBuildPlugins(text) {
  const ids = []
  const unknown = []
  let inPlugins = false
  const readStatements = (content, lineNumber) => {
    for (const statement of content.split(';').map((item) => item.trim()).filter((item) => item !== '')) {
      const match = PLUGIN_STATEMENT.exec(statement)
      if (!match) {
        unknown.push({ line: lineNumber, text: statement })
      } else if (match[1]) {
        ids.push(match[1])
      } else if (match[2]) {
        ids.push(`org.jetbrains.kotlin.${match[2]}`)
      }
    }
  }
  text.split('\n').forEach((raw, index) => {
    const line = raw.replace(/\r$/, '').replace(/\/\/.*$/, '')
    const lineNumber = index + 1
    if (/\bapply\s*\(\s*plugin\b|\bapply\s+plugin\b/.test(line)) {
      unknown.push({ line: lineNumber, text: line.trim() })
      return
    }
    let content = line
    if (!inPlugins) {
      const start = /^plugins\s*\{(.*)$/.exec(line)
      if (!start) {
        return
      }
      inPlugins = true
      content = start[1]
    }
    const end = /\}/.exec(content)
    if (end) {
      inPlugins = false
      content = content.slice(0, end.index)
    }
    readStatements(content, lineNumber)
  })
  return { ids, unknown }
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

if (isMain(import.meta.url)) {
  const buildFiles = listGitFiles(BUILD_PATHS).filter((path) => /\.gradle\.kts$/.test(path))
  exitIfNoFiles(buildFiles, 'Gradle のファイル')
  const used = []
  const unknownLines = []
  const catalog = readCatalogPlugins(readFileSync(CATALOG_FILE, 'utf8'))
  used.push(...catalog.ids)
  unknownLines.push(...catalog.unknown.map((item) => describeUnreadableLine({ file: CATALOG_FILE, line: item.line, text: item.text, reason: UNREADABLE_REASON })))
  for (const file of buildFiles) {
    const build = readBuildPlugins(readFileSync(file, 'utf8'))
    used.push(...build.ids)
    unknownLines.push(...build.unknown.map((item) => describeUnreadableLine({ file, line: item.line, text: item.text, reason: UNREADABLE_REASON })))
  }
  let listed
  try {
    listed = readPolicyPluginIds(JSON.parse(readFileSync(POLICY_FILE, 'utf8')))
  } catch (error) {
    console.log(error.message)
    process.exit(1)
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
