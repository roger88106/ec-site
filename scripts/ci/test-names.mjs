// バックエンドのすべてのテストに、表示の名前（@DisplayName）があることを確認する（テストの書き方 第 2.2 節）。
// テストのメソッド（@Test・@ParameterizedTest）の注釈のまとまりに @DisplayName がなければ失敗する。構文は解析しない。
// 使い方：node scripts/ci/test-names.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'

/** テストのコードの置き場所 */
const TEST_FILE = /^backend\/ecsite\/src\/(?:test|integrationTest)\/.*\.java$/

/** テストのメソッドの注釈 */
const TEST_ANNOTATION = /^\s*@(?:Test|ParameterizedTest)\b/

/** メソッドの宣言の行（修飾子、戻り値の型、名前、開き括弧） */
const METHOD = /^\s*(?:(?:public|protected|private|static|final|synchronized)\s+)*[\w.<>[\]?, ]+?\s+(\w+)\s*\(/

/**
 * 注釈のまとまりの始まりより前の行か（空行、コメントの終わり、文の終わり、ブロックの始まりと終わり）。
 *
 * @param {string} line 行
 * @returns {boolean} 注釈のまとまりの外なら true
 */
function isBoundary(line) {
  const trimmed = line.trim()
  return trimmed === '' || trimmed === '}' || /(?:\*\/|;|\{)$/.test(trimmed)
}

/**
 * 表示の名前のないテストのメソッドを探す。
 *
 * @param {string} source Java のソース
 * @returns {{ line: number, method: string }[]} @Test・@ParameterizedTest の行と、メソッドの名前
 */
export function findUnnamedTests(source) {
  const lines = source.split('\n')
  const found = []
  lines.forEach((line, index) => {
    if (!TEST_ANNOTATION.test(line)) return
    let start = index
    while (start > 0 && !isBoundary(lines[start - 1])) start -= 1
    let end = index + 1
    while (end < lines.length && (lines[end].trim().startsWith('@') || !METHOD.test(lines[end]))) end += 1
    const block = lines.slice(start, end + 1).join('\n')
    if (!/@DisplayName\(/.test(block)) {
      found.push({ line: index + 1, method: METHOD.exec(lines[end] ?? '')?.[1] ?? '?' })
    }
  })
  return found
}

if (isMain(import.meta.url)) {
  const files = listGitFiles().filter((file) => TEST_FILE.test(file))
  let count = 0
  for (const file of files) {
    for (const hit of findUnnamedTests(readFileSync(file, 'utf8'))) {
      console.log(`${file}:${hit.line} ${hit.method}`)
      count += 1
    }
  }
  console.log(`表示の名前（@DisplayName）のないテスト：${count}`)
  process.exit(count ? 1 : 0)
}
