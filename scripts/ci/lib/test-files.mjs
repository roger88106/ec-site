// テストのファイルの探し方と、テストの関数の読み方。
// AC の照合（ac-check）と、機能紹介の数え方（scripts/dev/showcase-counts）が使う
import path from 'node:path'
import { closingParen } from './closing-paren.mjs'

/**
 * テストのファイルの種類を返す。Java は src/test・src/integrationTest の下の .java、
 * Vitest・Playwright は *.test.ts(x)、*.spec.ts。
 *
 * @param {string} rel リポジトリのルートからの相対パス（/ 区切り）
 * @returns {'java' | 'ts' | null} 種類。テストのファイルでなければ null
 */
export function testFileKind(rel) {
  if (rel.endsWith('.java') && /(^|\/)src\/(test|integrationTest)\//.test(rel)) return 'java'
  if (/\.test\.tsx?$|\.spec\.ts$/.test(rel)) return 'ts'
  return null
}

/**
 * Git の対象のファイルから、テストのファイルを選ぶ。
 *
 * @param {string} root リポジトリのルート
 * @param {string[]} gitFiles Git の対象のファイル（ルートからの相対パス、/ 区切り）
 * @returns {{ file: string, kind: 'java' | 'ts' }[]} テストのファイル（絶対パス）と、その種類
 */
export function findTestFiles(root, gitFiles) {
  const files = []
  for (const rel of gitFiles) {
    const kind = testFileKind(rel)
    if (kind) files.push({ file: path.join(root, rel), kind })
  }
  return files
}

/**
 * Vitest・Playwright のテストの呼び出し。describe・step・beforeEach などは読まない。
 * 前が . や語の文字のもの（/re/.test(...) など）も読まない。1 つ目の括弧が呼び出しの連鎖、2 つ目が引用符、3 つ目がテスト名
 */
const TS_CALL =
  /(?<![.\w$])(?:it|test)((?:\.(?:serial|parallel|only|concurrent|skip|fixme|each\([^)]*\)))*)\(\s*(['"`])((?:(?!\2)[^\\]|\\.)*)\2/g

/** 実行されないテストのまとまりの呼び出し。末尾の ( が、まとまりの範囲の始まり */
const SKIPPED_GROUP = /(?<![.\w$])(?:(?:test\.)?describe\.(?:skip|fixme)|xdescribe)\(/g

/**
 * Vitest・Playwright のソースから、テストの呼び出しを読む。skip・fixme のテストと、
 * skip・fixme のまとまり（describe.skip、test.describe.fixme、xdescribe）の中のテストは、skipped を true にして返す。
 * まとまりの範囲は、呼び出しの括弧の対応で求める。
 *
 * @param {string} source テストのソース
 * @returns {{ name: string, index: number, skipped: boolean }[]} テスト名、ソースの中の位置、実行されないか
 */
export function readTsTests(source) {
  const skippedRanges = [...source.matchAll(SKIPPED_GROUP)].map((match) => {
    const open = match.index + match[0].length - 1
    return [open, closingParen(source, open)]
  })
  return [...source.matchAll(TS_CALL)].map((match) => ({
    name: match[3].replace(/\\(.)/g, '$1'),
    index: match.index,
    skipped: /\.(?:skip|fixme)\b/.test(match[1]) || skippedRanges.some(([from, to]) => match.index > from && match.index < to),
  }))
}

/**
 * テストのソースから、実行されるテストの名前を取り出す。まとまりの名前（describe、クラス）は読まない。
 *
 * @param {string} source テストのソース
 * @param {'java' | 'ts'} kind 'java'（メソッドの @DisplayName）か 'ts'（test・it の第 1 引数）
 * @returns {string[]} テスト名
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
  return readTsTests(source)
    .filter((test) => !test.skipped)
    .map((test) => test.name)
}
