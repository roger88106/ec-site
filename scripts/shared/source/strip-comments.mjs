// ソースのコメントを空白に置き換える（.ts・.tsx は stripComments、.java は stripJavaComments）。コメントだけを残す keepTsComments もある。
// コメントだけを残す読み方は、Kotlin（keepKotlinComments）、TOML（keepTomlComments）、properties（keepPropertiesComments）にもある。
// 走査（コメント・文字列・正規表現の読み分け）は、Kotlin・TOML・properties の分も含めて scan-code が行い、ここは結果を空白に置き換えるだけ。Java は、文字列とテキストブロックの中の // をコメントにしない。
//
// 限界：TSX の JSX のテキスト（<p>It's // here</p>）の中の '・"・// を、文字列やコメントと誤って判定する。
import { JAVA_SCAN, kotlinCommentMask, propertiesCommentMask, scanCode, tomlCommentMask } from './scan-code.mjs'

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
 * Kotlin（Gradle の *.kts）のコメントだけを残し、コメントの外（コード、文字列、raw 文字列）を空白に置き換える。
 *
 * 読み方（ブロックコメントの入れ子、raw 文字列、文字列の埋め込み）は scan-code の kotlinCommentMask。
 * 改行は残すので、位置と行番号は元のソースと同じ。
 *
 * @param {string} text ソース
 * @returns {string} コメントだけを残したソース
 */
export function keepKotlinComments(text) {
  return keepMasked(text, kotlinCommentMask(text))
}

/**
 * TOML（libs.versions.toml など）の # のコメントだけを残し、他を空白に置き換える。
 *
 * 読み方は scan-code の tomlCommentMask。改行は残すので、位置と行番号は元のソースと同じ。
 *
 * @param {string} text ソース
 * @returns {string} コメントだけを残したソース
 */
export function keepTomlComments(text) {
  return keepMasked(text, tomlCommentMask(text))
}

/**
 * properties（gradle.properties など）のコメントだけを残し、他を空白に置き換える。
 *
 * 読み方は scan-code の propertiesCommentMask。改行は残すので、位置と行番号は元のソースと同じ。
 *
 * @param {string} text ソース
 * @returns {string} コメントだけを残したソース
 */
export function keepPropertiesComments(text) {
  return keepMasked(text, propertiesCommentMask(text))
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
