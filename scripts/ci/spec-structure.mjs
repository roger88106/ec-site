// spec・plan・tasks と、固定の節に書き換えた機能仕様（docs/03-機能仕様/）の構造を確認する。
// 目的：構造の誤り（節の名前と順、ID の定義と参照の食い違い）を、書いた時点で見つける。人がレビューで見つけると、見落としや数え間違いが出るため。
// 判定の区分：誤りの防止（誤って書いたものを失敗にする）。意図して作った回避（検査を通すための書き方）は拒否の対象にしない。
//   許可する形の一覧で判定する。禁止する形の一覧に、回避の形を 1 つずつ加えることはしない。
// 許可する形：
//   - spec：1 行目 `# Spec <番号>：…`、3 行目 `種類：機能 または 整理　規模：L または M　ステータス：草案 または 確定`（区切りは全角の空白、コロンは全角）、
//     種類ごとに決めた `##` の節の名前と順（規模 M は最後に `## tasks`）。`###` の見出しは見ない
//   - plan：1 行目 `# Plan <番号>：…`、3 行目 `ステータス：草案 または 承認済み（tasks 作成済み）　対象：…`、最後の 2 つの節（段階と変えるファイル、利用者に確認すること）。
//     その前の決定の節は、見出しの末尾に担当の `（DS-…）` か `（US-…）` を書く
//   - tasks（段階のファイル）：ファイル名 `NN-<名前>.md`（NN は 2 桁）、1 行目 `# 段階 <N>：…`（N はファイル名の番号と同じ）、前提・完了条件・参照の箇条、`## タスク`
//   - ID：spec の ID は spec の番号の次に連番（0 で埋めない）。タスクの ID は `T<spec の番号>-<段階 2 桁>-<連番 2 桁>`（段階はファイルの段階と同じ）。
//     機能の spec は US・AC・BR・NFR を、整理の spec は DS・DC・DN を定義する。同じ ID を 2 回定義しない
//   - 参照：範囲（`〜`、`～`、`~`。前後の空白を許す）と列挙（`、`）を展開した ID の spec の番号が、既知の spec（進行中とアーカイブのディレクトリ、見本の番号）の番号である。
//     列挙に読むのは、`AC-<番号>-1、2` の形と、同じ spec の番号を付けた `DC-<番号>-4〜6、<番号>-32` の形と、タスクの `T<番号>-01-01、02` の形だけ。
//     範囲の端には、同じ接頭辞と同じ番号の ID（`DS-<番号>-2〜DS-<番号>-8`）も書ける。
//     同じ spec の ID は、spec か tasks に定義がある。範囲は逆順にせず、999 件までにする。
//     定義は次の 3 つだけ。他の太字は参照として読む
//       - spec の箇条の先頭の太字
//       - spec の見出しの先頭の US・DS
//       - tasks のタスクの行の先頭
//   - 機能仕様：`##` の節の名前と順が、雛形と完全に同じである（FUNCTIONAL_SPECS の文書だけ。script に節の名前を持たない）。
//     雛形は docs/03-機能仕様/template.md。見出し「写して使う骨組み」の節にある、ただ 1 つのコードブロックの `##` を読む。
//     雛形の側が読めないときは失敗にする：見出しがない・2 つある、コードブロックが 1 つでない・閉じていない、`##` が 0 件・同じ名前が 2 回、雛形のファイルを読めない
//   - 見出しは、ATX（閉じの `#` は名前に含めない）と Setext（段落の行の直後の `---` は `##`、`===` は `#`）を読む
//   - コードブロック、HTML のコメント、1 行のコードの中は読まない。閉じていないコードブロックは失敗にする（後ろを読めないため）
// 検査の対象：次の 4 つ。アーカイブは見ない（当時の記録で、形が古いため）。
//   - docs/08-spec/ の直下の進行中の spec。直下のディレクトリ（archive と templates を除く）の名前が `<番号>-<名前>` でなければ失敗にする（対象から外れるため）
//   - 雛形の見本（docs/08-spec/templates/examples/ の下）
//   - 雛形（`<…>` を仮の値に置き換えて検査する）
//   - 固定の節に書き換えた機能仕様
// 失敗したとき：示された文書の節の名前と順、ID の定義と参照を、雛形（docs/08-spec/templates/）に合わせて修正する。
//   機能仕様は docs/03-機能仕様/template.md の骨組みに合わせる。
//   本文で ID の書き方を例に挙げるときは、1 行のコード（`…`）の中に書く。理由：コードの中は参照として読まないため。
// 除外：なし
// 使い方：node scripts/ci/spec-structure.mjs（リポジトリのルートで）
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { readCodeBlocks } from './lib/code-block.mjs'
import { checkSections, checkUnclosedCodeBlocks, collectDefinitions, collectReferences, readDocument, readHeadings } from './lib/doc-structure.mjs'
import { isMain } from './lib/is-main.mjs'
import { ARCHIVE, isSpecDirName, listSpecDirs } from './lib/spec-dirs.mjs'

