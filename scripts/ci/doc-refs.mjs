// 文書への参照が切れていないことを確認する。
//   - Markdown のリンク：先のファイルと #見出し があるか（見出しの slug は GitHub と同じ作り方）
//   - 節の参照（`店舗画面の構成 第 4.5 節` など）：文書の名前・リンク・パスが指す文書に、その番号の見出しがあるか
// 文書の名前は、各文書の 1 行目の見出しから括弧（`（frontend/store）` など）を除いたもの。名前のない「第 N 節」は、Markdown ではその文書自身を指す。
// spec・plan の節（当時の記録で、番号が変わらない）と、適用済みのマイグレーション（直せない）は見ない。
// 使い方：node scripts/ci/doc-refs.mjs（リポジトリのルートで）
import { execSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { basename, dirname, join, normalize } from 'node:path/posix'
import { fileURLToPath } from 'node:url'

/** 見ないファイル（当時の記録、直せないもの、この検査のテストの例） */
const EXCLUDED = [/^docs\/specs\//, /\/db\/migration\//, /^\.local\//, /^scripts\/ci\/doc-refs\.test\.mjs$/]

/** 節の参照を探すファイル */
const TEXT_FILE = /\.(md|java|ts|tsx|mjs|cjs|js|yml|yaml|sh|toml|properties)$/

/**
 * 見出しを GitHub と同じ形の slug にする。
 *
 * @param {string} heading 見出しの文字
 * @returns {string} slug
 */
export function slug(heading) {
  return heading
    .replace(/`/g, '')
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s_-]/gu, '')
    .replace(/ /g, '-')
}

/**
 * Markdown の見出しを読み、リンクの先にできる slug と、節の番号を集める。
 *
 * @param {string} text Markdown
 * @returns {{ title: string | null, anchors: Set<string>, sections: Set<string> }} 1 行目の見出し、slug、節の番号
 */
export function readHeadings(text) {
  const anchors = new Set()
  const sections = new Set()
  const seen = {}
  let title = null
  let inCode = false
  for (const line of text.replace(/^﻿/, '').split('\n')) {
    if (line.startsWith('```')) inCode = !inCode
    const match = !inCode && line.match(/^(#{1,6})\s+(.*)$/)
    if (!match) continue
    if (match[1] === '#' && title === null) title = match[2].trim()
    let key = slug(match[2])
    if (seen[key] !== undefined) {
      seen[key] += 1
      key = `${key}-${seen[key]}`
    } else seen[key] = 0
    anchors.add(key)
    const number = match[2].match(/^(\d+(?:\.\d+)*)\.?\s/)
    if (number) sections.add(number[1])
  }
  return { title, anchors, sections }
}

/**
 * 文書の名前を、参照で使う形にする（括弧の中と、コードの印を除く）。
 *
 * @param {string} title 1 行目の見出し
 * @returns {string} 参照で使う名前
 */
export function referenceName(title) {
  return title.replace(/（[^）]*）/g, '').replace(/`/g, '').trim()
}

/**
 * 「第 4〜6・8 節」の番号の部分を、確認する番号に分ける（範囲は両端だけ）。
 *
 * @param {string} numbers 番号の部分（`4〜6・8` など）
 * @returns {string[]} 節の番号
 */
export function splitSectionNumbers(numbers) {
  return numbers
    .split(/[・、〜~]/)
    .map((number) => number.trim())
    .filter(Boolean)
}

/**
 * 1 つのファイルの中の参照を集める。
 *
 * @param {string} file ファイルのパス（リポジトリのルートから）
 * @param {string} text ファイルの中身
 * @param {Map<string, string>} names 文書の名前からファイルのパスへの対応
 * @param {Map<string, string>} basenames ファイル名（`admin.md` など）からファイルのパスへの対応（同じ名前が 2 つ以上あるものは除く）
 * @returns {{ line: number, target: string, anchor?: string, section?: string, text: string }[]} 参照（anchor はリンクの #、section は節の番号）
 */
export function findReferences(file, text, names, basenames) {
  const refs = []
  const isMarkdown = file.endsWith('.md')
  const lines = text.split('\n')
  let inCode = false
  lines.forEach((line, index) => {
    if (isMarkdown && line.startsWith('```')) inCode = !inCode
    const lineNumber = index + 1
    if (isMarkdown && !inCode) {
      for (const [, link] of line.matchAll(/\]\(([^)\s]+)\)/g)) {
        if (/^(https?:|mailto:)/.test(link)) continue
        const [path, anchor] = link.split('#')
        const target = path ? normalize(join(dirname(file), decodeURIComponent(path))) : file
        refs.push({ line: lineNumber, target, anchor: anchor ? decodeURIComponent(anchor) : undefined, text: link })
      }
    }
    for (const match of line.matchAll(/第 ([0-9][0-9.・、〜~ ]*?) ?節/g)) {
      const before = line.slice(0, match.index).trimEnd()
      const target = resolveTarget(file, before, names, basenames, isMarkdown)
      if (!target) continue
      for (const section of splitSectionNumbers(match[1])) {
        refs.push({ line: lineNumber, target, section, text: match[0] })
      }
    }
  })
  return refs
}

/**
 * 「第 N 節」の前の文字から、どの文書を指すかを決める。
 *
 * @param {string} file 参照のあるファイル
 * @param {string} rawBefore 「第」の前の文字（末尾の空白を除く）
 * @param {Map<string, string>} names 文書の名前からファイルのパスへの対応
 * @param {Map<string, string>} basenames ファイル名からファイルのパスへの対応
 * @param {boolean} isMarkdown 参照のあるファイルが Markdown か
 * @returns {string | null} 文書のパス。見ない参照（spec・plan の節、決められないもの）なら null
 */
function resolveTarget(file, rawBefore, names, basenames, isMarkdown) {
  // `docs/rules/frontend-coding.md` 第 9.1 節、「店舗画面の構成」第 4 節 のように、名前を印で囲んだもの
  const before = rawBefore.replace(/[`」]+$/, '').trimEnd()
  // spec・plan の節は、当時の記録で番号が変わらないので見ない
  if (/(spec|plan|\d{3})(\s*の)?$/.test(before)) return null
  const link = before.match(/\]\(([^)\s#]+)(#[^)]*)?\)$/)
  if (link) return /^https?:/.test(link[1]) ? null : normalize(join(dirname(file), link[1]))
  const path = before.match(/([\w./-]+\.md)$/)
  if (path) return existsSync(path[1]) ? path[1] : (basenames.get(basename(path[1])) ?? path[1])
  if (/constitution$/.test(before)) return 'docs/constitution.md'
  for (const [name, target] of names) {
    if (before.endsWith(name)) return target
  }
  // 名前のない参照は、Markdown ではその文書自身を指す。コードでは指す先が決められないので見ない
  return isMarkdown ? file : null
}

/**
 * 参照の切れたものを探す。
 *
 * @param {string[]} files 調べるファイル
 * @param {(file: string) => string} read ファイルを読む関数
 * @param {(file: string) => boolean} exists ファイルがあるかを返す関数
 * @returns {string[]} 切れた参照の説明
 */
export function checkReferences(files, read, exists) {
  const headings = new Map()
  const headingsOf = (file) => {
    if (!headings.has(file)) headings.set(file, readHeadings(read(file)))
    return headings.get(file)
  }
  const docs = files.filter((file) => file.startsWith('docs/') && file.endsWith('.md'))
  const names = new Map()
  for (const doc of docs) {
    const { title } = headingsOf(doc)
    if (title) names.set(referenceName(title), doc)
  }
  // 長い名前から当てる（「フロントの書き方」より先に「テストの書き方」が誤って当たらないよう、名前の長さの順）
  const sortedNames = new Map([...names].sort((a, b) => b[0].length - a[0].length))
  const counts = {}
  for (const doc of docs) counts[basename(doc)] = (counts[basename(doc)] ?? 0) + 1
  const basenames = new Map(docs.filter((doc) => counts[basename(doc)] === 1).map((doc) => [basename(doc), doc]))

  const problems = []
  for (const file of files) {
    for (const ref of findReferences(file, read(file), sortedNames, basenames)) {
      const where = `${file}:${ref.line} ${ref.text}`
      if (!exists(ref.target)) {
        problems.push(`ファイルがない：${where}`)
      } else if (ref.anchor && ref.target.endsWith('.md') && !headingsOf(ref.target).anchors.has(ref.anchor)) {
        problems.push(`見出しがない：${where}`)
      } else if (ref.section && ref.target.endsWith('.md') && !headingsOf(ref.target).sections.has(ref.section)) {
        problems.push(`節がない（${ref.target} 第 ${ref.section} 節）：${where}`)
      }
    }
  }
  return problems
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const files = execSync('git ls-files -co --exclude-standard', { encoding: 'utf8' })
    .split('\n')
    .filter((file) => file && TEXT_FILE.test(file) && !EXCLUDED.some((pattern) => pattern.test(file)) && existsSync(file))
  const problems = checkReferences(files, (file) => readFileSync(file, 'utf8'), existsSync)
  for (const problem of problems) console.log(problem)
  console.log(`切れた参照：${problems.length}`)
  process.exit(problems.length ? 1 : 0)
}
