// テストのファイルの探し方と、テストの関数の読み方。
// each の表は括弧の対応で読むので、表の中の関数の呼び出しでテスト名が切れない。
import path from 'node:path'
import { closingParen } from './closing-paren.mjs'
import { scanCode, templateEnd } from './scan-code.mjs'
import { stripJavaComments } from './strip-comments.mjs'

/**
 * テストのファイルの種類を返す。
 *
 * Java は src/test・src/integrationTest の下の .java、
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

/** テストの呼び出しの連鎖に付けられる修飾（each は表を伴うので別に読む） */
const TS_MODIFIERS = new Set(['serial', 'parallel', 'only', 'concurrent', 'sequential', 'fails', 'skip', 'fixme', 'todo'])

/** 条件を引数に取り、続けて名前を書く修飾（test.skipIf(条件)('名前', ...)） */
const TS_CONDITIONS = new Set(['skipIf', 'runIf'])

/** 表を引数に取り、続けて名前を書く修飾（test.each(表)('名前', ...)） */
const TS_TABLES = new Set(['each', 'for'])

/** 表や条件を引数に取り、続けて名前を書く修飾 */
export const TS_ARGUMENT_MODIFIERS = new Set([...TS_TABLES, ...TS_CONDITIONS])

/** 修飾の 1 つ（.skip など）。位置は lastIndex で指定する（sticky） */
const TS_MODIFIER = /\.(\w+)/y

/** 空白の並び。位置は lastIndex で指定する（sticky） */
const SPACES = /\s*/y

/**
 * 位置から始まる修飾（.名前）を読む。
 *
 * @param {string} source ソース
 * @param {number} pos 読む位置
 * @returns {{ name: string, end: number } | null} 修飾の名前と、その次の位置。修飾でなければ null
 */
function readModifier(source, pos) {
  TS_MODIFIER.lastIndex = pos
  const match = TS_MODIFIER.exec(source)
  return match ? { name: match[1], end: TS_MODIFIER.lastIndex } : null
}

/**
 * 位置から空白を読み飛ばした次の位置を返す。
 *
 * @param {string} source ソース
 * @param {number} pos 読む位置
 * @returns {number} 空白の次の位置
 */
export function skipSpaces(source, pos) {
  SPACES.lastIndex = pos
  SPACES.exec(source)
  return SPACES.lastIndex
}

/** テストの宣言でない test・it の呼び出し（まとまり、フック、設定）。これ以外の知らない修飾は、読めない呼び出しにする */
const TS_NON_DECLARATIONS = new Set([
  'describe',
  'step',
  'use',
  'extend',
  'expect',
  'info',
  'setTimeout',
  'slow',
  'configure',
  'beforeEach',
  'afterEach',
  'beforeAll',
  'afterAll',
  'onTestFinished',
  'onTestFailed',
])

/** テストの呼び出しの始まり。前が . や語の文字のもの（/re/.test(...) など）は読まない */
const TS_CALL_START = /(?<![.\w$])(?:it|test)(?![\w$])/g

