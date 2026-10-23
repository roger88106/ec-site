// Gradle のプラグインの id が、ライセンスの検査の一覧（scripts/ci/licenses/policy.json の gradlePlugins）と一致することを確認する。
// Gradle のプラグインは依存の構成（licenseReport が読む構成）に出ないので、ライセンスは policy.json に手で書いている。
// プラグインを追加・削除して一覧を直し忘れると、ライセンスを確認しないプラグインが入る、または使わないものが残るため、過不足を失敗にする。
// プラグインの id は、`gradle/libs.versions.toml` の `[plugins]` と、`build.gradle.kts` の `plugins {}` に直接書いた `id("…")` から読む。
// 使い方：node scripts/ci/gradle-plugins.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'

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
 * バージョンカタログの `[plugins]` の id を読む。
 *
 * @param {string} text libs.versions.toml の内容
 * @returns {string[]} id（書いた順）
 */
export function readCatalogPluginIds(text) {
  const ids = []
  let inPlugins = false
  for (const line of text.split('\n')) {
    const section = /^\s*\[([^\]]+)\]/.exec(line)
    if (section) {
      inPlugins = section[1] === 'plugins'
      continue
    }
    if (inPlugins) {
      const id = /^\s*[\w.-]+\s*=\s*\{[^}]*\bid\s*=\s*"([^"]+)"/.exec(line)?.[1]
      if (id) {
        ids.push(id)
      }
    }
  }
  return ids
}

/**
 * `build.gradle.kts` の `plugins {}` の中に直接書いた `id("…")` を読む。<br />
 * `alias(libs.plugins.…)` はバージョンカタログ側で読むので読まない。行コメントの中は読まない。
 *
 * @param {string} text Gradle のファイルの内容
 * @returns {string[]} id（書いた順）
 */
export function readBuildPluginIds(text) {
  const ids = []
  let inPlugins = false
  for (const raw of text.split('\n')) {
    const line = raw.replace(/\/\/.*$/, '')
    if (!inPlugins) {
      inPlugins = /^plugins\s*\{/.test(line)
      continue
    }
    if (/^\}/.test(line)) {
      inPlugins = false
      continue
    }
    const id = /^\s*id\(\s*"([^"]+)"\s*\)/.exec(line)?.[1]
    if (id) {
      ids.push(id)
    }
  }
  return ids
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
  const used = [...readCatalogPluginIds(readFileSync(CATALOG_FILE, 'utf8'))]
  for (const file of buildFiles) {
    used.push(...readBuildPluginIds(readFileSync(file, 'utf8')))
  }
  let listed
  try {
    listed = readPolicyPluginIds(JSON.parse(readFileSync(POLICY_FILE, 'utf8')))
  } catch (error) {
    console.log(error.message)
    process.exit(1)
  }
  const problems = findPluginProblems(used, listed)
  if (used.filter(isExternal).length === 0) {
    problems.push('Gradle のプラグインが 1 件も見つからない（読む場所が変わっていないか確認する）')
  }
  for (const problem of problems) {
    console.log(problem)
  }
  console.log(`Gradle のプラグイン：${new Set(used.filter(isExternal)).size} 件、読んだファイル：${buildFiles.length} 件、問題：${problems.length} 件`)
  process.exit(problems.length ? 1 : 0)
}
