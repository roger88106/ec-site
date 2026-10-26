// 文書の注の番号（※1 など）が、1 つの文書の中で重複せず、出る順に並ぶことを確認する。
//   - 定義：行頭の `※N：`。同じ番号を 2 回定義したら失敗にする
//   - 本文の参照：定義の行以外の `※N`（他の文書の注を指す `第 N 節 ※N` は除く）。定義も参照も、先の番号より小さい番号が新しく出たら失敗にする（同じ注を何度参照してもよい）
// 番号が飛ぶこと、参照だけ・定義だけの注は見ない。コードブロックの中は数えない。
// 見るのは docs/（アーカイブした spec を除く）、README.md、AGENTS.md、.claude/skills/ の Markdown（doc-terms と同じ）。
// 使い方：node scripts/ci/note-numbers.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'

/** 調べる文書 */
const DOCS = /^(docs\/.*|README|AGENTS|\.claude\/skills\/.*)\.md$/

/** 見ない文書（アーカイブした spec は当時の記録） */
const EXCLUDED = /^docs\/specs\/archive\//

/** 注の定義の行（行頭の `※N：`） */
const DEFINITION = /^※(\d+)：/

/** 本文の参照（`第 N 節 ※N` は他の文書の注を指すので、数えない） */
const REFERENCE = /(?<!節 ?)※(\d+)/g

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
  let lastDefinition = 0
  let lastReference = 0
  let inCode = false
  text.split(/\r?\n/).forEach((line, index) => {
    if (line.startsWith('```')) {
      inCode = !inCode
      return
    }
    if (inCode) return
    const lineNumber = index + 1
    const definition = line.match(DEFINITION)
    if (definition) {
      const number = Number(definition[1])
      if (defined.has(number)) {
        problems.push({ line: lineNumber, message: `※${number} の定義が重複している` })
      } else {
        if (number < lastDefinition) problems.push({ line: lineNumber, message: `※${number} の定義が、※${lastDefinition} の後で順に並んでいない` })
        defined.add(number)
        lastDefinition = Math.max(lastDefinition, number)
      }
      return
    }
    for (const match of line.matchAll(REFERENCE)) {
      const number = Number(match[1])
      if (referenced.has(number)) continue
      if (number < lastReference) problems.push({ line: lineNumber, message: `※${number} の参照が、※${lastReference} の後で順に並んでいない` })
      referenced.add(number)
      lastReference = Math.max(lastReference, number)
    }
  })
  return problems
}

/**
 * 調べる文書かを返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} 調べるなら true
 */
export function isTargetDoc(file) {
  return DOCS.test(file) && !EXCLUDED.test(file)
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
