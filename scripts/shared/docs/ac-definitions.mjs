// spec の AC の定義の行（- **AC-n**、1→10 の - **AC-n-m**）を読む。コードブロック・HTML のコメント・1 行のコードの中は読まない。
//
// 定義の形でない、定義を書こうとした行（太字のない箇条、見出し、表の行など）は、崩れた行として返す。
import { replaceCodeSpans } from '../source/code-span.mjs'
import { readDocument } from './doc-structure.mjs'

/** AC の番号の書き方。MVP の `12` と、1→10 の `7-1`（spec の番号 ＋ 連番）の 2 つの形 */
export const AC_NUMBER = '[0-9]+(?:-[0-9]+)?'

/**
 * AC の番号の文字列を、照合に使う値にする。MVP の `12` は数、1→10 の `7-1` は文字列のまま。
 *
 * 形で型を分けるので、`AC-s` と `AC-s-n` は別の AC になる。
 *
 * @param text AC- の後の番号（`12`、`7-1`）
 * @returns 数（MVP）か文字列（1→10）
 */
export function toAcKey(text) {
  return text.includes('-') ? text : Number(text)
}

/**
 * AC の番号の後に文が続く形。文の先頭が一覧の助詞か記号なら、文の中で番号に触れただけの行なので一致しない。
 *
 * 開きのかぎかっこ、[ で始まるリンク、* の強調は、文の先頭に書くので記号から除く。助詞の一覧は語の先頭で比べる
 */
const AC_SENTENCE = '\\s+(?!(?:の|に|は|を|が|で|と|も|へ|や|か|まで|より|など|だけ|および|または|(?![「『\\[*])[\\p{P}\\p{S}]))\\S'

/** AC の番号の後が、文か行の終わりである形 */
const AC_SENTENCE_OR_END = `(?:${AC_SENTENCE}|\\s*$)`

/** 太字でも太字でなくてもよい AC の番号 */
const AC_LABEL = `(?:\\*\\*AC-${AC_NUMBER}\\*\\*|AC-${AC_NUMBER})`

/** AC の定義を書こうとした行の形。定義の形でなければ、読み飛ばされて検査をすり抜けないよう、崩れた行として報告する */
const DEFINITION_ATTEMPTS = [
  // 箇条（- * + と番号。字下げしてもよい）の先頭の太字の AC
  /^\s*(?:[-*+]|\d+[.)])\s*\*\*AC-/,
  // 太字のない箇条
  new RegExp(`^\\s*(?:[-*+]|\\d+[.)])\\s+AC-${AC_NUMBER}${AC_SENTENCE}`, 'u'),
  // 箇条のない太字
  new RegExp(`^\\s*\\*\\*AC-${AC_NUMBER}\\*\\*${AC_SENTENCE_OR_END}`, 'u'),
  // チェックボックス
  new RegExp(`^\\s*(?:[-*+]|\\d+[.)])\\s*\\[[ xX]\\]\\s*${AC_LABEL}${AC_SENTENCE_OR_END}`, 'u'),
  // 見出し（節の番号を付けてもよい）
  new RegExp(`^\\s*#{1,6}\\s+(?:[0-9][0-9.]*\\s+)?${AC_LABEL}${AC_SENTENCE_OR_END}`, 'u'),
  // 表の行（先頭のセルが番号だけ）
  new RegExp(`^\\s*\\|\\s*${AC_LABEL}\\s*\\|`, 'u'),
]

/**
 * spec の本文から、AC の定義を読む。コードブロック・HTML のコメント・1 行のコードの中は読まない。
 *
 * 今の内容は定義の文にし、定義の下の箇条は「：」の後に読点でつなぐ。
 *
 * @param {string} markdown spec.md の本文
 * @param {string} specRel docs/08-spec からの spec のディレクトリのパス
 * @returns {{ definitions: { ac: number | string, specRel: string, content: string }[], problems: string[] }} 定義（書いた順）と、崩れた定義の行（行を付ける）
 */
export function parseSpecAcDefinitions(markdown, specRel) {
  const { lines, plain } = readDocument(markdown)
  const definitions = []
  const problems = []
  const definition = new RegExp(`^- \\*\\*AC-(${AC_NUMBER})\\*\\*\\s+(\\S.*)$`)
  plain.forEach((line, index) => {
    // 1 行のコードを文の先頭として扱えるよう、コードを記号でない文字（x）に替えた行で判定する
    const marked = replaceCodeSpans(lines[index], () => 'x')
    if (!DEFINITION_ATTEMPTS.some((attempt) => attempt.test(marked))) return
    const match = definition.exec(line)
    if (!match) {
      problems.push(`${index + 1} 行：AC の定義の行が崩れている（- **AC-<番号>** <文> の形にする）`)
      return
    }
    // 本文は、1 行のコードを残した行から読む
    const text = lines[index].slice(lines[index].indexOf('**', 4) + 2).trim()
    const items = []
    for (let next = index + 1; next < lines.length; next++) {
      const item = /^\s{2,}[-*]\s+(.*)$/.exec(lines[next])
      if (!item) break
      items.push(item[1].trim().replace(/。$/, ''))
    }
    const content = items.length === 0 ? text : `${text.replace(/[。：]$/, '')}：${items.join('、')}。`
    definitions.push({ ac: toAcKey(match[1]), specRel, content: content.replaceAll('|', '｜') })
  })
  return { definitions, problems }
}
