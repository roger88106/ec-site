// 文書の用語を確認する。言い換えの一覧は doc-terms.json。
//   ng：使わない語。見つけたら失敗にする。使わない語を一部に含む正しい語は、項目の allow に書く（その中の一致は数えない）
//   caution：文脈で言い換えるかを決める語。--caution を付けたときだけ示し、失敗にしない（文書を書くときに見る）
// 見るのは docs/（アーカイブした spec を除く）、README.md、AGENTS.md、.claude/skills/ の Markdown と、.github/ の yml・yaml と Markdown。
// 使い方：node scripts/ci/doc-terms.mjs [--caution]（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'

/** 調べる文書 */
const DOCS = /^((docs\/.*|README|AGENTS|\.claude\/skills\/.*)\.md|\.github\/.*\.(md|ya?ml))$/

/** 見ない文書（アーカイブした spec（当時の記録）と、言い換える前の語を例に挙げる言い回しの規則） */
const EXCLUDED = /^(docs\/specs\/archive\/|\.claude\/skills\/docs\/tech-writing\.md$)/

/**
 * 行の中に、使わない語を、許す語の外で書いているかを返す。
 * 日本語に語の境界はないので、使わない語を一部に含む正しい語は、語ごとの許す語（allow）で救う。
 *
 * @param {string} line 行
 * @param {{ word: string, allow?: string[] }} term 使わない語と許す語
 * @returns {boolean} 許す語の外に一致があれば true
 */
function hasDisallowedMatch(line, term) {
  const allowed = []
  for (const allow of term.allow ?? []) {
    for (let at = line.indexOf(allow); at !== -1; at = line.indexOf(allow, at + 1)) {
      allowed.push([at, at + allow.length])
    }
  }
  for (let at = line.indexOf(term.word); at !== -1; at = line.indexOf(term.word, at + 1)) {
    const end = at + term.word.length
    if (!allowed.some(([from, to]) => from <= at && end <= to)) return true
  }
  return false
}

/**
 * 一覧の語を書いた行を探す。許す語が使わない語を含まないときは、設定の誤りとして例外にする。
 *
 * @param {string} text 文書
 * @param {{ word: string, use: string, allow?: string[] }[]} terms 語と言い換えと許す語
 * @returns {{ line: number, word: string, use: string }[]} 見つけた行と語
 */
export function findTerms(text, terms) {
  for (const term of terms) {
    for (const allow of term.allow ?? []) {
      if (!allow.includes(term.word)) {
        throw new Error(`許す語「${allow}」が使わない語「${term.word}」を含まない（doc-terms.json）`)
      }
    }
  }
  const hits = []
  text.split('\n').forEach((line, index) => {
    for (const term of terms) {
      if (hasDisallowedMatch(line, term)) hits.push({ line: index + 1, ...term })
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

if (isMain(import.meta.url)) {
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
