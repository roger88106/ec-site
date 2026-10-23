// フロントの書き方の規則のうち、ソースの文字で確認できるものを確認する（フロントの書き方 第 1.2 節、第 5.1 節、第 7.3 節）。
// - 機能（features/<機能>/）は、ほかの機能を import しない
// - ロケールとタイムゾーンは frontend/shared/src/lib/locale.ts だけに書く。Intl と toLocaleString にロケールを直接書かない
// - 店舗画面と管理画面に、同じ相対パスで同じ内容のファイルを置かない（共有パッケージに置く）
// - 店舗画面と管理画面の本番のソースに、共有パッケージが公開する名前と同じ名前の関数・定数・クラスを定義しない（第 5.1 節。公開する名前は shared の package.json の exports から読む）
// - 共通の置き場所（actions/、lib/、api/）は features/ を import しない（第 1.2 節）
// - クエリ付きの URL を文字列で組み立てない（第 6 節）。line-through を直接書かない（第 10.1 節）
// - テストと E2E で CSS のクラスを確認しない。画面のテストで data-slot と querySelector で探さない（テストの書き方 第 4.1 節）
// 使い方：node scripts/ci/frontend-rules.mjs（リポジトリのルートで）
// 同じ内容でよいファイル（生成物の読む場所など）は frontend-rules-exclusions.json に、src からの相対パスと理由を書く。共有と同じ名前の定義でよいもの（各画面の translate など）は、キー <画面>:<名前> と理由を書く
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { checkExclusions, readExclusions } from './lib/exclusions.mjs'
import { listGitFiles } from './lib/git-files.mjs'
import { isScreenSource } from './lib/screen-sources.mjs'
import { stripComments } from './lib/strip-comments.mjs'

/** ロケールとタイムゾーンを書いてよいファイル */
const LOCALE_FILE = 'frontend/shared/src/lib/locale.ts'

/**
 * 調べる画面のソース（テストを除く）。
 * 画面の本番のソースの定義（lib/screen-sources.mjs）とは違い、生成物、shadcn/ui の部品、共有の ui、テストの補助（src/test/）も調べる。
 * ロケールの直接の記述と機能の間の import は、どのファイルに書いても規則に反するため、本番のソースに絞らない
 */
const SOURCE = /^frontend\/(?:store|admin|shared)\/src\/.*\.tsx?$/
const TEST = /\.test\.tsx?$/

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

/** テストの種類のファイル（画面のテストと E2E） */
const TEST_FILE = /^(?:frontend\/(?:store|admin|shared)\/src\/.*\.test\.tsx?|e2e\/.*\.tsx?)$/

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
  const hits = []
  for (const match of text.matchAll(IMPORT_SPECIFIER)) {
    const imported = featureOfSpecifier(match[2])
    if (imported === null || imported === feature) continue
    const line = text.slice(0, match.index).split('\n').length
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
  const hits = []
  literals.forEach((literal, index) => {
    const next = literals[index + 1]
    const joinedToParam =
      ENDS_WITH_QUERY_MARK.test(literal.content) && next && /^\s*\+\s*$/.test(code.slice(literal.end, next.start)) && STARTS_WITH_PARAM.test(next.content)
    if (!QUERY_PARAM_IN_STRING.test(literal.content) && !joinedToParam) return
    const line = code.slice(0, literal.start).split('\n').length
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
    if (TEST.test(file) || !/\.tsx?$/.test(file) || !patterns.some((pattern) => pattern.test(file))) continue
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

/** 共有の名前と同じ名前の定義の除外のキー（<画面>:<名前>）。画面は store か admin */
const shadowKey = (file, name) => `${file.split('/')[1]}:${name}`

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  // E2E（e2e/）は、CSS のクラスの確認だけを見る。ほかの規則は frontend/ のファイルだけに当てる
  const files = listGitFiles(['frontend', 'e2e'])
  const exclusions = readExclusions(path.join(path.dirname(fileURLToPath(import.meta.url)), 'frontend-rules-exclusions.json'))
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
  const shadowing = new Set()
  for (const file of files.filter((file) => SOURCE.test(file) && !TEST.test(file))) {
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
  for (const file of files.filter((file) => TEST_FILE.test(file))) {
    for (const hit of findCssClassChecks(readFileSync(file, 'utf8'))) {
      report(`${file}:${hit.line} CSS のクラスを確認する（役割と名前で探す。テストの書き方 第 4.1 節）：${hit.text}`)
    }
  }
  for (const file of files.filter((file) => /^frontend\/(?:store|admin|shared)\/src\/.*\.test\.tsx?$/.test(file))) {
    for (const hit of findInternalSelectors(readFileSync(file, 'utf8'))) {
      report(`${file}:${hit.line} コンポーネントの内部の属性（data-slot）か querySelector で探す（役割と名前で探す。テストの書き方 第 4.1 節）：${hit.text}`)
    }
  }
  const sourceFiles = files.filter((file) => /^frontend\/(?:store|admin)\/src\//.test(file))
  for (const relative of duplicatedFiles(sourceFiles, (file) => readFileSync(file, 'utf8'), Object.keys(exclusions))) {
    report(`frontend/{store,admin}/src/${relative} 店舗画面と管理画面に同じ内容のファイルがある（共有パッケージに置く）`)
  }
  // 除外が要るのは、2 つの画面に同じ内容のファイルがあるもの（キーは src からの相対パス）と、共有と同じ名前の定義があるもの（キーは <画面>:<名前>）だけ
  // （片方が消えた、内容が変わった、定義がなくなったものは不要）
  const duplicated = new Set(duplicatedFiles(sourceFiles, (file) => readFileSync(file, 'utf8'), []))
  for (const message of checkExclusions('frontend-rules-exclusions.json', exclusions, (key) => duplicated.has(key) || shadowing.has(key))) {
    report(message)
  }
  console.log(`フロントの書き方の違反：${count}`)
  process.exit(count ? 1 : 0)
}
