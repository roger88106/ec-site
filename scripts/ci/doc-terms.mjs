// 文書の用語を確認する。言い換えの一覧は doc-terms.json。
//   ng：使わない語。見つけたら失敗にする
//   caution：文脈で言い換えるかを決める語。--caution を付けたときだけ示し、失敗にしない（文書を書くときに見る）
// 見るのは docs/（アーカイブした spec を除く）、README.md、AGENTS.md、.claude/skills/ の Markdown と、.github/ の yml・yaml と Markdown。
// 使い方：node scripts/ci/doc-terms.mjs [--caution]（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { listGitFiles } from './lib/git-files.mjs'

/** 調べる文書 */
const DOCS = /^((docs\/.*|README|AGENTS|\.claude\/skills\/.*)\.md|\.github\/.*\.(md|ya?ml))$/

/** 見ない文書（アーカイブした spec（当時の記録）と、言い換える前の語を例に挙げる言い回しの規則） */
const EXCLUDED = /^(docs\/specs\/archive\/|\.claude\/skills\/docs\/tech-writing\.md$)/

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
  const files = listGitFiles().filter((file) => isTargetDoc(file))
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
