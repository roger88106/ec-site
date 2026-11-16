// フロントの書き方の規則のうち、Biome の plugin（frontend/biome-plugins/、e2e/biome-plugins/）で書けないものを確認する。
// 複数のファイルを比べる規則と、変数の範囲や式の終わりを追う規則が残る。各規則の対象と見る形は、関数のコメントにある。
//
// - 店舗画面と管理画面：同じ相対パスで同じ内容のファイルを置かない。共有のパッケージが公開する名前と同じ名前を定義しない（フロントの書き方の「入れるもの・入れないもの」）
// - 画面のテスト：要素のプロパティ・属性を toBe・toEqual で比べない（フロントのテストの書き方の「要素のプロパティ・属性・状態を jest-dom で確認する」）
// - 画面のテストと E2E：複数の結果を並びのインデックスで選ばない（フロントのテストの書き方の「画面に見えるもので探す」）
// - E2E：ボタンの名前を完全一致で探す（同上）。タイムアウトを数字のリテラルで書かない（テストの書き方の「待ち方」）
// - 画面のテストと補助と E2E：テストの間に空行を置く（フロントのテストの書き方の「テストとテストの間に空行を置く」）。
//   組み込みの名前と同じ名前の const・let を宣言しない（フロントのテストの書き方の「組み込みの名前をシャドーイングしない」）。
//   名前の一覧 globals/lib-globals.json は、lib-globals.mjs --write が TypeScript の lib から作る
//
// 失敗したとき：報告の括弧の中の書き方に直す。同じファイルは共有のパッケージに移す。TypeScript を上げて lib の名前が変わったときは、lib-globals.mjs --write で一覧を書き換える。
// 除外：frontend-rules-exclusions.json に {キー: 理由}。キーは src からの相対パス（同じ内容のファイル）か <画面>:<名前>（共有と同じ名前の定義。例：store:translate）。不要になった除外は失敗にする。
// 使い方：node scripts/ci/frontend-rules.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { closingParen } from './lib/closing-paren.mjs'
import { scanCode } from './lib/scan-code.mjs'
import { checkExclusions, exclusionsPath, readExclusions } from './lib/exclusions.mjs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isScreenSource } from './lib/screen-sources.mjs'
import { stripComments } from './lib/strip-comments.mjs'
import { isMain } from './lib/is-main.mjs'
import { createLineLocator } from './lib/line-number.mjs'

/** 画面のソース（テスト、生成物、shadcn/ui の部品、共有の ui、テストの補助を含む） */
export const SOURCE = /^frontend\/(?:store|admin|shared)\/src\/.*\.tsx?$/

/** 場所に関わらない、テストのファイル（.test.ts・.test.tsx）。公開する名前などから、どの場所のテストも除くときに使う */
export const ANY_TEST_FILE = /\.test\.tsx?$/

/** 画面の単体・結合テストのファイル */
export const FRONTEND_TEST = /^frontend\/(?:store|admin|shared)\/src\/.*\.test\.tsx?$/

/** 画面のテストの補助のファイル（src の下の test/ ディレクトリの下） */
export const FRONTEND_TEST_SUPPORT = /^frontend\/(?:store|admin|shared)\/src\/(?:.*\/)?test\/.*\.tsx?$/

/** E2E のファイル */
export const E2E_FILE = /^e2e\/.*\.tsx?$/

/**
 * ファイルを、規則を当てるまとまりに振り分ける。ファイルの順は保つ。どのまとまりにも入らないファイルは捨てる。
 * まとまりは重なる：sources はテストの補助を含み、tests と testsWithSupport は画面のテストを含む。
 *
 * @param {string[]} files リポジトリのルートからのパス
 * @returns {{ sources: string[], frontendTests: string[], testSupport: string[], e2e: string[], tests: string[], testsWithSupport: string[], testsWithSupportAndE2e: string[] }}
 *   sources は画面のソース（テストを除く。テストの補助を含む）、frontendTests は画面の単体・結合テスト、testSupport はテストの補助、e2e は E2E、
 *   tests は画面のテストと E2E、testsWithSupport は画面のテストとテストの補助（E2E を除く）、
 *   testsWithSupportAndE2e は画面のテストとテストの補助と E2E（src/test/ の下の .test.ts は 1 回だけ入る。ファイルごとに 1 回報告する検査用）
 */
export function groupTargets(files) {
  const pick = (matches) => files.filter(matches)
  const isFrontendTest = (file) => FRONTEND_TEST.test(file)
  const isTestSupport = (file) => FRONTEND_TEST_SUPPORT.test(file)
  const isE2e = (file) => E2E_FILE.test(file)
  return {
    sources: pick((file) => SOURCE.test(file) && !isFrontendTest(file)),
    frontendTests: pick(isFrontendTest),
    testSupport: pick(isTestSupport),
    e2e: pick(isE2e),
    tests: pick((file) => isFrontendTest(file) || isE2e(file)),
    testsWithSupport: pick((file) => isFrontendTest(file) || isTestSupport(file)),
    testsWithSupportAndE2e: pick((file) => isFrontendTest(file) || isTestSupport(file) || isE2e(file)),
  }
}

/**
 * 店舗画面と管理画面に、同じ相対パスで同じ内容のファイルを探す。
 *
 * @param {string[]} files リポジトリのルートからのパス
 * @param {(file: string) => string} read ファイルの中身を読む関数
 * @param {string[]} excluded 見ない src からの相対パス
 * @returns {string[]} 同じ内容のファイルの src からの相対パス（名前の順）
 */
export function duplicatedFiles(files, read, excluded) {
  const prefix = (app) => `frontend/${app}/src/`
  const storeFiles = new Set(files.filter((file) => file.startsWith(prefix('store'))).map((file) => file.slice(prefix('store').length)))
  return files
    .filter((file) => file.startsWith(prefix('admin')))
    .map((file) => file.slice(prefix('admin').length))
    .filter((relative) => storeFiles.has(relative) && !excluded.includes(relative))
    .filter((relative) => read(prefix('admin') + relative) === read(prefix('store') + relative))
    .sort()
}

/** 行頭の export function・const・class（async も）から、公開する名前 */
const EXPORT_DECLARATION = /^export\s+(?:async\s+)?(?:function\*?|const|class)\s+([\w$]+)/gm

