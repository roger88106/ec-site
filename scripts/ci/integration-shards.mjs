// 統合テストを分ける数が、手元の既定と CI で一致していることを確認する。
// ずれると、手元で見る分け方（./gradlew integrationTestShards）と、CI が実際に分ける分け方が変わるため。
//
// 手元の既定は、build.gradle.kts の integrationTestShardCount の `.orElse(N)`。
// CI は、ci.yml で統合テストの job の matrix に書いた shard の一覧の数（総数は strategy.job-total で読むので、一覧の数が正）。
// shard の一覧が数の一覧でないとき（式、空の一覧、複数の job）と、`.orElse` が数でないときも失敗にする。
//
// 失敗したとき：分ける数を、ci.yml の shard の一覧と `.orElse(N)` で合わせる。
// 除外：なし
// 使い方：node scripts/ci/integration-shards.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { load } from 'js-yaml'
import { isMain } from './lib/is-main.mjs'
import { describeUnreadableLine } from './lib/unreadable-line.mjs'

const CI_FILE = '.github/workflows/ci.yml'
const GRADLE_FILE = 'backend/ecsite/build.gradle.kts'

/**
 * ci.yml の matrix に書いた shard の一覧の数を読む。
 *
 * @param {string} text ci.yml の内容
 * @returns {{ count: number, job: string } | { reason: string }} 数と job の名前。読めないときは理由
 */
export function readCiShardCount(text) {
  const jobs = Object.entries(load(text)?.jobs ?? {}).filter(([, job]) => job?.strategy?.matrix?.shard !== undefined)
  if (jobs.length === 0) return { reason: 'matrix の shard の一覧が見つからない' }
  if (jobs.length > 1) return { reason: `shard の一覧が複数の job（${jobs.map(([name]) => name).join('、')}）にある（統合テストの job の 1 か所だけに書く）` }
  const [job, { strategy }] = jobs[0]
  const shards = strategy.matrix.shard
  if (!Array.isArray(shards) || shards.length === 0 || !shards.every(Number.isInteger)) {
    return { reason: `${job} の shard を読めない（[1, 2] のような数の一覧で書く）：${JSON.stringify(shards)}` }
  }
  return { count: shards.length, job }
}

/**
 * build.gradle.kts の `integrationTestShardCount` の既定の数を読む。
 *
 * @param {string} text build.gradle.kts の内容
 * @returns {{ count: number, line: number } | { reason: string, line?: number, text?: string }} 数と行。読めないときは理由（読めない行が分かるときは、その行も）
 */
export function readGradleShardCount(text) {
  const lines = text.split('\n').map((raw) => raw.replace(/\r$/, ''))
  const index = lines.findIndex((line) => /gradleProperty\(\s*"integrationTestShardCount"\s*\)/.test(line) && !/^\s*\/\//.test(line))
  if (index < 0) return { reason: 'integrationTestShardCount の既定の数が見つからない' }
  const line = lines[index]
  const value = /\.orElse\(\s*(\d+)\s*\)/.exec(line)?.[1]
  if (value === undefined) return { reason: '読めない書き方（.orElse(N) の N は数だけを読む）', line: index + 1, text: line }
  return { count: Number(value), line: index + 1 }
}

/**
 * 分ける数の食い違いを探す。
 *
 * @param {string} ciText ci.yml の内容
 * @param {string} gradleText build.gradle.kts の内容
 * @returns {string[]} 問題の説明（なければ空）
 */
export function findShardCountProblems(ciText, gradleText) {
  const ci = readCiShardCount(ciText)
  const gradle = readGradleShardCount(gradleText)
  const problems = []
  if ('reason' in ci) problems.push(`${CI_FILE}：${ci.reason}`)
  if ('reason' in gradle) {
    problems.push(gradle.line === undefined ? `${GRADLE_FILE}：${gradle.reason}` : describeUnreadableLine({ file: GRADLE_FILE, line: gradle.line, text: gradle.text, reason: gradle.reason }))
  }
  if ('count' in ci && 'count' in gradle && ci.count !== gradle.count) {
    problems.push(
      `統合テストを分ける数が違う：${CI_FILE} の ${ci.job} の shard の一覧は ${ci.count}、${GRADLE_FILE} の integrationTestShardCount の既定は ${gradle.count}（${gradle.line} 行）。どちらかに合わせる`,
    )
  }
  return problems
}

if (isMain(import.meta.url)) {
  const problems = findShardCountProblems(readFileSync(CI_FILE, 'utf8'), readFileSync(GRADLE_FILE, 'utf8'))
  for (const problem of problems) console.log(problem)
  console.log(`統合テストを分ける数の問題：${problems.length}`)
  process.exit(problems.length > 0 ? 1 : 0)
}
