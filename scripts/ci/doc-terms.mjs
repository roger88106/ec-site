// 文書の用語を確かめる。言い換えの一覧は doc-terms.json。
//   ng：使わない語。見つけたら失敗にする
//   caution：文脈で言い換えるかを決める語。--caution を付けたときだけ示し、失敗にしない（文書を書くときに見る）
// 見るのは docs/（spec・plan・tasks を除く）、README.md、AGENTS.md、.claude/skills/ の Markdown。
// 使い方：node scripts/ci/doc-terms.mjs [--caution]（リポジトリのルートで）
import { execSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/** 調べる文書 */
const DOCS = /^(docs\/.*|README|AGENTS|\.claude\/skills\/.*)\.md$/

/** 見ない文書（当時の記録） */
const EXCLUDED = /^docs\/specs\//

/**
 * 一覧の語を書いた行を探す。
 *
 * @param {string} text 文書
 * @param {{ word: string, use: string }[]} terms 語と言い換え
 * @returns {{ line: number, word: string, use: string }[]} 見つけた行と語
 */
export function findTerms(text, terms) {
  const hits = []
  text.split('\n').forEach((line, index) => {
    for (const term of terms) {
      if (line.includes(term.word)) hits.push({ line: index + 1, ...term })
    }
  })
  return hits
}

/**
 * 調べる文書かを返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} 調べるなら true
 */
export function isTargetDoc(file) {
  return DOCS.test(file) && !EXCLUDED.test(file)
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const caution = process.argv.includes('--caution')
  const list = JSON.parse(readFileSync(new URL('./doc-terms.json', import.meta.url), 'utf8'))
  const terms = caution ? list.caution : list.ng
  const files = execSync('git ls-files -co --exclude-standard', { encoding: 'utf8' })
    .split('\n')
    .filter((file) => file && isTargetDoc(file) && existsSync(file))
  let count = 0
  for (const file of files) {
    for (const hit of findTerms(readFileSync(file, 'utf8'), terms)) {
      console.log(`${file}:${hit.line} ${hit.word} → ${hit.use}`)
      count += 1
    }
  }
  console.log(`${caution ? '注意する語' : '使わない語'}：${count}`)
  process.exit(!caution && count ? 1 : 0)
}