/** export { … }（export { … } from '…' も）の波括弧の中 */
const EXPORT_LIST = /^export\s*\{([^}]*)\}/gm

/**
 * ソースが公開する名前を読む。
 * 読む書き方は、export function・const・class と export { a, b as c }（from 付きも）。型（export type、{ type X }）と default は、値の名前として使い分ける対象ではないので読まない。
 * 今の共有のパッケージはこの書き方だけで公開しているため、export * や再 export の連鎖は追わない
 *
 * @param {string} text ソース
 * @returns {string[]} 公開する名前（出てきた順）
 */
export function findExportedNames(text) {
  const hits = [...text.matchAll(EXPORT_DECLARATION)].map((match) => ({ index: match.index, names: [match[1]] }))
  for (const match of text.matchAll(EXPORT_LIST)) {
    const names = match[1]
      .split(',')
      .map((item) => item.trim())
      .filter((item) => item && !item.startsWith('type '))
      .map((item) => item.split(/\s+as\s+/).pop())
    hits.push({ index: match.index, names })
  }
  return hits.sort((a, b) => a.index - b.index).flatMap((hit) => hit.names)
}

/**
 * 共有のパッケージが公開する名前を集める。
 * package.json の exports の値（./src/lib/*.ts など。* は 1 つ以内）に合うソースのファイルを読む。テストは公開しないので読まない。
 * exports にない src のファイルは、パッケージの外から import できないので読まない
 *
 * @param {Record<string, string>} exportsMap shared の package.json の exports
 * @param {string[]} files リポジトリのルートからのパス
 * @param {(file: string) => string} read ファイルの中身を読む関数
 * @returns {Set<string>} 公開する名前
 */
export function sharedPublicNames(exportsMap, files, read) {
  const patterns = Object.values(exportsMap)
    .filter((target) => typeof target === 'string')
    .map((target) => {
      const escaped = target.replace(/^\.\//, 'frontend/shared/').replace(/[.+?^${}()|[\]\\]/g, '\\$&')
      return new RegExp(`^${escaped.replace('*', '.*')}$`)
    })
  const names = new Set()
  for (const file of files) {
    if (ANY_TEST_FILE.test(file) || !/\.tsx?$/.test(file) || !patterns.some((pattern) => pattern.test(file))) continue
    for (const name of findExportedNames(read(file))) names.add(name)
  }
  if (names.size === 0) {
    throw new Error('共有のパッケージが公開する名前を 1 件も読めない（frontend/shared/package.json の exports の形か、export の書き方が変わった。検査を直す）')
  }
  return names
}

/**
 * 指定した名前の関数・定数・クラスを定義する行を探す（export の有無によらない）。
 * 行頭から始まる定義だけを見る。関数の中の同じ名前の変数は、共有のものと取り違えないので対象にしない
 *
 * @param {string} text ソース
 * @param {Set<string>} names 探す名前
 * @returns {{ line: number, text: string, name: string }[]} 見つけた定義
 */
export function findDefinitions(text, names) {
  const definition = /^(?:export\s+)?(?:async\s+)?(?:function\*?|const|class)\s+([\w$]+)/
  return text
    .split('\n')
    .map((line, index) => ({ line: index + 1, text: line.trim(), name: line.match(definition)?.[1] }))
    .filter((hit) => hit.name !== undefined && names.has(hit.name))
}

/**
 * 行の番号と表示する文字（元のソースの行）にする。
 *
 * @param {string} text 元のソース
 * @param {number[]} lineNumbers 行の番号（1 始まり）
 * @returns {{ line: number, text: string }[]} 行の順、重複なし
 */
function toHits(text, lineNumbers) {
  const original = text.split('\n')
  return [...new Set(lineNumbers)].sort((a, b) => a - b).map((line) => ({ line, text: original[line - 1].trim() }))
}

/** getAllBy…・findAllBy…・queryAllBy… の呼び出しの始まり */
const MULTIPLE_QUERY = /\b(?:getAll|findAll|queryAll)By\w+\(/g

/** 呼び出しの後の、結果をそのまま持つ書き方（空白、閉じ括弧、!、;、as の型の変換）。`)` は await を囲む括弧とスプレッド・Array.from の閉じ */
const WRAPPER_TAIL = /^(?:\s|[)\]!;]|\bas\s+(?:readonly\s+)?[\w$.]+(?:<[^<>]*>)?(?:\[\])*)*/

/** 呼び出しの直後（WRAPPER_TAIL の後）の、並びのインデックス（[数字]、.at(数字)。?. を許す） */
const INDEX_ACCESS = /^(?:\?\.)?(?:\[\s*-?\d+\s*\]|\.at\(\s*-?\d+\s*\))/

/** 呼び出しの前の、結果をそのまま持つ書き方（await、括弧、スプレッド、Array.from(、screen.・within(…).・rtl.screen. などの受け手） */
const CALL_PREFIX = String.raw`(?:\s|\(|await\b|\[\s*\.\.\.|Array\s*\.\s*from\s*\(|within\s*\((?:[^()]|\((?:[^()]|\([^()]*\))*\))*\)\s*\.|[\w$]+\s*\.)*`

/**
 * 変数に入れる文（const items = await screen.getAllByRole(…)、const items: T[] = …、let の後の items = … など）の、呼び出しの前の部分。
 * 1 つ目が const・let・var の名前、2 つ目が配列の分割代入、3 つ目がオブジェクトの分割代入、4 つ目が宣言のない代入の名前
 */
const ASSIGNMENT_BEFORE = new RegExp(
  String.raw`(?:\b(?:const|let|var)\s+(?:([\w$]+)|(\[[^\]]*\])|(\{[^}]*\}))(?:\s*:[^=;]+?)?|(?<![.\w$])([\w$]+))\s*=(?![=>])${CALL_PREFIX}$`,
)

/** 呼び出しの前を遡って調べる字数 */
const ASSIGNMENT_LOOKBEHIND = 600

