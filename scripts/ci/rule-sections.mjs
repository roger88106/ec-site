// 書き方の規則の文書の各節に、`確認方法：` と `理由：` の行があることを確認する（書き方の規則とレビューの「規則の書き方」）。
// 対象は RULE_DOCS の文書（書き方の規則とレビュー（docs/05-開発規則/review.md）の冒頭の文が挙げる文書）。
// 節の単位は、`##` から `######` の見出し（Setext を含む）のうち、次の見出しまでに本文（空行以外の行）があるもの。最初の `##` より前は節にしない。
// 確認すること：
//   - 節に、行頭から始まる `確認方法：` の行がちょうど 1 つある（内容のない行の後は、最初の空行でない行が `- ` の箇条）
//   - 節に、行頭から始まり、内容のある `理由：` の行が 1 つ以上ある
//   - コードブロックが閉じている（閉じていないと、後ろの節を読めない）
//   コードブロック、インラインコード、HTML のコメントの中は見ない。
//   形の崩れた行（字下げ、半角のコロン、表のセルなど）は、行頭の `確認方法：` として数えないので、その節は「行がない」で失敗になる。
// 失敗したとき：行を加えるか、形を直す。形から外れる節は、除外の一覧に理由を書く。
//   規則の文書を加えた・名前を変えたときは、RULE_DOCS と、書き方の規則とレビューの冒頭の文を合わせる。
//   除外した節の見出しかファイル名を変えたときは、除外のキーを新しい名前に直す（一覧から消さない）。
// 除外：rule-sections-exclusions.json に {キー: 理由} を書く。キーは `<ファイル>「<見出し>」`（ルートからのパス）。
//   除外するのは、行の有無だけ。理由のない除外と、今の文書にない節の除外は失敗にする。
// 使い方：node scripts/ci/rule-sections.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { readDocument, readHeadings, SETEXT_UNDERLINE } from './lib/doc-structure.mjs'
import { checkExclusions, exclusionsPath, readExclusions } from './lib/exclusions.mjs'
import { isMain } from './lib/is-main.mjs'

/** 書き方の規則の文書（ルートからのパス） */
export const RULE_DOCS = ['backend-coding.md', 'frontend-coding.md', 'test-writing.md', 'test-writing-backend.md', 'test-writing-frontend.md', 'comments.md'].map(
  (file) => `docs/05-開発規則/${file}`,
)

/** 除外の一覧の名前 */
const EXCLUSIONS_NAME = 'rule-sections-exclusions.json'

/** 確認方法の行の始まり */
const CONFIRM_HEAD = '確認方法：'

/** 理由の行の始まり */
const REASON_HEAD = '理由：'

/**
 * 文書を節（本文のある深さ 2 以上の見出しの範囲）に分ける。
 *
 * @param {string} text 文書の内容
 * @returns {{ doc: ReturnType<typeof readDocument>, raw: string[], sections: { title: string, headingLine: number, start: number, end: number }[] }}
 *   doc は読んだ文書、raw は元の行、sections は節（start と end は本文の最初と最後の行。1 から）
 */
function analyze(text) {
  const raw = text.split(/\r?\n/)
  const doc = readDocument(text)
  const headings = readHeadings(text)
  const sections = []
  headings.forEach((heading, index) => {
    if (heading.level < 2) return
    // ATX の見出しは次の行から、Setext の見出しは下線の次の行から本文にする
    let start = heading.line + 1
    if (!/^ {0,3}#{1,6}[ \t]/.test(doc.lines[heading.line - 1])) {
      let underline = heading.line
      while (underline < doc.lines.length && !SETEXT_UNDERLINE.test(doc.lines[underline])) underline++
      start = underline + 2
    }
    const end = index + 1 < headings.length ? headings[index + 1].line - 1 : raw.length
    if (raw.slice(start - 1, end).some((line) => line.trim() !== '')) sections.push({ title: heading.title, headingLine: heading.line, start, end })
  })
  return { doc, raw, sections }
}

