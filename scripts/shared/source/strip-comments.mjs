// ソースのコメントを空白に置き換える（.ts・.tsx は stripComments、.java は stripJavaComments）。コメントだけを残す keepTsComments もある。
// コメントだけを残す読み方は、Kotlin（keepKotlinComments）、TOML（keepTomlComments）、properties（keepPropertiesComments）にもある。この 3 つは scan-code を使わず、それぞれの書き方で読む。
// 走査（コメント・文字列・正規表現の読み分け）は scan-code が行う。Java は、文字列とテキストブロックの中の // をコメントにしない。
//
// 限界：TSX の JSX のテキスト（<p>It's // here</p>）の中の '・"・// を、文字列やコメントと誤って判定する。
import { JAVA_SCAN, scanCode } from './scan-code.mjs'

/**
 * コメントを同じ長さの空白に置き換え、文字列とコードはそのまま残す。
 *
 * 改行は残すので、位置と行番号は変わらない。
 *
 * @param {string} text ソース
 * @param {Parameters<typeof scanCode>[1]} options 走査の選択肢
 * @param {boolean} [keepRegex] 正規表現のリテラルを残すか（false なら空白にする）
 * @param {boolean} [blankStrings] 文字列（テンプレートの文字の部分を含む）も空白にするか（false なら残す）
 * @returns {string} コメントを空白にしたソース
 */
function blankComments(text, options, keepRegex = true, blankStrings = false) {
  const parts = []
  scanCode(text, options, (type, from, to) => {
    const chunk = text.slice(from, to)
    const blank = type === 'comment' || (type === 'regex' && !keepRegex) || (type === 'string' && blankStrings)
    parts.push(blank ? chunk.replace(/[^\r\n]/g, ' ') : chunk)
  })
  return parts.join('')
}

/**
 * Java のコメント（行コメント、ブロックコメント、Javadoc）を、同じ長さの空白に置き換える。
 *
 * 改行は残すので、位置と行番号は変わらない。文字列、テキストブロック、文字リテラルの中の // などはコメントにしない。
 *
 * @param {string} text ソース
 * @returns {string} コメントを空白にしたソース
 */
export function stripJavaComments(text) {
  return blankComments(text, JAVA_SCAN)
}

/**
 * Java のコメントだけを残し、コメントの外（コード、文字列、テキストブロック）を空白に置き換える。
 *
 * 改行は残すので、位置と行番号は元のソースと同じ。コメントの判定は stripJavaComments と同じ（元のソースと stripJavaComments の結果を文字ごとに比べ、空白にされた文字だけを残す）。
 *
 * @param {string} text ソース
 * @returns {string} コメントだけを残したソース
 */
export function keepJavaComments(text) {
  return keepBlanked(text, stripJavaComments(text))
}

/**
 * TypeScript のコメントだけを残し、コメントの外を空白に置き換える。
 *
 * コメントの外は、コード、文字列、テンプレートの文字の部分と ${...} の中のコード、正規表現のリテラルである。
 * 改行は残すので、位置と行番号は元のソースと同じ。コメントの判定は stripComments と同じ。文字列の中の // や /*（URL、テスト名など）は残さない。
 * 限界：.tsx の JSX の文字（`<p>It's // here</p>`）は読み分けられない。文字の中の `'`・`"` を文字列の始まりと、`//` をコメントと読み違える。
 *
 * @param {string} text ソース
 * @returns {string} コメントだけを残したソース
 */
export function keepTsComments(text) {
  return keepBlanked(text, stripComments(text, { keepRegex: true }))
}

/**
 * コメントの区間（mask が 1 の字）だけを残し、他を空白にする。改行は残す。
 *
 * @param {string} text ソース
 * @param {Uint8Array} mask コメントの字の位置なら 1
 * @returns {string} コメントだけを残したソース
 */
function keepMasked(text, mask) {
  let result = ''
  for (let i = 0; i < text.length; i += 1) {
    const char = text[i]
    result += char === '\r' || char === '\n' || mask[i] ? char : ' '
  }
  return result
}