/** 結果の後に式が続く（変数に入れる前に変換する）ことを表す、後ろの始まり */
const CONTINUES = /^(?:\?\.|\.|\(|\[|&&|\|\||\?|:|\+|\*|\/|%|\||&|==|!=)/

/**
 * 名前を、同じ名前の別の宣言（const・let・var、分割代入、関数の引数、アロー関数の引数）で上書きする書き方。
 *
 * @param {string} name 変数の名前
 * @returns {RegExp} 上書きの宣言を探す正規表現
 */
function shadowingPattern(name) {
  const id = `(?<![\\w$])${name.replace(/\$/g, '\\$')}(?![\\w$])`
  return new RegExp(
    `\\b(?:const|let|var)\\s+(?:${id}|\\{[^}]*${id}[^}]*\\}|\\[[^\\]]*${id}[^\\]]*\\])|\\bfunction\\b[^(]*\\([^)]*${id}|\\([^()]*${id}[^()]*\\)\\s*(?::[^=()]*)?=>|${id}\\s*=>`,
  )
}

/**
 * 位置から後ろで、その位置を含むブロックが閉じる位置を返す。
 *
 * @param {string} code ソース（コメントと文字列を空白にしたもの）
 * @param {number} from 始まりの位置
 * @returns {number} 閉じる } の位置。なければソースの終わり
 */
function blockEnd(code, from) {
  let depth = 0
  return scanCode(code, { start: from }, (type, at) => {
    if (type !== 'code') return false
    if (code[at] === '{') depth += 1
    else if (code[at] === '}') {
      depth -= 1
      return depth < 0
    }
    return false
  })
}

/**
 * getAllBy…・findAllBy…・queryAllBy… の結果を、並びのインデックスで選ぶ所を探す。
 * 規則は、フロントのテストの書き方の「画面に見えるもので探す」。並びの 1 つ目は使わない。
 * 呼び出しの直後の [数字]・.at(数字) と、変数に入れて（配列の分割代入を含む）から [数字]・.at(数字) で選ぶ所を見つける。
 * 変数に入れる形：const・let・var の宣言と、宣言のない代入。= の後の改行、await、括弧、as の型の変換、型の注釈、
 * 受け手（screen.、within(…).）、スプレッド（[...x]）、Array.from(x) をはさんでもよい。
 * 変数の使用は、宣言した所より後で、同じ名前が別の宣言（const・let・var、分割代入、引数）で上書きされるまで、
 * const・let・var の宣言ならそのブロックが閉じるまでの範囲だけ見る（別のテストの同じ名前の普通の配列は見ない）。
 * 見つけない形：変数に入れる前に map・filter などをつなぐもの、関数の引数や戻り値で渡して選ぶもの。
 * Playwright の .first()・.nth() は対象にしない
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findIndexedMultiples(text) {
  const code = stripComments(text, { blankStrings: true })
  const lineOf = createLineLocator(code)
  const lineNumbers = []
  for (const match of code.matchAll(MULTIPLE_QUERY)) {
    const close = closingParen(code, match.index + match[0].length - 1)
    if (close >= code.length) continue
    const after = code.slice(close + 1)
    const wrapped = after.slice(WRAPPER_TAIL.exec(after)[0].length)
    if (INDEX_ACCESS.test(wrapped)) {
      lineNumbers.push(lineOf(match.index))
      continue
    }
    if (CONTINUES.test(wrapped)) continue
    const windowStart = Math.max(0, match.index - ASSIGNMENT_LOOKBEHIND)
    const assignment = ASSIGNMENT_BEFORE.exec(code.slice(windowStart, match.index))
    if (!assignment) continue
    if (assignment[2] || (assignment[3] && /^\{\s*\d+\s*:/.test(assignment[3]))) {
      lineNumbers.push(lineOf(match.index))
      continue
    }
    const name = assignment[1] ?? assignment[4]
    if (name === undefined) continue
    const limit = assignment[1] === undefined ? code.length : blockEnd(code, close + 1)
    const scope = code.slice(close + 1, limit)
    const shadow = shadowingPattern(name).exec(scope)
    const range = shadow ? scope.slice(0, shadow.index) : scope
    const use = new RegExp(`(?<![.\\w$])${name.replace(/\$/g, '\\$')}(?:\\?\\.|!)?(?:\\[\\s*-?\\d+\\s*\\]|\\.at\\(\\s*-?\\d+\\s*\\))`, 'g')
    for (const usage of range.matchAll(use)) lineNumbers.push(lineOf(close + 1 + usage.index))
  }
  return toHits(text, lineNumbers)
}

/**
 * 式の文の終わりの位置を返す（深さ 0 の ; か、続きのない改行。続きは、行末の演算子や = か、次の行の始まりの . ? : && || + as）。
 *
 * @param {string} code ソース（コメントと文字列を空白にしたもの）
 * @param {number} from 式の始まりの位置
 * @returns {number} 文の終わりの位置。なければソースの終わり
 */
function statementEnd(code, from) {
  const start = from + /^\s*/.exec(code.slice(from))[0].length
  let depth = 0
  let end = code.length
  scanCode(code, { start }, (type, at) => {
    if (type !== 'code') return false
    const char = code[at]
    if ('([{'.includes(char)) depth += 1
    else if (')]}'.includes(char)) {
      depth -= 1
      if (depth < 0) {
        end = at
        return true
      }
    } else if (depth === 0 && char === ';') {
      end = at
      return true
    } else if (depth === 0 && char === '\n') {
      const before = code.slice(start, at).trimEnd()
      const next = code.slice(at).trimStart()
      if (!/[=+\-*/&|?:,.(]$/.test(before) && !/^(?:\?\.|\.|\?|:|&&|\|\||\+|as\b)/.test(next)) {
        end = at
        return true
      }
    }
    return false
  })
  return end
}

/** expect( の始まり */
const EXPECT_CALL = /(?<![.\w$])expect\(/g

/** expect の後の、toBe・toEqual（.not などをはさんでもよい） */
const BE_OR_EQUAL = /^\s*(?:\.(?:not|resolves|rejects)\s*)*\.(?:toBe|toEqual)\(/

/** expect の引数の最後が、要素のプロパティか属性（.disabled、.checked、.value、.getAttribute(…)。?. も）。その前が受け手 */
const PROPERTY_TAIL = /\??\.(?:disabled|checked|value)$|\??\.getAttribute\((?:[^()]|\((?:[^()]|\([^()]*\))*\))*\)$/

/** 要素を返す式（getBy…・queryBy…・findBy…、screen.、within(、document.・container. からたどる形） */
const ELEMENT_SOURCE = /\b(?:get|query|find)(?:All)?By\w+\(|(?<![.\w$])screen\s*\.|\bwithin\s*\(|(?<![.\w$])(?:document|container)\s*\./

/** 要素の型（HTMLInputElement など） */
const ELEMENT_TYPE = /\bHTML\w*Element\b/

/**
 * 変数が要素かを、その変数の直前の宣言から判定する。
 * const・let・var の宣言は、型の注釈が要素の型か、初期値が要素を返す式か要素の型への変換であるもの。引数などの `名前: HTMLInputElement` の型の注釈も要素とする
 *
 * @param {string} code ソース（コメントと文字列を空白にしたもの）
 * @param {string} name 変数の名前
 * @param {number} before この位置より前の宣言を見る
 * @returns {boolean} 要素なら true
 */
function isElementVariable(code, name, before) {
  const id = name.replace(/\$/g, '\\$')
  const declaration = new RegExp(`\\b(?:const|let|var)\\s+${id}(?![\\w$])|(?<![\\w$.])${id}\\s*\\??:\\s*(?:Readonly<)?HTML\\w*Element\\b`, 'g')
  let last = null
  for (const match of code.slice(0, before).matchAll(declaration)) last = match
  if (!last) return false
  if (!/^(?:const|let|var)/.test(last[0])) return true
  const rest = code.slice(last.index + last[0].length)
  const assign = /^\s*(?::[^=;]*?)?=(?![=>])/.exec(rest)
  if (!assign) return false
  if (ELEMENT_TYPE.test(assign[0])) return true
  const init = code.slice(last.index + last[0].length + assign[0].length, statementEnd(code, last.index + last[0].length + assign[0].length))
  return ELEMENT_SOURCE.test(init) || ELEMENT_TYPE.test(init)
}

/**
 * 要素のプロパティ・属性（.disabled、.checked、.value、getAttribute(…)）を toBe・toEqual で比べる所を探す。
 * 規則は、フロントのテストの書き方の「要素のプロパティ・属性・状態を jest-dom で確認する」。
 * expect の引数の最後がそのプロパティか属性であるもので、受け手が要素のものだけを見つける。受け手が要素なのは、
 * getBy…・queryBy…・findBy…・screen.・within( の結果か、それを入れた変数（要素の型の注釈や変換も）、document.・container. からたどる形。
 * `parts.map((part) => part.value)` のように途中に出てくるものと、`result.current.value` のように要素でない値は見つけない。
 * .textContent は対象にしない
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findPropertyComparisons(text) {
  const code = stripComments(text, { blankStrings: true })
  const lineOf = createLineLocator(code)
  const lineNumbers = []
  for (const match of code.matchAll(EXPECT_CALL)) {
    const open = match.index + match[0].length - 1
    const close = closingParen(code, open)
    if (close >= code.length || !BE_OR_EQUAL.test(code.slice(close + 1))) continue
    const argument = code.slice(open + 1, close).trim().replace(/,$/, '').trim()
    const tail = PROPERTY_TAIL.exec(argument)
    if (!tail) continue
    const receiver = argument.slice(0, tail.index)
    const simple = receiver
      .replace(/\bas\s+[\w$.<>[\]]+/g, '')
      .replace(/\.at\([^)]*\)|\[[^\]]*\]/g, '')
      .replace(/[()!?\s]/g, '')
    const isElement = /^[\w$]+$/.test(simple) ? ELEMENT_TYPE.test(receiver) || isElementVariable(code, simple, match.index) : ELEMENT_SOURCE.test(receiver) || ELEMENT_TYPE.test(receiver)
    if (isElement) lineNumbers.push(lineOf(match.index))
  }
  return toHits(text, lineNumbers)
}

/** テストの呼び出しを閉じる行（`})`、`}, 30_000)`、`});`）。コメントと文字列を除いた行に当てる */
const CALL_CLOSE_LINE = /^\}.*\)[;,]?$/

/**
 * テスト・グループの呼び出しの始まりの行（test、it、describe と、test.each(…)、describe.skip(…)、test.describe(… など）。
 * `.` の後はテスト・グループの形に限る。test.beforeEach・test.use・test.step・test.info・test.setTimeout などは含めない（await 付きの test.step も含めない）
 */
const TEST_CALL_START = /^(?:test|it|describe)(?:\.(?:each|skip|only|fixme|fail|todo|concurrent|describe|serial|parallel))*\s*[(`]/

/** 1 行で閉じるテストの呼び出しの終わり（`)`、`);`） */
const CALL_ONE_LINE_END = /\)[;,]?$/

/**
 * 1 行で閉じる、テスト・グループの呼び出しかを返す（test('a', () => {}) のように、始まりの行で括弧が閉じるもの）。
 *
 * @param {string} line コメントと文字列を除いた行（前後の空白を除く）
 * @returns {boolean} 1 行で閉じていれば true
 */
function isOneLineTestCall(line) {
  if (!TEST_CALL_START.test(line) || !CALL_ONE_LINE_END.test(line)) return false
  let depth = 0
  for (const char of line) {
    if (char === '(') depth += 1
    else if (char === ')') depth -= 1
  }
  return depth === 0
}

/**
 * テストの間に空行がない箇所を探す（フロントのテストの書き方の「テストとテストの間に空行を置く」）。
 * テストを閉じる行（`})` と、1 行で閉じるテストの行）の直後に、空行なしで test・it・describe が続く箇所。間にコメントの行があっても、空行がなければ見つける。
 * test.beforeEach・test.use・test.step などはテスト・グループでないので、続く側として見ない。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行（続く側の test・it・describe の行）
 */
export function findMissingTestSpacing(text) {
  const original = text.split('\n')
  const code = stripComments(text, { blankStrings: true }).split('\n')
  const hits = []
  code.forEach((line, index) => {
    if (!CALL_CLOSE_LINE.test(line.trim()) && !isOneLineTestCall(line.trim())) return
    let next = index + 1
    while (next < code.length && code[next].trim() === '' && original[next].trim() !== '') next += 1
    if (next < code.length && TEST_CALL_START.test(code[next].trim())) hits.push({ line: next + 1, text: original[next].trim() })
  })
  return hits
}

/**
 * 括弧の中の引数や、オブジェクトの項目を、深さ 0 のカンマで分ける。
 *
 * @param {string} code 文字列を空白にしたソース（括弧とカンマが文字列の中にない）
 * @param {string} plain code と同じ位置で、文字列を残したソース（空の部分かの判定に使う）
 * @param {number} from 始まりの位置
 * @param {number} to 終わりの位置（含まない）
 * @returns {[number, number][]} 部分の範囲（始まりと終わりの次の位置。末尾のカンマの後の空の部分は除く）
 */
function splitTopLevel(code, plain, from, to) {
  const parts = []
  let depth = 0
  let start = from
  for (let i = from; i < to; i += 1) {
    if ('([{'.includes(code[i])) depth += 1
    else if (')]}'.includes(code[i])) depth -= 1
    else if (code[i] === ',' && depth === 0) {
      parts.push([start, i])
      start = i + 1
    }
  }
  parts.push([start, to])
  // 末尾のカンマの後の、空の部分を除く（文字列は code では空白なので、plain で判定する）
  const [last] = parts.slice(-1)
  return parts.length > 1 && plain.slice(...last).trim() === '' ? parts.slice(0, -1) : parts
}

/** getByRole の呼び出しの始まり */
const GET_BY_ROLE = /\bgetByRole\s*\(/g

/** 先頭の ^ と末尾の $ を固定した正規表現のリテラル（フラグを許す。最上位の選択 | は isAnchoredRegex が別に見る） */
const ANCHORED_REGEX = /^\/\^([\s\S]*)\$\/[a-z]*$/

/** オプションの name のキー（name、'name'、"name"、['name']）と、後ろの : */
const NAME_KEY = /^(?:name|(['"`])name\1|\[\s*(['"`])name\2\s*\])\s*:/

/**
 * 正規表現のリテラルが、先頭の ^ と末尾の $ で固定されているかを返す。
 * 末尾の $ がエスケープされていると（/^合計\$/）、文字の $ に一致するだけで行末を固定していないので固定とみなさない（\\$ の $ は固定）。
 * 最上位に | があると（/^a|b$/）、^a と b$ の選択で部分一致になるので固定とみなさない。グループ・文字クラス・エスケープの中の | は選択でない。
 *
 * @param {string} literal 正規表現のリテラル
 * @returns {boolean} 固定していれば true
 */
function isAnchoredRegex(literal) {
  const match = ANCHORED_REGEX.exec(literal)
  if (!match) return false
  const body = match[1]
  // 末尾の $ の前の \ が奇数個なら、その $ はエスケープされた文字そのもので、行末を固定していない
  if ((/\\*$/.exec(body)[0].length & 1) === 1) return false
  let depth = 0
  let inClass = false
  for (let i = 0; i < body.length; i += 1) {
    const char = body[i]
    if (char === '\\') i += 1
    else if (inClass) inClass = char !== ']'
    else if (char === '[') inClass = true
    else if (char === '(') depth += 1
    else if (char === ')') depth -= 1
    else if (char === '|' && depth === 0) return false
  }
  return true
}

/**
 * E2E で、ボタンを名前で探すのに、完全一致でない箇所を探す（フロントのテストの書き方の「画面に見えるもので探す」）。
 * name が正規表現のリテラルなら、先頭の ^ と末尾の $ を固定していること。それ以外（文字列、変数、テンプレート文字列）なら exact: true が付いていること。
 * 役割が button の getByRole だけを見る（row.getByRole(…) のような連鎖の途中、複数の行にまたがる呼び出しも読む）。
 * name のない呼び出しは名前で探さないので対象にしない。オプションがオブジェクトのリテラルでないとき、
 * または展開（...）を含み name を読めないときは、名前を読めないので見つける。
 * name に正規表現を入れた変数を渡す書き方は追わない（変数の名前は文字列にし、exact: true を付ける。フロントのテストの書き方の「画面に見えるもので探す」）
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行（getByRole の行）
 */
export function findButtonNameChecks(text) {
  // 構造を読むための、文字列と正規表現を空白にしたソースと、値を読むための、文字列と正規表現を残したソース（位置は同じ）
  const code = stripComments(text, { blankStrings: true })
  const plain = stripComments(text, { keepRegex: true })
  const lineOf = createLineLocator(code)
  const lineNumbers = []
  for (const match of code.matchAll(GET_BY_ROLE)) {
    const open = match.index + match[0].length - 1
    const close = closingParen(code, open)
    if (close >= code.length) continue
    const [role, options] = splitTopLevel(code, plain, open + 1, close)
    if (!/^(['"`])button\1$/.test(plain.slice(...role).trim()) || !options) continue
    const optionsCode = code.slice(...options).trim()
    if (!optionsCode.startsWith('{') || !optionsCode.endsWith('}')) {
      lineNumbers.push(lineOf(match.index))
      continue
    }
    const objectFrom = options[0] + code.slice(...options).indexOf('{')
    const objectTo = options[0] + code.slice(...options).trimEnd().length - 1
    let nameKind = null
    let exact = false
    let hasSpread = false
    for (const item of splitTopLevel(code, plain, objectFrom + 1, objectTo)) {
      const masked = code.slice(...item).trim()
      const value = plain.slice(...item).trim()
      const nameKey = NAME_KEY.exec(value)
      if (nameKey || value === 'name') {
        const nameValue = nameKey ? value.slice(nameKey[0].length).trim() : null
        if (nameValue?.startsWith('/')) nameKind = isAnchoredRegex(nameValue) ? 'anchored' : 'regex'
        else nameKind = 'other'
      } else if (/^exact\s*:\s*true$/.test(masked)) exact = true
      else if (masked.startsWith('...')) hasSpread = true
    }
    if (nameKind === null && hasSpread) lineNumbers.push(lineOf(match.index))
    if (nameKind === null || nameKind === 'anchored') continue
    if (nameKind === 'regex' || !exact) lineNumbers.push(lineOf(match.index))
  }
  return toHits(text, lineNumbers)
}

/** タイムアウトの定数を定義するファイル。数字のリテラルを書いてよい */
const TIMEOUT_CONSTANTS_FILE = 'e2e/src/waits.ts'

/**
 * タイムアウトの数字の検査を当てるファイルに絞る（タイムアウトの定数を定義するファイルを除く）。
 *
 * @param {string[]} files E2E のファイル
 * @returns {string[]} 検査するファイル（順は保つ）
 */
export function timeoutTargets(files) {
  return files.filter((file) => file !== TIMEOUT_CONSTANTS_FILE)
}

/** timeout を含む項目（timeout、actionTimeout、timeoutMs、navigationTimeoutMs など）と intervals の項目 */
const TIMEOUT_PROPERTY = /\b(?:[\w$]*timeout[\w$]*|intervals)\s*:(?!:)/gi

/** 引用符で囲んだ timeout を含む項目と intervals の項目（文字列を残したソースに当てる） */
const QUOTED_TIMEOUT_PROPERTY = /(['"`])(?:[\w$]*timeout[\w$]*|intervals)\1\s*:(?!:)/gi

/** setTimeout・test.setTimeout・setDefaultTimeout・setDefaultNavigationTimeout の呼び出しの始まり */
const SET_TIMEOUT = /\b(?:setTimeout|setDefaultTimeout|setDefaultNavigationTimeout)\s*\(/g

/** 型の注釈つきのデフォルト引数の値（timeout: number = 5000 の `number = 5000`）。項目の値でないので見ない */
const TYPE_ANNOTATED_DEFAULT = /^\s*[\w$.<>[\]|& ]+?(?<![<>!=])=(?![=>])/

/** 数字のリテラルの始まり（識別子の一部の数字は除く） */
const NUMBER_LITERAL = /(?<![\w$.])\d/

/**
 * 式の終わりの位置を返す（深さ 0 のカンマか、囲んでいる括弧の閉じ）。
 *
 * @param {string} code 文字列を空白にしたソース
 * @param {number} from 式の始まりの位置
 * @returns {number} 式の終わりの位置。なければソースの終わり
 */
function expressionEnd(code, from) {
  let depth = 0
  for (let i = from; i < code.length; i += 1) {
    if ('([{'.includes(code[i])) depth += 1
    else if (')]}'.includes(code[i])) {
      if (depth === 0) return i
      depth -= 1
    } else if ((code[i] === ',' || code[i] === ';') && depth === 0) return i
  }
  return code.length
}

/**
 * E2E で、タイムアウトを数字のリテラルで書く行を探す（テストの書き方の「待ち方」。e2e/src/waits.ts の定数を使う）。
 * timeout を含む項目（timeout、actionTimeout、navigationTimeout など。引用符で囲んだキーを含む）と intervals の項目の値、
 * setTimeout・test.setTimeout・setDefaultTimeout・setDefaultNavigationTimeout の引数（引数が複数なら最後の引数。global の setTimeout は最後が待つ時間）に、
 * 数字のリテラルを含む式（10_000、5 * 1000、ASYNC_TIMEOUT * 2 など）を見つける。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findTimeoutLiterals(text) {
  const code = stripComments(text, { blankStrings: true })
  const plain = stripComments(text)
  const lineOf = createLineLocator(code)
  const lineNumbers = []
  for (const match of code.matchAll(TIMEOUT_PROPERTY)) {
    const from = match.index + match[0].length
    const value = code.slice(from, expressionEnd(code, from))
    if (code.slice(0, match.index).trimEnd().endsWith('?') || TYPE_ANNOTATED_DEFAULT.test(value)) continue
    if (NUMBER_LITERAL.test(value)) lineNumbers.push(lineOf(match.index))
  }
  for (const match of plain.matchAll(QUOTED_TIMEOUT_PROPERTY)) {
    // 文字列の中の文字（"a 'timeout': 5" など）は、直前が { か , のときだけキーとみなす
    const before = plain.slice(0, match.index).trimEnd().at(-1)
    if (code[match.index] !== ' ' || (before !== '{' && before !== ',')) continue
    const from = match.index + match[0].length
    if (NUMBER_LITERAL.test(code.slice(from, expressionEnd(code, from)))) lineNumbers.push(lineOf(match.index))
  }
  for (const match of code.matchAll(SET_TIMEOUT)) {
    const open = match.index + match[0].length - 1
    const close = closingParen(code, open)
    if (close >= code.length) continue
    const target = splitTopLevel(code, plain, open + 1, close).at(-1)
    if (target && NUMBER_LITERAL.test(code.slice(...target))) lineNumbers.push(lineOf(match.index))
  }
  return toHits(text, lineNumbers)
}

/** 組み込みの名前の一覧（TypeScript の lib から生成する。scripts/ci/globals/lib-globals.mjs） */
const BUILTIN_NAMES_FILE = 'scripts/ci/globals/lib-globals.json'

/** const・let の宣言の始まり（as const の const と、プロパティの const は除く） */
const DECLARATION_KEYWORD = /(?<![\w$.])(?:const|let)\s+/g

/** 宣言の名前の後ろに続いてよいもの（初期値、型の注釈、次の宣言、文の終わり、for…of・for…in、行の終わり） */
const BINDING_FOLLOWER = /^[ \t]*(?:[=,:;)}]|of\b|in\b|\r?\n|$)/

/** 変数の名前 */
const IDENTIFIER_ONLY = /^[\w$]+$/

/**
 * 深さ 0 にある、初めての文字の位置を返す（括弧の中は数えない）。= は、=>・==・<=・>=・!= の一部でないものだけを数える。
 *
 * @param {string} code 文字列を空白にしたソース
 * @param {string} char 探す文字
 * @returns {number} 位置。なければ -1
 */
function topLevelIndexOf(code, char) {
  let depth = 0
  for (let i = 0; i < code.length; i += 1) {
    if ('([{'.includes(code[i])) depth += 1
    else if (')]}'.includes(code[i])) depth -= 1
    else if (depth === 0 && code[i] === char) {
      if (char === '=' && (/[=>]/.test(code[i + 1] ?? '') || /[=!<>]/.test(code[i - 1] ?? ''))) continue
      return i
    }
  }
  return -1
}

/**
 * 分割代入の対象（名前か入れ子の分割代入。後ろに既定値があってもよい）が宣言する名前を返す。
 *
 * @param {string} target 対象
 * @returns {string[]} 名前
 */
function targetNames(target) {
  const equals = topLevelIndexOf(target, '=')
  const value = (equals >= 0 ? target.slice(0, equals) : target).trim()
  if (value.startsWith('{') || value.startsWith('[')) return patternNames(value)
  return IDENTIFIER_ONLY.test(value) ? [value] : []
}

/**
 * 分割代入のパターン（{ … } か [ … ]）が宣言する名前を返す。
 * オブジェクトの { a: b } は右側の b が宣言される名前で、{ a } は a、{ ...rest } は rest。配列は全ての要素を見る。
 *
 * @param {string} pattern 括弧を含むパターン
 * @returns {string[]} 名前
 */
function patternNames(pattern) {
  const isObject = pattern.startsWith('{')
  const inner = pattern.slice(1, -1)
  const parts = []
  let depth = 0
  let start = 0
  for (let i = 0; i <= inner.length; i += 1) {
    if (i === inner.length || (inner[i] === ',' && depth === 0)) {
      parts.push(inner.slice(start, i).trim())
      start = i + 1
    } else if ('([{'.includes(inner[i])) depth += 1
    else if (')]}'.includes(inner[i])) depth -= 1
  }
  return parts.flatMap((part) => {
    if (part === '') return []
    if (part.startsWith('...')) return targetNames(part.slice(3))
    if (!isObject) return targetNames(part)
    const colon = topLevelIndexOf(part, ':')
    const equals = topLevelIndexOf(part, '=')
    return colon >= 0 && (equals < 0 || colon < equals) ? targetNames(part.slice(colon + 1)) : targetNames(part)
  })
}

/**
 * 宣言の名前（名前か分割代入のパターン）を、位置から読む。
 *
 * @param {string} code 文字列を空白にしたソース
 * @param {number} from 始まりの位置
 * @returns {{ names: string[], start: number, end: number } | null} 名前、始まりと終わり（含まない）の位置。名前の形でなければ null
 */
function readBinding(code, from) {
  const start = from + /^\s*/.exec(code.slice(from))[0].length
  let end = start
  if (code[start] === '{' || code[start] === '[') {
    let depth = 0
    for (end = start; end < code.length; end += 1) {
      if ('([{'.includes(code[end])) depth += 1
      else if (')]}'.includes(code[end])) {
        depth -= 1
        if (depth === 0) break
      }
    }
    if (end >= code.length) return null
    end += 1
    return BINDING_FOLLOWER.test(code.slice(end)) ? { names: patternNames(code.slice(start, end)), start, end } : null
  }
  end = start + (/^[\w$]*/.exec(code.slice(start))[0].length)
  if (end === start || !BINDING_FOLLOWER.test(code.slice(end))) return null
  return { names: [code.slice(start, end)], start, end }
}

/**
 * 名前の後ろの型の注釈（: の後ろ）を読み飛ばし、初期値の = か、次の宣言の , か、文の終わりの位置を返す。
 *
 * @param {string} code 文字列を空白にしたソース
 * @param {number} from 名前の終わりの位置
 * @returns {number} 位置
 */
function skipAnnotation(code, from) {
  let at = from + /^[ \t]*/.exec(code.slice(from))[0].length
  if (code[at] !== ':') return at
  let depth = 0
  for (at += 1; at < code.length; at += 1) {
    const char = code[at]
    const arrow = char === '>' && code[at - 1] === '='
    if ('([{<'.includes(char)) depth += 1
    else if (')]}>'.includes(char) && !arrow) depth -= 1
    else if (depth <= 0 && (char === ',' || char === ';' || char === '\n' || (char === '=' && code[at + 1] !== '>'))) break
  }
  return at
}

/**
 * ジェネリクスの型引数 `<…>` の閉じの位置を返す。呼び出し側は、`<` が識別子の直後にあるものだけを渡す
 * （比較の `a < b` は演算子の前後に空白を置くので渡されない）。`=>` の `>` は数えない。対応する `>` が文の終わりまでになければ、比較とみなす。
 *
 * @param {string} code 文字列を空白にしたソース
 * @param {number} open `<` の位置
 * @param {number} end 文の終わりの位置
 * @returns {number} 対応する `>` の位置。なければ -1
 */
function genericsEnd(code, open, end) {
  let depth = 0
  for (let i = open; i < end; i += 1) {
    if (code[i] === '<') depth += 1
    else if (code[i] === '>' && code[i - 1] !== '=') {
      depth -= 1
      if (depth === 0) return i
    }
  }
  return -1
}

/**
 * `<` がジェネリクスの型引数の始まりかを返す。識別子の直後（new Map<string, number>）か、
 * `=` か `(` の直後（const f = <T,>(a: T) => a の型引数を持つ矢印関数）にあるものを、型引数とみなす。
 *
 * @param {string} code 文字列を空白にしたソース
 * @param {number} open `<` の位置
 * @returns {boolean} 型引数の始まりなら true
 */
function opensGenerics(code, open) {
  if (/[\w$]/.test(code[open - 1] ?? '')) return true
  const before = code.slice(0, open).trimEnd().at(-1)
  return before === '=' || before === '('
}

/**
 * 初期値の後ろの、次の宣言の始まり（深さ 0 の , の次の位置）を返す。
 *
 * @param {string} code 文字列を空白にしたソース
 * @param {number} from 初期値の始まりの位置
 * @returns {number} 次の宣言の始まりの位置。なければ -1
 */
function nextDeclarator(code, from) {
  const end = statementEnd(code, from)
  let depth = 0
  for (let i = from; i < end; i += 1) {
    if (code[i] === '<' && opensGenerics(code, i)) {
      const close = genericsEnd(code, i, end)
      if (close > i) i = close
    } else if ('([{'.includes(code[i])) depth += 1
    else if (')]}'.includes(code[i])) depth -= 1
    else if (depth === 0 && code[i] === ',') return i + 1
  }
  return -1
}

/**
 * フロントのテストと E2E で、組み込みの名前と同じ名前の const・let の宣言を探す。組み込みの名前は、TypeScript の lib が宣言するグローバルの名前である。
 * 規則は、フロントのテストの書き方の「組み込みの名前をシャドーイングしない」。エラーにならずにグローバル変数の値が使われるのを防ぐ。
 * 対象は、const・let の宣言の名前（1 つの宣言に並べたもの、分割代入、for…of・for…in の宣言を含む）。
 * 関数の引数、catch の変数、関数・クラスの宣言、import は対象にしない。
 * 分割代入の { a: name } は右側の name が宣言される名前で、{ name: x } は x。
 *
 * @param {string} text ソース
 * @param {Set<string>} names 組み込みの名前
 * @returns {{ line: number, text: string, name: string }[]} 見つけた宣言（名前ごと。行は名前か、分割代入の始まりの行）
 */
export function findBuiltinShadowing(text, names) {
  const code = stripComments(text, { blankStrings: true })
  const lineOf = createLineLocator(code)
  const original = text.split('\n')
  const hits = []
  for (const match of code.matchAll(DECLARATION_KEYWORD)) {
    if (/\bas\s+$/.test(code.slice(Math.max(0, match.index - 12), match.index))) continue
    let from = match.index + match[0].length
    while (from >= 0) {
      const binding = readBinding(code, from)
      if (!binding) break
      for (const name of binding.names) {
        if (names.has(name)) hits.push({ line: lineOf(binding.start), text: original[lineOf(binding.start) - 1].trim(), name })
      }
      const at = skipAnnotation(code, binding.end)
      if (code[at] === '=') from = nextDeclarator(code, at + 1)
      else from = code[at] === ',' ? at + 1 : -1
    }
  }
  return hits
}

/** 共有の名前と同じ名前の定義の除外のキー（<画面>:<名前>）。画面は store か admin */
const shadowKey = (file, name) => `${file.split('/')[1]}:${name}`

if (isMain(import.meta.url)) {
  // E2E（e2e/）には、テストの種類のファイルに当てる規則（CSS のクラスの確認、toBeTruthy・toBeDefined、並びのインデックス）、E2E の書き方の規則、組み込みの名前のシャドーイングとテストの間の空行の検査だけを当てる。ほかの規則は frontend/ のファイルだけに当てる
  const files = listGitFiles(['frontend', 'e2e'])
  exitIfNoFiles(files, 'フロントのファイル')
  const exclusions = readExclusions(exclusionsPath('frontend-rules-exclusions.json'))
  let count = 0
  const report = (message) => {
    console.log(message)
    count += 1
  }
  const sharedExports = JSON.parse(readFileSync('frontend/shared/package.json', 'utf8')).exports
  let sharedNames
  try {
    sharedNames = sharedPublicNames(sharedExports, files, (file) => readFileSync(file, 'utf8'))
  } catch (error) {
    console.log(error.message)
    process.exit(1)
  }
  const targets = groupTargets(files)
  const shadowing = new Set()
  for (const file of targets.sources) {
    const text = readFileSync(file, 'utf8')
    if (isScreenSource(file) && !file.startsWith('frontend/shared/')) {
      for (const hit of findDefinitions(text, sharedNames)) {
        const key = shadowKey(file, hit.name)
        shadowing.add(key)
        if (!(key in exclusions)) {
          report(`${file}:${hit.line} 共有のパッケージが公開する ${hit.name} と同じ名前の定義（共有のパッケージの ${hit.name} を使う。フロントの書き方の「入れるもの・入れないもの」。別の定義が正しいときは除外の一覧にキー ${key} と理由を書く）：${hit.text}`)
        }
      }
    }
  }
  for (const file of targets.frontendTests) {
    const text = readFileSync(file, 'utf8')
    for (const hit of findPropertyComparisons(text)) {
      report(`${file}:${hit.line} 要素のプロパティ・属性を toBe・toEqual で比べる（toBeDisabled、toHaveValue、toHaveAttribute などを使う。フロントのテストの書き方の「要素のプロパティ・属性・状態を jest-dom で確認する」）：${hit.text}`)
    }
  }
  for (const file of targets.tests) {
    const text = readFileSync(file, 'utf8')
    for (const hit of findIndexedMultiples(text)) {
      report(`${file}:${hit.line} 複数の結果を並びのインデックスで選ぶ（役割と名前で 1 つに絞る。フロントのテストの書き方の「画面に見えるもので探す」）：${hit.text}`)
    }
  }
  const timeoutFiles = new Set(timeoutTargets(targets.e2e))
  for (const file of targets.e2e) {
    const text = readFileSync(file, 'utf8')
    for (const hit of findButtonNameChecks(text)) {
      report(`${file}:${hit.line} ボタンの名前が完全一致でない（文字列は exact: true を付け、正規表現は先頭の ^ と末尾の $ を固定する。フロントのテストの書き方の「画面に見えるもので探す」）：${hit.text}`)
    }
    if (!timeoutFiles.has(file)) continue
    for (const hit of findTimeoutLiterals(text)) {
      report(`${file}:${hit.line} タイムアウトを数字のリテラルで書く（e2e/src/waits.ts の定数を使う。テストの書き方の「待ち方」）：${hit.text}`)
    }
  }
  for (const file of targets.testsWithSupportAndE2e) {
    for (const hit of findMissingTestSpacing(readFileSync(file, 'utf8'))) {
      report(`${file}:${hit.line} テストの間に空行がない（}) の直後に test・it・describe を続けず、空行を置く。フロントのテストの書き方の「テストとテストの間に空行を置く」）：${hit.text}`)
    }
  }
  const builtinNames = new Set(JSON.parse(readFileSync(BUILTIN_NAMES_FILE, 'utf8')))
  for (const file of targets.testsWithSupportAndE2e) {
    for (const hit of findBuiltinShadowing(readFileSync(file, 'utf8'), builtinNames)) {
      report(`${file}:${hit.line} 組み込みの名前 ${hit.name} と同じ名前の const・let（意味の分かる名前にする。フロントのテストの書き方の「組み込みの名前をシャドーイングしない」）：${hit.text}`)
    }
  }
  const sourceFiles = files.filter((file) => /^frontend\/(?:store|admin)\/src\//.test(file))
  // 同じ内容のファイルの一覧は 1 回だけ求め、違反の報告（除外を除く）と除外の要否の判定の両方に使う
  const duplicated = new Set(duplicatedFiles(sourceFiles, (file) => readFileSync(file, 'utf8'), []))
  for (const relative of [...duplicated].filter((relative) => !Object.hasOwn(exclusions, relative))) {
    report(`frontend/{store,admin}/src/${relative} 店舗画面と管理画面に同じ内容のファイルがある（共有パッケージに置く）`)
  }
  // 除外が必要なのは、2 つの画面に同じ内容のファイルがあるもの（キーは src からの相対パス）と、共有と同じ名前の定義があるもの（キーは <画面>:<名前>）だけ
  // （片方が消えた、内容が変わった、定義がなくなったものは不要）
  for (const message of checkExclusions('frontend-rules-exclusions.json', exclusions, (key) => duplicated.has(key) || shadowing.has(key))) {
    report(message)
  }
  console.log(`フロントの書き方の違反：${count}`)
  process.exit(count ? 1 : 0)
}
