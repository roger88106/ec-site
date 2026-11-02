// Markdown の囲みのコードブロック（```、~~~）を読む。Mermaid の検査（mermaid/mermaid-check）と、文書の構造の検査が使う。文書の構造の検査は、コードブロックの中を除いて見出しを読むために使う。
// CommonMark と同じく読む：
//   - 開き：0〜3 個の空白で字下げした、3 個以上の ` か ~ の並び。後ろは info string。` の並びの開きは、info string に ` を含められない（1 行のコードの範囲になるため）
//   - 閉じ：0〜3 個の空白で字下げした、開きと同じ字で、開き以上の長さの並び。後ろは空白だけ
//   - 閉じの行がなければ、文書の終わりまでを中身とし、closed を false にして返す
//   - 4 個以上の空白かタブで字下げした行は、開きにしない
// HTML のコメント（<!-- から --> まで。コメントの中の行は開きにも閉じにもしない）の中は読まない。コメントの範囲も返す（readBlocks）。
//   - 行頭（0〜3 個の空白の後）の <!-- は、--> の行まで続くコメントにする（--> がなければ文書の終わりまで）
//   - 行の途中の <!-- は、同じ行の --> までをコメントにする。同じ行に --> がなければ、コメントにしない（ただの文字）
//   - 1 行のコード（`...`）の中の <!-- は、コメントの開きにしない
// 箇条書き・引用・HTML のブロック・front matter の中のコードブロックは、これらの構造を解釈せず、通常のコードブロックと同じ規則で読む。このリポジトリの文書は、これらの中にコードブロックを書いていないため。
import { readCodeSpans } from './code-span.mjs'

/** 囲みの開きの行（字下げ 0〜3 個、3 個以上の ` か ~、info string） */
const OPEN = /^ {0,3}(`{3,}|~{3,})(.*)$/

/**
 * 文書の中の囲みのコードブロックを、出る順に読む。
 *
 * @param {string} text 文書の内容（改行は LF でも CRLF でもよい。末尾に改行がなくてもよい）
 * @returns {{ char: string, length: number, info: string, line: number, closeLine: number | null, closed: boolean, body: string[] }[]}
 *   各要素は次の値を持つ。
 *   - char：囲みの字（` か ~）
 *   - length：開きの長さ
 *   - info：info string（前後の空白を除く）
 *   - line：開きの行（1 から）
 *   - closeLine：閉じの行（閉じがなければ null）
 *   - closed：閉じたか
 *   - body：中身の行（開きと閉じの行は含めない）。i 番目の行は、元の文書の line + 1 + i 行にある
 */
export function readCodeBlocks(text) {
  return readBlocks(text).codeBlocks
}

/**
 * 文書の中の囲みのコードブロックと、HTML のコメントの範囲を、出る順に読む。
 *
 * @param {string} text 文書の内容（改行は LF でも CRLF でもよい）
 * @returns {{ codeBlocks: object[], comments: { line: number, start: number, end: number }[] }}
 *   codeBlocks は readCodeBlocks の結果と同じ形。comments はコメントの範囲を行ごとに分けたもの（line は行（1 から）、start と end は行の中の位置。end は含まない）
 */
export function readBlocks(text) {
  const lines = text.split(/\r?\n/)
  const codeBlocks = []
  const comments = []
  let open = null
  let inComment = false
  lines.forEach((line, index) => {
    if (open !== null) {
      if (isClose(line, open)) {
        open.closeLine = index + 1
        open.closed = true
        codeBlocks.push(open)
        open = null
      } else {
        open.body.push(line)
      }
      return
    }
    if (!inComment) {
      const match = OPEN.exec(line)
      if (match && !(match[1][0] === '`' && match[2].includes('`'))) {
        open = { char: match[1][0], length: match[1].length, info: match[2].trim(), line: index + 1, closeLine: null, closed: false, body: [] }
        return
      }
    }
    const scanned = scanComment(line, inComment)
    for (const range of scanned.ranges) comments.push({ line: index + 1, ...range })
    inComment = scanned.inComment
  })
  if (open !== null) codeBlocks.push(open)
  return { codeBlocks, comments }
}

/**
 * 行が、開いているコードブロックの閉じか。
 *
 * @param {string} line 1 行
 * @param {{ char: string, length: number }} open 開いているコードブロック
 * @returns {boolean} 閉じなら true
 */
function isClose(line, open) {
  const match = /^ {0,3}(`+|~+)[ \t]*$/.exec(line)
  return match !== null && match[1][0] === open.char && match[1].length >= open.length
}

/**
 * 1 行の中の HTML のコメントの範囲と、行の終わりにコメントの中にいるかを返す。
 *
 * @param {string} line 1 行
 * @param {boolean} inComment 行の始めにコメントの中にいたか
 * @returns {{ inComment: boolean, ranges: { start: number, end: number }[] }} 行の終わりにコメントの中にいれば inComment が true。ranges はコメントの範囲（end は含まない）
 */
function scanComment(line, inComment) {
  const ranges = []
  let position = 0
  if (inComment) {
    const end = line.indexOf('-->')
    if (end < 0) return { inComment: true, ranges: [{ start: 0, end: line.length }] }
    ranges.push({ start: 0, end: end + 3 })
    position = end + 3
  }
  // コメントの外では、1 行のコードの中の <!-- を見ない（同じ長さの空白に替えるので、位置は変わらない）
  let outside = line
  for (const { start, end } of readCodeSpans(line).spans) outside = outside.slice(0, start) + ' '.repeat(end - start) + outside.slice(end)
  for (;;) {
    const start = outside.indexOf('<!--', position)
    if (start < 0) return { inComment: false, ranges }
    const end = line.indexOf('-->', start + 4)
    if (end >= 0) {
      ranges.push({ start, end: end + 3 })
      position = end + 3
      continue
    }
    // 閉じのない <!-- は、行頭にあれば後ろの行に続くコメントにし、行の途中にあればただの文字にする（後ろに --> がないので、後ろの <!-- も同じ）
    if (!inComment && /^ {0,3}$/.test(line.slice(0, start))) {
      ranges.push({ start, end: line.length })
      return { inComment: true, ranges }
    }
    return { inComment: false, ranges }
  }
}
