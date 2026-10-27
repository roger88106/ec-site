// フロントの書き方の規則のうち、ソースの文字で確認できるものを確認する（フロントの書き方 第 1.2 節、第 5.1 節、第 7.3 節）。
// - 機能（features/<機能>/）は、ほかの機能を import しない
// - ロケールとタイムゾーンは frontend/shared/src/lib/locale.ts だけに書く。Intl と toLocaleString にロケールを直接書かない
// - 店舗画面と管理画面に、同じ相対パスで同じ内容のファイルを置かない（共有パッケージに置く）
// - 店舗画面と管理画面の本番のソースに、共有パッケージが公開する名前と同じ名前の関数・定数・クラスを定義しない（第 5.1 節。公開する名前は shared の package.json の exports から読む）
// - 共通の置き場所（actions/、lib/、api/）は features/ を import しない（第 1.2 節）
// - クエリ付きの URL を文字列で組み立てない（第 6 節）。line-through を直接書かない（第 10.1 節）
// - テストと E2E で CSS のクラスを確認しない。画面のテストで data-slot と querySelector で探さない（テストの書き方 第 4.1 節）
// - フロントのテストと補助：理由のコメントのない型の回避（as never、as unknown as）、fireEvent の直接の使用を書かない（第 4.6、4.8 節）
// - フロントのテスト：describe の名前を識別子だけにしない、要素のプロパティ・属性を toBe・toEqual で比べない、描画した HTML の文字を要素や属性の並びで確かめない（第 2.2、4.7、4.1 節）
// - フロントのテストと E2E：toBeTruthy・toBeDefined で確かめない、getAllBy…の結果を並びのインデックスで選ばない（第 4.1 節）
// - E2E：.first()・.nth()・.last() で選ばない、ボタンの名前を完全一致で探す（第 4.1 節）。networkidle で待たない、タイムアウトを数字のリテラルで書かない（e2e/src/waits.ts を除く。第 5 節）
// 使い方：node scripts/ci/frontend-rules.mjs（リポジトリのルートで）
// 同じ内容でよいファイル（生成物の読む場所など）は frontend-rules-exclusions.json に、src からの相対パスと理由を書く。共有と同じ名前の定義でよいもの（各画面の translate など）は、キー <画面>:<名前> と理由を書く
// fireEvent を使ってよいのは、同じ行のコメントか、コメントだけの直前の行に理由を書いた呼び出しだけ（除外の一覧には書かない）
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { closingParen } from './lib/closing-paren.mjs'
import { scanCode, templateEnd } from './lib/scan-code.mjs'
import { checkExclusions, exclusionsPath, readExclusions } from './lib/exclusions.mjs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isScreenSource } from './lib/screen-sources.mjs'
import { stripComments } from './lib/strip-comments.mjs'
import { isMain } from './lib/is-main.mjs'
import { createLineLocator } from './lib/line-number.mjs'

/** ロケールとタイムゾーンを書いてよいファイル */
const LOCALE_FILE = 'frontend/shared/src/lib/locale.ts'

/**
 * 画面のソース（テストを含む）。
 * 画面の本番のソースの定義（lib/screen-sources.mjs）とは違い、生成物、shadcn/ui の部品、共有の ui、テストの補助（src/test/）も含む。
 * ロケールの直接の記述と機能の間の import は、どのファイルに書いても規則に反するため、本番のソースに絞らない
 */
export const SOURCE = /^frontend\/(?:store|admin|shared)\/src\/.*\.tsx?$/

/** 場所に関わらない、テストのファイル（.test.ts・.test.tsx）。公開する名前などから、どの場所のテストも除くときに使う */
export const ANY_TEST_FILE = /\.test\.tsx?$/

/** 画面の単体・結合テストのファイル */
export const FRONTEND_TEST = /^frontend\/(?:store|admin|shared)\/src\/.*\.test\.tsx?$/

/** 画面のテストの補助のファイル（src の下の test/ ディレクトリの下） */
export const FRONTEND_TEST_SUPPORT = /^frontend\/(?:store|admin|shared)\/src\/(?:.*\/)?test\/.*\.tsx?$/

/** E2E のファイル */
export const E2E_FILE = /^e2e\/.*\.tsx?$/

/** 機能のファイル */
const FEATURE_FILE = /^frontend\/(store|admin)\/src\/features\/([^/]+)\//

