// バックエンドの書き方の規則のうち、ソースの文字で確認できるものを確認する（バックエンドの書き方 第 1.3 節、第 5.1 節）。
// - テストのソース（test、integrationTest、testFixtures）で Lombok を使わない。Lombok の注釈はクラスファイルに残らず、ArchUnit で見えないため
// - 本番のソースで、列挙型の変換に name() を渡さない（valueOf(x.name())、fromValue(x.name())）。値を追加したときの写し忘れをコンパイルで見つけられないため
// - 本番のソースのうち domain と infrastructure/persistence 以外で、列挙型を名前の文字列から変換しない（valueOf(文字列リテラル以外)、fromValue(文字列リテラル以外)）。モジュールやレイヤーの間の列挙を文字列で受け渡すと、相手に値を追加したときにコンパイルでもテストでも気づけないため
// - 本番・テスト・tools のソースで、JSpecify（org.jspecify.annotations.Nullable）以外の Nullable を使わない。NullAway が見る注釈をそろえるため
// 使い方：node scripts/ci/java-rules.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { closingParen } from './lib/closing-paren.mjs'
import { listGitFiles } from './lib/git-files.mjs'

/** テストのソース */
const TEST_SOURCE = /^backend\/ecsite\/src\/(?:test|integrationTest|testFixtures)\/.*\.java$/

/** 本番のソース */
const MAIN_SOURCE = /^backend\/ecsite\/src\/main\/.*\.java$/

/** Lombok の import と、完全修飾名で書いた注釈 */
const LOMBOK = /^\s*import\s+(?:static\s+)?lombok\.|@lombok\./

