// ============================================================================
// 機能の spec の AC と、テスト名の AC 番号を照合する（テストの書き方 第 2.1 節）
//
//   使い方：node scripts/ci/ac-check.mjs
//
// - AC：spec.md（docs/specs/ の直下とアーカイブの下の spec）の定義の行（MVP の - **AC-n** と、1→10 の - **AC-7-1**）。
//   2 つの形は別の AC として扱う（テスト名の AC-7-1 を AC-7 と読まない）
// - テスト名：Java のメソッドの @DisplayName（src/test、src/integrationTest）と、
//   Vitest・Playwright の test・it の第 1 引数（*.test.ts(x)、*.spec.ts）。まとまりの名前（describe、クラス）は読まない。
//   skip・fixme のテストと、skip・fixme のまとまりの中のテストは数えない。Git の対象のファイルだけを読む
// - 次のときに失敗する：テスト名に出ない AC がある、テスト名の AC 番号が範囲（AC-8〜10）で書かれている、
//   新しい形の後に数字だけの番号を並べている（AC-7-1、2。AC-7-1、AC-7-2 と書く）、
//   空白で区切った AC の並びがある（AC-1 AC-2。AC-1、AC-2 と書く）、
//   テスト名を読めない test・it の呼び出し（名前が文字列でない、each の括弧が閉じない）がある、
//   spec にない AC 番号がテスト名にある、除外の一覧（ac-exclusions.json）が正しくない
// - テスト名に出ない AC は、CI（環境変数 CI=true）でだけ失敗にし、手元では警告にする。
//   手元で CI と同じに確認するとき：CI=true node scripts/ci/ac-check.mjs
// - AC 一覧（docs/design/acceptance-criteria.md）を読む。廃止の AC はテストが必要なく、テストが参照すると失敗する。
//   spec と AC 一覧の AC が食い違うとき、状態が有効・変更・廃止でないとき、表が古いときに失敗する。
//   表の要約・元の spec・テストの列は再作成する列で、--write で書き直す（状態・変えた spec・今の内容は残す）
// ============================================================================
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { extractTestNames, findTestFiles, findUnreadableTsTests } from './lib/test-files.mjs'
import { createLineLocator } from './lib/line-number.mjs'
import { findEmptyReasons, readExclusions } from './lib/exclusions.mjs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { listSpecDirs } from './lib/spec-dirs.mjs'
import { isMain } from './lib/is-main.mjs'

/** 範囲を表す記号（全角の波ダッシュ、全角のチルダ、半角のチルダ） */
const RANGE = '[〜～~]'

/** AC の番号の書き方。MVP の `12` と、1→10 の `7-1`（spec の番号 ＋ 連番）の 2 つの形 */
const AC_NUMBER = '[0-9]+(?:-[0-9]+)?'

/** テスト名の中の AC の並び（`AC-1、2`、`AC-8〜10` など）に合う正規表現の元。1 つ目の括弧が、`AC-` の後の並び全体 */
const AC_LIST_SOURCE = `AC-(${AC_NUMBER}(?:\\s*(?:[、,]\\s*(?:AC-)?|${RANGE}\\s*)${AC_NUMBER})*)`

/** AC の番号だけに合う正規表現（`AC-` の後の全体） */
const AC_NUMBER_ONLY = new RegExp(`^(${AC_NUMBER})$`)

/**
 * AC の番号の文字列を、照合に使う値にする。MVP の `12` は数、1→10 の `7-1` は文字列のまま。
 * 形で型を分けるので、`AC-7` と `AC-7-1` は別の AC になる。
 *
 * @param text AC- の後の番号（`12`、`7-1`）
 * @returns 数（MVP）か文字列（1→10）
 */
export function toAcKey(text) {
  return text.includes('-') ? text : Number(text)
}

/** AC の値を並べるための組。MVP は番号の順、その後に 1→10 の spec の番号、連番の順 */
function acOrder(ac) {
  if (typeof ac === 'number') return [0, ac, 0]
  const [spec, seq] = ac.split('-').map(Number)
  return [1, spec, seq]
}

