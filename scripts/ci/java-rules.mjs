// バックエンドの書き方の規則のうち、ソースの文字で確認できるものを確認する（バックエンドの書き方の「他のモジュールは腐敗防止層で包む」「検査を抑えるとき」）。
// - 本番のソースで、列挙型の変換に name() を渡さない（valueOf(x.name())、fromValue(x.name())）。値を追加したときの写し忘れを、コンパイルで見つけられないため
// - 全てのソースセットで、@SuppressWarnings の直前の行に、理由を // のコメントで書く。間にある他の注釈の行は飛ばし、Javadoc だけでは違反にする
//
// コメントの中は、どの規則でも読まない。どちらの規則も、クラスファイルに残らないもの（引数の式、ソースだけの注釈）を見るので、ArchUnit で書けない。
// 列挙型を名前の文字列から変換する場所は ArchUnit（EnumStringConversionTest）が、JSpecify 以外の Nullable は Checkstyle が確認する。
//
// 失敗したとき：列挙型は switch 式で変換する。@SuppressWarnings の直前の行に、なぜ抑えてよいかを // で書く。
// 除外：なし
// 使い方：node scripts/ci/java-rules.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { closingParen } from './lib/closing-paren.mjs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { createLineLocator } from './lib/line-number.mjs'
import { stripJavaComments } from './lib/strip-comments.mjs'

/** 本番のソース */
const MAIN_SOURCE = /^backend\/ecsite\/src\/main\/.*\.java$/

/** 手で書いた全てのソース：本番、テスト、tools（生成したコードは含まない） */
const JAVA_SOURCE = /^backend\/ecsite\/src\/(?:main|test|integrationTest|testFixtures|tools)\/.*\.java$/

