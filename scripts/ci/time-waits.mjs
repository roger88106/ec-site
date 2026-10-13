// テストが時間で待っていないことを確かめる（テストの書き方 第 5 節）。
// 時間で待つテストは、遅い環境で失敗し、速い環境で時間を無駄にする。条件で待つ（latch、Playwright の expect の再試行など）。
// 使い方：node scripts/ci/time-waits.mjs（リポジトリのルートで）
import { execSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/** テストのコードの置き場所 */
const TEST_FILES = [
  /^backend\/ecsite\/src\/(?:test|integrationTest)\/.*\.java$/,
  /^frontend\/(?:admin|store|shared)\/src\/.*\.test\.tsx?$/,
  /^e2e\/(?:tests|src)\/.*\.ts$/,
]

/** 時間で待つ呼び出し（Thread.sleep、TimeUnit の sleep、Playwright の waitForTimeout） */
const TIME_WAIT = /\bThread\.sleep\(|\bTimeUnit\.\w+\.sleep\(|\bwaitForTimeout\(/g

/**
 * 時間で待つ呼び出しを書いた行を探す。
 *
 * @param {string} text ソース
 * @returns {{ line: number, call: string }[]} 見つけた行と呼び出し
 */
export function findTimeWaits(text) {
  const hits = []
  text.split('\n').forEach((line, index) => {
    for (const match of line.matchAll(TIME_WAIT)) hits.push({ line: index + 1, call: match[0] })
  })
  return hits
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
  const files = execSync('git ls-files -co --exclude-standard', { encoding: 'utf8' })
    .split('\n')
    .filter((file) => file && isTestFile(file) && existsSync(file))
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