/** AC の値の比較（並べ替え用） */
export function compareAc(a, b) {
  const left = acOrder(a)
  const right = acOrder(b)
  return left[0] - right[0] || left[1] - right[1] || left[2] - right[2]
}

/**
 * spec の本文から、AC の定義の番号を取り出す。
 *
 * @param markdown spec.md の本文
 * @returns AC の番号
 */
export function parseSpecAcs(markdown) {
  const acs = new Set()
  for (const match of markdown.matchAll(new RegExp(`^- \\*\\*AC-(${AC_NUMBER})\\*\\*`, 'gm'))) {
    acs.add(toAcKey(match[1]))
  }
  return acs
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
  const list = new RegExp(AC_LIST_SOURCE, 'g')
  const item = new RegExp(`(${AC_NUMBER})\\s*(${RANGE})\\s*(${AC_NUMBER})|(${AC_NUMBER})`, 'g')
  for (const [, numbers] of name.matchAll(list)) {
    for (const [, from, sign, to, single] of numbers.matchAll(item)) {
      if (single !== undefined) {
        acs.push(toAcKey(single))
      } else {
        // 新しい形の範囲は終わりの連番だけでもよい（AC-7-1〜3 は AC-7-1 と AC-7-3）
        const end = from.includes('-') && !to.includes('-') ? `${from.split('-')[0]}-${to}` : to
        acs.push(toAcKey(from), toAcKey(end))
        ranges.push(`${from}${sign}${to}`)
      }
    }
  }
  return { acs, ranges }
}

/**
 * テスト名から、曖昧な AC の並べ方を取り出す。新しい形（AC-7-1）の後に、読点・カンマと数字だけの番号（AC-7-1、2）が続く書き方。<br />
 * 理由：MVP の AC-2 と読め、MVP 側の抜けを隠すため。新しい形は AC-7-1、AC-7-2 と書く。
 *
 * @param name テスト名
 * @returns 曖昧な並べ方の文字列
 */
export function findAmbiguousAcLists(name) {
  const list = new RegExp(AC_LIST_SOURCE, 'g')
  const found = []
  for (const match of name.matchAll(list)) {
    // 範囲を含まない並びの区切りで分け、AC- が付かない数字だけの項目が、新しい形の直後にあるかを見る
    const items = match[0].split(/\s*[、,]\s*/)
    const bad = items.some((item, i) => i > 0 && /^[0-9]+(?!\d|-)/.test(item) && /^(?:AC-)?[0-9]+-[0-9]+/.test(items[i - 1]))
    if (bad) found.push(match[0])
  }
  return found
}

/**
 * テスト名から、空白（半角・全角）で区切った AC の並び（AC-1 AC-2）を取り出す。<br />
 * 理由：複数の AC は読点で 1 つずつ並べる決まりで（AC-1、AC-2）、空白の並びは読み方が人によって分かれるため。
 *
 * @param name テスト名
 * @returns 空白で区切った並びの文字列
 */
