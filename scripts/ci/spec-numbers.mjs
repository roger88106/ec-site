// 本番のコードに、spec・plan・tasks の番号を書いた所がないことを確認する（コメントの書き方 第 1 節）。
// 規則は言葉で書き、spec との対応はテストの名前に書く。テスト、生成物、適用済みのマイグレーションは見ない。
// 使い方：node scripts/ci/spec-numbers.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { listGitFiles } from './lib/git-files.mjs'

/**
 * 本番のコードの置き場所。バックエンドも見るので、画面の本番のソースの定義（lib/screen-sources.mjs）は使わない。
 * 画面は、定義が除く共有の ui（src/ui/）と shadcn/ui の部品（components/ui/）も、番号を書かないものとして見る
 */
const SOURCE_DIRS = ['backend/ecsite/src/main/', 'frontend/admin/src/', 'frontend/store/src/', 'frontend/shared/src/']

/** 本番のコードでないもの（テスト、テストの補助、生成物、適用済みのマイグレーション） */
const EXCLUDED = [/\.test\.tsx?$/, /\/src\/test\//, /\/generated\//, /\/db\/migration\//]

/** spec の番号（AC-12、1→10 の spec の AC-7-1 など）、タスクの番号（T011-01-01、T7-01-01、古い形式の T6803 など）、spec・plan の番号（plan 001、spec 7 など） */
const SPEC_NUMBER = /\b(?:AC|DC|BR|NFR|US|DS|DN)-\d+(?:-\d+)?\b|\bT\d+-\d{2}-\d{2}\b|\bT\d{3,4}\b|\b(?:spec|plan) ?\d+\b(?!\.)/g

/**
 * spec・plan・tasks の番号を書いた行を探す。
 *
 * @param {string} text ソース
 * @returns {{ line: number, number: string }[]} 見つけた行と番号
 */
export function findSpecNumbers(text) {
  const hits = []
  text.split('\n').forEach((line, index) => {
    for (const match of line.matchAll(SPEC_NUMBER)) hits.push({ line: index + 1, number: match[0] })
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

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const files = listGitFiles().filter((file) => isProductionFile(file))
  let count = 0
  for (const file of files) {
    for (const hit of findSpecNumbers(readFileSync(file, 'utf8'))) {
      console.log(`${file}:${hit.line} ${hit.number}`)
      count += 1
    }
  }
  console.log(`spec の番号：${count}`)
  process.exit(count ? 1 : 0)
}
