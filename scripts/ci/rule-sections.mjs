// 書き方の規則の文書の各節に、`確認方法：` と `理由：` の行があることを確認する（書き方の規則とレビューの「規則の書き方」）。
// 対象の文書は、書き方の規則とレビュー（docs/05-開発規則/review.md）の題の次にある最初の空行でない行（冒頭の文）の、Markdown のリンクから読む。
//   リンクが 1 つもないとき、リンク先を読めないとき、同じフォルダーのファイル名でないリンク（パス・URL）があるときは失敗にする。
//   リンクにない同じフォルダーの文書が、行頭の `確認方法：` の行を持つときも失敗にする（リンクの書き漏らしと、折り返しで読まれないリンクを見つける）。
// 節の単位は、`##` から `######` の見出し（Setext を含む）のうち、次の見出しまでに本文（空行以外の行。コードブロックを含む）があるもの。
//   本文のない親の見出しと、最初の `##` より前（文書の冒頭）は節にしない。
// 確認すること：
//   - 節に、字下げのない行頭から始まる `確認方法：` の行が 1 つある（内容のない行の後は、最初の空行でない行が `- ` の箇条）
//   - 節に、字下げのない行頭から始まり、内容のある `理由：` の行が 1 つ以上ある
//   - `確認方法：` が、行の途中・箇条・表のセル・字下げ・見出し・太字・半角のコロン・コロンの前の空白の形で書かれていない（節の外も同じ）
//   - コードブロック、インラインコード、HTML のコメントの中は見ない
// 判定の区分は誤りの防止。誤って書いたものを拒否し、意図して作った回避（見出しの偽装など）は対象にしない。許可する形の一覧で判定する。
// 失敗したとき：行を加えるか、形を直す。形から外れる節は、除外の一覧に理由を書く。
//   対象の文書を読めないとき・リンクにないときは、書き方の規則とレビューの冒頭の文のリンクを文書の置き場所に合わせる（対象は同じフォルダーだけ）。
//   除外した節の見出しかファイル名を変えたときは、除外のキーを新しい名前に直す（一覧から消さない）。
//   閉じていないコードブロックかコメントは閉じる。
// 除外：rule-sections-exclusions.json に {キー: 理由} を書く。キーは `<ファイル>「<番号を除いた見出し>」`（ルートからのパス）。
//   除外するのは、行の有無だけ。形の誤りは除外しない。理由のない除外と、今の文書にない節の除外は失敗にする。
// 使い方：node scripts/ci/rule-sections.mjs（リポジトリのルートで）
import { readdirSync, readFileSync } from 'node:fs'
import { readBlocks } from './lib/code-block.mjs'
import { readDocument, readHeadings, SETEXT_UNDERLINE } from './lib/doc-structure.mjs'
import { checkExclusions, exclusionsPath, readExclusions } from './lib/exclusions.mjs'
import { isMain } from './lib/is-main.mjs'

/** 書き方の規則とレビューの文書 */
const REVIEW_DOC = 'docs/05-開発規則/review.md'

/** 対象の文書を置くフォルダー（書き方の規則とレビューと同じ） */
const RULE_DIR = 'docs/05-開発規則'

/** 除外の一覧の名前 */
const EXCLUSIONS_NAME = 'rule-sections-exclusions.json'

/** 確認方法の行の候補（コロンの前の空白と * を許して拾い、許す形かは別に判定する） */
const CONFIRM_CANDIDATE = /確認方法[\s*]*[：:]/g

/** 許す確認方法の行の始まり */
const CONFIRM_HEAD = '確認方法：'

/** 理由の行の始まり */
const REASON_HEAD = '理由：'

/** 見出しの番号 */
const HEADING_NUMBER = /^\d+(?:\.\d+)*\.?\s+/

/**
 * 見出しから番号を除く。
 *
 * @param {string} title 見出しの文字
 * @returns {string} 番号を除いた見出し
 */
function stripNumber(title) {
  return title.replace(HEADING_NUMBER, '')
}

/**
 * 文書を、見出しと節に分けて読む。
 *
 * @param {string} text 文書の内容
 * @returns {{ raw: string[], doc: ReturnType<typeof readDocument>, sections: object[] }}
 *   raw は元の行、sections は本文のある深さ 2 以上の見出し
 */
