// ============================================================================
// 機能の spec の AC と、テスト名の AC 番号を照合する（テストの書き方 第 2.1 節）
//
//   使い方：node scripts/ci/ac-check.mjs
//
// - AC：spec.md（docs/specs/ の直下とアーカイブの下の spec）の定義の行（- **AC-n**）
// - テスト名：Java のメソッドの @DisplayName（src/test、src/integrationTest）と、
//   Vitest・Playwright の test・it の第 1 引数（*.test.ts(x)、*.spec.ts）。まとまりの名前（describe、クラス）は読まない
// - 次のときに失敗する：テスト名に出ない AC がある、テスト名の AC 番号が範囲（AC-8〜10）で書かれている、
//   spec にない AC 番号がテスト名にある、除外の一覧（ac-exclusions.json）が正しくない
// - テスト名に出ない AC は、CI（環境変数 CI=true）でだけ失敗にし、手元では警告にする。
//   手元で CI と同じに確認するとき：CI=true node scripts/ci/ac-check.mjs
// - AC 一覧（docs/design/acceptance-criteria.md）を読む。廃止の AC はテストが必要なく、テストが参照すると失敗する。
//   spec と AC 一覧の AC が食い違うとき、状態が有効・変更・廃止でないとき、表が古いときに失敗する。
//   表の要約・元の spec・テストの列は再作成する列で、--write で書き直す（状態・変えた spec・今の内容は残す）
// ============================================================================
import { existsSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { listSpecDirs } from './lib/spec-dirs.mjs'

/** 範囲を表す記号（全角の波ダッシュ、全角のチルダ、半角のチルダ） */
const RANGE = '[〜～~]'

/**
 * spec の本文から、AC の定義の番号を取り出す。
 *
 * @param markdown spec.md の本文
 * @returns AC の番号
 */
export function parseSpecAcs(markdown) {
  const acs = new Set()
  for (const match of markdown.matchAll(/^- \*\*AC-([0-9]+)\*\*/gm)) {
    acs.add(Number(match[1]))
  }
  return acs
}

/**
 * テストのソースから、テスト名を取り出す。
 *
 * @param source テストのソース
 * @param kind   'java'（メソッドの @DisplayName）か 'ts'（test・it の第 1 引数）
 * @returns テスト名
 */
export function extractTestNames(source, kind) {
  if (kind === 'java') {
    // クラス（@Nested を含む）に付いたものは、テストのまとまりの名前なので読まない。
    // 後に続く注釈と修飾子を飛ばした次の語が class かで見分ける（構文は解析しない）
    const classAfter =
      /^(?:\s*@[\w.]+(?:\((?:[^()"]|"(?:[^"\\]|\\.)*")*\))?)*\s*(?:(?:public|protected|private|static|final|abstract|sealed|non-sealed)\s+)*(?:class|record|enum|interface)\b/
    return [...source.matchAll(/@DisplayName\(\s*"((?:[^"\\]|\\.)*)"\s*\)/g)]
      .filter((match) => !classAfter.test(source.slice(match.index + match[0].length)))
      .map((match) => match[1].replace(/\\(.)/g, '$1'))
  }
  // describe はテストのまとまりの名前なので読まない（test.describe も同じ）
  const call =
    /\b(?:it|test)(?:\.(?:serial|parallel|only|skip|fixme|concurrent|each\([^)]*\)))*\(\s*(['"`])((?:(?!\1)[^\\]|\\.)*)\1/g
  return [...source.matchAll(call)].map((match) => match[2].replace(/\\(.)/g, '$1'))
}

/**
 * テスト名から AC 番号を取り出す。AC- の後に読点・カンマで並べた番号（AC- は先頭だけでもよい）を読む。
 *
 * @param name テスト名
 * @returns AC の番号と、範囲の表記（範囲の両端は番号に含める）
 */
export function parseAcRefs(name) {
  const acs = []
  const ranges = []
  const list = new RegExp(`AC-([0-9]+(?:\\s*(?:[、,]\\s*(?:AC-)?|${RANGE}\\s*)[0-9]+)*)`, 'g')
  const item = new RegExp(`([0-9]+)\\s*(${RANGE})\\s*([0-9]+)|([0-9]+)`, 'g')
  for (const [, numbers] of name.matchAll(list)) {
    for (const [, from, sign, to, single] of numbers.matchAll(item)) {
      if (single !== undefined) {
        acs.push(Number(single))
      } else {
        acs.push(Number(from), Number(to))
        ranges.push(`${from}${sign}${to}`)
      }
    }
  }
  return { acs, ranges }
}

/**
 * AC とテスト名を照合する。
 *
 * @param input.specAcs    spec の AC の番号
 * @param input.tests      テスト（ファイルと名前）
 * @param input.exclusions 除外の一覧（"AC-n" → 理由）
 * @param input.strict     テスト名に出ない AC を失敗にするか。false なら警告にする（missing には出す）
 * @returns 照合した結果。ok が false なら失敗
 */
export function check({ specAcs, tests, exclusions, deprecated = new Set(), strict = true }) {
  const covered = new Set()
  const ranges = []
  const unknown = []
  const deprecatedRefs = []
  for (const test of tests) {
    const refs = parseAcRefs(test.name)
    if (refs.ranges.length > 0) {
      ranges.push(test)
    }
    for (const ac of refs.acs) {
      if (!specAcs.has(ac)) {
        unknown.push({ ...test, ac })
      } else if (deprecated.has(ac)) {
        deprecatedRefs.push({ ...test, ac })
      } else {
        covered.add(ac)
      }
    }
  }

  const excluded = new Set()
  const invalidExclusions = []
  for (const [key, reason] of Object.entries(exclusions)) {
    const match = /^AC-([0-9]+)$/.exec(key)
    if (!match || !specAcs.has(Number(match[1])) || typeof reason !== 'string' || reason.trim() === '') {
      invalidExclusions.push(key)
    } else {
      excluded.add(Number(match[1]))
    }
  }

  const sorted = (values) => [...values].sort((a, b) => a - b)
  const missing = sorted([...specAcs].filter((ac) => !covered.has(ac) && !excluded.has(ac) && !deprecated.has(ac)))
  const staleExclusions = sorted([...excluded].filter((ac) => covered.has(ac)))
  const ok =
    (!strict || missing.length === 0) &&
    ranges.length === 0 &&
    unknown.length === 0 &&
    deprecatedRefs.length === 0 &&
    staleExclusions.length === 0 &&
    invalidExclusions.length === 0
  return { ok, missing, ranges, unknown, deprecatedRefs, staleExclusions, invalidExclusions }
}

/** AC 一覧の状態 */
const STATUSES = ['有効', '変更', '廃止']

/** AC 一覧の要約の長さの上限（字） */
const SUMMARY_LENGTH = 40

/** AC 一覧のテストの列に書くファイルの数の上限 */
const TEST_FILES_SHOWN = 3

/**
 * spec の本文から、AC の定義の番号と要約（最初の文）を取り出す。
 *
 * @param markdown spec.md の本文
 * @param spec     spec のディレクトリの名前（`001-catalog-and-inventory` など）
 * @param specPath docs/specs からの spec のディレクトリのパス（アーカイブの spec は `archive/mvp/001-…`）。省略すると名前
 * @returns AC の定義（spec の順）
 */
export function parseSpecAcDefinitions(markdown, spec, specPath = spec) {
  return [...markdown.matchAll(/^- \*\*AC-([0-9]+)\*\*\s*(.*)$/gm)].map(([, number, text]) => {
    // 最初の文：括弧の外の最初の句点まで
    let depth = 0
    let end = text.length
    for (let i = 0; i < text.length; i++) {
      if (text[i] === '（' || text[i] === '(') depth++
      else if (text[i] === '）' || text[i] === ')') depth = Math.max(0, depth - 1)
      else if (text[i] === '。' && depth === 0) {
        end = i + 1
        break
      }
    }
    const chars = [...text.slice(0, end).replaceAll('|', '｜')]
    const summary = chars.length > SUMMARY_LENGTH ? `${chars.slice(0, SUMMARY_LENGTH).join('')}…` : chars.join('')
    return { ac: Number(number), spec, specPath, summary }
  })
}

/** AC 一覧の文書（リポジトリのルートから） */
const AC_LIST = 'docs/design/acceptance-criteria.md'

/** AC 一覧の生成の範囲の印 */
const LIST_START = '<!-- generated:ac-list -->'
const LIST_END = '<!-- /generated:ac-list -->'

/**
 * AC 一覧の表を作る。状態・変えた spec・今の内容は手で書く列なので、前の一覧の値を残す。
 *
 * @param input.definitions spec の AC の定義
 * @param input.previous    前の一覧（AC → 手で書く列）
 * @param input.testFiles   AC → その AC をテスト名に持つテストのファイル（リポジトリのルートからのパス）
 * @param input.exclusions  除外の一覧（"AC-n" → 理由）
 * @returns 表の Markdown
 */
export function renderAcList({ definitions, previous, testFiles, exclusions }) {
  const lines = ['| AC | 要約 | 状態 | 元の spec | 変えた spec | 今の内容 | テスト |', '|----|------|------|-----------|-------------|----------|--------|']
  for (const { ac, spec, specPath, summary } of [...definitions].sort((a, b) => a.ac - b.ac)) {
    const kept = previous.get(ac) ?? { status: '有効', changedBy: '—', location: 'spec' }
    // ファイルを探す順は OS で違うので、名前の順にする
    const files = [...new Set((testFiles.get(ac) ?? []).map((file) => path.basename(file).replace(/\.java$/, '')))].sort()
    let test = 'なし'
    if (files.length > 0) {
      const shown = files.slice(0, TEST_FILES_SHOWN).map((file) => `\`${file}\``).join('、')
      test = files.length > TEST_FILES_SHOWN ? `${shown} ほか ${files.length - TEST_FILES_SHOWN}` : shown
    } else if (exclusions[`AC-${ac}`]) {
      test = '除外'
    }
    const origin = `[${spec.slice(0, 3)}](../specs/${specPath}/spec.md)`
    lines.push(`| AC-${ac} | ${summary} | ${kept.status} | ${origin} | ${kept.changedBy} | ${kept.location} | ${test} |`)
  }
  return `${lines.join('\n')}\n`
}

/**
 * AC 一覧の表から、手で書く列を読む。
 *
 * @param markdown AC 一覧（文書の全体か表）
 * @returns AC → 状態・変えた spec・今の内容
 */
export function parseAcList(markdown) {
  const list = new Map()
  for (const line of markdown.split('\n')) {
    const cells = line.split('|').map((cell) => cell.trim())
    const match = /^AC-([0-9]+)$/.exec(cells[1] ?? '')
    if (match && cells.length >= 9) {
      list.set(Number(match[1]), { status: cells[3], changedBy: cells[5], location: cells[6] })
    }
  }
  return list
}

/**
 * spec の AC と AC 一覧を照合する。
 *
 * @param input.definitions spec の AC の定義
 * @param input.list        AC 一覧（AC → 手で書く列）
 * @returns 食い違い（なければ空）
 */
export function checkAcList({ definitions, list }) {
  const problems = []
  const specAcs = new Set(definitions.map((definition) => definition.ac))
  const missing = [...specAcs].filter((ac) => !list.has(ac)).sort((a, b) => a - b)
  if (missing.length > 0) problems.push(`AC 一覧にない AC：${missing.map((ac) => `AC-${ac}`).join('、')}`)
  const extra = [...list.keys()].filter((ac) => !specAcs.has(ac)).sort((a, b) => a - b)
  if (extra.length > 0) problems.push(`spec にない AC が AC 一覧にある：${extra.map((ac) => `AC-${ac}`).join('、')}`)
  const invalid = [...list].filter(([, row]) => !STATUSES.includes(row.status)).sort(([a], [b]) => a - b)
  if (invalid.length > 0) {
    problems.push(`AC 一覧の状態が有効・変更・廃止でない：${invalid.map(([ac, row]) => `AC-${ac}（${row.status}）`).join('、')}`)
  }
  return problems
}

/**
 * テスト名に出ない AC を失敗にするかを、環境から決める。CI では失敗にし、手元では警告にする。<br />
 * 理由：spec を書いてからテストを書くまでの間も、手元で commit できるようにするため。
 * テストの漏れは、CI（push と pull request）で止める。
 *
 * @param env 環境変数
 * @returns CI なら true
 */
export function isStrict(env) {
  return env.CI === 'true'
}

/** テストを探さないディレクトリ */
const SKIPPED_DIRS = new Set([
  '.git',
  '.gradle',
  '.idea',
  '.local',
  '.next',
  'build',
  'coverage',
  'dist',
  'generated',
  'node_modules',
  'test-results',
])

/**
 * リポジトリの中のテストのファイルを探す。
 *
 * @param dir 探すディレクトリ
 * @returns テストのファイルと、その種類
 */
function findTestFiles(dir) {
  const files = []
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      if (!SKIPPED_DIRS.has(entry.name)) {
        files.push(...findTestFiles(full))
      }
    } else if (entry.name.endsWith('.java') && /[\\/]src[\\/](test|integrationTest)[\\/]/.test(full)) {
      files.push({ file: full, kind: 'java' })
    } else if (/\.test\.tsx?$|\.spec\.ts$/.test(entry.name)) {
      files.push({ file: full, kind: 'ts' })
    }
  }
  return files
}

/** 照合した結果を、標準出力に書く */
function report(result, root, strict) {
  const where = (test) => `${path.relative(root, test.file).replaceAll('\\', '/')}：${test.name}`
  if (result.missing.length > 0) {
    // 手元では警告。check-all.sh は「警告：」で始まる行を、通ったときも出す
    const prefix = strict ? '' : '警告：'
    console.log(`${prefix}テスト名に出ない AC：${result.missing.map((ac) => `AC-${ac}`).join('、')}`)
    console.log('  テストを追加するか、テストで確認できない理由を scripts/ci/ac-exclusions.json に書く。')
  }
  for (const test of result.ranges) {
    console.log(`範囲の表記（AC 番号を 1 つずつ書く）：${where(test)}`)
  }
  for (const test of result.unknown) {
    console.log(`spec にない AC-${test.ac}：${where(test)}`)
  }
  for (const test of result.deprecatedRefs) {
    console.log(`廃止した AC-${test.ac} を参照するテスト（AC 番号を消すか、AC 一覧の状態を直す）：${where(test)}`)
  }
  if (result.staleExclusions.length > 0) {
    console.log(`テストがあるのに除外されている AC：${result.staleExclusions.map((ac) => `AC-${ac}`).join('、')}`)
  }
  if (result.invalidExclusions.length > 0) {
    console.log(`除外の一覧の誤り（spec にない AC、理由がない）：${result.invalidExclusions.join('、')}`)
  }
}

/** spec とテストを読んで照合する */
function main() {
  const here = path.dirname(fileURLToPath(import.meta.url))
  const root = path.resolve(here, '../..')
  const specsDir = path.join(root, 'docs/specs')
  const definitions = []
  for (const { name, rel, dir } of listSpecDirs(specsDir)) {
    try {
      definitions.push(...parseSpecAcDefinitions(readFileSync(path.join(dir, 'spec.md'), 'utf8'), name, rel))
    } catch (error) {
      if (error.code !== 'ENOENT') throw error
    }
  }
  const specAcs = new Set(definitions.map((definition) => definition.ac))
  const tests = findTestFiles(root).flatMap(({ file, kind }) =>
    extractTestNames(readFileSync(file, 'utf8'), kind).map((name) => ({ file, name })),
  )
  const exclusions = JSON.parse(readFileSync(path.join(here, 'ac-exclusions.json'), 'utf8'))

  // AC 一覧：手で書く列（状態など）を読み、テストの列を再作成して比べる
  const listFile = path.join(root, AC_LIST)
  const document = existsSync(listFile) ? readFileSync(listFile, 'utf8') : null
  const from = document?.indexOf(LIST_START) ?? -1
  const to = document?.indexOf(LIST_END) ?? -1
  const current = from >= 0 && to > from ? document.slice(from + LIST_START.length, to).trim() : null
  const previous = parseAcList(current ?? '')
  const testFiles = new Map()
  for (const test of tests) {
    for (const ac of parseAcRefs(test.name).acs) {
      testFiles.set(ac, [...(testFiles.get(ac) ?? []), test.file])
    }
  }
  const rendered = renderAcList({ definitions, previous, testFiles, exclusions })
  if (process.argv.includes('--write')) {
    if (current === null) {
      console.log(`${AC_LIST} に ${LIST_START} と ${LIST_END} の範囲がない`)
      process.exit(1)
    }
    writeFileSync(listFile, `${document.slice(0, from + LIST_START.length)}\n${rendered}${document.slice(to)}`)
    console.log(`書き直した：${AC_LIST}`)
    process.exit(0)
  }
  const listProblems = []
  if (current === null) {
    listProblems.push(`${AC_LIST} に ${LIST_START} と ${LIST_END} の範囲がない`)
  } else {
    listProblems.push(...checkAcList({ definitions, list: previous }))
    if (`${current}\n` !== rendered) {
      listProblems.push(`AC 一覧が古い（テストか spec が変わった）。node scripts/ci/ac-check.mjs --write で書き直し、差分を確認する`)
    }
  }
  const deprecated = new Set([...previous].filter(([, row]) => row.status === '廃止').map(([ac]) => ac))

  const strict = isStrict(process.env)
  const result = check({ specAcs, tests, exclusions, deprecated, strict })
  report(result, root, strict)
  for (const problem of listProblems) console.log(problem)
  const ok = result.ok && listProblems.length === 0
  console.log(
    `${ok ? 'OK' : 'NG'}：AC ${specAcs.size} 件（廃止 ${deprecated.size} 件）、テスト名 ${tests.length} 件、除外 ${Object.keys(exclusions).length} 件`,
  )
  process.exit(ok ? 0 : 1)
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main()
}
