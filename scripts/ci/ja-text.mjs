// 日本語の文を確認する。言い換えの一覧は ja-text.json。
//   error：使わない語。見つけたら失敗にする。使わない語を一部に含む正しい語は、項目の allow に {許す語: 理由} で書く（その中の一致は数えない）
//       理由のない許す語と、どの文書にも使われなくなった許す語は失敗にする
//   warning：文脈で言い換えるかを決める語。--warning（--caution も同じ）を付けたときだけ示し、失敗にしない（文書を書くときに見る）
// 見るのは docs/（アーカイブした spec を除く）、README.md、AGENTS.md、.claude/skills/ の Markdown と、.github/ の yml・yaml と Markdown。
// コードも見る：アーカイブの外の .java、.kts、.ts、.tsx、.mjs、.cjs、.sh の、コメントとテスト名（@DisplayName、describe・it・test の名前）だけを調べる。
//   コメントの外の文字列とコード（画面の文言など）は調べない。取り出し方は lib/comment-text
// 使い方：node scripts/ci/ja-text.mjs [--warning]（リポジトリのルートで）
//   node scripts/ci/ja-text.mjs --message <ファイル>：コミットメッセージのファイルを調べる（# で始まる行は除く）。error があれば失敗にする
import { readFileSync } from 'node:fs'
import { checkExclusions } from './lib/exclusions.mjs'
import { codeKind, commentText } from './lib/comment-text.mjs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'

/** 調べる文書 */
const DOCS = /^((docs\/.*|README|AGENTS|\.claude\/skills\/.*)\.md|\.github\/.*\.(md|ya?ml))$/

/** 見ない文書（アーカイブした spec（当時の記録）と、言い換える前の語を例に挙げる言い回しの規則） */
const EXCLUDED = /^(docs\/specs\/archive\/|\.claude\/skills\/docs\/tech-writing\.md$)/

/**
 * 調べないコード。
 * - 生成したコード、依存のパッケージ、ビルドの出力（書き手が直せない）
 * - アーカイブした spec の下（当時の記録）
 * - 語を例に挙げるテスト（サンプルの文に、使わない語を書く）
 */
const CODE_EXCLUDED = /(^|\/)(node_modules|generated|build|dist|\.next)\/|^docs\/specs\/archive\/|^scripts\/ci\/(ja-text\.test\.mjs|lib\/comment-text\.test\.mjs)$/

/**
 * 行の中に、使わない語を、許す語の外で書いているかを返す。
 * 日本語に語の境界はないので、使わない語を一部に含む正しい語は、語ごとの許す語（allow）で救う。
 *
 * @param {string} line 行
 * @param {{ word: string, allow?: Record<string, string> }} term 使わない語と許す語（許す語と理由）
 * @returns {boolean} 許す語の外に一致があれば true
 */
function hasDisallowedMatch(line, term) {
  const allowed = []
  for (const allow of Object.keys(term.allow ?? {})) {
    for (let at = line.indexOf(allow); at !== -1; at = line.indexOf(allow, at + 1)) {
      allowed.push([at, at + allow.length])
    }
  }
  for (let at = line.indexOf(term.word); at !== -1; at = line.indexOf(term.word, at + 1)) {
    const end = at + term.word.length
    if (!allowed.some(([from, to]) => from <= at && end <= to)) return true
  }
  return false
}

/**
 * 一覧の語を書いた行を探す。許す語が使わない語を含まないときは、設定の誤りとして例外にする。
 *
 * @param {string} text 文書
 * @param {{ word: string, use: string, allow?: Record<string, string> }[]} terms 語と言い換えと許す語（許す語は {許す語: 理由}）
 * @returns {{ line: number, word: string, use: string }[]} 見つけた行と語
 */
export function findTerms(text, terms) {
  for (const term of terms) {
    if (Array.isArray(term.allow)) {
      throw new Error(`「${term.word}」の allow は {許す語: 理由} の形で書く（ja-text.json）`)
    }
    for (const allow of Object.keys(term.allow ?? {})) {
      if (!allow.includes(term.word)) {
        throw new Error(`許す語「${allow}」が使わない語「${term.word}」を含まない（ja-text.json）`)
      }
    }
  }
  const hits = []
  text.split('\n').forEach((line, index) => {
    for (const term of terms) {
      if (hasDisallowedMatch(line, term)) hits.push({ line: index + 1, ...term })
    }
  })
  return hits
}

