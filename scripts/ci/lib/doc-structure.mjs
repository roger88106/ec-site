// 文書の構造（見出しの名前と順）と、ID（US・DS・AC・BR・NFR・DC・DN・T）の定義と参照を読む関数。
// spec・plan・tasks の構造の検査（spec-structure）と、固定の節に書き換えた機能仕様の検査が使う。
// 見出しと ID は、コードブロック（lib/code-block）と 1 行のコード（lib/code-span）の中を読まない。
//   - ID の定義は 3 つ：字下げしない箇条の先頭の太字、見出しの先頭の US・DS、タスクの行の先頭の太字。他の太字は参照として読む
//   - ID の参照：範囲（`〜`）と列挙（`、`）を展開する。列挙に読むのは、許可する形（ENUM_END、同じ spec の `、7-32`）だけ
import { readCodeBlocks } from './code-block.mjs'
import { replaceCodeSpans } from './code-span.mjs'

/** 範囲を展開してよい上限（1 つの参照が展開する件数） */
export const MAX_RANGE = 999

/**
 * 列挙の要素の数の後ろに許す形（区切り、行末、助詞「の」「と」「を」「が」「は」「に」「で」「も」「や」。空白を挟んでよい）。
 * これ以外（「、2 段階」「、30 秒」、日付 `、2026-11-05`）は、数であって列挙の要素でない。
 * 範囲の端（`〜` の後ろの数）には当てない（RANGE_END）
 */
const ENUM_END = /^\s*(?:$|[、〜~）)。：]|[のとをがはにでもや])/

/** ID の接頭辞のうち、見出しで定義するもの */
const HEADING_PREFIXES = ['US', 'DS']

/** ID の接頭辞のうち、箇条の先頭の太字で定義するもの */
const BOLD_PREFIXES = ['AC', 'BR', 'NFR', 'DC', 'DN']

const PREFIXES = [...HEADING_PREFIXES, ...BOLD_PREFIXES]

/** 語の途中の接頭辞（MAC- など）を読まないため、前に英数字と _ がないことを求める */
const NOT_AFTER_WORD = '(?<![A-Za-z0-9_])'

/** spec の中の ID の始まり：接頭辞、spec の番号、連番 */
const ID_HEAD = new RegExp(`${NOT_AFTER_WORD}(${PREFIXES.join('|')})-(\\d+)-(\\d+)`, 'g')

/** タスクの ID の始まり：spec の番号、段階、連番 */
const TASK_HEAD = new RegExp(`${NOT_AFTER_WORD}T(\\d+)-(\\d+)-(\\d+)`, 'g')

/** 列挙の要素の後ろの「の前半」（「の」とそれに続く語）。次の要素へ読み進めるため飛ばす */
const POSSESSIVE = /^\s*の[^\s、〜~）)。：]*/

/** 範囲の端（`〜5`） */
const RANGE_END = /^[〜~](\d+)(?![\d.-])/

/** 列挙の要素（`、3` と `、3〜5`）。範囲の端は、数字・`-`・`.` が続かないときだけ読む */
const LIST_ELEMENT = /^(、(\d+))(?:[〜~](\d+)(?![\d.-]))?/

/** spec の番号を付けた列挙の要素（`、7-32` と `、7-32〜35`）。spec の番号が直前の ID と同じときだけ読む */
const LIST_ELEMENT_WITH_NUMBER = /^(、(\d+)-(\d+))(?:[〜~](\d+)(?![\d.-]))?/

/**
 * 文書を、コードブロックを除いた行と、1 行のコードも除いた行にする。行の数と番号は変えない。
 *
 * @param {string} text 文書の内容
 * @returns {{ lines: string[], plain: string[], unclosed: number[] }}
 *   lines はコードブロックの中を空にした行、plain はさらに 1 行のコードを空白にした行、unclosed は閉じていないコードブロックの開きの行（1 から）
 */
export function readDocument(text) {
  const lines = text.split(/\r?\n/)
  const unclosed = []
  for (const block of readCodeBlocks(text)) {
    const last = block.closed ? block.closeLine : lines.length
    for (let line = block.line; line <= last; line++) lines[line - 1] = ''
    if (!block.closed) unclosed.push(block.line)
  }
  const plain = lines.map((line) => replaceCodeSpans(line, () => ' '))
  return { lines, plain, unclosed }
}

/**
 * 閉じていないコードブロックを探す。閉じていないと、後ろの見出しと ID を読めない。
 *
 * @param {string} text 文書の内容
 * @returns {string[]} 問題の文
 */
