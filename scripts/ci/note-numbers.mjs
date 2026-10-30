// 文書の注の番号（※1 など）が、1 つの文書の中で重複せず、出る順に並ぶことを確認する。
//   - 定義：行頭（リストの中の字下げを含む）の `※N：`。同じ番号を 2 回定義したら失敗にする。枝番（※2a）も 1 つの注として読み、順序は ※2、※2a、※3 の順
//   - 本文の参照：定義の行以外の `※N`（他の文書の注を指す `第 N 節 ※N`・`第 N 節の ※N` と、リンク・文書の名前・パスの後の `の「…」 ※N`・`の「…」の ※N` は除く。
//     前の文書は、doc-refs の resolveDocument で、インラインコードを残した元の行から決める（パスの後と同じく、インラインコードで書いたパスの後も除く）。
//     外のリンクの後も除く（doc-refs は先を見ないが、他の文書を指すため）。「この節 ※1」、文書の名前のない `「…」 ※N`、`この文書の「…」 ※N`・`表の「…」 ※N` は同じ文書の参照）。定義も参照も、先の番号より小さい番号が新しく出たら失敗にする（同じ注を何度参照してもよい）
// 番号が飛ぶこと、参照だけ・定義だけの注は見ない。コードブロック（CommonMark の囲み：字下げしてよく、開きと同じ字（バッククォートか ~）で、同じ数以上で閉じる）とインラインコードの中は数えない。
// 見る文書は ja-text と同じ（ja-text の isTargetDoc を使う）。
// 使い方：node scripts/ci/note-numbers.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { documentIndex, readHeadings, resolveDocument } from './doc-refs.mjs'
import { isTargetDoc } from './ja-text.mjs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'

/** 注の定義の行（行頭の `※N：`。リストの中の字下げも可。枝番は `※2a：`） */
const DEFINITION = /^\s*※(\d+[a-z]?)：/

/** 本文の参照 */
const REFERENCE = /※(\d+[a-z]?)/g

/** 他の文書の注を指す参照（`第 N 節 ※N`、`第 N 節の ※N`）。この文書の参照に数えない */
const SECTION_NOTE = /第 ?\d+(?:\.\d+)* ?節(?: ?の)? ?※\d+[a-z]?/g

/**
 * 見出しの後の注の参照（`の「…」 ※N`・`の「…」の ※N`）。前がリンク（外のリンクを含む）か文書の名前かパス（doc-refs の resolveDocument で決める）で、他の文書を指すときだけ、この文書の参照に数えない。
 * 文書の名前を付けない `「…」 ※N` と、`この文書の「…」 ※N`・`表の「…」 ※N` は、同じ文書の注を指すので数える。
 */
const HEADING_NOTE = /の「[^「」\n]*」(?:の「[^「」\n]*」)*(?: ?の)? ?※\d+[a-z]?/g

/** 囲みのコードブロックの開き・閉じの行（字下げしてよい。文字は ` か ~ で 3 つ以上） */
const FENCE = /^\s*(`{3,}|~{3,})(.*)$/

/** インラインコード（同じ数のバッククォートで囲んだ所） */
const INLINE_CODE = /(`+)(?!`).*?(?<!`)\1(?!`)/g

/**
 * 注の番号を、並びを比べる数に直す（※2 < ※2a < ※3）。
 *
 * @param {string} id 番号（枝番を含む）
 * @returns {number} 比べる数
 */
function order(id) {
  const match = id.match(/^(\d+)([a-z]?)$/)
  return Number(match[1]) * 100 + (match[2] ? match[2].charCodeAt(0) - 96 : 0)
}

/**
 * 1 つの文書の注の番号の問題を探す。
 *
 * @param {string} text 文書
 * @param {Map<string, string>} [names] 文書の名前からパスへの対応（doc-refs の documentIndex。長い名前から順）
 * @param {string} [file] 文書のパス（リンクの先を決めるため）
 * @param {Map<string, string>} [basenames] ファイル名からパスへの対応（doc-refs の documentIndex）
 * @returns {{ line: number, message: string }[]} 問題の行と内容
 */
export function findNoteProblems(text, names = new Map(), file = '', basenames = new Map()) {
  /**
   * 1 行から、数える参照の部分を残す。インラインコードと他の文書の注は、同じ長さの空白に替える（位置を保つため）。
   * 見出しの後の注の前の文書は、インラインコードを残した元の行で決める（`` `docs/x.md` の「…」 ※3 `` のパスを読むため）。
   */
  const countedPart = (line) => {
    const blank = (text) => ' '.repeat(text.length)
    const masked = line.replace(INLINE_CODE, blank).replace(SECTION_NOTE, blank)
    return masked.replace(HEADING_NOTE, (match, offset) => {
      const before = line.slice(0, offset)
      // 外のリンク（`[x](https://…)の「Y」`）は、doc-refs は先を見ないが、他の文書なので除く
      if (/\]\((?:https?:|mailto:)[^)]*\)\s*$/.test(before)) return blank(match)
      const target = resolveDocument(file, before, names, basenames)
      return target !== null && target !== file ? blank(match) : match
    })
  }
  const problems = []
  const defined = new Set()
  const referenced = new Set()
  let lastDefinition = null
  let lastReference = null
  let fence = null
  text.split(/\r?\n/).forEach((line, index) => {
    const fenceMatch = line.match(FENCE)
    if (fence) {
      if (fenceMatch && fenceMatch[1][0] === fence.char && fenceMatch[1].length >= fence.length && fenceMatch[2].trim() === '') fence = null
      return
    }
    if (fenceMatch && !(fenceMatch[1][0] === '`' && fenceMatch[2].includes('`'))) {
      fence = { char: fenceMatch[1][0], length: fenceMatch[1].length }
      return
    }
    const lineNumber = index + 1
    const definition = line.match(DEFINITION)
    if (definition) {
      const id = definition[1]
      if (defined.has(id)) {
        problems.push({ line: lineNumber, message: `※${id} の定義が重複している` })
      } else {
        if (lastDefinition !== null && order(id) < order(lastDefinition)) problems.push({ line: lineNumber, message: `※${id} の定義が、※${lastDefinition} の後で順に並んでいない` })
        defined.add(id)
        if (lastDefinition === null || order(id) > order(lastDefinition)) lastDefinition = id
      }
      return
    }
    const body = countedPart(line)
    for (const match of body.matchAll(REFERENCE)) {
      const id = match[1]
      if (referenced.has(id)) continue
      if (lastReference !== null && order(id) < order(lastReference)) problems.push({ line: lineNumber, message: `※${id} の参照が、※${lastReference} の後で順に並んでいない` })
      referenced.add(id)
      if (lastReference === null || order(id) > order(lastReference)) lastReference = id
    }
  })
  return problems
}

if (isMain(import.meta.url)) {
  const allFiles = listGitFiles()
  const files = allFiles.filter((file) => isTargetDoc(file))
  exitIfNoFiles(files, '注の番号を調べる文書')
  const { names, basenames } = documentIndex(allFiles, (file) => readHeadings(readFileSync(file, 'utf8')).title)
  let count = 0
  for (const file of files) {
    for (const problem of findNoteProblems(readFileSync(file, 'utf8'), names, file, basenames)) {
      console.log(`${file}:${problem.line} ${problem.message}`)
      count += 1
    }
  }
  console.log(`注の番号の問題：${count}`)
  process.exit(count ? 1 : 0)
}
