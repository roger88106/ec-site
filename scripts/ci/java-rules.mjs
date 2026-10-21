// バックエンドの書き方の規則のうち、ソースの文字で確認できるものを確認する（バックエンドの書き方 第 1.3 節、第 5.1 節）。
// - テストのソース（test、integrationTest、testFixtures）で Lombok を使わない。Lombok の注釈はクラスファイルに残らず、ArchUnit で見えないため
// - 本番のソースで、列挙型の変換に name() を渡さない（valueOf(x.name())、fromValue(x.name())）。値を追加したときの写し忘れをコンパイルで見つけられないため
// 使い方：node scripts/ci/java-rules.mjs（リポジトリのルートで）
import { execSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/** テストのソース */
const TEST_SOURCE = /^backend\/ecsite\/src\/(?:test|integrationTest|testFixtures)\/.*\.java$/

/** 本番のソース */
const MAIN_SOURCE = /^backend\/ecsite\/src\/main\/.*\.java$/

/** Lombok の import と、完全修飾名で書いた注釈 */
const LOMBOK = /^\s*import\s+(?:static\s+)?lombok\.|@lombok\./

/** 列挙型の変換に name() を渡す呼び出し */
const ENUM_NAME = /\b(?:valueOf|fromValue)\(\s*[\w.()]*\.name\(\)\s*\)/

/**
 * 正規表現に合う行を探す。
 *
 * @param {string} text ソース
 * @param {RegExp} pattern 行に当てる正規表現
 * @returns {{ line: number, text: string }[]} 見つけた行（行の順）
 */
function findLines(text, pattern) {
  return text
    .split('\n')
    .map((line, index) => ({ line: index + 1, text: line.trim() }))
    .filter((hit) => pattern.test(hit.text))
}

/**
 * Lombok を使う行を探す。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findLombok(text) {
  return findLines(text, LOMBOK)
}

/**
 * 列挙型の変換に name() を渡す行を探す。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findEnumNameConversions(text) {
  return findLines(text, ENUM_NAME)
}

/**
 * ファイルに当てる規則を返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {'lombok' | 'enum-name' | null} テストのソースなら Lombok、本番のソースなら name() の変換、ほかは null
 */
export function ruleFor(file) {
  if (TEST_SOURCE.test(file)) return 'lombok'
  if (MAIN_SOURCE.test(file)) return 'enum-name'
  return null
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const files = execSync('git ls-files -co --exclude-standard', { encoding: 'utf8' })
    .split('\n')
    .filter((file) => file && ruleFor(file) && existsSync(file))
  const messages = { lombok: 'テストで Lombok を使う', 'enum-name': '列挙型の変換に name() を渡す' }
  let count = 0
  for (const file of files) {
    const rule = ruleFor(file)
    const text = readFileSync(file, 'utf8')
    const hits = rule === 'lombok' ? findLombok(text) : findEnumNameConversions(text)
    for (const hit of hits) {
      console.log(`${file}:${hit.line} ${messages[rule]}：${hit.text}`)
      count += 1
    }
  }
  console.log(`バックエンドの書き方の違反：${count}`)
  process.exit(count ? 1 : 0)
}