/**
 * 許す語の誤り（理由がない、どの文書にも使われなくなった）を、表示する文にして返す。
 *
 * @param {string[]} texts 調べる文書の内容（全て）
 * @param {{ word: string, allow?: Record<string, string> }[]} terms 語と許す語
 * @returns {string[]} 誤りの文
 */
export function findAllowProblems(texts, terms) {
  return terms.flatMap((term) =>
    checkExclusions(`ja-text.json の「${term.word}」の allow`, term.allow ?? {}, (allow) => texts.some((text) => text.includes(allow))),
  )
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

/**
 * 調べるコードかを返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} 調べるなら true
 */
export function isTargetCode(file) {
  return codeKind(file) !== null && !CODE_EXCLUDED.test(file)
}

/**
 * コードのコメントとテスト名から、使わない語を書いた行を探す。行番号は元のファイルの行である。
 *
 * @param {string} file リポジトリのルートからのパス
 * @param {string} text ファイルの内容
 * @param {{ word: string, use: string, allow?: Record<string, string> }[]} terms 語と言い換えと許す語
 * @returns {{ line: number, word: string, use: string }[]} 見つけた行と語（調べないファイルは空）
 */
export function findCodeTerms(file, text, terms) {
  return isTargetCode(file) ? findTerms(commentText(text, codeKind(file)), terms) : []
}

/**
 * コミットメッセージから、git が付けるコメントの行（# で始まる行）を除く。行の番号は変えない。
 *
 * @param {string} message コミットメッセージ
 * @returns {string} # で始まる行を空にしたメッセージ
 */
export function stripMessageComments(message) {
  return message
    .split('\n')
    .map((line) => (line.startsWith('#') ? '' : line))
    .join('\n')
}

if (isMain(import.meta.url)) {
  const args = process.argv.slice(2)
  const warning = args.includes('--warning') || args.includes('--caution')
  const list = JSON.parse(readFileSync(new URL('./ja-text.json', import.meta.url), 'utf8'))
  const terms = warning ? list.warning : list.error
  const messageIndex = args.indexOf('--message')
  if (messageIndex !== -1) {
    const file = args[messageIndex + 1]
    if (!file) {
      console.error('--message にはコミットメッセージのファイルを指定する')
      process.exit(2)
    }
    const hits = findTerms(stripMessageComments(readFileSync(file, 'utf8')), terms)
    for (const hit of hits) {
      console.log(`${file}:${hit.line} ${hit.word} → ${hit.use}`)
    }
    console.log(`${warning ? '注意する語' : '使わない語'}：${hits.length}`)
    process.exit(!warning && hits.length ? 1 : 0)
  }
  const gitFiles = listGitFiles()
  const files = gitFiles.filter((file) => isTargetDoc(file))
  exitIfNoFiles(files, '語を調べる文書')
  const codeFiles = gitFiles.filter((file) => isTargetCode(file))
  exitIfNoFiles(codeFiles, '語を調べるコード')
  const texts = files.map((file) => readFileSync(file, 'utf8'))
  const codeTexts = codeFiles.map((file) => readFileSync(file, 'utf8'))
  const allowTexts = [...texts, ...codeFiles.map((file, index) => commentText(codeTexts[index], codeKind(file)))]
  const allowProblems = warning ? [] : findAllowProblems(allowTexts, terms)
  for (const problem of allowProblems) {
    console.log(problem)
  }
  let count = allowProblems.length
  for (const [index, file] of files.entries()) {
    for (const hit of findTerms(texts[index], terms)) {
      console.log(`${file}:${hit.line} ${hit.word} → ${hit.use}`)
      count += 1
    }
  }
  for (const [index, file] of codeFiles.entries()) {
    for (const hit of findCodeTerms(file, codeTexts[index], terms)) {
      console.log(`${file}:${hit.line} ${hit.word} → ${hit.use}`)
      count += 1
    }
  }
  console.log(`${warning ? '注意する語' : '使わない語'}：${count}`)
  process.exit(!warning && count ? 1 : 0)
}

