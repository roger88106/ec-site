// フロントの書き方の規則のうち、ソースの文字で確認できるものを確認する（フロントの書き方 第 1.2 節、第 5.1 節、第 7.3 節）。
// - 機能（features/<機能>/）は、ほかの機能を import しない
// - ロケールとタイムゾーンは frontend/shared/src/lib/locale.ts だけに書く。Intl と toLocaleString にロケールを直接書かない
// - 店舗画面と管理画面に、同じ相対パスで同じ内容のファイルを置かない（共有パッケージに置く）
// - 共通の置き場所（actions/、lib/、api/）は features/ を import しない（第 1.2 節）
// - クエリ付きの URL を文字列で組み立てない（第 6 節）。line-through を直接書かない（第 10.1 節）
// - テストと E2E で CSS のクラスを確認しない（テストの書き方 第 4.1 節）
// 使い方：node scripts/ci/frontend-rules.mjs（リポジトリのルートで）
// 同じ内容でよいファイル（生成物の読む場所など）は frontend-rules-exclusions.json に、src からの相対パスと理由を書く
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { checkExclusions, readExclusions } from './lib/exclusions.mjs'
import { listGitFiles } from './lib/git-files.mjs'
import { isScreenSource } from './lib/screen-sources.mjs'

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

/** クエリ付きの URL を文字列で組み立てる書き方（テンプレート文字列の ?x=${…}、連結の '?x=' + … や … + '&x='） */
const QUERY_URL_STRING = [/[?&][\w-]+=\$\{/, /[?&][\w-]+=['"`]\s*\+/, /\+\s*['"`][?&][\w-]+=/]

/** CSS のクラスを確認する書き方 */
const CSS_CLASS_CHECK = /\btoHaveClass\b|\.className\b|\.classList\b|getAttribute\(\s*['"`]class['"`]/

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
  return findLines(text, (line) => QUERY_URL_STRING.some((pattern) => pattern.test(line)))
}

/**
 * CSS のクラスを確認する行を探す（テストの書き方 第 4.1 節。役割と名前で探す）。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findCssClassChecks(text) {
  return findLines(text, (line) => CSS_CLASS_CHECK.test(line))
}

/**
 * line-through を直接書いた行を探す（フロントの書き方 第 10.1 節。取り消し線は <s> で示す）。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findLineThrough(text) {
  return findLines(text, (line) => line.includes('line-through'))
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

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const files = listGitFiles(['frontend'])
  const exclusions = readExclusions(path.join(path.dirname(fileURLToPath(import.meta.url)), 'frontend-rules-exclusions.json'))
  let count = 0
  const report = (message) => {
    console.log(message)
    count += 1
  }
  for (const file of files.filter((file) => SOURCE.test(file) && !TEST.test(file))) {
    const text = readFileSync(file, 'utf8')
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
  const sourceFiles = files.filter((file) => /^frontend\/(?:store|admin)\/src\//.test(file))
  for (const relative of duplicatedFiles(sourceFiles, (file) => readFileSync(file, 'utf8'), Object.keys(exclusions))) {
    report(`frontend/{store,admin}/src/${relative} 店舗画面と管理画面に同じ内容のファイルがある（共有パッケージに置く）`)
  }
  // 除外が要るのは、2 つの画面に同じ内容のファイルがあるものだけ（片方が消えた、内容が変わったものは不要）
  const duplicated = new Set(duplicatedFiles(sourceFiles, (file) => readFileSync(file, 'utf8'), []))
  for (const message of checkExclusions('frontend-rules-exclusions.json', exclusions, (relative) => duplicated.has(relative))) {
    report(message)
  }
  console.log(`フロントの書き方の違反：${count}`)
  process.exit(count ? 1 : 0)
}