/**
 * Kotlin のソースを、位置 from から読み、コメントの字を mask に記録する。
 *
 * Java と違い、ブロックコメントは入れ子にでき、raw 文字列（""" で囲む。エスケープがない）と文字列の埋め込み（${...}。中はコード）がある。
 *
 * @param {string} text ソース
 * @param {number} from 読み始める位置
 * @param {Uint8Array} mask コメントの字の位置に 1 を書く
 * @param {boolean} inEmbedded 文字列の埋め込みの中か（対応する } で読み終える）
 * @returns {number} 読み終えた次の位置（埋め込みなら、閉じる } の次）
 */
function scanKotlin(text, from, mask, inEmbedded) {
  let i = from
  let braces = 0
  while (i < text.length) {
    if (text.startsWith('//', i)) {
      const end = text.indexOf('\n', i)
      const stop = end === -1 ? text.length : end
      mask.fill(1, i, stop)
      i = stop
    } else if (text.startsWith('/*', i)) {
      const start = i
      let depth = 0
      do {
        if (text.startsWith('/*', i)) {
          depth += 1
          i += 2
        } else if (text.startsWith('*/', i)) {
          depth -= 1
          i += 2
        } else {
          i += 1
        }
      } while (depth > 0 && i < text.length)
      mask.fill(1, start, Math.min(i, text.length))
    } else if (text.startsWith('"""', i)) {
      i = skipKotlinString(text, i + 3, mask, true)
    } else if (text[i] === '"') {
      i = skipKotlinString(text, i + 1, mask, false)
    } else if (text[i] === "'") {
      i += 1
      while (i < text.length && text[i] !== "'" && text[i] !== '\n') i += text[i] === '\\' ? 2 : 1
      i += 1
    } else if (text[i] === '{') {
      braces += 1
      i += 1
    } else if (text[i] === '}') {
      if (inEmbedded && braces === 0) return i + 1
      braces -= 1
      i += 1
    } else {
      i += 1
    }
  }
  return text.length
}

/**
 * Kotlin の文字列の中を、閉じる引用符の次の位置まで読む。埋め込み（${...}）の中はコードとして読む。
 *
 * @param {string} text ソース
 * @param {number} from 開く引用符の次の位置
 * @param {Uint8Array} mask コメントの字の位置に 1 を書く
 * @param {boolean} raw raw 文字列（""" で囲む）か。エスケープがなく、改行で終わらない
 * @returns {number} 閉じる引用符の次の位置（普通の文字列は、閉じなければ行の終わり）
 */
function skipKotlinString(text, from, mask, raw) {
  let i = from
  while (i < text.length) {
    if (raw && text.startsWith('"""', i)) {
      // 引用符が続くときは、最後の 3 つが閉じる（""""a"""" の最初の " は文字）
      while (text[i + 3] === '"') i += 1
      return i + 3
    }
    if (!raw && text[i] === '"') return i + 1
    if (!raw && text[i] === '\n') return i
    if (!raw && text[i] === '\\') i += 2
    else if (text.startsWith('${', i)) i = scanKotlin(text, i + 2, mask, true)
    else i += 1
  }
  return text.length
}

/**
 * Kotlin（Gradle の *.kts）のコメントだけを残し、コメントの外（コード、文字列、raw 文字列）を空白に置き換える。
 *
 * ブロックコメントの入れ子、raw 文字列、文字列の埋め込み（${...}）を、Kotlin の書き方で読む（Java の読み方は keepJavaComments）。
 * 改行は残すので、位置と行番号は元のソースと同じ。
 *
 * @param {string} text ソース
 * @returns {string} コメントだけを残したソース
 */
export function keepKotlinComments(text) {
  const mask = new Uint8Array(text.length)
  scanKotlin(text, 0, mask, false)
  return keepMasked(text, mask)
}

/**
 * Kotlin（Gradle の *.kts）のコメントの区間を返す。読み方は keepKotlinComments と同じ。
 *
 * @param {string} text ソース
 * @returns {{ from: number, to: number }[]} コメントの区間（始まり、終わりの次の位置）。続くコメントは 1 つにまとまる
 */