/** 機能の spec の `##` の節 */
export const FEATURE_SECTIONS = ['背景と目的', 'スコープ', 'User stories', '受け入れ条件', '業務ルール', '非機能要件', '依存', 'Non-goals', 'Open questions']

/** 整理の spec の `##` の節 */
export const REFACTOR_SECTIONS = ['背景と目的', 'スコープ', '目的と完了条件', '変える範囲', '非機能要件', '依存', 'Non-goals', 'Open questions']

/** plan の最後の `##` の節 */
const PLAN_LAST_SECTIONS = ['段階と変えるファイル', '利用者に確認すること']

/** 固定の節に書いた機能仕様。モジュールを追加したら、その ③ をこの一覧に加える（加えないと、節の構造の検査と「今有効な AC」の表の生成から外れる） */
export const FUNCTIONAL_SPECS = [
  'docs/03-機能仕様/order.md',
  'docs/03-機能仕様/tax.md',
  'docs/03-機能仕様/catalog.md',
  'docs/03-機能仕様/inventory.md',
  'docs/03-機能仕様/media.md',
  'docs/03-機能仕様/cart.md',
  'docs/03-機能仕様/member.md',
  'docs/03-機能仕様/region.md',
  'docs/03-機能仕様/storefront.md',
  'docs/03-機能仕様/payment.md',
  'docs/03-機能仕様/shipping.md',
  'docs/03-機能仕様/notification.md',
  'docs/03-機能仕様/reporting.md',
]

/** 機能仕様の雛形。`##` の節の名前と順は、この雛形の骨組みから読む */
const FUNCTIONAL_TEMPLATE = 'docs/03-機能仕様/template.md'

/** 機能仕様の雛形で、骨組みのコードブロックを置く節の見出し */
const SKELETON_HEADING = '写して使う骨組み'

/** 進行中の spec の置き場所 */
const SPECS_DIR = 'docs/08-spec'

/** 雛形の置き場所 */
const TEMPLATES_DIR = 'docs/08-spec/templates'

/** 雛形の見本の置き場所 */
const EXAMPLES_DIR = 'docs/08-spec/templates/examples'

/** 見本の spec の番号（実在の spec ではない） */
export const EXAMPLE_NUMBER = '900'

/** 機能の spec が定義しない ID の接頭辞 */
const FEATURE_FORBIDDEN = ['DS', 'DC', 'DN']

/** 整理の spec が定義しない ID の接頭辞 */
const REFACTOR_FORBIDDEN = ['US', 'AC', 'BR', 'NFR']

/**
 * spec の 3 行目（種類・規模・ステータス）を読む。
 *
 * @param {string} text spec.md の内容
 * @returns {{ kind: string, size: string, status: string } | null} 3 行目の形が合えば値。合わなければ null
 */
export function readSpecMeta(text) {
  const match = /^種類：(機能|整理)　規模：(L|M)　ステータス：(草案|確定)$/.exec(text.split(/\r?\n/)[2] ?? '')
  return match ? { kind: match[1], size: match[2], status: match[3] } : null
}