export function checkUnclosedCodeBlocks(text) {
  return readDocument(text).unclosed.map((line) => `${line} 行：閉じていないコードブロックがある（後ろの見出しと ID を読めない）`)
}

/**
 * 見出しを読む。コードブロックの中は読まない。
 *
 * @param {string} text 文書の内容
 * @returns {{ level: number, title: string, line: number }[]} 見出しの深さ、名前、行（1 から）
 */
export function readHeadings(text) {
  const headings = []
  readDocument(text).lines.forEach((line, index) => {
    const match = /^ {0,3}(#{1,6})[ \t]+(.+?)[ \t]*$/.exec(line)
    if (match) headings.push({ level: match[1].length, title: match[2], line: index + 1 })
  })
  return headings
}

/**
 * ある深さの見出しが、決めた名前を決めた順に持つかを調べる。
 *
 * @param {{ level: number, title: string, line: number }[]} headings readHeadings の結果
 * @param {number} level 調べる見出しの深さ
 * @param {string[]} required 決めた名前（この順に出る）
 * @param {{ extra?: (title: string) => boolean }} [options] extra：決めた名前の他に置いてよい見出し。なければ置けない
 * @returns {string[]} 問題の文
 */
export function checkSections(headings, level, required, { extra = () => false } = {}) {
  const problems = []
  const found = headings.filter((heading) => heading.level === level)
  const seen = new Set()
  // 決めた節は、欠けた節を飛ばして、前の節より後に出ることだけを見る（1 つ欠けても、後の節を全て「位置が違う」としない）
  let last = -1
  for (const heading of found) {
    const index = required.indexOf(heading.title)
    if (index < 0) {
      if (!extra(heading.title)) problems.push(`${heading.line} 行：決めた節にない見出し「${heading.title}」`)
      continue
    }
    if (seen.has(heading.title)) {
      problems.push(`${heading.line} 行：「${heading.title}」が 2 回ある`)
      continue
    }
    seen.add(heading.title)
    if (index < last) problems.push(`${heading.line} 行：「${heading.title}」の位置が違う（決めた順：${required.join('、')}）`)
    else last = index
  }
  for (const missing of required) {
    if (!seen.has(missing)) problems.push(`節「${missing}」がない`)
  }
  return problems
}

/**
 * ID の定義を集める。
 *
 * @param {string} text 文書の内容
 * @returns {{ id: string, kind: 'bold' | 'heading' | 'task', prefix: string, number: string, stage?: string, seq: string, line: number }[]}
 *   定義した ID。kind：bold は箇条の先頭の太字、heading は見出しの先頭の US・DS、task はタスクの行の先頭。
 *   number、stage、seq は、書かれたままの文字（0 で埋めた連番を見分けるため）
 */
export function collectDefinitions(text) {
  const { plain } = readDocument(text)
  const definitions = []
  const bold = new RegExp(`^- \\*\\*(${BOLD_PREFIXES.join('|')})-(\\d+)-(\\d+)\\*\\*`)
  const heading = new RegExp(`^ {0,3}#{1,6}[ \\t]+(${HEADING_PREFIXES.join('|')})-(\\d+)-(\\d+)\\b`)
  const task = /^- \[[ x]\] \*\*T(\d+)-(\d+)-(\d+)\*\*/
  plain.forEach((line, index) => {
    const lineNumber = index + 1
    let match
    if ((match = bold.exec(line)) !== null) {
      definitions.push({ id: `${match[1]}-${match[2]}-${match[3]}`, kind: 'bold', prefix: match[1], number: match[2], seq: match[3], line: lineNumber })
    } else if ((match = heading.exec(line)) !== null) {
      definitions.push({ id: `${match[1]}-${match[2]}-${match[3]}`, kind: 'heading', prefix: match[1], number: match[2], seq: match[3], line: lineNumber })
    } else if ((match = task.exec(line)) !== null) {
      definitions.push({ id: `T${match[1]}-${match[2]}-${match[3]}`, kind: 'task', prefix: 'T', number: match[1], stage: match[2], seq: match[3], line: lineNumber })
    }
  })
  return definitions
}

/**
 * 参照の範囲と列挙（始めの番号、範囲の端、列挙の要素）を、番号の組の一覧に読む。
 *
 * @param {string} line 1 行
 * @param {number} position 始めの番号の次の位置
 * @param {string} first 始めの番号の文字
 * @param {string} number 直前の ID の spec の番号
 * @returns {{ elements: { from: string, to: string | null }[], end: number }} 番号の組（to は範囲の端。なければ null）と、読み終えた位置
 */
function readTail(line, position, first, number) {
  const elements = []
  let end = position
  const head = { from: first, to: null }
  const range = RANGE_END.exec(line.slice(end))
  // 範囲の端は、`〜` の後ろの数なので、常に読む（数字・`-`・`.` が続く形は RANGE_END が除く）。列挙の要素は ENUM_END が続くときだけ読む
  if (range) {
    head.to = range[1]
    end += range[0].length
  }
  elements.push(head)
  for (;;) {
    const rest = line.slice(end)
    const withNumber = LIST_ELEMENT_WITH_NUMBER.exec(rest)
    const plain = LIST_ELEMENT.exec(rest)
    // `、7-32` は spec の番号が同じときだけ。違うときは、`、7` と読まずに列挙を終える
    const element = withNumber !== null
      ? (withNumber[2] === number ? { text: withNumber[0], first: withNumber[1], from: withNumber[3], to: withNumber[4] ?? null } : null)
      : (plain !== null ? { text: plain[0], first: plain[1], from: plain[2], to: plain[3] ?? null } : null)
    // ENUM_END は、要素の始めの数（`、2`、`、7-32`）の後ろに当てる
    if (!element || !ENUM_END.test(rest.slice(element.first.length))) break
    elements.push({ from: element.from, to: element.to })
    end += element.text.length
    // 「6 の前半、13」の「の前半」は読み飛ばし、後ろの要素を続けて読む
    end += POSSESSIVE.exec(line.slice(end))?.[0].length ?? 0
  }
  return { elements, end }
}

/**
 * ID の参照を集める。全ての spec の番号の参照を返す（どの spec の番号を調べるかは呼ぶ側が決める）。
 *
 * @param {string} text 文書の内容
 * @returns {{ references: { id: string, number: string, line: number, text: string }[], problems: string[] }}
 *   references：参照した ID（範囲と列挙は展開する。text は、書かれた形）。
 *   problems：逆順の範囲、上限を超える範囲、0 で埋めた連番、2 桁でないタスクの段階と連番（この ID は references に入れない）
 */
export function collectReferences(text) {
  const references = []
  const problems = []
  readDocument(text).plain.forEach((line, index) => {
    const lineNumber = index + 1
    for (const match of line.matchAll(ID_HEAD)) {
      const { elements, end } = readTail(line, match.index + match[0].length, match[3], match[2])
      const written = line.slice(match.index, end)
      const numbers = expand(elements, written, lineNumber, problems, (digits) => /^0\d/.test(digits), '連番を 0 で埋めている')
      for (const seq of numbers) references.push({ id: `${match[1]}-${match[2]}-${seq}`, number: match[2], line: lineNumber, text: written })
    }
    for (const match of line.matchAll(TASK_HEAD)) {
      const tail = RANGE_END.exec(line.slice(match.index + match[0].length))
      const written = line.slice(match.index, match.index + match[0].length + (tail ? tail[0].length : 0))
      const wide = match[2].length !== 2 || match[3].length !== 2 || (tail && tail[1].length !== 2)
      if (wide) {
        problems.push(`${lineNumber} 行：タスクの段階と連番が 2 桁でない（${written}）`)
        continue
      }
      const numbers = expand([{ from: match[3], to: tail ? tail[1] : null }], written, lineNumber, problems, () => false, '')
      for (const seq of numbers) {
        references.push({ id: `T${match[1]}-${match[2]}-${String(seq).padStart(2, '0')}`, number: match[1], line: lineNumber, text: written })
      }
    }
  })
  return { references, problems }
}

/**
 * 番号の組を展開する。問題のある組は、問題に書いて、展開しない。
 *
 * @param {{ from: string, to: string | null }[]} elements 番号の組
 * @param {string} written 書かれた参照の文字（問題の文に使う）
 * @param {number} line 行（1 から）
 * @param {string[]} problems 問題の文を追加する先
 * @param {(digits: string) => boolean} isBad 番号の文字の形が誤りか
 * @param {string} badMessage 誤りの文
 * @returns {number[]} 展開した番号
 */
function expand(elements, written, line, problems, isBad, badMessage) {
  const numbers = []
  for (const element of elements) {
    if (isBad(element.from) || (element.to !== null && isBad(element.to))) {
      problems.push(`${line} 行：${badMessage}（${written}）`)
      continue
    }
    const from = Number(element.from)
    const to = element.to === null ? from : Number(element.to)
    if (to < from) {
      problems.push(`${line} 行：範囲が逆順（${written}）`)
    } else if (to - from + 1 > MAX_RANGE) {
      problems.push(`${line} 行：範囲が ${MAX_RANGE} 件を超える（${written}）`)
    } else {
      for (let n = from; n <= to; n++) numbers.push(n)
    }
  }
  return numbers
}
