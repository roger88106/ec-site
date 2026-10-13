// 画面の入力の制約（長さ・範囲）に、数字を直接書いた所がないことを確かめる（フロントの書き方 第 2.3 節）。
// 制約は API の契約から書き出した定数（src/api/generated/constraints.ts）を使う。
// 使い方：node scripts/ci/input-constraints.mjs（リポジトリのルートで）
import { readdirSync, readFileSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

/** 調べる画面のソースの置き場所 */
const SOURCE_DIRS = ['frontend/admin/src', 'frontend/store/src', 'frontend/shared/src']

/** 調べない置き場所（生成物と、shadcn/ui の部品） */
const EXCLUDED = [/[\\/]generated[\\/]/, /[\\/]components[\\/]ui[\\/]/, /[\\/]src[\\/]ui[\\/]/]

/** 制約の属性に、数字を直接書いた所（例：maxLength={50}、min="0"） */
const LITERAL = /\b(maxLength|minLength|max|min)=(\{\s*-?\d|"-?\d)/g

/**
 * ファイルの中の、数字を直接書いた制約の属性を探す。
 *
 * @param {string} text ファイルの中身
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findLiterals(text) {
  return text.split('\n').flatMap((line, index) => (line.match(LITERAL) ? [{ line: index + 1, text: line.trim() }] : []))
}

/** 調べる .tsx のファイル（テストを除く） */
function sourceFiles(dir) {
  return readdirSync(dir, { recursive: true, withFileTypes: true })
    .filter((entry) => entry.isFile() && entry.name.endsWith('.tsx') && !entry.name.includes('.test.'))
    .map((entry) => join(entry.parentPath, entry.name))
    .filter((file) => !EXCLUDED.some((pattern) => pattern.test(file)))
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const found = SOURCE_DIRS.flatMap((dir) =>
    sourceFiles(dir).flatMap((file) =>
      findLiterals(readFileSync(file, 'utf8')).map((hit) => `${relative('.', file)}:${hit.line}  ${hit.text}`),
    ),
  )
  if (found.length > 0) {
    console.error('入力の制約に数字を直接書いた所がある。src/api/generated/constraints.ts の定数を使う：')
    for (const line of found) console.error(`  ${line}`)
    process.exit(1)
  }
  console.log('入力の制約に、数字を直接書いた所はない')
}