/** 引用符で閉じた文字列。1 つ目の括弧が引用符、2 つ目が中身。位置は lastIndex で指定する（sticky） */
const TS_NAME = /(['"`])((?:(?!\1)[^\\]|\\.)*)\1/y

/** 実行されないテストのまとまりの呼び出し。末尾の ( が、まとまりの範囲の始まり */
const SKIPPED_GROUP = /(?<![.\w$])(?:(?:test\.)?describe\.(?:skip|fixme)|xdescribe)\(/g

/**
 * describe・it・test の名前の前にある修飾の連鎖（.skip、.each(表)、.skipIf(条件) など）を読み飛ばす。
 *
 * 引数を取る修飾は、続く括弧かテンプレートの表も読み飛ばす。閉じない括弧とテンプレートは、読み飛ばした位置が範囲の外になるので、呼び出し側が見る。
 *
 * @param {string} source ソース（コメントの中を読まないものを渡す）
 * @param {number} pos describe・it・test の直後の位置
 * @param {ReadonlySet<string>} [argumentModifiers] 表や条件を引数に取る修飾の名前。なければ TS_ARGUMENT_MODIFIERS
 * @returns {{ pos: number, modifiers: string[] }} 修飾の後の位置と、読んだ修飾の名前
 */
export function skipModifiers(source, pos, argumentModifiers = TS_ARGUMENT_MODIFIERS) {
  const modifiers = []
  for (let modifier = readModifier(source, pos); modifier; modifier = readModifier(source, pos)) {
    pos = modifier.end
    modifiers.push(modifier.name)
    if (!argumentModifiers.has(modifier.name)) continue
    if (source[pos] === '(') pos = closingParen(source, pos) + 1
    else if (source[pos] === '`') pos = templateEnd(source, pos)
  }
  return { pos, modifiers }
}

/**
 * 1 つの呼び出しを読む。
 *
 * @param {string} source ソース
 * @param {number} start it・test の位置
 * @param {number} nameLength it・test の長さ
 * @returns {{ name: string, skipped: boolean } | { reason: string } | null} テスト、読めない呼び出し、テストの宣言でないもの（null）
 */
function readTsCall(source, start, nameLength) {
  let pos = start + nameLength
  let skipped = false
  let last = ''
  let afterArgs = false
  while (source[pos] !== '(') {
    const modifier = readModifier(source, pos)
    if (!modifier) {
      // 表や条件の括弧の後に名前の括弧が続かないものは、読み方の外れか知らない書き方なので、数えずに消えないよう読めない呼び出しにする
      if (afterArgs) return { reason: `${last} の後に、テスト名の括弧が続かない` }
      return null
    }
    afterArgs = false
    pos = modifier.end
    if (TS_MODIFIERS.has(modifier.name)) {
      skipped ||= modifier.name === 'skip' || modifier.name === 'fixme' || modifier.name === 'todo'
      last = modifier.name
    } else if (TS_CONDITIONS.has(modifier.name)) {
      last = modifier.name
      if (source[pos] !== '(') return { reason: `${modifier.name} の条件の括弧がない` }
      const close = closingParen(source, pos)
      if (close >= source.length) return { reason: `${modifier.name} の条件の括弧が閉じない` }
      pos = close + 1
      afterArgs = true
    } else if (TS_TABLES.has(modifier.name)) {
      last = modifier.name
      if (source[pos] === '(') {
        const close = closingParen(source, pos)
        if (close >= source.length) return { reason: 'each の表の括弧が閉じない' }
        pos = close + 1
        afterArgs = true
      } else if (source[pos] === '`') {
        pos = templateEnd(source, pos)
        if (source[pos - 1] !== '`') return { reason: 'each の表のテンプレートが閉じない' }
        afterArgs = true
      } else {
        return null
      }
    } else {
      // describe、step、use、beforeEach などは、テストの宣言でない。知らない修飾は、数えずに消えないよう読めない呼び出しにする
      if (TS_NON_DECLARATIONS.has(modifier.name)) return null
      return { reason: `知らない修飾 .${modifier.name} が付いている（読み方を足すか、テストの宣言でない呼び出しに加える）` }
    }
  }
  const nameStart = skipSpaces(source, pos + 1)
  if (source[nameStart] === '`') {
    // テンプレートの名前は、`${...}` の中の入れ子のテンプレートで切れないよう、テンプレートの終わりまで読む
    const end = templateEnd(source, nameStart)
    if (source[end - 1] === '`' && end - 1 > nameStart) return { name: source.slice(nameStart + 1, end - 1).replace(/\\(.)/g, '$1'), skipped }
    return { reason: 'テスト名のテンプレートが閉じない' }
  }
  TS_NAME.lastIndex = nameStart
  const name = TS_NAME.exec(source)
  if (name) return { name: name[2].replace(/\\(.)/g, '$1'), skipped }
  // test.skip(条件, 理由)・test.fixme() は、実行中に飛ばす書き方で、テストの宣言でない
  if (last === 'skip' || last === 'fixme') return null
  return { reason: 'テスト名が文字列でもテンプレートでもない（または閉じていない）' }
}

/**
 * Vitest・Playwright のソースから、テストの呼び出しを全て読む。
 *
 * @param {string} source テストのソース
 * @returns {{ tests: { name: string, index: number, skipped: boolean }[], unreadable: { index: number, reason: string }[] }}
 *   読めたテストと、読めない呼び出し（コード上のものだけ。コメントと文字列の中は含めない）
 */
export function scanTsTests(source) {
  const code = new Uint8Array(source.length)
  scanCode(source, {}, (type, from, to) => {
    if (type === 'code') code.fill(1, from, to)
  })
  // コメントと文字列の中の describe.skip( は、まとまりの範囲にしない（後ろのテストを skipped にしない）
  const skippedRanges = [...source.matchAll(SKIPPED_GROUP)]
    .filter((match) => code[match.index])
    .map((match) => {
      const open = match.index + match[0].length - 1
      return [open, closingParen(source, open)]
    })
  const tests = []
  const unreadable = []
  for (const match of source.matchAll(TS_CALL_START)) {
    // コメントと文字列の中の呼び出し（コメントアウトしたテスト、説明の例）は、テストでも読めない呼び出しでもない
    if (!code[match.index]) continue
    const call = readTsCall(source, match.index, match[0].length)
    if (!call) continue
    if ('reason' in call) {
      unreadable.push({ index: match.index, reason: call.reason })
      continue
    }
    tests.push({
      name: call.name,
      index: match.index,
      skipped: call.skipped || skippedRanges.some(([from, to]) => match.index > from && match.index < to),
    })
  }
  return { tests, unreadable }
}

/**
 * Vitest・Playwright のソースから、テストの呼び出しを読む。
 *
 * skip・fixme のテストと、
 * skip・fixme のまとまり（describe.skip、test.describe.fixme、xdescribe）の中のテストは、skipped を true にして返す。
 * まとまりの範囲と each の表の終わりは、呼び出しの括弧の対応で求める（表の中の関数の呼び出しで切れない）。
 *
 * @param {string} source テストのソース
 * @returns {{ name: string, index: number, skipped: boolean }[]} テスト名、ソースの中の位置、実行されないか
 */
export function readTsTests(source) {
  return scanTsTests(source).tests
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
      /(?:\s*@[\w.]+(?![\w.])(?:\((?:[^()"]|"(?:[^"\\]|\\.)*")*\))?)*\s*(?:(?:public|protected|private|static|final|abstract|sealed|non-sealed)\s+)*(?:class|record|enum|interface)\b/y
    // コメントアウトしたテストは数えない（位置を保ったままコメントを空白にしてから読む）
    const code = stripJavaComments(source)
    return [...code.matchAll(/@DisplayName\(\s*"((?:[^"\\]|\\.)*)"\s*\)/g)]
      .filter((match) => {
        classAfter.lastIndex = match.index + match[0].length
        return !classAfter.test(code)
      })
      .map((match) => match[1].replace(/\\(.)/g, '$1'))
  }
  return readTsTests(source)
    .filter((test) => !test.skipped)
    .map((test) => test.name)
}
