// ソース（.ts・.tsx）のコメントを空白に置き換える。画面のソースを読む検査（hardcoded-text、frontend-rules）が共通で使う

/**
 * コメントを空白に置き換える（行の数は変えない）。文字列の中の // は、URL などなので消さない。
 *
 * @param {string} text ソース
 * @param {{ keepRegex?: boolean }} [options] keepRegex が true なら、正規表現のリテラルを残す（中の文字を読む検査用。既定は空白にする）
 * @returns {string} コメントを除いたソース
 */
export function stripComments(text, options = {}) {
  let result = ''
  let index = 0
  let quote = null
  while (index < text.length) {
    const char = text[index]
    const next = text[index + 1]
    if (quote) {
      result += char
      if (char === '\\') {
        result += next ?? ''
        index += 2
        continue
      }
      if (char === quote) quote = null
      index += 1
      continue
    }
    if (char === '/' && next === '*') {
      const end = text.indexOf('*/', index + 2)
      const stop = end < 0 ? text.length : end + 2
      result += text.slice(index, stop).replace(/[^\n]/g, ' ')
      index = stop
      continue
    }
    if (char === '/' && next === '/') {
      const end = text.indexOf('\n', index)
      const stop = end < 0 ? text.length : end
      result += ' '.repeat(stop - index)
      index = stop
      continue
    }
    if (char === '/' && next !== '>' && startsRegex(result)) {
      const stop = regexEnd(text, index)
      result += options.keepRegex ? text.slice(index, stop) : ' '.repeat(stop - index)
      index = stop
      continue
    }
    if (char === "'" || char === '"' || char === '`') quote = char
    result += char
    index += 1
  }
  return result
}

/**
 * 正規表現のリテラルの前に来る記号（この後の / は割り算でなく正規表現の始まり）。
 * } は JSX の属性の後の /> と見分けられないので入れない（ブロックの後の正規表現は、たいてい行の始まりにある）。
 * JSX の文字の中の記号の後の /（`<p>(/日本語)</p>` など）も正規表現と読み、行の終わりまでを見ない。まれなので、見逃しとして許す
 */
const BEFORE_REGEX_CHARS = '(,=:[!&|?{;'

/** 正規表現のリテラルの前に来る語 */
const BEFORE_REGEX_WORD = /\b(?:return|typeof|case|in|of|delete|void|throw|new|instanceof|yield|await)$/

/**
 * ここまでのコードの後の / が、正規表現のリテラルの始まりかを返す。
 *
 * @param {string} before ここまでのコード
 * @returns {boolean} 行の始まりか、式の始まりになる記号・語の後なら true（それ以外は割り算）
 */
function startsRegex(before) {
  const trimmed = before.replace(/[ \t]+$/, '')
  const last = trimmed.at(-1)
  return last === undefined || last === '\n' || BEFORE_REGEX_CHARS.includes(last) || BEFORE_REGEX_WORD.test(trimmed)
}

/**
 * 正規表現のリテラルの終わりの位置を返す。
 *
 * @param {string} text ファイルの中身
 * @param {number} start 始まりの / の位置
 * @returns {number} フラグの次の位置（行の終わりまでに閉じなければ、行の終わり）
 */
function regexEnd(text, start) {
  let index = start + 1
  let inClass = false
  while (index < text.length && text[index] !== '\n') {
    const char = text[index]
    if (char === '\\') {
      index += 2
      continue
    }
    if (char === '[') inClass = true
    else if (char === ']') inClass = false
    else if (char === '/' && !inClass) {
      index += 1
      while (index < text.length && /[a-z]/.test(text[index])) index += 1
      return index
    }
    index += 1
  }
  return index
}
