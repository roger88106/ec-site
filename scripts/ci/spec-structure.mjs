// spec・plan・tasks と、固定の節に書き換えた機能仕様（docs/03-機能仕様/）の構造を確認する。
// 目的：構造の誤り（節の名前と順、ID の定義と参照の食い違い）を、書いた時点で見つける。人がレビューで見つけると、見落としや数え間違いが出るため。
// 判定の区分：誤りの防止（誤って書いたものを失敗にする）。意図して作った回避（検査を通すための書き方）は対象にしない。
//   許可する形の一覧で判定する。禁止する形の一覧に、回避の形を 1 つずつ加えることはしない。
// 許可する形：
//   - spec：1 行目 `# Spec <番号>：…`、3 行目 `種類：機能（整理）　規模：L（M）　ステータス：草案（確定）`（区切りは全角の空白、コロンは全角）、
//     種類ごとに決めた `##` の節の名前と順（規模 M は最後に `## tasks`）。`###` の見出しは見ない
//   - plan：1 行目 `# Plan <番号>：…`、3 行目 `ステータス：草案（承認済み（tasks 作成済み））　対象：…`、最後の 2 つの節（段階と変えるファイル、利用者に確認すること）。
//     その前の決定の節は、見出しの末尾に担当の `（DS-…）` か `（US-…）` を書く
//   - tasks（段階のファイル）：ファイル名 `NN-<名前>.md`（NN は 2 桁）、1 行目 `# 段階 <N>：…`（N はファイル名の番号と同じ）、前提・完了条件・参照の箇条、`## タスク`
//   - ID：spec の ID は spec の番号の次に連番（0 で埋めない）。タスクの ID は `T<spec の番号>-<段階 2 桁>-<連番 2 桁>`（段階はファイルの段階と同じ）。
//     機能の spec は US・AC・BR・NFR を、整理の spec は DS・DC・DN を定義する。同じ ID を 2 回定義しない
//   - 参照：範囲（`〜`）と列挙（`、`）を開いた ID の番号が、既知の spec（進行中とアーカイブのディレクトリ、見本の番号）である。
//     同じ spec の ID は、spec か tasks に定義がある。範囲は逆順にせず、999 件までにする。
//     定義は次の 3 つだけ。他の太字は参照として読む
//       - spec の箇条の先頭の太字
//       - spec の見出しの先頭の US・DS
//       - tasks のタスクの行の先頭
//   - 機能仕様：`##` の節が、業務規則、状態の遷移、データモデル、今有効な AC の順である（FUNCTIONAL_SPECS の文書だけ）
//   - コードブロックと 1 行のコードの中は読まない。閉じていないコードブロックは失敗にする（後ろを読めないため）
// 見る所：次の 4 つ。アーカイブは見ない（当時の記録で、形が古いため）。
//   - docs/08-spec/ の直下の進行中の spec
//   - 雛形の見本（docs/08-spec/templates/examples/ の下）
//   - 雛形（`<…>` を仮の値に置き換えて検査する）
//   - 固定の節に書き換えた機能仕様
// 失敗したとき：示された文書の節の名前と順、ID の定義と参照を、雛形（docs/08-spec/templates/）に合わせて修正する。
//   本文で ID の書き方を例に挙げるときは、1 行のコード（`…`）の中に書く。
// 除外：なし
// 使い方：node scripts/ci/spec-structure.mjs（リポジトリのルートで）
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { checkSections, checkUnclosedCodeBlocks, collectDefinitions, collectReferences, readDocument, readHeadings } from './lib/doc-structure.mjs'
import { isMain } from './lib/is-main.mjs'
import { listSpecDirs } from './lib/spec-dirs.mjs'

/** 機能の spec の `##` の節 */
export const FEATURE_SECTIONS = ['背景と目的', 'スコープ', 'User stories', '受け入れ条件', '業務ルール', '非機能要件', '依存', 'Non-goals', 'Open questions']

/** 整理の spec の `##` の節 */
export const REFACTOR_SECTIONS = ['背景と目的', 'スコープ', '目的と完了条件', '変える範囲', '非機能要件', '依存', 'Non-goals', 'Open questions']

/** plan の最後の `##` の節 */
const PLAN_LAST_SECTIONS = ['段階と変えるファイル', '利用者に確認すること']

/** 機能仕様の `##` の節 */
export const FUNCTIONAL_SECTIONS = ['業務規則', '状態の遷移', 'データモデル', '今有効な AC']

/** 固定の節に書き換えた機能仕様（他のモジュールは、書き換えたときに加える） */
export const FUNCTIONAL_SPECS = ['docs/03-機能仕様/order.md']

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
 * 固定の節に書き換えた機能仕様の `##` の節を調べる。
 *
 * @param {string} text 機能仕様の内容
 * @returns {string[]} 問題の文
 */
export function checkFunctionalSpec(text) {
  return [...checkUnclosedCodeBlocks(text), ...checkSections(readHeadings(text), 2, FUNCTIONAL_SECTIONS)]
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
      if (!known.has(reference.number) && reference.number !== number) problem = `${reference.text} の spec（番号 ${reference.number}）がない`
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
 * 雛形の `<…>` の中身を、仮の値にする。
 *
 * @param {string} content `<` と `>` の間の文字
 * @returns {string} 仮の値
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
  return '仮'
}

/**
 * 雛形の `<…>` を、仮の値に置き換える。番号は見本の番号、段階と連番は 2 桁にする。HTML の注釈（`<!-- … -->`）は置き換えない。
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
    const next = filled.replace(/<(?!!--)([^<>]*)>/g, (_, content) => placeholderValue(content))
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
 * @param {{ transform?: (name: string, text: string) => string }} [options] transform：読んだ雛形を置き換える前に変える関数（テストで雛形の誤りを作る）
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

function main() {
  const problems = []
  const { targets, known } = findTargets(SPECS_DIR)
  for (const target of targets) problems.push(...checkSpecDir(target.dir, target.number, known))
  const examples = existsSync(EXAMPLES_DIR) ? readdirSync(EXAMPLES_DIR, { withFileTypes: true }).filter((entry) => entry.isDirectory()) : []
  for (const entry of examples) problems.push(...checkSpecDir(path.join(EXAMPLES_DIR, entry.name), EXAMPLE_NUMBER, known))
  problems.push(...checkTemplates(TEMPLATES_DIR))
  for (const file of FUNCTIONAL_SPECS) {
    problems.push(...checkFunctionalSpec(readFileSync(file, 'utf8')).map((problem) => `${file}：${problem}`))
  }
  for (const problem of problems) console.log(problem)
  console.log(`構造の問題：${problems.length}（spec ${targets.length}、見本 ${examples.length}、雛形 ${Object.keys(TEMPLATE_FILES).length + 1}、機能仕様 ${FUNCTIONAL_SPECS.length}）`)
  process.exit(problems.length > 0 ? 1 : 0)
}

if (isMain(import.meta.url)) main()
