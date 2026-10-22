// 機能紹介のページ（showcase/index.html）の数字を数える（開発環境の起動 第 6 節）。
//   spec：docs/specs/ の直下とアーカイブの下の spec のディレクトリ（NNN-名前）の数
//   AC：機能の spec の定義の行（- **AC-n**、1→10 の - **AC-n-m**）の数
//   テスト：Java は、./gradlew check の結果（build/test-results/ の XML）があれば実行した件数（パラメーターの組ごと）、
//          なければソースの @Test・@ParameterizedTest の数。Vitest・Playwright は、ソースの test・it の数
//          （*.test.ts(x)、*.spec.ts。test.describe などのまとまりは数えない）。ページの数字は実行した件数で書く
// 使い方：node scripts/dev/showcase-counts.mjs（どこから流してもよい）
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { listSpecDirs } from '../ci/lib/spec-dirs.mjs'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')

/** テストを探さないディレクトリ */
const SKIPPED_DIRS = new Set(['.git', '.gradle', '.idea', '.local', '.next', 'build', 'coverage', 'dist', 'generated', 'node_modules', 'test-results'])

/** Java のテストのメソッドの注釈 */
const JAVA_TEST = /^\s*@(?:Test|ParameterizedTest)\b/gm

/** Vitest・Playwright のテストの呼び出し（describe・step・beforeEach などは数えない） */
const TS_TEST = /\b(?:it|test)(?:\.(?:only|skip|fixme|concurrent|serial|each\([^)]*\)))*\(\s*['"`]/g

/**
 * テストのファイルを探す。
 *
 * @param {string} dir 探すディレクトリ
 * @returns {{ file: string, kind: 'java' | 'ts' }[]} テストのファイルと種類
 */
function findTestFiles(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) return SKIPPED_DIRS.has(entry.name) ? [] : findTestFiles(full)
    if (entry.name.endsWith('.java') && /[\\/]src[\\/](test|integrationTest)[\\/]/.test(full)) return [{ file: full, kind: 'java' }]
    if (/\.test\.tsx?$|\.spec\.ts$/.test(entry.name)) return [{ file: full, kind: 'ts' }]
    return []
  })
}

const specDirs = listSpecDirs(path.join(ROOT, 'docs/specs'))

let acs = 0
for (const { dir } of specDirs) {
  try {
    acs += [...readFileSync(path.join(dir, 'spec.md'), 'utf8').matchAll(/^- \*\*AC-[0-9]+(?:-[0-9]+)?\*\*/gm)].length
  } catch (error) {
    if (error.code !== 'ENOENT') throw error
  }
}

const tests = { java: 0, ts: 0 }
for (const { file, kind } of findTestFiles(ROOT)) {
  tests[kind] += [...readFileSync(file, 'utf8').matchAll(kind === 'java' ? JAVA_TEST : TS_TEST)].length
}

/**
 * Java のテストを実行した件数を、Gradle の結果の XML から数える。
 *
 * @returns {number | null} 件数。結果がなければ null
 */
function executedJavaTests() {
  const dirs = ['test', 'integrationTest'].map((set) => path.join(ROOT, 'backend/ecsite/build/test-results', set))
  if (!dirs.every(existsSync)) return null
  return dirs
    .flatMap((dir) => readdirSync(dir).filter((name) => name.endsWith('.xml')).map((name) => path.join(dir, name)))
    .reduce((sum, file) => sum + Number(/<testsuite [^>]*\btests="(\d+)"/.exec(readFileSync(file, 'utf8'))?.[1] ?? 0), 0)
}

const executed = executedJavaTests()
const java = executed ?? tests.java
const javaNote = executed === null ? 'ソースの数。./gradlew check の後に流すと実行した件数になる' : '実行した件数'

console.log(`spec：${specDirs.length}`)
console.log(`AC：${acs}`)
console.log(`テスト：${java + tests.ts}（Java ${java}（${javaNote}）、Vitest・Playwright ${tests.ts}）`)
