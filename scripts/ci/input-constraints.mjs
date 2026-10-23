// 画面の入力の制約（長さ・範囲）に、数字を直接書いた所がないことを確認する（フロントの書き方 第 2.3 節）。
// 制約は API の契約から書き出した定数（src/api/generated/constraints.ts）を使う。
// 使い方：node scripts/ci/input-constraints.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { listGitFiles } from './lib/git-files.mjs'
import { isScreenSource } from './lib/screen-sources.mjs'

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

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const found = listGitFiles(['frontend'])
    .filter(isScreenSource)
    .flatMap((file) => findLiterals(readFileSync(file, 'utf8')).map((hit) => `${file}:${hit.line}  ${hit.text}`))
  if (found.length > 0) {
    console.error('入力の制約に数字を直接書いた所がある。src/api/generated/constraints.ts の定数を使う：')
    for (const line of found) console.error(`  ${line}`)
    process.exit(1)
  }
  console.log('入力の制約に、数字を直接書いた所はない')
}