function analyze(text) {
  const raw = text.split(/\r?\n/)
  const doc = readDocument(text)
  const headings = readHeadings(text)
  const bodyStarts = headings.map((heading) => {
    if (/^ {0,3}#{1,6}[ \t]/.test(doc.lines[heading.line - 1])) return heading.line + 1
    let underline = heading.line
    while (underline < doc.lines.length && !SETEXT_UNDERLINE.test(doc.lines[underline])) underline++
    return underline + 2
  })
  const sections = []
  headings.forEach((heading, index) => {
    if (heading.level < 2) return
    const start = bodyStarts[index]
    const end = index + 1 < headings.length ? headings[index + 1].line - 1 : raw.length
    let hasBody = false
    for (let line = start; line <= end; line++) {
      if (raw[line - 1].trim() !== '') hasBody = true
    }
    if (hasBody) sections.push({ title: stripNumber(heading.title), headingLine: heading.line, start, end, confirmRows: [] })
  })
  return { raw, doc, sections }
}

/**
 * 節（本文のある見出し）の、番号を除いた見出しを返す。
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
 * @param {(title: string) => boolean} [isExcluded] 番号を除いた見出しを受け取り、除外の節なら true（行の有無だけを見ない）
 * @returns {{ line: number, message: string }[]} 問題（行の順）
 */
export function findSectionProblems(text, isExcluded = () => false) {
  const { raw, doc, sections } = analyze(text)
  const problems = [
    ...doc.unclosed.map((line) => ({ line, message: '閉じていないコードブロックがある（後ろの節を読めない）' })),
    ...readBlocks(text).unclosedComments.map((line) => ({ line, message: '閉じていない HTML のコメントがある（後ろの行を読めない）' })),
  ]
  const ownerOf = new Map()
  sections.forEach((section) => {
    for (let line = section.start; line <= section.end; line++) ownerOf.set(line, section)
  })

  for (let line = 1; line <= raw.length; line++) {
    const matches = [...doc.plain[line - 1].matchAll(CONFIRM_CANDIDATE)]
    if (matches.length === 0) continue
    const owner = ownerOf.get(line)
    const [first, ...others] = matches
    const firstIsRow = owner !== undefined && first.index === 0 && first[0] === CONFIRM_HEAD
    if (firstIsRow) owner.confirmRows.push(line)
    if (!firstIsRow || others.length > 0) {
      problems.push({ line, message: '確認方法：は、字下げのない行頭に全角のコロンで書く（行の途中・箇条・表・字下げ・見出し・太字・半角のコロンは不可）' })
    }
  }

  for (const section of sections) {
    for (const line of section.confirmRows) {
      if (doc.lines[line - 1].slice(CONFIRM_HEAD.length).trim() !== '') continue
      const next = raw.slice(line).find((item) => item.trim() !== '')
      if (next === undefined || !next.startsWith('- ')) {
        problems.push({ line, message: '内容のない確認方法：の後の最初の空行でない行が、- の箇条でない' })
      }
    }
    if (section.confirmRows.length >= 2) {
      problems.push({ line: section.headingLine, message: `「${section.title}」に確認方法：の行が 2 つ以上ある` })
    }
    if (isExcluded(section.title)) continue
    if (section.confirmRows.length === 0) {
      problems.push({ line: section.headingLine, message: `「${section.title}」に確認方法：の行がない` })
    }
    let reasons = 0
    for (let line = section.start; line <= section.end; line++) {
      if (doc.plain[line - 1].startsWith(REASON_HEAD) && doc.lines[line - 1].slice(REASON_HEAD.length).trim() !== '') reasons++
    }
    if (reasons === 0) {
      problems.push({ line: section.headingLine, message: `「${section.title}」に理由：の行（行頭から始まり、内容のある行）がない` })
    }
  }
  return problems.sort((a, b) => a.line - b.line)
}

/**
 * 複数の文書を調べ、除外の誤りも見つける。
 *
 * @param {{ file: string, text: string }[]} docs 文書
 * @param {Record<string, unknown>} exclusions 除外の一覧（キーは `<ファイル>「<番号を除いた見出し>」`）
 * @returns {{ problems: { file: string, line: number, message: string }[], exclusionProblems: string[] }} 問題と、除外の誤りの文
 */
export function checkDocuments(docs, exclusions) {
  const existing = new Set()
  const problems = []
  for (const { file, text } of docs) {
    for (const title of listSections(text)) existing.add(keyOf(file, title))
    const isExcluded = (title) => Object.hasOwn(exclusions, keyOf(file, title))
    for (const problem of findSectionProblems(text, isExcluded)) problems.push({ file, ...problem })
  }
  const exclusionProblems = checkExclusions(EXCLUSIONS_NAME, exclusions, (key) => existing.has(key))
  return { problems, exclusionProblems }
}

/**
 * 除外のキーを作る。
 *
 * @param {string} file 文書のパス
 * @param {string} title 番号を除いた見出し
 * @returns {string} `<ファイル>「<見出し>」`
 */
function keyOf(file, title) {
  return `${file}「${title}」`
}

/**
 * 書き方の規則とレビューの本文から、対象の文書のファイル名を読む。
 *
 * @param {string} reviewText 書き方の規則とレビューの本文
 * @returns {string[]} 題の次にある最初の空行でない行（冒頭の文）の Markdown のリンク先（`#` より後を除く。出る順）。リンクがなければ空
 */
export function readTargetDocs(reviewText) {
  const lines = reviewText.split(/\r?\n/)
  const title = lines.findIndex((line) => /^#\s/.test(line))
  const sentence = lines.slice(title + 1).find((line) => line.trim() !== '')
  if (sentence === undefined) return []
  return [...sentence.matchAll(/\[[^\]]*\]\(([^)\s]+)\)/g)].map((match) => match[1].split('#')[0])
}