export function findSpacedAcLists(name) {
  const list = new RegExp(`AC-${AC_NUMBER}(?:[\\s\\u3000]+AC-${AC_NUMBER})+`, 'g')
  return [...name.matchAll(list)].map((match) => match[0])
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
  const ambiguous = []
  const spaced = []
  const unknown = []
  const deprecatedRefs = []
  for (const test of tests) {
    const refs = parseAcRefs(test.name)
    if (refs.ranges.length > 0) {
      ranges.push(test)
    }
    if (findAmbiguousAcLists(test.name).length > 0) {
      ambiguous.push(test)
    }
    if (findSpacedAcLists(test.name).length > 0) {
      spaced.push(test)
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
  const emptyReasons = new Set(findEmptyReasons(exclusions))
  for (const key of Object.keys(exclusions)) {
    const match = key.startsWith('AC-') ? AC_NUMBER_ONLY.exec(key.slice(3)) : null
    if (!match || !specAcs.has(toAcKey(match[1])) || emptyReasons.has(key)) {
      invalidExclusions.push(key)
    } else {
      excluded.add(toAcKey(match[1]))
    }
  }

  const sorted = (values) => [...values].sort(compareAc)
  const missing = sorted([...specAcs].filter((ac) => !covered.has(ac) && !excluded.has(ac) && !deprecated.has(ac)))
  const staleExclusions = sorted([...excluded].filter((ac) => covered.has(ac)))
  const ok =
    (!strict || missing.length === 0) &&
    ranges.length === 0 &&
    ambiguous.length === 0 &&
    spaced.length === 0 &&
    unknown.length === 0 &&
    deprecatedRefs.length === 0 &&
    staleExclusions.length === 0 &&
    invalidExclusions.length === 0
  return { ok, missing, ranges, ambiguous, spaced, unknown, deprecatedRefs, staleExclusions, invalidExclusions }
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
  return [...markdown.matchAll(new RegExp(`^- \\*\\*AC-(${AC_NUMBER})\\*\\*\\s*(.*)$`, 'gm'))].map(([, number, text]) => {
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
    return { ac: toAcKey(number), spec, specPath, summary }
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
  for (const { ac, spec, specPath, summary } of [...definitions].sort((a, b) => compareAc(a.ac, b.ac))) {
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
    const origin = `[${/^[0-9]+/.exec(spec)?.[0] ?? spec}](../specs/${specPath}/spec.md)`
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
    const cell = cells[1] ?? ''
    const match = cell.startsWith('AC-') ? AC_NUMBER_ONLY.exec(cell.slice(3)) : null
    if (match && cells.length >= 9) {
      list.set(toAcKey(match[1]), { status: cells[3], changedBy: cells[5], location: cells[6] })
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
  const missing = [...specAcs].filter((ac) => !list.has(ac)).sort(compareAc)
  if (missing.length > 0) problems.push(`AC 一覧にない AC：${missing.map((ac) => `AC-${ac}`).join('、')}`)
  const extra = [...list.keys()].filter((ac) => !specAcs.has(ac)).sort(compareAc)
  if (extra.length > 0) problems.push(`spec にない AC が AC 一覧にある：${extra.map((ac) => `AC-${ac}`).join('、')}`)
  const invalid = [...list].filter(([, row]) => !STATUSES.includes(row.status)).sort(([a], [b]) => compareAc(a, b))
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
  for (const test of result.ambiguous) {
    console.log(`曖昧な AC の並べ方（新しい形は AC-7-1、AC-7-2 と書く）：${where(test)}`)
  }
  for (const test of result.spaced) {
    console.log(`空白で区切った AC の並び（AC-1、AC-2 と読点で 1 つずつ書く）：${where(test)}`)
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

/**
 * テスト名を読めない呼び出しを、失敗の文にして返す。読めないテストは AC の照合から消えるので、黙って落とさない。
 *
 * @param {string} source Vitest・Playwright のテストのソース
 * @param {string} rel 表示するファイルのパス
 * @returns {string[]} `ファイル:行：理由`
 */
export function describeUnreadableTests(source, rel) {
  const lineOf = createLineLocator(source)
  return findUnreadableTsTests(source).map(({ index, reason }) => `${rel}:${lineOf(index)}：${reason}`)
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
  const gitFiles = listGitFiles([], root)
  exitIfNoFiles(gitFiles, 'Git のファイル')
  const unreadable = []
  const tests = findTestFiles(root, gitFiles).flatMap(({ file, kind }) => {
    const source = readFileSync(file, 'utf8')
    if (kind === 'ts') unreadable.push(...describeUnreadableTests(source, path.relative(root, file).replaceAll('\\', '/')))
    return extractTestNames(source, kind).map((name) => ({ file, name }))
  })
  const exclusions = readExclusions(path.join(here, 'ac-exclusions.json'))

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
  for (const problem of unreadable) console.log(`読めないテストの呼び出し（テスト名を文字列かテンプレートで書く）：${problem}`)
  const ok = result.ok && listProblems.length === 0 && unreadable.length === 0
  console.log(
    `${ok ? 'OK' : 'NG'}：AC ${specAcs.size} 件（廃止 ${deprecated.size} 件）、テスト名 ${tests.length} 件、除外 ${Object.keys(exclusions).length} 件`,
  )
  process.exit(ok ? 0 : 1)
}

if (isMain(import.meta.url)) {
  main()
}