/**
 * spec.md の構造（1 行目、3 行目、`##` の節）を調べる。
 *
 * @param {string} text spec.md の内容
 * @param {string} number spec の番号
 * @returns {string[]} 問題の文
 */
export function checkSpec(text, number) {
  const problems = []
  if (!new RegExp(`^# Spec ${number}：`).test(text.split(/\r?\n/)[0])) problems.push(`1 行目が「# Spec ${number}：…」でない`)
  problems.push(...checkUnclosedCodeBlocks(text))
  const meta = readSpecMeta(text)
  if (!meta) {
    problems.push('3 行目が「種類：機能（整理）　規模：L（M）　ステータス：草案（確定）」の形でない（区切りは全角の空白、コロンは全角）')
    return problems
  }
  const sections = meta.kind === '機能' ? FEATURE_SECTIONS : REFACTOR_SECTIONS
  const required = meta.size === 'M' ? [...sections, 'tasks'] : sections
  return [...problems, ...checkSections(readHeadings(text), 2, required)]
}

/**
 * plan.md の構造（1 行目、3 行目、最後の 2 つの節、決定の節の見出し）を調べる。
 *
 * @param {string} text plan.md の内容
 * @param {string} number spec の番号
 * @returns {string[]} 問題の文
 */
export function checkPlan(text, number) {
  const lines = text.split(/\r?\n/)
  const problems = []
  if (!new RegExp(`^# Plan ${number}：`).test(lines[0])) problems.push(`1 行目が「# Plan ${number}：…」でない`)
  if (!/^ステータス：(?:草案|承認済み（tasks 作成済み）)　対象：\S/.test(lines[2] ?? '')) {
    problems.push('3 行目が「ステータス：草案（承認済み（tasks 作成済み））　対象：…」の形でない（区切りは全角の空白、コロンは全角）')
  }
  problems.push(...checkUnclosedCodeBlocks(text))
  const headings = readHeadings(text)
  const decision = (title) => /（(?:DS|US)-\d+-\d+[^）]*）$/.test(title)
  problems.push(...checkSections(headings, 2, PLAN_LAST_SECTIONS, { extra: decision }))
  const h2 = headings.filter((heading) => heading.level === 2)
  const firstLast = h2.findIndex((heading) => heading.title === PLAN_LAST_SECTIONS[0])
  if (firstLast >= 0 && h2.slice(firstLast).some((heading) => !PLAN_LAST_SECTIONS.includes(heading.title))) {
    problems.push(`決定の節は「${PLAN_LAST_SECTIONS[0]}」の前に置く`)
  }
  return problems
}

/**
 * 段階のファイルの構造（ファイル名、1 行目、冒頭の箇条、`## タスク`、タスクの段階）を調べる。
 *
 * @param {string} text 段階のファイルの内容
 * @param {string} fileName ファイル名（`tasks/NN-<名前>.md` か `NN-<名前>.md`）
 * @param {string} number spec の番号
 * @returns {string[]} 問題の文
 */