export function kotlinCommentRanges(text) {
  const mask = new Uint8Array(text.length)
  scanKotlin(text, 0, mask, false)
  const ranges = []
  for (let i = 0; i < text.length; i += 1) {
    if (!mask[i]) continue
    const from = i
    while (i < text.length && mask[i]) i += 1
    ranges.push({ from, to: i })
  }
  return ranges
}

/**
 * TOML（libs.versions.toml など）の # のコメントだけを残し、他を空白に置き換える。
 *
 * 文字列（"…"、'…'、複数行の """…""" と '''…'''）の中の # はコメントにしない。"…" と複数行の "…" の中は \ でエスケープする。
 * 改行は残すので、位置と行番号は元のソースと同じ。
 *
 * @param {string} text ソース
 * @returns {string} コメントだけを残したソース
 */
export function keepTomlComments(text) {
  const mask = new Uint8Array(text.length)
  let i = 0
  while (i < text.length) {
    if (text[i] === '#') {
      const end = text.indexOf('\n', i)
      const stop = end === -1 ? text.length : end
      mask.fill(1, i, stop)
      i = stop
    } else if (text.startsWith('"""', i) || text.startsWith("'''", i)) {
      const quote = text.slice(i, i + 3)
      i += 3
      while (i < text.length && !text.startsWith(quote, i)) i += quote[0] === '"' && text[i] === '\\' ? 2 : 1
      i += 3
    } else if (text[i] === '"' || text[i] === "'") {
      const quote = text[i]
      i += 1
      while (i < text.length && text[i] !== quote && text[i] !== '\n') i += quote === '"' && text[i] === '\\' ? 2 : 1
      i += 1
    } else {
      i += 1
    }
  }
  return keepMasked(text, mask)
}

/**
 * properties（gradle.properties など）のコメントだけを残し、他を空白に置き換える。
 *
 * コメントは、行頭（空白の後）の # と ! の行だけ。値の途中の # はコメントでなく、\ で終わる行の次の行は値の続きなので、# で始まってもコメントにしない。
 * 改行は残すので、位置と行番号は元のソースと同じ。
 *
 * @param {string} text ソース
 * @returns {string} コメントだけを残したソース
 */
export function keepPropertiesComments(text) {
  const mask = new Uint8Array(text.length)
  let continued = false
  let start = 0
  while (start <= text.length) {
    const end = text.indexOf('\n', start)
    const stop = end === -1 ? text.length : end
    const line = text.slice(start, stop).replace(/\r$/, '')
    const isComment = !continued && /^\s*[#!]/.test(line)
    if (isComment) mask.fill(1, start, start + line.length)
    // 行末の \ が奇数個なら、次の行に続く（コメントの行は続かない）
    continued = !isComment && (line.match(/\\*$/)?.[0].length ?? 0) % 2 === 1
    start = stop + 1
  }
  return keepMasked(text, mask)
}

/**
 * 元のソースと、コメントを空白にしたソースを文字ごとに比べ、空白にされた文字（コメント）だけを残す。
 *
 * @param {string} text 元のソース
 * @param {string} stripped コメントを空白にしたソース（text と同じ長さ）
 * @returns {string} コメントだけを残したソース
 */
function keepBlanked(text, stripped) {
  let result = ''
  for (let i = 0; i < text.length; i += 1) {
    const char = text[i]
    result += char === '\r' || char === '\n' || stripped[i] !== char ? char : ' '
  }
  return result
}

/**
 * コメントを空白に置き換える（行の数は変えない）。
 *
 * 文字列の中の // は、URL などなので消さない。テンプレートの `${...}` の中はコードとして読む（入れ子のテンプレートも数える）。
 *
 * @param {string} text ソース
 * @param {{ keepRegex?: boolean, blankStrings?: boolean }} [options] keepRegex が true なら、正規表現のリテラルを残す（中の文字を読む検査用。既定は空白にする）。
 *   blankStrings が true なら、文字列も空白にする（コードの書き方だけを探す検査用。既定は残す）
 * @returns {string} コメントを除いたソース
 */
export function stripComments(text, options = {}) {
  return blankComments(text, {}, options.keepRegex ?? false, options.blankStrings ?? false)
}
