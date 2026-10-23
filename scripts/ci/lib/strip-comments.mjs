// ソースのコメントを空白に置き換える。
// - .ts・.tsx：画面のソースを読む検査（hardcoded-text、frontend-rules）が共通で使う（stripComments）
// - .java：Java のソースを読む検査（java-rules、env-defaults）が共通で使う（stripJavaComments）

/**
 * Java のコメント（行コメント、ブロックコメント、Javadoc）を、同じ長さの空白に置き換える。改行は残すので、位置と行番号は変わらない。<br />
 * 文字列、テキストブロック、文字リテラルの中の // などはコメントにしない。
 *
 * @param {string} text ソース
 * @returns {string} コメントを空白にしたソース
 */
export function stripJavaComments(text) {
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
 * } は JSX の属性の後の /> と見分けられないので入れない（改行を挟んだ } の後だけを、ブロックの後の文の始まりとして正規表現と読む）。
 * JSX の文字の中の記号の後の /（`<p>(/日本語)</p>` など）も正規表現と読み、行の終わりまでを見ない。まれなので、見逃しとして許す
 */
const BEFORE_REGEX_CHARS = '(,=:[!&|?{;'

/** 正規表現のリテラルの前に来る語 */
const BEFORE_REGEX_WORD = /\b(?:return|typeof|case|in|of|delete|void|throw|new|instanceof|yield|await)$/

/**
 * ここまでのコードの後の / が、正規表現のリテラルの始まりかを返す。
 * 直前の意味のある字（空白と改行を飛ばす）で決める。行頭の / も、前の行の終わりが識別子や ) なら割り算と読む（式を改行して演算子を行頭に置く書き方）。
 * 簡易の判定のため、if (a) の後の行頭の正規表現は割り算と読み、生のソースの行コメントの後の / はコメントの最後の字で決まる
 *
 * @param {string} before ここまでのコード
 * @returns {boolean} ソースの始まりか、式の始まりになる記号・語の後か、改行を挟んだ } の後なら true（それ以外は割り算）
 */
export function startsRegex(before) {
  const trimmed = before.replace(/\s+$/, '')
  const last = trimmed.at(-1)
  if (last === undefined || BEFORE_REGEX_CHARS.includes(last) || BEFORE_REGEX_WORD.test(trimmed)) return true
  return last === '}' && before.slice(trimmed.length).includes('\n')
}

/**
 * 正規表現のリテラルの終わりの位置を返す。
 *
 * @param {string} text ファイルの中身
 * @param {number} start 始まりの / の位置
 * @returns {number} フラグの次の位置（行の終わりまでに閉じなければ、行の終わり）
 */
export function regexEnd(text, start) {
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