export function checkStage(text, fileName, number) {
  const problems = []
  const nameMatch = /^(\d{2})-[^/]+\.md$/.exec(fileName.split('/').pop())
  if (!nameMatch) problems.push('ファイル名が「NN-<名前>.md」（NN は 2 桁）の形でない')
  const first = /^# 段階 ([1-9]\d*)：/.exec(text.split(/\r?\n/)[0])
  if (!first) problems.push('1 行目が「# 段階 <N>：…」でない')
  else if (nameMatch && Number(first[1]) !== Number(nameMatch[1])) problems.push(`1 行目の段階（${first[1]}）がファイル名の番号（${nameMatch[1]}）と違う`)
  problems.push(...checkUnclosedCodeBlocks(text))
  const { lines } = readDocument(text)
  const taskHeading = lines.findIndex((line) => /^## タスク\s*$/.test(line))
  const header = taskHeading < 0 ? lines : lines.slice(0, taskHeading)
  for (const item of ['前提', '完了条件', '参照']) {
    if (!header.some((line) => line.startsWith(`- ${item}：`))) problems.push(`箇条「${item}：」がない`)
  }
  if (taskHeading < 0) problems.push('節「タスク」がない')
  if (nameMatch) {
    for (const definition of collectDefinitions(text).filter((item) => item.kind === 'task')) {
      if (Number(definition.stage) !== Number(nameMatch[1])) {
        problems.push(`${definition.line} 行：${definition.id} の段階（${Number(definition.stage)}）がファイルの段階（${Number(nameMatch[1])}）と違う`)
      }
    }
  }
  return problems
}

/**
 * 機能仕様の雛形から、骨組みの `##` の節の名前と順を読む。
 * 見出し「写して使う骨組み」の節（次の `##` まで）にある、ただ 1 つのコードブロックの中の `##` を読む。
 *
 * @param {string} text 機能仕様の雛形の内容
 * @returns {{ sections: string[], problems: string[] }} sections：節の名前（出る順）。problems：読めない理由（あれば sections は空）
 */
export function readSkeleton(text) {
  const fail = (problem) => ({ sections: [], problems: [problem] })
  const unclosed = checkUnclosedCodeBlocks(text)
  if (unclosed.length > 0) return { sections: [], problems: unclosed }
  const h2 = readHeadings(text).filter((heading) => heading.level === 2)
  const found = h2.filter((heading) => heading.title === SKELETON_HEADING)
  if (found.length === 0) return fail(`「${SKELETON_HEADING}」の見出しがない`)
  if (found.length > 1) return fail(`「${SKELETON_HEADING}」の見出しが ${found.length} つある（${found.map((heading) => `${heading.line} 行`).join('、')}）`)
  const next = h2.find((heading) => heading.line > found[0].line)
  const blocks = readCodeBlocks(text).filter((block) => block.line > found[0].line && (next === undefined || block.line < next.line))
  if (blocks.length !== 1) return fail(`「${SKELETON_HEADING}」の節のコードブロックが ${blocks.length} 個ある（ただ 1 つにする）`)
  const sections = readHeadings(blocks[0].body.join('\n')).filter((heading) => heading.level === 2).map((heading) => heading.title)
  if (sections.length === 0) return fail(`「${SKELETON_HEADING}」のコードブロックに ## の節が 0 件`)
  const duplicated = sections.filter((title, index) => sections.indexOf(title) !== index)
  if (duplicated.length > 0) return fail(`「${SKELETON_HEADING}」のコードブロックで、「${duplicated[0]}」が 2 回ある`)
  return { sections, problems: [] }
}

/**
 * 固定の節に書き換えた機能仕様の全てを、雛形の骨組みと照合する。
 *
 * @param {{ templateFile: string, specs: string[] }} options templateFile：機能仕様の雛形のパス。specs：検査する機能仕様のパス
 * @returns {string[]} 問題の文（ファイルのパスを前に付ける）
 */
export function checkFunctionalDocs({ templateFile, specs }) {
  let template
  try {
    template = readFileSync(templateFile, 'utf8')
  } catch {
    return [`${templateFile.replaceAll('\\', '/')}：雛形を読めない`]
  }
  const { sections, problems } = readSkeleton(template)
  if (problems.length > 0) return problems.map((problem) => `${templateFile.replaceAll('\\', '/')}：${problem}`)
  return specs.flatMap((file) => {
    let text
    try {
      text = readFileSync(file, 'utf8')
    } catch {
      return [`${file.replaceAll('\\', '/')}：機能仕様を読めない`]
    }
    return checkFunctionalSpec(text, sections).map((problem) => `${file.replaceAll('\\', '/')}：${problem}`)
  })
}

/**
 * 固定の節に書き換えた機能仕様の `##` の節を調べる。
 *
 * @param {string} text 機能仕様の内容
 * @param {string[]} sections 雛形の骨組みから読んだ `##` の節の名前と順
 * @returns {string[]} 問題の文
 */
export function checkFunctionalSpec(text, sections) {
  return [...checkUnclosedCodeBlocks(text), ...checkSections(readHeadings(text), 2, sections)]
}

/**
 * ID の定義と参照を調べる。
 *
 * @param {Record<string, string>} files 文書の組（`spec.md`、`plan.md`、`tasks/NN-<名前>.md`）
 * @param {string} number spec の番号
 * @param {Set<string>} known 既知の spec の番号
 * @param {{ resolve: boolean, meta: { kind: string } | null }} options resolve：同じ spec の ID の定義を求めるか。meta：spec の 3 行目の値
 * @returns {string[]} 問題の文（ファイルを前に付ける）
 */
function checkIds(files, number, known, { resolve, meta }) {
  const problems = []
  const defined = new Set()
  for (const [file, text] of Object.entries(files)) {
    const isSpec = file === 'spec.md'
    const isStage = file.startsWith('tasks/')
    if (!isSpec && !isStage) continue
    for (const definition of collectDefinitions(text)) {
      // 段階のファイルで定義とみなすのは、タスクの行だけ
      if (isStage && definition.kind !== 'task') continue
      const where = `${file}：${definition.line} 行：`
      if (definition.number !== number) problems.push(`${where}${definition.id} の番号が spec の番号（${number}）と違う`)
      if (isSpec && meta) {
        const forbidden = meta.kind === '機能' ? FEATURE_FORBIDDEN : REFACTOR_FORBIDDEN
        if (forbidden.includes(definition.prefix)) problems.push(`${where}${meta.kind}の spec は ${definition.id} を定義しない`)
      }
      if (defined.has(definition.id)) problems.push(`${where}${definition.id} を 2 回定義している`)
      defined.add(definition.id)
    }
  }
  const reported = new Set()
  for (const [file, text] of Object.entries(files)) {
    const { references, problems: referenceProblems } = collectReferences(text)
    problems.push(...referenceProblems.map((problem) => `${file}：${problem}`))
    for (const reference of references) {
      let problem = null
      if (!known.has(reference.number) && reference.number !== number) problem = `${reference.text} の spec（番号 ${reference.number}）がない（まだ作業ブランチにない spec は「spec <番号>」と書く）`
      else if (resolve && reference.number === number && !defined.has(reference.id)) problem = `${reference.id} が定義されていない`
      const message = problem && `${file}：${reference.line} 行：${problem}`
      if (message && !reported.has(message)) {
        reported.add(message)
        problems.push(message)
      }
    }
  }
  return problems
}

/**
 * 1 つの spec の文書の組（spec.md、plan.md、tasks/）を調べる。
 *
 * @param {Record<string, string>} files 文書の組。キーは `spec.md`、`plan.md`、`tasks/NN-<名前>.md`
 * @param {string} number spec の番号
 * @param {Set<string>} known 既知の spec の番号
 * @param {{ resolve?: boolean }} [options] resolve：同じ spec の ID の定義を求めるか（既定は true。雛形は定義を持たないので false）
 * @returns {string[]} 問題の文（ファイルを前に付ける）
 */
export function checkSpecFiles(files, number, known, { resolve = true } = {}) {
  if (files['spec.md'] === undefined) return ['spec.md：spec.md がない']
  const problems = []
  const add = (file, list) => problems.push(...list.map((problem) => `${file}：${problem}`))
  add('spec.md', checkSpec(files['spec.md'], number))
  if (files['plan.md'] !== undefined) add('plan.md', checkPlan(files['plan.md'], number))
  for (const file of Object.keys(files).filter((name) => name.startsWith('tasks/')).sort()) add(file, checkStage(files[file], file, number))
  problems.push(...checkIds(files, number, known, { resolve, meta: readSpecMeta(files['spec.md']) }))
  return problems
}

/**
 * spec のディレクトリの文書を読んで調べる。
 *
 * @param {string} dir spec のディレクトリ
 * @param {string} number spec の番号
 * @param {Set<string>} known 既知の spec の番号
 * @returns {string[]} 問題の文（ディレクトリのパスを前に付ける）
 */
export function checkSpecDir(dir, number, known) {
  const files = {}
  for (const name of ['spec.md', 'plan.md']) {
    if (existsSync(path.join(dir, name))) files[name] = readFileSync(path.join(dir, name), 'utf8')
  }
  const tasksDir = path.join(dir, 'tasks')
  if (existsSync(tasksDir)) {
    for (const name of readdirSync(tasksDir).filter((item) => item.endsWith('.md')).sort()) {
      files[`tasks/${name}`] = readFileSync(path.join(tasksDir, name), 'utf8')
    }
  }
  const prefix = dir.replaceAll('\\', '/')
  return checkSpecFiles(files, number, known).map((problem) => `${prefix}/${problem}`)
}

/**
 * HTML のタグの形（`<br/>`、`</details>`、`<a href="…">`）。名前は英字で始まり、属性の名前は英字で始まる。
 * 属性の形まで見る理由：`<plan へのリンク>` のような、英字で始まる雛形の `<…>` をタグと読まないため
 */
const HTML_TAG = /^\/?[A-Za-z][A-Za-z0-9-]*(?:\s+[A-Za-z_:][\w:.-]*(?:\s*=\s*(?:"[^"]*"|'[^']*'|[^\s"'=<>`]+))?)*\s*\/?$/

/**
 * 雛形の `<…>` の中身を、仮の値にする。
 *
 * @param {string} content `<` と `>` の間の文字
 * @returns {string | null} 仮の値。HTML のタグなら null（置き換えない）
 */
function placeholderValue(content) {
  const options = content.split(/\s*または\s*/)
  const first = options[0].replace(/など$/, '')
  // 「A または B」と「A など」は、最初の A にする（雛形の選択肢の最初は、検査を通る値にしてある）
  if (options.length > 1 || content.endsWith('など')) return first
  if (content === 'N' || content === 'n') return '1'
  if (content === 'm') return '2'
  // ID の形（接頭辞、番号、連番）は、中身を残す
  if (/^[A-Z]{1,3}-\d/.test(content)) return content
  // 上の雛形の形（`<n>` など）は、タグの形にも合うので、先に決める
  if (HTML_TAG.test(content)) return null
  return '仮'
}

/**
 * 雛形の `<…>` を、仮の値に置き換える。番号は見本の番号、段階と連番は 2 桁にする。HTML の注釈（`<!-- … -->`）とタグ（`<br/>`、`<details>`）は置き換えない。
 *
 * @param {string} text 雛形の内容
 * @returns {string} 置き換えた内容
 */
export function fillTemplate(text) {
  let filled = text.replaceAll('<番号>', EXAMPLE_NUMBER).replaceAll('<段階 2 桁>', '01')
  // タスクの連番の `<n>`、`<n+1>` は、2 桁にする（先のタスクの番号の 01〜03 と重ならない 04 から）
  filled = filled.replace(new RegExp(`(T${EXAMPLE_NUMBER}-01-)<n(?:\\+(\\d+))?>`, 'g'), (_, head, plus) => `${head}${String(4 + Number(plus ?? 0)).padStart(2, '0')}`)
  // 入れ子は、内側から順に置き換える
  for (;;) {
    const next = filled.replace(/<(?!!--)([^<>]*)>/g, (tag, content) => placeholderValue(content) ?? tag)
    if (next === filled) return filled
    filled = next
  }
}

/** 雛形のファイルと、検査の名前（spec.md、plan.md、tasks/NN-<名前>.md）の対応 */
const TEMPLATE_FILES = { 'spec-feature.md': 'spec.md', 'plan.md': 'plan.md', 'tasks-stage.md': 'tasks/01-template.md' }

/**
 * 雛形を、`<…>` を仮の値に置き換えて検査する。機能の spec の雛形、plan、段階のファイルを 1 組、整理の spec の雛形を別に検査する。
 *
 * @param {string} dir 雛形のディレクトリ
 * @param {{ transform?: (name: string, text: string) => string }} [options] transform：`<…>` を置き換える前の雛形を書き換える関数（テストで雛形に誤りを入れるため）
 * @returns {string[]} 問題の文（雛形のファイル名を前に付ける）
 */
export function checkTemplates(dir, { transform = (_name, text) => text } = {}) {
  if (!existsSync(dir)) return [`${dir.replaceAll('\\', '/')}：雛形のディレクトリがない`]
  const read = (name) => fillTemplate(transform(name, readFileSync(path.join(dir, name), 'utf8')))
  const known = new Set([EXAMPLE_NUMBER])
  const feature = Object.fromEntries(Object.entries(TEMPLATE_FILES).map(([name, key]) => [key, read(name)]))
  const relabel = (problems, labels) => problems.map((problem) => {
    const key = Object.keys(labels).find((item) => problem.startsWith(`${item}：`))
    return key ? `${labels[key]}${problem.slice(key.length)}` : problem
  })
  const labels = Object.fromEntries(Object.entries(TEMPLATE_FILES).map(([name, key]) => [key, name]))
  return [
    ...relabel(checkSpecFiles(feature, EXAMPLE_NUMBER, known, { resolve: false }), labels),
    ...relabel(checkSpecFiles({ 'spec.md': read('spec-refactor.md') }, EXAMPLE_NUMBER, known, { resolve: false }), { 'spec.md': 'spec-refactor.md' }),
  ]
}

/**
 * 検査する進行中の spec と、既知の spec の番号を探す。
 *
 * @param {string} specsDir docs/08-spec のパス
 * @returns {{ targets: { dir: string, number: string }[], known: Set<string> }}
 *   targets：進行中の spec（アーカイブは入れない）。known：進行中とアーカイブの spec の番号と、見本の番号
 */
export function findTargets(specsDir) {
  const all = listSpecDirs(specsDir)
  const numberOf = (spec) => spec.name.split('-')[0]
  return {
    targets: all.filter((spec) => !spec.rel.startsWith('archive/')).map((spec) => ({ dir: spec.dir, number: numberOf(spec) })),
    known: new Set([...all.map(numberOf), EXAMPLE_NUMBER]),
  }
}

/**
 * docs/08-spec/ の直下のディレクトリの名前を調べる。名前を誤った spec は、検査の対象から外れて失敗にならないため。
 *
 * @param {string} specsDir docs/08-spec のパス
 * @returns {string[]} 問題の文（ディレクトリの名前を前に付ける）。アーカイブと雛形のディレクトリは見ない
 */
export function checkSpecDirNames(specsDir) {
  const templates = path.basename(TEMPLATES_DIR)
  return readdirSync(specsDir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && entry.name !== ARCHIVE && entry.name !== templates && !isSpecDirName(entry.name))
    .map((entry) => `${entry.name}：spec のディレクトリの名前が <番号>-<名前> でない`)
    .sort()
}

function main() {
  const problems = []
  problems.push(...checkSpecDirNames(SPECS_DIR).map((problem) => `${SPECS_DIR}/${problem}`))
  const { targets, known } = findTargets(SPECS_DIR)
  for (const target of targets) problems.push(...checkSpecDir(target.dir, target.number, known))
  const examples = existsSync(EXAMPLES_DIR) ? readdirSync(EXAMPLES_DIR, { withFileTypes: true }).filter((entry) => entry.isDirectory()) : []
  for (const entry of examples) problems.push(...checkSpecDir(path.join(EXAMPLES_DIR, entry.name), EXAMPLE_NUMBER, known))
  problems.push(...checkTemplates(TEMPLATES_DIR))
  problems.push(...checkFunctionalDocs({ templateFile: FUNCTIONAL_TEMPLATE, specs: FUNCTIONAL_SPECS }))
  for (const problem of problems) console.log(problem)
  console.log(`構造の問題：${problems.length}（spec ${targets.length}、見本 ${examples.length}、雛形 ${Object.keys(TEMPLATE_FILES).length + 1}、機能仕様 ${FUNCTIONAL_SPECS.length}）`)
  process.exit(problems.length > 0 ? 1 : 0)
}

if (isMain(import.meta.url)) main()
