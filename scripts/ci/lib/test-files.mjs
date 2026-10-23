// テストのファイルの探し方と、テストの関数の読み方。
// AC の照合（ac-check）と、機能紹介の数え方（scripts/dev/showcase-counts）が使う
import path from 'node:path'
import { closingParen } from './closing-paren.mjs'
import { regexEnd, startsRegex } from './strip-comments.mjs'

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

/** テストの呼び出しの連鎖に付けられる修飾（each は表を伴うので別に読む） */
const TS_MODIFIERS = new Set(['serial', 'parallel', 'only', 'concurrent', 'skip', 'fixme'])

/** テストの呼び出しの始まり。前が . や語の文字のもの（/re/.test(...) など）は読まない */
const TS_CALL_START = /(?<![.\w$])(?:it|test)(?![\w$])/g

/** 引用符で閉じた文字列。1 つ目の括弧が引用符、2 つ目が中身。位置は lastIndex で指定する（sticky） */
const TS_NAME = /(['"`])((?:(?!\1)[^\\]|\\.)*)\1/y

/** 実行されないテストのまとまりの呼び出し。末尾の ( が、まとまりの範囲の始まり */
const SKIPPED_GROUP = /(?<![.\w$])(?:(?:test\.)?describe\.(?:skip|fixme)|xdescribe)\(/g

/**
 * テンプレートリテラルの終わりの次の位置を返す。`${...}` の中の式は、コードとして読む（中の ` で終わらない）。
 *
 * @param {string} source ソース
 * @param {number} start 始まりの ` の位置
 * @param {Uint8Array} [code] 与えると、`${...}` の中のコードの位置に 1 を書く
 * @returns {number} 終わりの次の位置。閉じなければソースの終わり
 */
function templateEnd(source, start, code) {
  let i = start + 1
  while (i < source.length) {
    if (source[i] === '\\') i += 2
    else if (source[i] === '`') return i + 1
    else if (source[i] === '$' && source[i + 1] === '{') i = codeEnd(source, i + 2, true, code)
    else i += 1
  }
  return source.length
}

/**
 * コードを読み進め、コードの位置を code に記録する。コメント、文字列、正規表現のリテラルは記録しない。
 *
 * @param {string} source ソース
 * @param {number} start 読み始める位置
 * @param {boolean} nested true なら、対応する } で止まる（テンプレートの `${` の中）
 * @param {Uint8Array} [code] コードの位置に 1 を書く
 * @returns {number} 止まった位置の次。nested でなければソースの終わり
 */
function codeEnd(source, start, nested, code) {
  let depth = 0
  let i = start
  while (i < source.length) {
    const ch = source[i]
    if (ch === '/' && source[i + 1] === '/') {
      const end = source.indexOf('\n', i)
      i = end === -1 ? source.length : end
    } else if (ch === '/' && source[i + 1] === '*') {
      const end = source.indexOf('*/', i + 2)
      i = end === -1 ? source.length : end + 2
    } else if (ch === '/' && source[i + 1] !== '>' && startsRegex(source.slice(Math.max(start, i - 200), i))) {
      i = regexEnd(source, i)
    } else if (ch === "'" || ch === '"') {
      i += 1
      while (i < source.length && source[i] !== ch && source[i] !== '\n') i += source[i] === '\\' ? 2 : 1
      i += 1
    } else if (ch === '`') {
      i = templateEnd(source, i, code)
    } else {
      if (nested && ch === '}' && depth === 0) return i + 1
      if (ch === '{') depth += 1
      else if (ch === '}') depth -= 1
      if (code) code[i] = 1
      i += 1
    }
  }
  return source.length
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
  while (source[pos] !== '(') {
    const modifier = /^\.(\w+)/.exec(source.slice(pos, pos + 20))
    if (!modifier) return null
    pos += modifier[0].length
    if (TS_MODIFIERS.has(modifier[1])) {
      skipped ||= modifier[1] === 'skip' || modifier[1] === 'fixme'
      last = modifier[1]
    } else if (modifier[1] === 'each') {
      last = 'each'
      if (source[pos] === '(') {
        const close = closingParen(source, pos)
        if (close >= source.length) return { reason: 'each の表の括弧が閉じない' }
        pos = close + 1
      } else if (source[pos] === '`') {
        pos = templateEnd(source, pos)
        if (source[pos - 1] !== '`') return { reason: 'each の表のテンプレートが閉じない' }
      } else {
        return null
      }
    } else {
      // describe、step、use、beforeEach などは、テストの宣言でない
      return null
    }
  }
  TS_NAME.lastIndex = pos + 1 + /^\s*/.exec(source.slice(pos + 1))[0].length
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
function scanTsTests(source) {
  const skippedRanges = [...source.matchAll(SKIPPED_GROUP)].map((match) => {
    const open = match.index + match[0].length - 1
    return [open, closingParen(source, open)]
  })
  const code = new Uint8Array(source.length)
  codeEnd(source, 0, false, code)
  const tests = []
  const unreadable = []
  for (const match of source.matchAll(TS_CALL_START)) {
    const call = readTsCall(source, match.index, match[0].length)
    if (!call) continue
    if ('reason' in call) {
      if (code[match.index]) unreadable.push({ index: match.index, reason: call.reason })
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
 * Vitest・Playwright のソースから、テストの呼び出しを読む。skip・fixme のテストと、
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
 * テスト名を読めない test・it の呼び出し（名前が文字列でもテンプレートでもない、括弧が閉じない）を返す。
 * 黙って数えないと AC の照合から消えるので、検査が失敗にする。
 *
 * @param {string} source テストのソース
 * @returns {{ index: number, reason: string }[]} ソースの中の位置と理由
 */
export function findUnreadableTsTests(source) {
  return scanTsTests(source).unreadable
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
