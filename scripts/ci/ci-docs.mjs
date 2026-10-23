// CI の検査の文書（docs/guides/ci.md）の第 3 節の表と、scripts/ci/ の直下の検査が 1 対 1 に対応することを確認する。
// - 直下の検査：*.test.mjs 以外の *.mjs（check-all.sh が引数なしで実行するもの）
// - 表：第 3 節の最初の表（第 3.1 節より前）の 1 列目の名前（`ac-check` など）
// 表にない検査と、検査のない表の行の両方を失敗にする。
// 使い方：node scripts/ci/ci-docs.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { listGitFiles } from './lib/git-files.mjs'

/** CI の検査の文書 */
const DOC = 'docs/guides/ci.md'

/** 検査を置くディレクトリ */
const CHECK_DIR = 'scripts/ci/'

/**
 * 文書の第 3 節の表（第 3.1 節より前）の 1 列目の名前を読む。
 *
 * @param {string} markdown 文書の中身
 * @returns {string[]} 表の順の名前
 */
export function readTableChecks(markdown) {
  const names = []
  let inSection = false
  for (const line of markdown.split('\n')) {
    if (/^#{2,3} /.test(line)) inSection = /^## 3\. /.test(line)
    if (!inSection) continue
    const match = line.match(/^\| `([^`]+)` \|/)
    if (match) names.push(match[1])
  }
  return names
}

/**
 * ファイルの一覧から、scripts/ci/ の直下の検査の名前を選ぶ。
 *
 * @param {string[]} files リポジトリのルートからのパス
 * @returns {string[]} 拡張子を除いた名前。名前の順
 */
export function listChecks(files) {
  return files
    .filter((file) => file.startsWith(CHECK_DIR) && /^[^/]+\.mjs$/.test(file.slice(CHECK_DIR.length)) && !file.endsWith('.test.mjs'))
    .map((file) => file.slice(CHECK_DIR.length, -'.mjs'.length))
    .sort()
}

/**
 * 表の名前と検査の名前を比べる。
 *
 * @param {string[]} tableNames 表の名前
 * @param {string[]} checkNames 検査の名前
 * @returns {string[]} 食い違いの説明
 */
export function compareChecks(tableNames, checkNames) {
  const problems = []
  const table = new Set(tableNames)
  const checks = new Set(checkNames)
  for (const name of checkNames) {
    if (!table.has(name)) problems.push(`表にない検査：${name}（${DOC} 第 3 節の表に行を加える）`)
  }
  for (const name of table) {
    if (!checks.has(name)) problems.push(`検査のない表の行：${name}（${CHECK_DIR} の直下に ${name}.mjs がない）`)
  }
  if (table.size !== tableNames.length) problems.push('表に同じ名前の行が 2 つ以上ある')
  return problems
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const problems = compareChecks(readTableChecks(readFileSync(DOC, 'utf8')), listChecks(listGitFiles([CHECK_DIR])))
  for (const problem of problems) console.log(problem)
  console.log(`食い違い：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
