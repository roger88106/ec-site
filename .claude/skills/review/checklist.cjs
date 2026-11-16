// 書き方の規則の各文書の「確認方法：」の行から、確認方法に「レビュー」を含むものを抜き出し、レビューのチェックリストにする
// 使い方：node .claude/skills/review/checklist.cjs（リポジトリのルートで。結果を標準出力に Markdown で出す）
// 対象は、docs/05-開発規則/ の文書。確認方法の行は、直前の見出し（## から ######）の節のものとして扱う。
// 形 1：「確認方法：<方法>」の 1 行。形 2：「確認方法：」だけの行の後に「- <規則の短い名前>：<方法>」の箇条を続ける。
// 行の形の誤り（字下げ、半角のコロン、箇条のない形 2 など）は、CI の rule-sections が確認するので、ここでは見ない。
// 見出しとコードブロックは、CI の検査と同じ lib/doc-structure で読む。
const fs = require('fs'), { execSync } = require('child_process')
const { readDocument, readHeadings } = require('../../../scripts/ci/lib/doc-structure.mjs')

const TARGET_DIR = 'docs/05-開発規則/'

/**
 * チェックリストの対象の文書か。
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} docs/05-開発規則/ の文書なら true
 */
function isTargetFile(file) {
  return file.startsWith(TARGET_DIR) && file.endsWith('.md')
}

/**
 * 文書の「確認方法：」の行を読む。形 2 の箇条は、空行の後に続く「- <名前>：<方法>」の行で、次の空行か見出しで終わる。
 * @param {string} text 文書の全文
 * @param {string} file 題名がないときに使う文書のパス
 * @returns {{title: string, entries: {section: string, name: string, check: string}[]} | null}
 *   確認方法の行がなければ null。形 1 の name は空文字
 */
function readCheckMethods(text, file) {
  const headings = readHeadings(text)
  const headingAt = new Map(headings.map((heading) => [heading.line, heading]))
  const entries = []
  let section = ''
  // none：箇条を待たない、head：形 2 の行の後で箇条を待つ、bullets：箇条の中
  let state = 'none'
  readDocument(text).lines.forEach((raw, index) => {
    const heading = headingAt.get(index + 1)
    if (heading) {
      if (heading.level >= 2) section = heading.title
      state = 'none'
      return
    }
    const line = raw.trimEnd()
    const method = /^確認方法：(.*)$/.exec(line)
    if (method) {
      const check = method[1].trim()
      if (check) entries.push({ section, name: '', check })
      state = check ? 'none' : 'head'
      return
    }
    if (state === 'none') return
    if (line.trim() === '') {
      if (state === 'bullets') state = 'none'
      return
    }
    const bullet = /^- (.*?)：(.*)$/.exec(line)
    state = bullet ? 'bullets' : 'none'
    if (bullet) entries.push({ section, name: bullet[1].trim(), check: bullet[2].trim() })
  })
  if (!entries.length) return null
  return { title: headings.find((heading) => heading.level === 1)?.title ?? file, entries }
}

/**
 * 確認方法に「レビュー」を含むものを、チェックリストの項目（Markdown）にする。
 * @param {{section: string, name: string, check: string}[]} entries 確認方法の行
 * @returns {string[]} チェック項目の行
 */
function reviewItems(entries) {
  return entries
    .filter((e) => e.check.includes('レビュー'))
    .map((e) => `- [ ] 「${e.section}」${e.name}：${e.check}`)
}

/** git で管理している docs/05-開発規則/ の文書からチェックリストを作って標準出力に出す。項目がなければ異常終了する。 */
function main() {
  // 日本語を含むパスを、引用符と 8 進数の形にせずに読む
  const files = execSync('git -c core.quotepath=false ls-files "docs/*.md"', { encoding: 'utf8' })
    .split('\n').filter(Boolean).filter(isTargetFile)
  const out = []
  for (const file of files) {
    const doc = readCheckMethods(fs.readFileSync(file, 'utf8'), file)
    const items = doc ? reviewItems(doc.entries) : []
    if (items.length) out.push(`## ${doc.title}（${file}）`, '', ...items, '')
  }
  if (!out.length) {
    console.error('docs/05-開発規則/ に確認方法が「レビュー」の行がない')
    process.exit(1)
  }
  console.log(out.join('\n'))
}

module.exports = { isTargetFile, readCheckMethods, reviewItems }

if (require.main === module) main()
