// ============================================================================
// 機能の spec の AC と、テスト名の AC 番号を照らす（テストの書き方 第 2.1 節）
//
//   使い方：node scripts/ci/ac-check.mjs
//
// - AC：docs/specs/*/spec.md の定義の行（- **AC-n**）
// - テスト名：Java の @DisplayName（src/test、src/integrationTest）と、
//   Vitest・Playwright の test・it・describe の第 1 引数（*.test.ts(x)、*.spec.ts）
// - 次のときに失敗する：テスト名に出ない AC がある、テスト名の AC 番号が範囲（AC-8〜10）で書かれている、
//   spec にない AC 番号がテスト名にある、除外の一覧（ac-exclusions.json）が正しくない
// ============================================================================
import { readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

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
 * @param kind   'java'（@DisplayName）か 'ts'（test・it・describe の第 1 引数）
 * @returns テスト名
 */
export function extractTestNames(source, kind) {
  if (kind === 'java') {
    return [...source.matchAll(/@DisplayName\(\s*"((?:[^"\\]|\\.)*)"\s*\)/g)].map((match) =>
      match[1].replace(/\\(.)/g, '$1'),
    )
  }
  const call =
    /\b(?:describe|it|test)(?:\.(?:describe|serial|parallel|only|skip|fixme|concurrent|each\([^)]*\)))*\(\s*(['"`])((?:(?!\1)[^\\]|\\.)*)\1/g
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
 * AC とテスト名を照らす。
 *
 * @param input.specAcs    spec の AC の番号
 * @param input.tests      テスト（ファイルと名前）
 * @param input.exclusions 除外の一覧（"AC-n" → 理由）
 * @returns 照らした結果。ok が false なら失敗
 */
export function check({ specAcs, tests, exclusions }) {
  const covered = new Set()
  const ranges = []
  const unknown = []
  for (const test of tests) {
    const refs = parseAcRefs(test.name)
    if (refs.ranges.length > 0) {
      ranges.push(test)
    }
    for (const ac of refs.acs) {
      if (specAcs.has(ac)) {
        covered.add(ac)
      } else {
        unknown.push({ ...test, ac })
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
  const missing = sorted([...specAcs].filter((ac) => !covered.has(ac) && !excluded.has(ac)))
  const staleExclusions = sorted([...excluded].filter((ac) => covered.has(ac)))
  const ok =
    missing.length === 0 &&
    ranges.length === 0 &&
    unknown.length === 0 &&
    staleExclusions.length === 0 &&
    invalidExclusions.length === 0
  return { ok, missing, ranges, unknown, staleExclusions, invalidExclusions }
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

/** 照らした結果を、標準出力に書く */
function report(result, root) {
  const where = (test) => `${path.relative(root, test.file).replaceAll('\\', '/')}：${test.name}`
  if (result.missing.length > 0) {
    console.log(`テスト名に出ない AC：${result.missing.map((ac) => `AC-${ac}`).join('、')}`)
    console.log('  テストを足すか、テストで確かめられない理由を scripts/ci/ac-exclusions.json に書く。')
  }
  for (const test of result.ranges) {
    console.log(`範囲の表記（AC 番号を 1 つずつ書く）：${where(test)}`)
  }
  for (const test of result.unknown) {
    console.log(`spec にない AC-${test.ac}：${where(test)}`)
  }
  if (result.staleExclusions.length > 0) {
    console.log(`テストがあるのに除外されている AC：${result.staleExclusions.map((ac) => `AC-${ac}`).join('、')}`)
  }
  if (result.invalidExclusions.length > 0) {
    console.log(`除外の一覧の誤り（spec にない AC、理由がない）：${result.invalidExclusions.join('、')}`)
  }
}

/** spec とテストを読んで照らす */
function main() {
  const here = path.dirname(fileURLToPath(import.meta.url))
  const root = path.resolve(here, '../..')
  const specsDir = path.join(root, 'docs/specs')
  const specAcs = new Set()
  for (const entry of readdirSync(specsDir, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      try {
        for (const ac of parseSpecAcs(readFileSync(path.join(specsDir, entry.name, 'spec.md'), 'utf8'))) {
          specAcs.add(ac)
        }
      } catch (error) {
        if (error.code !== 'ENOENT') throw error
      }
    }
  }
  const tests = findTestFiles(root).flatMap(({ file, kind }) =>
    extractTestNames(readFileSync(file, 'utf8'), kind).map((name) => ({ file, name })),
  )
  const exclusions = JSON.parse(readFileSync(path.join(here, 'ac-exclusions.json'), 'utf8'))

  const result = check({ specAcs, tests, exclusions })
  report(result, root)
  console.log(
    `${result.ok ? 'OK' : 'NG'}：AC ${specAcs.size} 件、テスト名 ${tests.length} 件、除外 ${Object.keys(exclusions).length} 件`,
  )
  process.exit(result.ok ? 0 : 1)
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main()
}
