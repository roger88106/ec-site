// Markdown の囲みのコードブロック（```、~~~）を読む。Mermaid の検査（mermaid/mermaid-check）と、文書の構造の検査（見出しを読むとき、コードブロックの中を除く）が使う。
// CommonMark と同じく読む：
//   - 開き：0〜3 個の空白で字下げした、3 個以上の ` か ~ の並び。後ろは info string。` の並びの開きは、info string に ` を含められない（1 行のコードの範囲になるため）
//   - 閉じ：0〜3 個の空白で字下げした、開きと同じ字で、開き以上の長さの並び。後ろは空白だけ
//   - 閉じがなければ、文書の終わりまでが中身で、closed を false で返す
//   - 4 個以上の空白かタブで字下げした行は、開きにしない
// HTML のコメント（<!-- から --> まで。コメントの中の行は開きにも閉じにもしない）の中は読まない。1 行のコード（`...`）の中の <!-- は、コメントの開きにしない。
// 箇条書き・引用・HTML のブロック・front matter の中の囲みは、特別に扱わない。このリポジトリの文書は、そこにコードブロックを書いていないため。
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
  const lines = text.split(/\r?\n/)
  const blocks = []
  let open = null
  let inComment = false
  lines.forEach((line, index) => {
    if (open !== null) {
      if (isClose(line, open)) {
        open.closeLine = index + 1
        open.closed = true
        blocks.push(open)
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
    inComment = scanComment(line, inComment)
  })
  if (open !== null) blocks.push(open)
  return blocks
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
 * 1 行を読んだ後に、HTML のコメントの中にいるかを返す。
 *
 * @param {string} line 1 行
 * @param {boolean} inComment 行の始めにコメントの中にいたか
 * @returns {boolean} 行の終わりにコメントの中にいれば true
 */
function scanComment(line, inComment) {
  // コメントの外では、1 行のコードの中の <!-- を見ない（同じ長さの空白に替えるので、位置は変わらない）
  let outside = line
  for (const { start, end } of readCodeSpans(line).spans) outside = outside.slice(0, start) + ' '.repeat(end - start) + outside.slice(end)
  let state = inComment
  let position = 0
  for (;;) {
    if (state) {
      const end = line.indexOf('-->', position)
      if (end < 0) return true
      state = false
      position = end + 3
    } else {
      const start = outside.indexOf('<!--', position)
      if (start < 0) return false
      state = true
      position = start + 4
    }
  }
}