/**
 * import のパス（import … from、export … from、import(…)、import '…'）。from・( と指定子の間の改行を許す。
 * テンプレート文字列は ${ を含まない固定の文字列だけを対象にする。変数を含むパスは、どの機能か解決できないため
 */
const IMPORT_SPECIFIER = /(?:\bfrom\s+|\bimport\s*\(\s*|\bimport\s+)(['"`])([^'"`$]+)\1/g

/** 別名 @/ で書いた機能の import */
const ALIAS_FEATURE = /^@\/features\/([^/]+)/

/** Intl・toLocaleString に直接書いたロケール、ロケールとタイムゾーンの文字 */
const LOCALE_LITERAL = [
  /\bIntl\.\w+\(\s*['"`]/,
  /\.toLocale\w*String\(\s*['"`]/,
  /(['"`])(?:ja|ja-JP|en|en-US|Asia\/Tokyo)\1/,
]

/** 店舗画面と管理画面の、機能の外の共通の置き場所（actions/、lib/、api/）のファイル */
const COMMON_FILE = /^frontend\/(?:store|admin)\/src\/(?:actions|lib|api)\//

/** 文字列のリテラルとテンプレート文字列（引用符で閉じるまで。' と " は同じ行の中） */
const STRING_LITERAL = /'(?:[^'\\\n]|\\.)*'|"(?:[^"\\\n]|\\.)*"|`(?:[^`\\]|\\.)*`/g

/** クエリのパラメーター名（名前か ${…}）と =。? か & の直後に続く */
const PARAM_NAME = String.raw`(?:[\w-]+|\$\{[^}]*\})=`

/** 文字列の中の ?名前= か &名前=（'/x?a=1'、'/x?page=' + p、`${b}?${k}=${v}`） */
const QUERY_PARAM_IN_STRING = new RegExp(`[?&]${PARAM_NAME}`)

/** 連結の前半（? か & で終わる文字列。'/x?' + 'page=' + p の '/x?'） */
const ENDS_WITH_QUERY_MARK = /[?&]$/

/** 連結の後半（名前= で始まる文字列。'/x?' + 'page=' + p の 'page='） */
const STARTS_WITH_PARAM = new RegExp(`^${PARAM_NAME}`)

/** CSS のクラスを確認する書き方（toHaveClass、className、classList、属性の class、描画した HTML の class="、クラスのセレクター） */
const CSS_CLASS_CHECK = [
  /\btoHaveClass\b|\.className\b|\.classList\b/,
  /(?:getAttribute|toHaveAttribute)\(\s*['"`]class['"`]/,
  /\bclass=\\?["']/,
  /(?:\blocator|\bquerySelector(?:All)?|\bclosest|\bmatches|\$\$?)\(\s*['"`][\w\s>+~*#-]*\.[A-Za-z_-]/,
]

/** コンポーネントの内部の属性（data-slot）と、DOM のセレクターで探す書き方 */
const INTERNAL_SELECTOR = [/\bdata-slot\b/, /\bquerySelector(?:All)?\b/]

/**
 * ファイルを、規則を当てるまとまりに振り分ける。ファイルの順は保つ。どのまとまりにも入らないファイルは捨てる。
 * まとまりは重なる：sources はテストの補助を含み、tests と testsWithSupport は画面のテストを含む。
 *
 * @param {string[]} files リポジトリのルートからのパス
 * @returns {{ sources: string[], frontendTests: string[], testSupport: string[], e2e: string[], tests: string[], testsWithSupport: string[] }}
 *   sources は画面のソース（テストを除く。テストの補助を含む）、frontendTests は画面の単体・結合テスト、testSupport はテストの補助、e2e は E2E、
 *   tests は画面のテストと E2E、testsWithSupport は画面のテストとテストの補助（E2E を除く）
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
  }
}

/**
 * 正規表現のどれかに合う行を探す。
 *
 * @param {string} text ソース
 * @param {(line: string) => boolean} matches 行の判定
 * @returns {{ line: number, text: string }[]} 見つけた行（行の順）
 */
function findLines(text, matches) {
  return text
    .split('\n')
    .map((line, index) => ({ line: index + 1, text: line.trim() }))
    .filter((hit) => matches(hit.text))
}

/**
 * コメントを除いた行のうち、判定に合う行を探す。行の番号と表示する文字は元のソースのもの。
 *
 * @param {string} text ソース
 * @param {(line: string) => boolean} matches コメントを除いた行の判定
 * @param {{ keepRegex?: boolean }} [options] stripComments の指定
 * @returns {{ line: number, text: string }[]} 見つけた行（行の順）
 */
function findCodeLines(text, matches, options) {
  const original = text.split('\n')
  return stripComments(text, options)
    .split('\n')
    .flatMap((line, index) => (matches(line) ? [{ line: index + 1, text: original[index].trim() }] : []))
}

/**
 * ファイルの画面と機能を返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {{ app: string, feature: string } | null} 機能のファイルなら画面と機能の名前、ほかは null
 */
export function featureOf(file) {
  const match = file.match(FEATURE_FILE)
  return match ? { app: match[1], feature: match[2] } : null
}

/**
 * ほかの機能を import する行を探す。
 *
 * @param {string} text ソース
 * @param {string} feature そのファイルの機能の名前
 * @param {string} [file] リポジトリのルートからのパス。相対パスの import を解決するのに使う（なければ相対パスは見ない）
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findCrossFeatureImports(text, feature, file) {
  const featureOfSpecifier = (specifier) => {
    const alias = specifier.match(ALIAS_FEATURE)
    if (alias) return alias[1]
    if (file && specifier.startsWith('.')) {
      return featureOf(`${path.posix.join(path.posix.dirname(file), specifier)}/`)?.feature ?? null
    }
    return null
  }
  // 全文に当て、一致の位置から行番号を求める（import が複数の行にまたがってもよい）
  const lines = text.split('\n')
  const lineOf = createLineLocator(text)
  const hits = []
  // コメントの中の指定（移した元の説明など）は import でないので、空白にしてから探す（行番号は変わらない）
  for (const match of stripComments(text).matchAll(IMPORT_SPECIFIER)) {
    const imported = featureOfSpecifier(match[2])
    if (imported === null || imported === feature) continue
    const line = lineOf(match.index)
    if (!hits.some((hit) => hit.line === line)) hits.push({ line, text: lines[line - 1].trim() })
  }
  return hits
}

/**
 * 直接書いたロケールとタイムゾーンの行を探す。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findLocaleLiterals(text) {
  return findLines(text, (line) => LOCALE_LITERAL.some((pattern) => pattern.test(line)))
}

/**
 * 店舗画面と管理画面の、機能の外の共通の置き場所のファイルかを返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} actions/、lib/、api/ のファイルなら true
 */
export function isCommonFile(file) {
  return COMMON_FILE.test(file)
}

/**
 * クエリ付きの URL を文字列で組み立てる行を探す（フロントの書き方 第 6 節。URLSearchParams で作る）。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findQueryUrlStrings(text) {
  const code = stripComments(text)
  const literals = [...code.matchAll(STRING_LITERAL)].map((match) => ({ start: match.index, end: match.index + match[0].length, content: match[0].slice(1, -1) }))
  const lines = text.split('\n')
  const lineOf = createLineLocator(code)
  const hits = []
  literals.forEach((literal, index) => {
    const next = literals[index + 1]
    const joinedToParam =
      ENDS_WITH_QUERY_MARK.test(literal.content) && next && /^\s*\+\s*$/.test(code.slice(literal.end, next.start)) && STARTS_WITH_PARAM.test(next.content)
    if (!QUERY_PARAM_IN_STRING.test(literal.content) && !joinedToParam) return
    const line = lineOf(literal.start)
    if (!hits.some((hit) => hit.line === line)) hits.push({ line, text: lines[line - 1].trim() })
  })
  return hits
}

/**
 * CSS のクラスを確認する行を探す（テストの書き方 第 4.1 節。役割と名前で探す）。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findCssClassChecks(text) {
  return findCodeLines(text, (line) => CSS_CLASS_CHECK.some((pattern) => pattern.test(line)), { keepRegex: true })
}

/**
 * コンポーネントの内部の属性（data-slot）と querySelector・querySelectorAll で要素を探す行を探す（テストの書き方 第 4.1 節。役割と名前で探す）。
 * 画面のテストだけに当てる。E2E は対象にしない：E2E の locator は、IdP の画面（id で探す。e2e/src/auth.ts）と詳細の項目（xpath。e2e/src/detail.ts）に限って許し、
 * 今の E2E に data-slot と querySelector の使用がなく、locator 全体を禁じると許した所まで失敗になるため
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findInternalSelectors(text) {
  return findCodeLines(text, (line) => INTERNAL_SELECTOR.some((pattern) => pattern.test(line)), { keepRegex: true })
}

/**
 * line-through を直接書いた行を探す（フロントの書き方 第 10.1 節。取り消し線は <s> で示す）。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findLineThrough(text) {
  return findCodeLines(text, (line) => line.includes('line-through'))
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

/** 型を回避する変換（as と never、as unknown as の間の改行を許す） */
const TYPE_CAST = /\bas\s+never\b|\bas\s+unknown\s+as\b/g

/**
 * 理由のコメントがあるかを調べる関数を作る。
 * 理由のコメントは、対象の行（複数の行にまたがるなら、そのどれか）のコメントか、直前の行がコメントだけの行であるものとみなす。
 *
 * @param {string} text ソース
 * @returns {(firstLine: number, lastLine: number) => boolean} 対象の行の範囲（1 始まり）に理由のコメントがあるか
 */
function createReasonChecker(text) {
  const original = text.split('\n')
  // 正規表現のリテラルを残す（空白にすると、同じ行に正規表現があるだけでコメントのある行と読む）
  const withStrings = stripComments(text, { keepRegex: true }).split('\n')
  const hasComment = (index) => index >= 0 && original[index] !== withStrings[index]
  const commentOnly = (index) => hasComment(index) && withStrings[index].trim() === ''
  return (firstLine, lastLine) => {
    for (let index = firstLine - 1; index <= lastLine - 1; index += 1) if (hasComment(index)) return true
    return commentOnly(firstLine - 2)
  }
}

/**
 * 理由のコメントのない、型を回避する変換（as never、as unknown as）を探す（テストの書き方 第 4.6 節）。
 * 理由のコメントは、同じ行のコメントか、直前の行がコメントだけの行であるものとみなす。変換が改行をまたぐときは、またぐ行のどれかのコメントも理由とみなす。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行（変換の始まりの行）
 */
export function findTypeCasts(text) {
  const code = stripComments(text, { blankStrings: true })
  const lineOf = createLineLocator(code)
  const hasReason = createReasonChecker(text)
  return toHits(
    text,
    [...code.matchAll(TYPE_CAST)].flatMap((match) => {
      const first = lineOf(match.index)
      return hasReason(first, lineOf(match.index + match[0].length - 1)) ? [] : [first]
    }),
  )
}

/**
 * toBeTruthy・toBeDefined の呼び出しを探す（テストの書き方 第 4.1 節。探した要素の確認は toBeInTheDocument か値の形で行う）。
 * 探した要素かどうかは機械で区別しないので、全てを対象にする。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findWeakExistenceChecks(text) {
  return findCodeLines(text, (line) => /\.(?:toBeTruthy|toBeDefined)\s*\(/.test(line), { blankStrings: true })
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
 * getAllBy…・findAllBy…・queryAllBy… の結果を、並びのインデックスで選ぶ所を探す（テストの書き方 第 4.1 節の「並びの 1 つ目は使わない」）。
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

/** 値の確認の呼び出し（. の後の名前と括弧）。1 つ目が名前 */
const MATCHER_CALL = /\.(toMatch|toContain|toBe|toEqual|toStrictEqual)\(/g

/** 引数にある、HTML の開始タグ・終了タグ（< の後に英字、正規表現のリテラルの \/ も読む） */
const HTML_TAG = /<\\?\/?[A-Za-z]/

/** 引数にある、属性の書き方（名前="…"、名前='…'。正規表現のリテラルの \" も読む） */
const HTML_ATTRIBUTE = /[A-Za-z][\w:-]*=\\?["']/

/**
 * 引数の中の、文字列・テンプレートの文字の部分・正規表現のリテラルだけを集める（コードの部分は除く。`Map<string, number>` の < をタグと読まないため）。
 *
 * @param {string} code ソース（コメントを空白にし、文字列と正規表現を残したもの）
 * @param {number} from 引数の始まりの位置
 * @param {number} to 引数の終わりの位置
 * @returns {string} 集めた文字（改行でつなぐ）
 */
function literalsOf(code, from, to) {
  const parts = []
  scanCode(code.slice(from, to), {}, (type, start, end) => {
    if (type === 'string' || type === 'regex') parts.push(code.slice(from + start, from + end))
    return false
  })
  return parts.join('\n')
}

/**
 * 描画した HTML の文字を、要素や属性の並びに依存して確かめる所を探す（テストの書き方 第 4.1 節。描画した結果を DOM に読み込み、役割と名前で探す）。
 * 対象の形は、toMatch・toContain・toBe・toEqual・toStrictEqual の引数の文字列・テンプレート・正規表現に HTML のタグ（`<a`、`</p`）があるもの、
 * toMatch・toContain の引数の文字列・テンプレート・正規表現に属性（`href="`、`aria-label="`）があるもの。
 * 引数のコード（`new Map<string, number>()` の型引数など）は見ない。空の描画の確認（toBe('')）は、引数にタグも属性もないので対象にならない
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findMarkupTextChecks(text) {
  const code = stripComments(text, { keepRegex: true })
  const blanked = stripComments(text, { blankStrings: true })
  const lineOf = createLineLocator(code)
  const lineNumbers = []
  for (const match of blanked.matchAll(MATCHER_CALL)) {
    const open = match.index + match[0].length - 1
    const close = closingParen(code, open)
    if (close >= code.length) continue
    const argument = literalsOf(code, open + 1, close)
    const attributeAllowed = match[1] === 'toMatch' || match[1] === 'toContain'
    if (HTML_TAG.test(argument) || (attributeAllowed && HTML_ATTRIBUTE.test(argument))) lineNumbers.push(lineOf(match.index))
  }
  return toHits(text, lineNumbers)
}

/** describe の呼び出しの始まり（前が . や語の文字のものは読まない） */
const DESCRIBE_CALL = /(?<![.\w$])describe(?![\w$])/g

/** 識別子だけの名前（英数字、_、$、.、<>、/、()、-、:、#、@ だけ。空白と日本語を含まない。コンポーネントの書き方 `<Foo />` は空白を許す） */
const IDENTIFIER_NAME = /^(?:[A-Za-z0-9_$.<>/()\-:#@]+|<[A-Za-z0-9_$.]+\s*\/?>)$/

/** describe.each・describe.for の名前の、表の値に置き換わる記号（%s・%d など、$名前・$a.b） */
const NAME_PLACEHOLDER = /%[sdifjoOp#%]|\$[A-Za-z_]/

/**
 * 識別子だけの describe の名前を探す（テストの書き方 第 2.2 節。関数名・コンポーネント名でなく、振る舞いを書く）。
 * describe.skip・describe.each(表)(名前) などの修飾を読む。describe.each・describe.for の名前の置き換えの記号（%s、$名前）は、表の値で名前が決まるので識別子だけの名前にしない。名前が文字列でもテンプレートでもないもの（変数や関数を渡す書き方）は読まない
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findIdentifierDescribes(text) {
  const code = stripComments(text, { keepRegex: true })
  const blanked = stripComments(text, { blankStrings: true })
  const lineOf = createLineLocator(code)
  const lineNumbers = []
  for (const match of blanked.matchAll(DESCRIBE_CALL)) {
    let pos = match.index + match[0].length
    let table = false
    for (let modifier = /^\.(\w+)/.exec(code.slice(pos, pos + 20)); modifier; modifier = /^\.(\w+)/.exec(code.slice(pos, pos + 20))) {
      pos += modifier[0].length
      if (modifier[1] !== 'each' && modifier[1] !== 'for') continue
      table = true
      if (code[pos] === '(') pos = closingParen(code, pos) + 1
      else if (code[pos] === '`') pos = templateEnd(code, pos)
    }
    if (code[pos] !== '(') continue
    const start = pos + 1 + /^\s*/.exec(code.slice(pos + 1))[0].length
    let name
    if (code[start] === '`') {
      const end = templateEnd(code, start)
      if (code[end - 1] === '`' && end - 1 > start) name = code.slice(start + 1, end - 1)
    } else if (code[start] === "'" || code[start] === '"') {
      const quoted = new RegExp(`^${code[start]}((?:[^\\\\\\n${code[start]}]|\\\\.)*)${code[start]}`).exec(code.slice(start))
      if (quoted) name = quoted[1]
    }
    if (name !== undefined && !(table && NAME_PLACEHOLDER.test(name)) && IDENTIFIER_NAME.test(name)) lineNumbers.push(lineOf(match.index))
  }
  return toHits(text, lineNumbers)
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
 * 要素のプロパティ・属性（.disabled、.checked、.value、getAttribute(…)）を toBe・toEqual で比べる所を探す（テストの書き方 第 4.7 節）。
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

/** import 文（import { a } from、import a, * as b from、import type { a } from）。取り込む名前の部分まで */
const IMPORT_STATEMENT = /\bimport\s+(?:type\s+)?(?:[\w$]+\s*,?\s*)?(?:\*\s*as\s+[\w$]+|\{[^}]*\})?\s*from\b/g

/**
 * 理由のコメントのない fireEvent の使用を探す（テストの書き方 第 4.8 節。import は除く）。
 * 理由のコメントは、同じ行のコメントか、直前の行がコメントだけの行であるものとみなす（型の変換と同じ）。呼び出しごとに理由が要る。
 * fireEvent を別名で import したとき（import { fireEvent as fe }）は、別名の呼び出し（fe.click(…)、fe(…)）も見つける。
 * 名前空間の import（import * as rtl）の rtl.fireEvent は、fireEvent の語で見つける
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行（行の順）
 */
export function findFireEvents(text) {
  const stripped = stripComments(text, { blankStrings: true })
  const aliases = []
  const code = stripped.replace(IMPORT_STATEMENT, (statement) => {
    for (const item of /\{([^}]*)\}/.exec(statement)?.[1].split(',') ?? []) {
      const alias = /^\s*(?:type\s+)?fireEvent\s+as\s+([\w$]+)/.exec(item)
      if (alias) aliases.push(alias[1])
    }
    return statement.replace(/[^\r\n]/g, ' ')
  })
  const patterns = [/\bfireEvent\b/, ...aliases.map((alias) => new RegExp(`(?<![.\\w$])${alias.replace(/\$/g, '\\$')}\\s*[.(]`))]
  const hasReason = createReasonChecker(text)
  const lineNumbers = code.split('\n').flatMap((line, index) => (patterns.some((pattern) => pattern.test(line)) && !hasReason(index + 1, index + 1) ? [index + 1] : []))
  return toHits(text, lineNumbers)
}

/** 位置で要素を選ぶメソッドの呼び出し（.first()、.last()、.nth(…)。?. を含む） */
const POSITION_SELECTOR = /\.\s*(?:first|last)\s*\(\s*\)|\.\s*nth\s*\(/

/**
 * E2E で、位置で要素を選ぶメソッド（.first()・.nth(…)・.last()）を使う行を探す（テストの書き方 第 4.1 節。名前や親の要素で絞る）。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findPositionSelectors(text) {
  return findCodeLines(text, (line) => POSITION_SELECTOR.test(line), { blankStrings: true })
}

/**
 * E2E で、ネットワークが静まるまで待つ networkidle を書いた行を探す（テストの書き方 第 5 節。画面の状態を条件にして待つ）。
 * waitUntil の値は文字列なので、文字列は残してコメントだけを除く。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findNetworkIdleWaits(text) {
  return findCodeLines(text, (line) => /\bnetworkidle\b/.test(line))
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

/** 先頭の ^ と末尾の $ を固定した正規表現のリテラル（フラグを許す） */
const ANCHORED_REGEX = /^\/\^[\s\S]*\$\/[a-z]*$/

/**
 * E2E で、ボタンを名前で探すのに、完全一致でない箇所を探す（テストの書き方 第 4.1 節）。
 * name が正規表現のリテラルなら、先頭の ^ と末尾の $ を固定していること。それ以外（文字列、変数、テンプレート文字列）なら exact: true が付いていること。
 * 役割が button の getByRole だけを見る（row.getByRole(…) のような連鎖の途中、複数の行にまたがる呼び出しも読む）。
 * name のない呼び出しは名前で探さないので対象にしない。オプションがオブジェクトのリテラルでないときは、名前を読めないので見つける
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
    for (const item of splitTopLevel(code, plain, objectFrom + 1, objectTo)) {
      const masked = code.slice(...item).trim()
      const value = plain.slice(...item).trim()
      if (/^name\b/.test(masked)) {
        const nameValue = /^name\s*:/.test(value) ? value.slice(value.indexOf(':') + 1).trim() : null
        if (nameValue === null && value !== 'name') continue
        if (nameValue?.startsWith('/')) nameKind = ANCHORED_REGEX.test(nameValue) ? 'anchored' : 'regex'
        else nameKind = 'other'
      } else if (/^exact\s*:\s*true$/.test(masked)) exact = true
    }
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

/** timeout・intervals の項目 */
const TIMEOUT_PROPERTY = /\b(?:timeout|intervals)\s*:(?!:)/g

/** setTimeout・test.setTimeout の呼び出しの始まり */
const SET_TIMEOUT = /\bsetTimeout\s*\(/g

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
 * E2E で、タイムアウトを数字のリテラルで書く行を探す（テストの書き方 第 5 節。e2e/src/waits.ts の定数を使う）。
 * timeout・intervals の項目の値、setTimeout・test.setTimeout の引数（引数が複数なら最後の引数。global の setTimeout は最後が待つ時間）に、
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
    if ('([{'.includes(code[i])) depth += 1
    else if (')]}'.includes(code[i])) depth -= 1
    else if (depth === 0 && code[i] === ',') return i + 1
  }
  return -1
}

/**
 * フロントのテストと E2E で、組み込みの名前（TypeScript の lib が宣言するグローバルの名前）と同じ名前の const・let の宣言を探す
 * （テストの書き方 第 4.10 節。エラーにならずにグローバルの値が使われるのを防ぐ）。
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
  // E2E（e2e/）には、テストの種類のファイルに当てる規則（CSS のクラスの確認、toBeTruthy・toBeDefined、並びのインデックス）と、E2E の書き方の規則だけを当てる。ほかの規則は frontend/ のファイルだけに当てる
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
          report(`${file}:${hit.line} 共有のパッケージが公開する ${hit.name} と同じ名前の定義（共有のパッケージの ${hit.name} を使う。フロントの書き方 第 5.1 節。別の定義が正しいときは除外の一覧にキー ${key} と理由を書く）：${hit.text}`)
        }
      }
    }
    const feature = featureOf(file)
    if (feature) {
      for (const hit of findCrossFeatureImports(text, feature.feature, file)) {
        report(`${file}:${hit.line} ほかの機能を import する：${hit.text}`)
      }
    }
    if (file !== LOCALE_FILE) {
      for (const hit of findLocaleLiterals(text)) {
        report(`${file}:${hit.line} ロケールかタイムゾーンを直接書く：${hit.text}`)
      }
    }
    if (isCommonFile(file)) {
      for (const hit of findCrossFeatureImports(text, null, file)) {
        report(`${file}:${hit.line} 共通の置き場所（actions/・lib/・api/）が features/ を import する（フロントの書き方 第 1.2 節）：${hit.text}`)
      }
    }
    if (isScreenSource(file)) {
      for (const hit of findQueryUrlStrings(text)) {
        report(`${file}:${hit.line} クエリ付きの URL を文字列で組み立てる（機能の *Query.ts で URLSearchParams を使う。フロントの書き方 第 6 節）：${hit.text}`)
      }
      for (const hit of findLineThrough(text)) {
        report(`${file}:${hit.line} line-through を直接書く（取り消し線は <s> で示す。フロントの書き方 第 10.1 節）：${hit.text}`)
      }
    }
  }
  for (const file of targets.tests) {
    for (const hit of findCssClassChecks(readFileSync(file, 'utf8'))) {
      report(`${file}:${hit.line} CSS のクラスを確認する（役割と名前で探す。テストの書き方 第 4.1 節）：${hit.text}`)
    }
  }
  for (const file of targets.frontendTests) {
    for (const hit of findInternalSelectors(readFileSync(file, 'utf8'))) {
      report(`${file}:${hit.line} コンポーネントの内部の属性（data-slot）か querySelector で探す（役割と名前で探す。テストの書き方 第 4.1 節）：${hit.text}`)
    }
  }
  for (const file of targets.testsWithSupport) {
    const text = readFileSync(file, 'utf8')
    for (const hit of findTypeCasts(text)) {
      report(`${file}:${hit.line} 理由のコメントのない型を回避する変換（型に合う値を補助で作る。避けられないときは理由をコメントに書く。テストの書き方 第 4.6 節）：${hit.text}`)
    }
    for (const hit of findFireEvents(text)) {
      report(`${file}:${hit.line} 理由のコメントのない fireEvent の使用（userEvent で操作する。起こせない出来事だけは、同じ行か直前の行のコメントに理由を書く。テストの書き方 第 4.8 節）：${hit.text}`)
    }
  }
  for (const file of targets.frontendTests) {
    const text = readFileSync(file, 'utf8')
    for (const hit of findMarkupTextChecks(text)) {
      report(`${file}:${hit.line} 描画した HTML の文字を、要素や属性の並びで確かめる（描画した結果を DOM に読み込み、役割と名前で探す。テストの書き方 第 4.1 節）：${hit.text}`)
    }
    for (const hit of findIdentifierDescribes(text)) {
      report(`${file}:${hit.line} describe の名前が識別子だけ（振る舞いを書く。テストの書き方 第 2.2 節）：${hit.text}`)
    }
    for (const hit of findPropertyComparisons(text)) {
      report(`${file}:${hit.line} 要素のプロパティ・属性を toBe・toEqual で比べる（toBeDisabled、toHaveValue、toHaveAttribute などを使う。テストの書き方 第 4.7 節）：${hit.text}`)
    }
  }
  for (const file of targets.tests) {
    const text = readFileSync(file, 'utf8')
    for (const hit of findWeakExistenceChecks(text)) {
      report(`${file}:${hit.line} toBeTruthy・toBeDefined で確かめる（toBeInTheDocument か、値の形で確かめる。テストの書き方 第 4.1 節）：${hit.text}`)
    }
    for (const hit of findIndexedMultiples(text)) {
      report(`${file}:${hit.line} 複数の結果を並びのインデックスで選ぶ（役割と名前で 1 つに絞る。テストの書き方 第 4.1 節）：${hit.text}`)
    }
  }
  const timeoutFiles = new Set(timeoutTargets(targets.e2e))
  for (const file of targets.e2e) {
    const text = readFileSync(file, 'utf8')
    for (const hit of findPositionSelectors(text)) {
      report(`${file}:${hit.line} 位置で要素を選ぶ（.first()・.nth()・.last() を使わず、名前や親の要素で絞る。テストの書き方 第 4.1 節）：${hit.text}`)
    }
    for (const hit of findButtonNameChecks(text)) {
      report(`${file}:${hit.line} ボタンの名前が完全一致でない（文字列は exact: true を付け、正規表現は先頭の ^ と末尾の $ を固定する。テストの書き方 第 4.1 節）：${hit.text}`)
    }
    for (const hit of findNetworkIdleWaits(text)) {
      report(`${file}:${hit.line} networkidle で待つ（画面の状態を条件にして待つ。テストの書き方 第 5 節）：${hit.text}`)
    }
    if (!timeoutFiles.has(file)) continue
    for (const hit of findTimeoutLiterals(text)) {
      report(`${file}:${hit.line} タイムアウトを数字のリテラルで書く（e2e/src/waits.ts の定数を使う。テストの書き方 第 5 節）：${hit.text}`)
    }
  }
  const builtinNames = new Set(JSON.parse(readFileSync(BUILTIN_NAMES_FILE, 'utf8')))
  for (const file of [...targets.frontendTests, ...targets.testSupport, ...targets.e2e]) {
    for (const hit of findBuiltinShadowing(readFileSync(file, 'utf8'), builtinNames)) {
      report(`${file}:${hit.line} 組み込みの名前 ${hit.name} と同じ名前の const・let（意味の分かる名前にする。テストの書き方 第 4.10 節）：${hit.text}`)
    }
  }
  const sourceFiles = files.filter((file) => /^frontend\/(?:store|admin)\/src\//.test(file))
  // 同じ内容のファイルの一覧は 1 回だけ求め、違反の報告（除外を除く）と除外の要否の判定の両方に使う
  const duplicated = new Set(duplicatedFiles(sourceFiles, (file) => readFileSync(file, 'utf8'), []))
  for (const relative of [...duplicated].filter((relative) => !Object.hasOwn(exclusions, relative))) {
    report(`frontend/{store,admin}/src/${relative} 店舗画面と管理画面に同じ内容のファイルがある（共有パッケージに置く）`)
  }
  // 除外が要るのは、2 つの画面に同じ内容のファイルがあるもの（キーは src からの相対パス）と、共有と同じ名前の定義があるもの（キーは <画面>:<名前>）だけ
  // （片方が消えた、内容が変わった、定義がなくなったものは不要）
  for (const message of checkExclusions('frontend-rules-exclusions.json', exclusions, (key) => duplicated.has(key) || shadowing.has(key))) {
    report(message)
  }
  console.log(`フロントの書き方の違反：${count}`)
  process.exit(count ? 1 : 0)
}
