// テスト名を読み、AC の番号を読む（ac-check のテスト名の読み取り）。
//
// 読むもの：
//   - AC：MVP の AC-n と、1→10 の AC-s-n（s は spec の番号、n は連番）。2 つの形は別の AC として扱う。
//     AC-s-n を AC-s と読まない。番号は、文書の記号と番号の「spec の番号」
//   - テスト名：Java のメソッドの @DisplayName（src/test、src/integrationTest）。
//     Vitest・Playwright の test・it の第 1 引数（*.test.ts(x)、*.spec.ts）。Git の対象のファイルだけを読む
//   - 読まないテスト名：まとまりの名前（describe、クラス）、skip・fixme のテスト、skip・fixme のまとまりの中のテスト。
//     コメント・文字列の中の呼び出しも読まない
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { exitIfNoFiles } from '../git/git-files.mjs'
import { createLineLocator } from '../source/line-number.mjs'
import { extractTestNames, findTestFiles, scanTsTests } from '../source/test-files.mjs'
import { describeUnreadableLine } from '../source/unreadable-line.mjs'

/** 範囲を表す記号（全角の波ダッシュ、全角のチルダ、半角のチルダ） */
const RANGE = '[〜～~]'

/** AC の番号の書き方。MVP の `12` と、1→10 の `7-1`（spec の番号 ＋ 連番）の 2 つの形 */
export const AC_NUMBER = '[0-9]+(?:-[0-9]+)?'

/** テスト名の中の AC の並び（`AC-n、m`、`AC-n〜m` など）に合う正規表現の元。1 つ目の括弧が、`AC-` の後の並び全体 */
const AC_LIST_SOURCE = `AC-(${AC_NUMBER}(?:\\s*(?:[、,]\\s*(?:AC-)?|${RANGE}\\s*)${AC_NUMBER})*)`

/** AC の番号だけに合う正規表現（`AC-` の後の全体） */
export const AC_NUMBER_ONLY = new RegExp(`^(${AC_NUMBER})$`)

/**
 * AC の番号の文字列を、照合に使う値にする。MVP の `12` は数、1→10 の `7-1` は文字列のまま。
 *
 * 形で型を分けるので、`AC-s` と `AC-s-n` は別の AC になる。
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
        // 新しい形の範囲は終わりの連番だけでもよい（AC-s-n〜m は AC-s-n と AC-s-m）
        const end = from.includes('-') && !to.includes('-') ? `${from.split('-')[0]}-${to}` : to
        acs.push(toAcKey(from), toAcKey(end))
        ranges.push(`${from}${sign}${to}`)
      }
    }
  }
  return { acs, ranges }
}

/**
 * テスト名から、曖昧な AC の並べ方を取り出す。新しい形（AC-s-n）の後に、読点・カンマと数字だけの番号（AC-s-n、m）が続く書き方。
 *
 * 理由：MVP の AC-m と読め、MVP 側の抜けを隠すため。新しい形は AC-s-n、AC-s-m と書く。
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
 * テスト名から、空白（半角・全角）で区切った AC の並び（AC-n AC-m）を取り出す。
 *
 * 理由：複数の AC は読点で 1 つずつ並べる決まりで（AC-n、AC-m）、空白の並びは読み方が人によって分かれるため。
 *
 * @param name テスト名
 * @returns 空白で区切った並びの文字列
 */
export function findSpacedAcLists(name) {
  const list = new RegExp(`AC-${AC_NUMBER}(?:[\\s\\u3000]+AC-${AC_NUMBER})+`, 'g')
  return [...name.matchAll(list)].map((match) => match[0])
}

/**
 * テスト名を読めない呼び出しを、失敗の文にして返す。読めないテストは AC の照合から消えるので、読み飛ばさない。
 *
 * @param {string} source Vitest・Playwright のテストのソース
 * @param {string} rel 表示するファイルのパス
 * @param {{ index: number, reason: string }[]} unreadable scanTsTests が返した、読めない呼び出し
 * @returns {string[]} `ファイル:行 内容（理由）`
 */
export function describeUnreadableTests(source, rel, unreadable) {
  const lineOf = createLineLocator(source)
  const lines = source.split('\n')
  return unreadable.map(({ index, reason }) => {
    const line = lineOf(index)
    return describeUnreadableLine({ file: rel, line, text: lines[line - 1], reason })
  })
}

/**
 * Git の対象のテストを読み、テスト名と、AC → テストのファイルの対応を返す。
 *
 * @param {string} root リポジトリのルート
 * @param {string[]} gitFiles Git の対象のファイル
 * @returns {{ tests: { file: string, name: string }[], unreadable: string[], testFiles: Map<number | string, string[]> }}
 */
export function readTests(root, gitFiles) {
  const testSources = findTestFiles(root, gitFiles)
  exitIfNoFiles(testSources, 'テストのファイル')
  const unreadable = []
  const tests = testSources.flatMap(({ file, kind }) => {
    const source = readFileSync(file, 'utf8')
    if (kind === 'ts') {
      // 1 回の走査で、テスト名と読めない呼び出しの両方を得る
      const scan = scanTsTests(source)
      unreadable.push(...describeUnreadableTests(source, path.relative(root, file).replaceAll('\\', '/'), scan.unreadable))
      return scan.tests.filter((test) => !test.skipped).map((test) => ({ file, name: test.name }))
    }
    return extractTestNames(source, kind).map((name) => ({ file, name }))
  })
  const testFiles = new Map()
  for (const test of tests) {
    for (const ac of parseAcRefs(test.name).acs) {
      testFiles.set(ac, [...(testFiles.get(ac) ?? []), path.relative(root, test.file).replaceAll('\\', '/')])
    }
  }
  return { tests, unreadable, testFiles }
}
