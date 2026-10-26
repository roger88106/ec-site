// ソースのコメントを空白に置き換える。走査（コメント・文字列・正規表現の読み分け）は scan-code が行う。
// - .ts・.tsx：画面のソースを読む検査（hardcoded-text、frontend-rules）が共通で使う（stripComments）
// - .java：Java のソースを読む検査（java-rules、env-defaults）が共通で使う（stripJavaComments）
import { scanCode } from './scan-code.mjs'

/** Java の走査の選択肢。正規表現のリテラルとテンプレートがなく、テキストブロックがある */
const JAVA_SCAN = { regex: false, templates: false, textBlocks: true }

/**
 * コメントを同じ長さの空白に置き換え、文字列とコードはそのまま残す。改行は残すので、位置と行番号は変わらない。
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
 * Java のコメント（行コメント、ブロックコメント、Javadoc）を、同じ長さの空白に置き換える。改行は残すので、位置と行番号は変わらない。<br />
 * 文字列、テキストブロック、文字リテラルの中の // などはコメントにしない。
 *
 * @param {string} text ソース
 * @returns {string} コメントを空白にしたソース
 */
export function stripJavaComments(text) {
  return blankComments(text, JAVA_SCAN)
}

/**
 * Java のコメントだけを残し、コメントの外（コード、文字列、テキストブロック）を空白に置き換える。改行は残すので、位置と行番号は元のソースと同じ。<br />
 * コメントの判定は stripJavaComments と同じ（元のソースと stripJavaComments の結果を文字ごとに比べ、空白にされた文字だけを残す）。
 *
 * @param {string} text ソース
 * @returns {string} コメントだけを残したソース
 */
export function keepJavaComments(text) {
  const stripped = stripJavaComments(text)
  let result = ''
  for (let i = 0; i < text.length; i += 1) {
    const char = text[i]
    result += char === '\r' || char === '\n' || stripped[i] !== char ? char : ' '
  }
  return result
}

/**
 * コメントを空白に置き換える（行の数は変えない）。文字列の中の // は、URL などなので消さない。
 * テンプレートの `${...}` の中はコードとして読む（入れ子のテンプレートも数える）。
 *
 * @param {string} text ソース
 * @param {{ keepRegex?: boolean, blankStrings?: boolean }} [options] keepRegex が true なら、正規表現のリテラルを残す（中の文字を読む検査用。既定は空白にする）。
 *   blankStrings が true なら、文字列も空白にする（コードの書き方だけを探す検査用。既定は残す）
 * @returns {string} コメントを除いたソース
 */
export function stripComments(text, options = {}) {
  return blankComments(text, {}, options.keepRegex ?? false, options.blankStrings ?? false)
}
