// テストが時間で待っていないことを確認する（テストの書き方 第 5 節）。
// 時間で待つテストは、遅い環境で失敗し、速い環境で時間を無駄にする。条件で待つ（latch、Playwright の expect の再試行など）。
// 使い方：node scripts/ci/time-waits.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { listGitFiles } from './lib/git-files.mjs'

/** テストのコードの置き場所。本番のソースを読む検査ではなくテストを読むので、画面の本番のソースの定義（lib/screen-sources.mjs）は使わない */
const TEST_FILES = [
  /^backend\/ecsite\/src\/(?:test|integrationTest)\/.*\.java$/,
  /^frontend\/(?:admin|store|shared)\/src\/.*\.test\.tsx?$/,
  /^e2e\/(?:tests|src|showcase)\/.*\.ts$/,
]

/**
 * 時間で待つ呼び出し（Thread.sleep、TimeUnit の sleep、Playwright の waitForTimeout）と、
 * 決まった時間だけ見る・待ってから見る Awaitility の書き方（during、pollDelay）
 */
const TIME_WAIT = /\bThread\.sleep\(|\bTimeUnit\.\w+\.sleep\(|\bwaitForTimeout\(|\.during\(|\.pollDelay\(/g

/** Promise で包んで、決まった時間だけ待つ setTimeout（new Promise((resolve) => setTimeout(resolve, …))）。行をまたいでもよい */
const PROMISE_TIMEOUT = /new Promise\(\s*\(?\s*(\w+)\s*\)?\s*=>\s*(setTimeout\()\s*\1\b/g

/**
 * 時間で待つ呼び出しを書いた行を探す。
 *
 * @param {string} text ソース
 * @returns {{ line: number, call: string }[]} 見つけた行と呼び出し（行の順）
 */
export function findTimeWaits(text) {
  const lineOf = (index) => text.slice(0, index).split('\n').length
  const hits = [...text.matchAll(TIME_WAIT)].map((match) => ({ line: lineOf(match.index), call: match[0] }))
  for (const match of text.matchAll(PROMISE_TIMEOUT)) {
    hits.push({ line: lineOf(match.index + match[0].lastIndexOf(match[2])), call: match[2] })
  }
  return hits.sort((a, b) => a.line - b.line)
}

/**
 * 調べるファイルかを返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} テストのコードなら true
 */
export function isTestFile(file) {
  return TEST_FILES.some((pattern) => pattern.test(file))
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const files = listGitFiles().filter((file) => isTestFile(file))
  let count = 0
  for (const file of files) {
    for (const hit of findTimeWaits(readFileSync(file, 'utf8'))) {
      console.log(`${file}:${hit.line} ${hit.call}`)
      count += 1
    }
  }
  console.log(`時間で待つテスト：${count}`)
  process.exit(count ? 1 : 0)
}