/**
 * 節（本文のある見出し）の、見出しを返す。
 *
 * @param {string} text 文書の内容
 * @returns {string[]} 見出しの文字（出る順）
 */
export function listSections(text) {
  return analyze(text).sections.map((section) => section.title)
}

/**
 * 1 つの文書の問題を探す。
 *
 * @param {string} text 文書の内容
 * @param {(title: string) => boolean} [isExcluded] 見出しを受け取り、除外の節なら true（行の有無だけを見ない）
 * @returns {{ line: number, message: string }[]} 問題（行の順）
 */
export function findSectionProblems(text, isExcluded = () => false) {
  const { doc, raw, sections } = analyze(text)
  const problems = doc.unclosed.map((line) => ({ line, message: '閉じていないコードブロックがある（後ろの節を読めない）' }))
  for (const section of sections) {
    const rows = []
    let hasReason = false
    for (let line = section.start; line <= section.end; line++) {
      const plain = doc.plain[line - 1]
      if (plain.startsWith(CONFIRM_HEAD)) rows.push(line)
      if (plain.startsWith(REASON_HEAD) && doc.lines[line - 1].slice(REASON_HEAD.length).trim() !== '') hasReason = true
    }
    for (const line of rows) {
      if (doc.lines[line - 1].slice(CONFIRM_HEAD.length).trim() !== '') continue
      const next = raw.slice(line).find((item) => item.trim() !== '')
      if (next === undefined || !next.startsWith('- ')) problems.push({ line, message: '内容のない確認方法：の後の最初の空行でない行が、- の箇条でない' })
    }
    if (rows.length >= 2) problems.push({ line: section.headingLine, message: `「${section.title}」に確認方法：の行が 2 つ以上ある` })
    if (isExcluded(section.title)) continue
    if (rows.length === 0) problems.push({ line: section.headingLine, message: `「${section.title}」に確認方法：の行（字下げのない行頭に、全角のコロンで書く）がない` })
    if (!hasReason) problems.push({ line: section.headingLine, message: `「${section.title}」に理由：の行（行頭から始まり、内容のある行）がない` })
  }
  return problems.sort((a, b) => a.line - b.line)
}

/**
 * 複数の文書を調べ、除外の誤りも見つける。
 *
 * @param {{ file: string, text: string }[]} docs 文書
 * @param {Record<string, unknown>} exclusions 除外の一覧（キーは `<ファイル>「<見出し>」`）
 * @returns {{ problems: { file: string, line: number, message: string }[], exclusionProblems: string[] }} 問題と、除外の誤りの文
 */
export function checkDocuments(docs, exclusions) {
  const existing = new Set()
  const problems = []
  for (const { file, text } of docs) {
    const keyOf = (title) => `${file}「${title}」`
    const titles = listSections(text)
    for (const title of titles) existing.add(keyOf(title))
    if (titles.length === 0) problems.push({ file, line: 1, message: '節が 1 つもない（本文のある ## から ###### の見出しがない）' })
    for (const problem of findSectionProblems(text, (title) => Object.hasOwn(exclusions, keyOf(title)))) problems.push({ file, ...problem })
  }
  return { problems, exclusionProblems: checkExclusions(EXCLUSIONS_NAME, exclusions, (key) => existing.has(key)) }
}

if (isMain(import.meta.url)) {
  const exclusions = readExclusions(exclusionsPath(EXCLUSIONS_NAME))
  const { problems, exclusionProblems } = checkDocuments(
    RULE_DOCS.map((file) => ({ file, text: readFileSync(file, 'utf8') })),
    exclusions,
  )
  for (const message of exclusionProblems) console.log(message)
  for (const problem of problems) console.log(`${problem.file}:${problem.line} ${problem.message}`)
  console.log(`規則の文書の節の問題：${problems.length}、除外の誤り：${exclusionProblems.length}`)
  process.exit(problems.length > 0 || exclusionProblems.length > 0 ? 1 : 0)
}