/** 列挙型の変換の呼び出しの開き括弧まで */
const ENUM_CALL = /\b(?:valueOf|fromValue)\(/g

/** 引数（コメントを空白にしたもの）が name() で終わる */
const ENDS_WITH_NAME = /\.\s*name\(\s*\)\s*$/

/** @SuppressWarnings の注釈の始まり（行の途中も。@java.lang.SuppressWarnings も） */
const SUPPRESS_WARNINGS = /@\s*(?:java\s*\.\s*lang\s*\.\s*)?SuppressWarnings\b/g

/** 注釈の始まりと名前（@interface は注釈の宣言なので含まない） */
const ANNOTATION_NAME = /@\s*(?!interface\b)[A-Za-z_][\w.]*/g

/**
 * 列挙型の変換に name() を渡す呼び出しを探す。
 *
 * 改行やコメントを挟んだ呼び出しも見つけ、呼び出しの始まりの行を返す。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findEnumNameConversions(text) {
  const lines = text.split('\n')
  const lineOf = createLineLocator(text)
  const hits = []
  const blanked = stripJavaComments(text)
  for (const match of blanked.matchAll(ENUM_CALL)) {
    // 引数は、開き括弧から対応する閉じ括弧まで。引数の最後が name() のときだけ違反にする
    const open = match.index + match[0].length - 1
    const argument = blanked.slice(open + 1, closingParen(blanked, open, { regex: false }))
    if (!ENDS_WITH_NAME.test(argument)) continue
    const line = lineOf(match.index)
    hits.push({ line, text: lines[line - 1].trim() })
  }
  return hits
}

/**
 * 直前に理由の // のコメントがない @SuppressWarnings を探す。間にある他の注釈の行は飛ばす。Javadoc と /* *\/ のコメントだけでは理由にしない。
 *
 * @param {string} text ソース
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findUnexplainedSuppressWarnings(text) {
  const original = text.split('\n').map((line) => line.trim())
  const code = blankLiterals(text)
  const mask = annotationMask(code)
  const codeLines = code.split('\n')
  const offsets = []
  let offset = 0
  for (const line of codeLines) {
    offsets.push(offset)
    offset += line.length + 1
  }
  // 空でなく、空白でない字が全て注釈の範囲にある行（複数行の注釈の続きの行も）
  const isAnnotationOnly = (index) => {
    let seen = false
    for (let k = 0; k < codeLines[index].length; k += 1) {
      if (/\s/.test(codeLines[index][k])) continue
      if (!mask[offsets[index] + k]) return false
      seen = true
    }
    return seen
  }
  const lineOf = createLineLocator(text)
  const hits = []
  for (const match of code.matchAll(SUPPRESS_WARNINGS)) {
    const line = lineOf(match.index)
    let above = line - 2
    while (above >= 0 && isAnnotationOnly(above)) above -= 1
    const explained = above >= 0 && codeLines[above].trim() === '' && original[above].startsWith('//')
    if (!explained) hits.push({ line, text: original[line - 1] })
  }
  return hits
}

/**
 * コメント、文字列、文字、テキストブロックの中を空白にする（改行は残すので、位置と行番号は変わらない）。この規則だけが使う。
 *
 * @param {string} text ソース
 * @returns {string} コードだけを残したソース
 */
function blankLiterals(text) {
  const source = stripJavaComments(text)
  const blank = (chunk) => chunk.replace(/[^\r\n]/g, ' ')
  let result = ''
  let i = 0
  while (i < source.length) {
    if (source.startsWith('"""', i)) {
      const end = source.indexOf('"""', i + 3)
      const stop = end < 0 ? source.length : end + 3
      result += blank(source.slice(i, stop))
      i = stop
    } else if (source[i] === '"' || source[i] === "'") {
      const quote = source[i]
      let j = i + 1
      while (j < source.length && source[j] !== quote && source[j] !== '\n') j += source[j] === '\\' ? 2 : 1
      const stop = Math.min(j + 1, source.length)
      result += blank(source.slice(i, stop))
      i = stop
    } else {
      result += source[i]
      i += 1
    }
  }
  return result
}

/**
 * 注釈（@名前 と、続く括弧の対応する閉じ括弧まで。入れ子も含む）の範囲の字に印を付ける。
 *
 * @param {string} code コードだけを残したソース
 * @returns {Uint8Array} 注釈の中の字が 1
 */
function annotationMask(code) {
  const mask = new Uint8Array(code.length)
  for (const match of code.matchAll(ANNOTATION_NAME)) {
    let end = match.index + match[0].length
    let probe = end
    while (probe < code.length && /\s/.test(code[probe])) probe += 1
    if (code[probe] === '(') {
      let depth = 0
      for (let k = probe; k < code.length; k += 1) {
        if (code[k] === '(') depth += 1
        else if (code[k] === ')') {
          depth -= 1
          if (depth === 0) {
            end = k + 1
            break
          }
        }
      }
    }
    mask.fill(1, match.index, end)
  }
  return mask
}
/**
 * @SuppressWarnings の理由を求める場所か。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} 本番、テスト、tools のソースなら true（生成したコードは含まない）
 */
export function isSuppressWarningsTarget(file) {
  return JAVA_SOURCE.test(file)
}

/**
 * 列挙型の変換に name() を渡すことを禁じる場所か。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} 本番のソースなら true
 */
export function isEnumNameTarget(file) {
  return MAIN_SOURCE.test(file)
}

if (import.meta.main) {
  const files = listGitFiles().filter((file) => isSuppressWarningsTarget(file))
  exitIfNoFiles(files, 'Java の規則の対象のファイル')
  let count = 0
  const report = (file, hits, message) => {
    for (const hit of hits) {
      console.log(`${file}:${hit.line} ${message}：${hit.text}`)
      count += 1
    }
  }
  for (const file of files) {
    const text = readFileSync(file, 'utf8')
    report(file, findUnexplainedSuppressWarnings(text), '@SuppressWarnings の直前の行に、理由の // のコメントを書く')
    if (!isEnumNameTarget(file)) continue
    report(file, findEnumNameConversions(text), '列挙型の変換に name() を渡す')
  }
  console.log(`バックエンドの書き方の違反：${count}`)
  process.exit(count ? 1 : 0)
}
