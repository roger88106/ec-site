// 統合テストを分ける数が、手元の既定と CI で一致していることを確認する。
// 手元の既定：`backend/ecsite/build.gradle.kts` の `integrationTestShardCount` の `.orElse(N)`（`integrationTestShards` が各側のクラスを示すときの数）。
// CI：`.github/workflows/ci.yml` の統合テストの job の matrix の `shard` の一覧の数（総数は `strategy.job-total` で読むので、一覧の数が正）。
// ずれると、手元で見る分け方（./gradlew integrationTestShards）と、CI が実際に分ける分け方が変わる。
// `shard` の一覧は、`[1, 2]` の形か、`- 1` の行の並びだけを読む。ほかの書き方（式、空の一覧、複数の定義）は、読み飛ばさず失敗にする。
// 使い方：node scripts/ci/integration-shards.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { isMain } from './lib/is-main.mjs'
import { describeUnreadableLine } from './lib/unreadable-line.mjs'

const CI_FILE = '.github/workflows/ci.yml'
const GRADLE_FILE = 'backend/ecsite/build.gradle.kts'

/** @typedef {{ count: number, line: number, text: string } | { reason: string, line?: number, text?: string }} ShardCount */

/**
 * ci.yml の matrix の `shard` の一覧の数を読む。
 *
 * @param {string} text ci.yml の内容
 * @returns {ShardCount} 数と行。読めないときは理由（読めない行が分かるときは、その行も）
 */
export function readCiShardCount(text) {
  const lines = text.split('\n').map((raw) => raw.replace(/\r$/, ''))
  const keys = lines.map((line, index) => ({ line, index })).filter(({ line }) => /^\s*shard\s*:/.test(line))
  if (keys.length === 0) return { reason: 'matrix の shard の一覧が見つからない' }
  if (keys.length > 1) return { reason: 'shard の一覧が複数ある（統合テストの job の matrix の 1 か所だけに書く）', line: keys[1].index + 1, text: keys[1].line }
  const { line, index } = keys[0]
  const unreadable = (reason) => ({ reason, line: index + 1, text: line })
  const value = line.replace(/^\s*shard\s*:/, '').replace(/\s+#.*$/, '').trim()
  if (value === '') {
    const indent = line.match(/^\s*/)[0].length
    let count = 0
    for (const next of lines.slice(index + 1)) {
      if (/^\s*(#.*)?$/.test(next)) continue
      if (next.match(/^\s*/)[0].length < indent || !/^\s*-\s+\d+\s*(#.*)?$/.test(next)) break
      count += 1
    }
    return count > 0 ? { count, line: index + 1, text: line } : unreadable('読めない書き方（数の並びの `- 1` の行にする）')
  }
  if (!/^\[\s*\d+(?:\s*,\s*\d+)*\s*,?\s*\]$/.test(value)) return unreadable('読めない書き方（[1, 2] の形か、`- 1` の行の並びだけを読む）')
  return { count: value.slice(1, -1).split(',').filter((item) => item.trim() !== '').length, line: index + 1, text: line }
}

/**
 * build.gradle.kts の `integrationTestShardCount` の既定の数を読む。
 *
 * @param {string} text build.gradle.kts の内容
 * @returns {ShardCount} 数と行。読めないときは理由
 */
export function readGradleShardCount(text) {
  const lines = text.split('\n').map((raw) => raw.replace(/\r$/, ''))
  const index = lines.findIndex((line) => /gradleProperty\(\s*"integrationTestShardCount"\s*\)/.test(line) && !/^\s*\/\//.test(line))
  if (index < 0) return { reason: 'integrationTestShardCount の既定の数が見つからない' }
  const line = lines[index]
  const value = /\.orElse\(\s*(\d+)\s*\)/.exec(line)?.[1]
  if (value === undefined) return { reason: '読めない書き方（.orElse(N) の N は数だけを読む）', line: index + 1, text: line }
  return { count: Number(value), line: index + 1, text: line }
}

/**
 * 分ける数の食い違いを探す。
 *
 * @param {string} ciText ci.yml の内容
 * @param {string} gradleText build.gradle.kts の内容
 * @returns {string[]} 問題の説明（読めない行は `ファイル:行 内容（理由）`。なければ空）
 */
export function findShardCountProblems(ciText, gradleText) {
  const sources = [
    { file: CI_FILE, result: readCiShardCount(ciText) },
    { file: GRADLE_FILE, result: readGradleShardCount(gradleText) },
  ]
  const problems = []
  for (const { file, result } of sources) {
    if ('count' in result) continue
    problems.push(result.line === undefined ? `${file}：${result.reason}` : describeUnreadableLine({ file, line: result.line, text: result.text, reason: result.reason }))
  }
  const [ci, gradle] = sources.map(({ result }) => result)
  if ('count' in ci && 'count' in gradle && ci.count !== gradle.count) {
    problems.push(
      `統合テストを分ける数が違う：${CI_FILE} の shard の一覧は ${ci.count}（${ci.line} 行）、${GRADLE_FILE} の integrationTestShardCount の既定は ${gradle.count}（${gradle.line} 行）。どちらかに合わせる`,
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
