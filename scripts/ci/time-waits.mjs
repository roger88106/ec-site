// テストが時間で待っていないことを確認する（テストの書き方 第 5 節）。
// 時間で待つテストは、遅い環境で失敗し、速い環境で時間を無駄にする。条件で待つ（latch、Playwright の expect の再試行など）。
// 対象は、バックエンド・画面・E2E のテストに加え、E2E の実行の script、Playwright・Vitest・Vite の設定、画面のテストの補助（src/test/）。
// 時間で待つ呼び出し（Thread.sleep、waitForTimeout、Awaitility の during・pollDelay、Promise で包んだ setTimeout）があると失敗にする。
// 例外は time-waits-exclusions.json に理由を書く。キーは `<ファイル>:<待ちを書いた行の文字>`（前後の空白は除く）。
// 行番号でなく行の文字で当てるので、行が動いても除外は効き、同じファイルに後から書いた別の待ちは失敗になる。
// 同じ文字の行は、除外 1 件につき最初の 1 行だけに効く（コピーした行は失敗になる）。当たる行がなくなった除外は失敗にする。
// 対象のファイルが 0 件のときも失敗にする。
// 使い方：node scripts/ci/time-waits.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { checkExclusions, exclusionsPath, readExclusions } from './lib/exclusions.mjs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'
import { createLineLocator } from './lib/line-number.mjs'

/**
 * テストのコードの置き場所。本番のソースを読む検査ではなくテストを読むので、画面の本番のソースの定義（lib/screen-sources.mjs）は使わない。
 * テストを動かす側（E2E の実行の script、Playwright・Vitest・Vite の設定、画面のテストの補助）も、時間で待てば同じ問題になるので読む
 */
const TEST_FILES = [
  /^backend\/ecsite\/src\/(?:test|integrationTest)\/.*\.java$/,
  /^frontend\/(?:admin|store|shared)\/src\/.*\.test\.tsx?$/,
  /^frontend\/(?:admin|store|shared)\/src\/test\/.*\.tsx?$/,
  /^frontend\/(?:admin|store|shared)\/(?:vite|vitest)\.config\.[mc]?[tj]s$/,
  /^e2e\/(?:tests|src|showcase|scripts)\/.*\.ts$/,
  /^e2e\/playwright(?:\.\w+)?\.config\.[mc]?[tj]s$/,
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
 * @returns {{ line: number, call: string, text: string }[]} 見つけた行、呼び出し、前後の空白を除いた行の文字（行の順）
 */
export function findTimeWaits(text) {
  const lineOf = createLineLocator(text)
  const lines = text.split('\n')
  const hits = [...text.matchAll(TIME_WAIT)].map((match) => ({ line: lineOf(match.index), call: match[0] }))
  for (const match of text.matchAll(PROMISE_TIMEOUT)) {
    hits.push({ line: lineOf(match.index + match[0].lastIndexOf(match[2])), call: match[2] })
  }
  return hits.sort((a, b) => a.line - b.line).map((hit) => ({ ...hit, text: lines[hit.line - 1].trim() }))
}

/**
 * 除外のキーを作る。
 *
 * @param {string} file リポジトリのルートからのパス
 * @param {string} text 待ちを書いた行の文字
 * @returns {string} `<ファイル>:<行の文字>`（行の文字の前後の空白は除く）
 */
export function exclusionKey(file, text) {
  return `${file}:${text.trim()}`
}

/**
 * 除外に当たらない待ちを集める。除外は、同じキーの行のうち最初の 1 行だけに効く。
 *
 * @param {Map<string, { line: number, call: string, text: string }[]>} hitsByFile ファイルごとの時間で待つ行
 * @param {Record<string, unknown>} exclusions 除外の一覧
 * @returns {{ found: { file: string, line: number, call: string, text: string }[], used: Set<string> }} 除外に当たらない待ちと、当たった除外のキー
 */
export function applyExclusions(hitsByFile, exclusions) {
  const found = []
  const used = new Set()
  for (const [file, hits] of hitsByFile) {
    for (const hit of hits) {
      const key = exclusionKey(file, hit.text)
      if (key in exclusions && !used.has(key)) {
        used.add(key)
      } else {
        found.push({ file, ...hit })
      }
    }
  }
  return { found, used }
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

/**
 * ファイルごとに、時間で待つ行を集める。
 *
 * @param {string[]} files 調べるファイル
 * @param {(file: string) => string} read ファイルの中身を読む関数
 * @returns {Map<string, { line: number, call: string }[]>} 時間で待つ行があるファイルと、その行（ない場合はファイルを含めない）
 */
export function scanTestFiles(files, read) {
  const hitsByFile = new Map()
  for (const file of files) {
    const hits = findTimeWaits(read(file))
    if (hits.length > 0) hitsByFile.set(file, hits)
  }
  return hitsByFile
}

if (isMain(import.meta.url)) {
  const exclusions = readExclusions(exclusionsPath('time-waits-exclusions.json'))
  const files = listGitFiles().filter((file) => isTestFile(file))
  exitIfNoFiles(files, 'テストのファイル')
  const hitsByFile = scanTestFiles(files, (file) => readFileSync(file, 'utf8'))
  const { found, used } = applyExclusions(hitsByFile, exclusions)
  const problems = checkExclusions('time-waits-exclusions.json', exclusions, (key) => used.has(key))
  for (const problem of problems) console.log(problem)
  for (const hit of found) console.log(`${hit.file}:${hit.line} ${hit.call}`)
  console.log(`時間で待つテスト：${found.length}、除外の誤り：${problems.length}`)
  process.exit(found.length > 0 || problems.length > 0 ? 1 : 0)
}
