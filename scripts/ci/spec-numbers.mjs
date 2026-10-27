// 本番のコードと、バックエンドのテストのコメントに、spec・plan・tasks の番号を書いた所がないことを確認する（コメントの書き方 第 1 節）。
// 規則は言葉で書き、spec との対応はテストの名前（@DisplayName）に書く。
// - 本番のコード：行の全体を見る
// - バックエンドのテストとテストの補助（src/test、src/integrationTest、src/testFixtures の .java）：コメントだけを見る（@DisplayName の文字列は見ない）
// 画面のテスト、生成物、適用済みのマイグレーションは見ない。
// 使い方：node scripts/ci/spec-numbers.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'
import { keepJavaComments } from './lib/strip-comments.mjs'

/**
 * 本番のコードの置き場所。バックエンドも見るので、画面の本番のソースの定義（lib/screen-sources.mjs）は使わない。
 * 画面は、定義が除く共有の ui（src/ui/）と shadcn/ui の部品（components/ui/）も、番号を書かないものとして見る
 */
const SOURCE_DIRS = ['backend/ecsite/src/main/', 'frontend/admin/src/', 'frontend/store/src/', 'frontend/shared/src/']

/** 本番のコードでないもの（テスト、テストの補助、生成物、適用済みのマイグレーション） */
const EXCLUDED = [/\.test\.tsx?$/, /\/src\/test\//, /\/generated\//, /\/db\/migration\//]

/** spec の番号（AC-12、1→10 の spec の AC-7-1 など）、タスクの番号（T011-01-01、T7-01-01、古い形式の T6803 など）、spec・plan の番号（plan 001、spec 7 など） */
const SPEC_NUMBER = /\b(?:AC|DC|BR|NFR|US|DS|DN)-?\d+(?:-\d+)?\b|\bT\d+-\d{2}-\d{2}\b|\bT\d{3,4}\b|\b(?:spec|plan) ?\d+\b(?!\.)/g

/**
 * spec・plan・tasks の番号を書いた行を探す。
 *
 * @param {string} text ソース
 * @returns {{ line: number, number: string, text: string }[]} 見つけた行、番号（正規化した後の形）、行の文字（正規化の前）
 */
export function findSpecNumbers(text) {
  const hits = []
  text.split('\n').forEach((line, index) => {
    // 全角の英字・数字・ハイフンで書いた番号も見つけるため、照合の前に NFKC で正規化する。報告する行の文字は正規化の前のものにする
    for (const match of line.normalize('NFKC').matchAll(SPEC_NUMBER)) {
      hits.push({ line: index + 1, number: match[0], text: line.trim() })
    }
  })
  return hits
}

/**
 * 調べるファイルかを返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} 本番のコードなら true
 */
export function isProductionFile(file) {
  return SOURCE_DIRS.some((dir) => file.startsWith(dir)) && !EXCLUDED.some((pattern) => pattern.test(file))
}

/** バックエンドのテストとテストの補助の置き場所 */
const BACKEND_TEST_DIRS = ['backend/ecsite/src/test/', 'backend/ecsite/src/integrationTest/', 'backend/ecsite/src/testFixtures/']

/**
 * コメントだけを調べるファイルかを返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} バックエンドのテスト・テストの補助の .java なら true
 */
export function isBackendTestFile(file) {
  return BACKEND_TEST_DIRS.some((dir) => file.startsWith(dir)) && file.endsWith('.java')
}

if (isMain(import.meta.url)) {
  const files = listGitFiles().filter((file) => isProductionFile(file) || isBackendTestFile(file))
  exitIfNoFiles(files, '本番のコードとテストのファイル')
  let count = 0
  for (const file of files) {
    const text = readFileSync(file, 'utf8')
    for (const hit of findSpecNumbers(isBackendTestFile(file) ? keepJavaComments(text) : text)) {
      console.log(`${file}:${hit.line} ${hit.number}（${hit.text}）`)
      count += 1
    }
  }
  console.log(`spec の番号：${count}`)
  process.exit(count ? 1 : 0)
}
