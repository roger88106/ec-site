// ソースを、コード・コメント・文字列・正規表現のリテラルの区間に分けて読む。コメント・文字列・正規表現を読み飛ばす処理は、ここだけにある。
// - 閉じ括弧の探し方（closing-paren）、コメントの除去（strip-comments）、テストの関数の読み方（test-files）が使う
// - TypeScript と Java の違いは選択肢で決める。Java には正規表現のリテラルとテンプレートがなく、テキストブロックがある

/**
 * 正規表現のリテラルの前に来る記号（この後の / は割り算でなく正規表現の始まり）。
 * } は JSX の属性の後の /> と見分けられないので入れない（改行を挟んだ } の後だけを、ブロックの後の文の始まりとして正規表現と読む）。
 * JSX の文字の中の記号の後の /（`<p>(/日本語)</p>` など）も正規表現と読み、行の終わりまでを見ない。まれなので、見逃しとして許す
 */
const BEFORE_REGEX_CHARS = '(,=:[!&|?{;'

/** 正規表現のリテラルの前に来る語 */
const BEFORE_REGEX_WORD = /\b(?:return|typeof|case|in|of|delete|void|throw|new|instanceof|yield|await)$/

/** 正規表現かの判定で、/ の前に遡る字数 */
const REGEX_LOOKBEHIND = 200

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

/**
 * テンプレートの文字の部分を、閉じる ` か `${` の終わりまで読む。
 *
 * @param {string} text ソース
 * @param {number} from 文字の部分の始まり（` の次、または ${...} を閉じる } の次）
 * @returns {{ stop: number, open: boolean }} 次の位置と、`${` で止まったか（閉じる ` か、ソースの終わりで止まったら false）
 */
function readTemplateBody(text, from) {
  let i = from
  while (i < text.length) {
    if (text[i] === '\\') i += 2
    else if (text[i] === '`') return { stop: i + 1, open: false }
    else if (text[i] === '$' && text[i + 1] === '{') return { stop: i + 2, open: true }
    else i += 1
  }
  return { stop: text.length, open: false }
}

/**
 * ソースを読み進め、区間ごとに visit を呼ぶ。
 * - 'code'：コードの 1 字（空白も含む）
 * - 'comment'：行コメント（改行は含まない）かブロックコメント
 * - 'string'：' と " の文字列（改行で終える。JSX の it's などの単独の引用符で、後ろを全て文字列と読まない）、Java のテキストブロック、
 *   テンプレートの文字の部分（` から `${` まで、`${...}` を閉じる } から次の `${` か閉じる ` まで）
 * - 'regex'：正規表現のリテラル（フラグを含む）
 * `${...}` の中はコードとして読む（入れ子のテンプレートも数える）。
 *
 * @param {string} text ソース
 * @param {{ start?: number, regex?: boolean, templates?: boolean, textBlocks?: boolean }} [options]
 *   start：読み始める位置（既定は 0）、regex：正規表現のリテラルを読む（既定は true。Java は false）、
 *   templates：テンプレートを読む（既定は true。Java は false）、textBlocks：テキストブロックを読む（既定は false。Java は true）
 * @param {(type: 'code' | 'comment' | 'string' | 'regex', from: number, to: number, depth: number) => boolean | void} visit
 *   区間の始まりと終わりの次の位置、区間を読んだ後の `${...}` の深さ（テンプレートの外は 0）を受け取る。true を返すと止まる
 * @returns {number} visit が true を返した区間の始まりの位置。止まらなければソースの終わり
 */
export function scanCode(text, options, visit) {
  const { start = 0, regex = true, templates = true, textBlocks = false } = options ?? {}
  // `${...}` を開いたときの { の深さを積む。対応する } で、テンプレートの文字の部分に戻る
  let braces = 0
  const open = []
  let i = start
  const emit = (type, from, to) => visit(type, from, to, open.length) === true
  while (i < text.length) {
    const ch = text[i]
    const next = text[i + 1]
    let type = 'code'
    let stop = i + 1
    if (ch === '/' && next === '/') {
      const end = text.indexOf('\n', i)
      type = 'comment'
      stop = end === -1 ? text.length : end
    } else if (ch === '/' && next === '*') {
      const end = text.indexOf('*/', i + 2)
      type = 'comment'
      stop = end === -1 ? text.length : end + 2
    } else if (regex && ch === '/' && next !== '>' && startsRegex(text.slice(Math.max(start, i - REGEX_LOOKBEHIND), i))) {
      type = 'regex'
      stop = regexEnd(text, i)
    } else if (textBlocks && text.startsWith('"""', i)) {
      const end = text.indexOf('"""', i + 3)
      type = 'string'
      stop = end === -1 ? text.length : end + 3
    } else if (ch === "'" || ch === '"') {
      let j = i + 1
      while (j < text.length && text[j] !== ch && text[j] !== '\n') j += text[j] === '\\' ? 2 : 1
      type = 'string'
      stop = Math.min(j + 1, text.length)
    } else if (templates && ch === '`') {
      const body = readTemplateBody(text, i + 1)
      if (body.open) open.push(braces)
      type = 'string'
      stop = body.stop
    } else if (ch === '{') {
      braces += 1
    } else if (ch === '}') {
      if (open.length > 0 && open.at(-1) === braces) {
        open.pop()
        const body = readTemplateBody(text, i + 1)
        if (body.open) open.push(braces)
        type = 'string'
        stop = body.stop
      } else {
        braces -= 1
      }
    }
    if (emit(type, i, stop)) return i
    i = stop
  }
  return text.length
}

/**
 * テンプレートリテラルの終わりの次の位置を返す。`${...}` の中の式は、コードとして読む（中の ` で終わらない）。
 *
 * @param {string} text ソース
 * @param {number} start 始まりの ` の位置
 * @returns {number} 終わりの次の位置。閉じなければソースの終わり
 */
export function templateEnd(text, start) {
  let end = text.length
  scanCode(text, { start }, (type, from, to, depth) => {
    // 外のテンプレートが閉じるのは、` で終わる文字の区間が、`${...}` の外に出たとき
    if (type === 'string' && depth === 0 && text[to - 1] === '`') {
      end = to
      return true
    }
  })
  return end
}
