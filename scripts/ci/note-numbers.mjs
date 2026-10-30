// 文書の注の番号（※1 など）が、1 つの文書の中で重複せず、出る順に並ぶことを確認する。
//   - 定義：行頭（リストの中の字下げを含む）の `※N：`。同じ番号を 2 回定義したら失敗にする。枝番（※2a）も 1 つの注として読み、順序は ※2、※2a、※3 の順
//   - 本文の参照：定義の行以外の `※N`（他の文書の注を指す `第 N 節 ※N`・`第 N 節の ※N`・`の「…」 ※N`・`の「…」の ※N` は除く。「この節 ※1」と、文書の名前のない `「…」 ※N` は同じ文書の参照）。定義も参照も、先の番号より小さい番号が新しく出たら失敗にする（同じ注を何度参照してもよい）
// 番号が飛ぶこと、参照だけ・定義だけの注は見ない。コードブロック（CommonMark の囲み：字下げしてよく、開きと同じ字（バッククォートか ~）で、同じ数以上で閉じる）とインラインコードの中は数えない。
// 見る文書は ja-text と同じ（ja-text の isTargetDoc を使う）。
// 使い方：node scripts/ci/note-numbers.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { isTargetDoc } from './ja-text.mjs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'

/** 注の定義の行（行頭の `※N：`。リストの中の字下げも可。枝番は `※2a：`） */
const DEFINITION = /^\s*※(\d+[a-z]?)：/

/** 本文の参照 */
const REFERENCE = /※(\d+[a-z]?)/g

/**
 * 他の文書の注を指す参照（`第 N 節 ※N`、`第 N 節の ※N`、文書の名前かリンクの後の `の「…」 ※N`・`の「…」の ※N`）。この文書の参照に数えない。
 * 文書の名前を付けない `「…」 ※N` は、同じ文書にある見出しの注を指すので数える。
 */
const OTHER_DOC_REFERENCE = /(?:第 ?\d+(?:\.\d+)* ?節|の「[^「」\n]*」(?:の「[^「」\n]*」)*)(?: ?の)? ?※\d+[a-z]?/g

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
 * @returns {{ line: number, message: string }[]} 問題の行と内容
 */
export function findNoteProblems(text) {
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
    const body = line.replace(INLINE_CODE, '').replace(OTHER_DOC_REFERENCE, '')
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
  const files = listGitFiles().filter((file) => isTargetDoc(file))
  exitIfNoFiles(files, '注の番号を調べる文書')
  let count = 0
  for (const file of files) {
    for (const problem of findNoteProblems(readFileSync(file, 'utf8'))) {
      console.log(`${file}:${problem.line} ${problem.message}`)
      count += 1
    }
  }
  console.log(`注の番号の問題：${count}`)
  process.exit(count ? 1 : 0)
}