/** 列挙型の変換の呼び出しの開き括弧まで */
const ENUM_CALL = /\b(?:valueOf|fromValue)\(/g

/** 引数（コメントを空白にしたもの）が name() で終わる */
const ENDS_WITH_NAME = /\.\s*name\(\s*\)\s*$/

/** 他の Nullable：JSpecify 以外の Nullable の import（static import も）と、完全修飾名で書いた注釈 */
const OTHER_NULLABLE = /^\s*import\s+(?:static\s+)?(?!org\.jspecify\.annotations\.)[\w.]+\.Nullable\b|@(?!org\.jspecify\.annotations\.)(?:[a-z]\w*\.)+Nullable\b/

/** 他の Nullable を検査するソース：本番、テスト、tools（生成したコードは含まない） */
const NULLABLE_SOURCE = /^backend\/ecsite\/src\/(?:main|test|integrationTest|testFixtures|tools)\/.*\.java$/

/** 検査から除く型：列挙でない値の型と、型を実行時に受ける汎用の Enum.valueOf（型ごとの switch 式は書けない） */
const NON_ENUM_TYPES = 'String|Integer|Long|Boolean|BigDecimal|BigInteger|Double|Float|Short|Byte|Character|Enum'

/** 文字列リテラル以外を渡す valueOf・fromValue の呼び出し（列挙の名前の文字列からの変換） */
const ENUM_STRING = new RegExp(`\\b(?!(?:${NON_ENUM_TYPES})\\b)[A-Z]\\w*\\.(?:valueOf|fromValue)\\(\\s*(?!")`)

/** 名前の文字列からの変換を許す場所（ドメインと、DB の文字列を読む永続化） */
const STRING_CONVERSION_ALLOWED = /\/(?:domain|infrastructure\/persistence)\//

/**
 * 正規表現に合う行を探す。
 *
 * @param {string} text ソース
 * @param {RegExp} pattern 行に当てる正規表現
 * @returns {{ line: number, text: string }[]} 見つけた行（行の順）
 */
function findLines(text, pattern) {
  return text
    .split('\n')
    .map((line, index) => ({ line: index + 1, text: line.trim() }))
    .filter((hit) => pattern.test(hit.text))
}

/**
 * Lombok を使う行を探す。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findLombok(text) {
  return findLines(text, LOMBOK)
}

/**
 * 行コメントとブロックコメントを、同じ長さの空白に置き換える。改行は残すので、位置と行番号は変わらない。<br />
 * 文字列、テキストブロック、文字リテラルの中の // などはコメントにしない。
 *
 * @param {string} text ソース
 * @returns {string} コメントを空白にしたソース
 */
export function blankComments(text) {
  let result = ''
  let i = 0
  const blank = (chunk) => chunk.replace(/[^\r\n]/g, ' ')
  while (i < text.length) {
    if (text.startsWith('//', i)) {
      const end = text.indexOf('\n', i)
      const stop = end < 0 ? text.length : end
      result += blank(text.slice(i, stop))
      i = stop
    } else if (text.startsWith('/*', i)) {
      const end = text.indexOf('*/', i + 2)
      const stop = end < 0 ? text.length : end + 2
      result += blank(text.slice(i, stop))
      i = stop
    } else if (text.startsWith('"""', i)) {
      const end = text.indexOf('"""', i + 3)
      const stop = end < 0 ? text.length : end + 3
      result += text.slice(i, stop)
      i = stop
    } else if (text[i] === '"' || text[i] === "'") {
      // 文字列と文字は、閉じる引用符まで（同じ行の中）そのまま残す
      const quote = text[i]
      let j = i + 1
      while (j < text.length && text[j] !== quote && text[j] !== '\n') j += text[j] === '\\' ? 2 : 1
      const stop = Math.min(j + 1, text.length)
      result += text.slice(i, stop)
      i = stop
    } else {
      result += text[i]
      i += 1
    }
  }
  return result
}

/**
 * 列挙型の変換に name() を渡す呼び出しを探す。改行を挟んだ呼び出しも見つけ、呼び出しの始まりの行を返す。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findEnumNameConversions(text) {
  const lines = text.split('\n')
  const hits = []
  const blanked = blankComments(text)
  for (const match of blanked.matchAll(ENUM_CALL)) {
    // 引数は、開き括弧から対応する閉じ括弧まで。引数の最後が name() のときだけ違反にする
    const open = match.index + match[0].length - 1
    const argument = blanked.slice(open + 1, closingParen(blanked, open))
    if (!ENDS_WITH_NAME.test(argument)) continue
    const line = text.slice(0, match.index).split('\n').length
    hits.push({ line, text: lines[line - 1].trim() })
  }
  return hits
}

/**
 * 列挙型を名前の文字列から変換する行（文字列リテラル以外を渡す valueOf・fromValue）を探す。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findEnumStringConversions(text) {
  return findLines(text, ENUM_STRING)
}

/**
 * JSpecify 以外の Nullable を使う行を探す。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findOtherNullable(text) {
  return findLines(text, OTHER_NULLABLE)
}

/**
 * 他の Nullable を禁じる場所か。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} 本番、テスト、tools のソースなら true
 */
export function isNullableTarget(file) {
  return NULLABLE_SOURCE.test(file)
}

/**
 * 名前の文字列からの変換を禁じる場所か。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} 本番のソースのうち、domain と infrastructure/persistence 以外なら true
 */
export function isStringConversionTarget(file) {
  return MAIN_SOURCE.test(file) && !STRING_CONVERSION_ALLOWED.test(file)
}

/**
 * ファイルに当てる規則を返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {'lombok' | 'enum-name' | null} テストのソースなら Lombok、本番のソースなら name() の変換、ほかは null
 */
export function ruleFor(file) {
  if (TEST_SOURCE.test(file)) return 'lombok'
  if (MAIN_SOURCE.test(file)) return 'enum-name'
  return null
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const files = listGitFiles().filter((file) => ruleFor(file) || isNullableTarget(file))
  const messages = { lombok: 'テストで Lombok を使う', 'enum-name': '列挙型の変換に name() を渡す' }
  let count = 0
  for (const file of files) {
    const rule = ruleFor(file)
    const text = readFileSync(file, 'utf8')
    if (isNullableTarget(file)) {
      for (const hit of findOtherNullable(text)) {
        console.log(`${file}:${hit.line} JSpecify 以外の Nullable を使う（org.jspecify.annotations.Nullable を使う）：${hit.text}`)
        count += 1
      }
    }
    if (!rule) continue
    const hits = rule === 'lombok' ? findLombok(text) : findEnumNameConversions(text)
    for (const hit of hits) {
      console.log(`${file}:${hit.line} ${messages[rule]}：${hit.text}`)
      count += 1
    }
    if (isStringConversionTarget(file)) {
      const reported = new Set(hits.map((hit) => hit.line))
      for (const hit of findEnumStringConversions(text).filter((found) => !reported.has(found.line))) {
        console.log(`${file}:${hit.line} 列挙型を名前の文字列から変換する（switch 式で変換する）：${hit.text}`)
        count += 1
      }
    }
  }
  console.log(`バックエンドの書き方の違反：${count}`)
  process.exit(count ? 1 : 0)
}