/**
 * 対象の文書を読む。
 *
 * @param {string} reviewText 書き方の規則とレビューの本文
 * @param {(file: string) => string} read ファイル名から内容を返す関数。ないときは例外を投げる
 * @returns {{ docs: { file: string, text: string }[], problems: string[] }} 読めた文書と、読めなかったことの文
 */
export function loadDocuments(reviewText, read) {
  const names = readTargetDocs(reviewText)
  const docs = []
  const problems = []
  if (names.length === 0) problems.push(`${REVIEW_DOC}：冒頭の文に対象の文書のリンクがない（対象を読めない）`)
  for (const file of names) {
    if (file === '' || /[/:]/.test(file)) {
      problems.push(`${REVIEW_DOC}：リンク先「${file}」は対象にできない（同じフォルダーの文書だけを対象にする。ファイル名だけで書く）`)
      continue
    }
    try {
      docs.push({ file, text: read(file) })
    } catch (error) {
      problems.push(`${file}：対象の文書を読めない：${error.message}`)
    }
  }
  return { docs, problems }
}

/**
 * 冒頭の文のリンクにない文書で、行頭の確認方法の行を持つものを探す（リンクの書き漏らしや、折り返しで読まれないリンクを見つける）。
 *
 * @param {{ file: string, text: string }[]} allDocs 同じフォルダーの全ての文書
 * @param {string[]} names リンクから読んだ文書のファイル名
 * @returns {{ file: string, line: number, message: string }[]} リンクにない文書の、最初の行頭の確認方法の行
 */
export function findUnlistedDocs(allDocs, names) {
  const problems = []
  for (const { file, text } of allDocs) {
    if (names.includes(file)) continue
    const index = readDocument(text).plain.findIndex((line) => line.startsWith(CONFIRM_HEAD))
    if (index >= 0) {
      problems.push({ file, line: index + 1, message: '行頭の確認方法：の行があるが、書き方の規則とレビューの冒頭の文のリンクにない（リンクに加える）' })
    }
  }
  return problems
}

if (isMain(import.meta.url)) {
  const exclusions = readExclusions(exclusionsPath(EXCLUSIONS_NAME))
  const reviewText = readFileSync(REVIEW_DOC, 'utf8')
  const { docs, problems: loadProblems } = loadDocuments(reviewText, (file) => readFileSync(`${RULE_DIR}/${file}`, 'utf8'))
  if (loadProblems.length > 0) {
    for (const message of loadProblems) console.log(message)
    console.log(`規則の文書の節の問題：${loadProblems.length}、除外の誤り：0（対象の文書を読めないので、節は調べていない）`)
    process.exit(1)
  }
  const allDocs = readdirSync(RULE_DIR)
    .filter((file) => file.endsWith('.md'))
    .map((file) => ({ file, text: readFileSync(`${RULE_DIR}/${file}`, 'utf8') }))
  const unlisted = findUnlistedDocs(allDocs, readTargetDocs(reviewText))
  const { problems, exclusionProblems } = checkDocuments(
    docs.map(({ file, text }) => ({ file: `${RULE_DIR}/${file}`, text })),
    exclusions,
  )
  for (const message of exclusionProblems) console.log(message)
  for (const problem of unlisted) console.log(`${RULE_DIR}/${problem.file}:${problem.line} ${problem.message}`)
  for (const problem of problems) console.log(`${problem.file}:${problem.line} ${problem.message}`)
  const count = problems.length + unlisted.length
  console.log(`規則の文書の節の問題：${count}、除外の誤り：${exclusionProblems.length}`)
  process.exit(count > 0 || exclusionProblems.length > 0 ? 1 : 0)
}
