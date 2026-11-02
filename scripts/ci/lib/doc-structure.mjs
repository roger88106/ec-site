// 文書の構造（見出しの名前と順）と、ID（US・DS・AC・BR・NFR・DC・DN・T）の定義と参照を読む関数。
// spec・plan・tasks の構造の検査（spec-structure）と、固定の節に書き換えた機能仕様の検査が使う。Mermaid の検査（mermaid/mermaid-check）は、readDocument を使う。
// 見出しと ID は、コードブロックと HTML のコメント（lib/code-block）と、1 行のコード（lib/code-span）の中を読まない。
//   - 見出し：ATX の見出し（`## 名前`。閉じの `#` は名前に含めない）と、Setext の見出し（段落の行の直後の `---` は `##`、`===` は `#`。段落の前は空行・ATX の見出し・区切り線・文書の始めだけで、箇条・引用・HTML のブロックの続きの行や、4 個以上字下げした行は見出しにしない）
//   - ID の定義は 3 つ：字下げしない箇条の先頭の太字、見出しの先頭の US・DS、タスクの行の先頭の太字。他の太字は参照として読む
//   - ID の参照：範囲（`〜`、`～`、`~`。前後の空白を許す）と列挙（`、`）を展開する。列挙に読むのは、許可する形（ENUM_END、同じ spec の `、7-32`）だけ。
//     タスクの ID の列挙は、2 桁の連番（`、02`、`、02〜03`）を読む。2 桁でない数（`、3`）は、列挙の終わりが続くなら、2 桁でない問題にする。範囲の端には、同じ接頭辞と同じ番号の ID（`DS-<番号>-2〜DS-<番号>-8`）も書ける
import { readBlocks } from './code-block.mjs'
import { replaceCodeSpans } from './code-span.mjs'

/** 範囲を展開してよい上限（1 つの参照が展開する件数） */
export const MAX_RANGE = 999

/**
 * 列挙の要素の数の後ろに許す形（区切り・行末・表の区切り・太字・リンク・かぎかっこ・「など」・
 * 助詞「の」「と」「を」「が」「は」「に」「で」「も」「や」。空白を挟んでよい）。
 * これ以外（「、2 段階」「、30 秒」、日付 `、2026-11-05`）は、数であって列挙の要素でない。
 * 範囲の端（`〜` の後ろの数）には当てない（rangeEnd）
 */
const ENUM_END = /^\s*(?:$|[、〜～~）)。：|*\]」]|など|[のとをがはにでもや])/

/** 範囲の記号（前後の空白を許す） */
const RANGE_MARK = '\\s*[〜～~]\\s*'

/** 範囲の端の数の後ろに続いてはいけない字（数字・`-`・`.`。日付や小数を範囲の端に読まないため） */
const NUMBER_END = '(?![\\d.-])'

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
const POSSESSIVE = /^\s*の[^\s、〜～~）)。：]*/

/** spec の ID の列挙の要素（`、3` と `、3〜5`）。範囲の端は、数字・`-`・`.` が続かないときだけ読む */
const LIST_ELEMENT = new RegExp(`^(、(\\d+))(?:${RANGE_MARK}(\\d+)${NUMBER_END})?`)

/** spec の番号を付けた列挙の要素（`、7-32` と `、7-32〜35`）。spec の番号が直前の ID と同じときだけ読む */
const LIST_ELEMENT_WITH_NUMBER = new RegExp(`^(、(\\d+)-(\\d+))(?:${RANGE_MARK}(\\d+)${NUMBER_END})?`)

/** タスクの ID の列挙の要素（`、02` と `、02〜03`）。2 桁でない数も要素として読み、2 桁でない問題にする */
const TASK_LIST_ELEMENT = new RegExp(`^(、(\\d+))(?:${RANGE_MARK}(\\d+)${NUMBER_END})?`)

/** Setext の見出しの下線（`---` は `##`、`===` は `#`） */
const SETEXT_UNDERLINE = /^ {0,3}(-+|=+)[ \t]*$/

/** 段落の行でない行（空行・ATX の見出し・箇条・表・引用・HTML のブロック・区切り線） */
const NOT_PARAGRAPH = /^\s*$|^ {0,3}#{1,6}(?:[ \t]|$)|^\s*(?:[-*+]|\d{1,9}[.)])(?:[ \t]|$)|^\s*\||^\s*>|^ {0,3}<[A-Za-z/!?]|^ {0,3}([*_])(?:[ \t]*\1){2,}[ \t]*$/

/** 段落の前に置いてよい行（空行・ATX の見出し・区切り線・Setext の下線）。これ以外で止まったら、段落とみなさない */
const PARAGRAPH_START = /^\s*$|^ {0,3}#{1,6}(?:[ \t]|$)|^ {0,3}([*_])(?:[ \t]*\1){2,}[ \t]*$|^ {0,3}(?:-+|=+)[ \t]*$/

/**
 * 範囲の端（`〜5`、`〜<接頭辞>-<番号>-5`）の正規表現を作る。
 *
 * @param {string} head 範囲の端に書ける ID の前半（`DS-<番号>-`。直前の ID と同じ接頭辞と番号）
 * @returns {RegExp} 範囲の端。1 番目のグループが端の数
 */
function rangeEnd(head) {
  return new RegExp(`^${RANGE_MARK}(?:${head})?(\\d+)${NUMBER_END}`)
}

/**
 * 文書を、コードブロックと HTML のコメントを除いた行と、1 行のコードも除いた行にする。行の数と番号は変えない。
 *
 * @param {string} text 文書の内容
 * @returns {{ lines: string[], plain: string[], unclosed: number[] }}
 *   lines はコードブロックの中を空にし、コメントの範囲を空白にした行、plain はさらに 1 行のコードを空白にした行、
 *   unclosed は閉じていないコードブロックの開きの行（1 から）
 */
export function readDocument(text) {
  const lines = text.split(/\r?\n/)
  const unclosed = []
  const { codeBlocks, comments } = readBlocks(text)
  for (const block of codeBlocks) {
    const last = block.closed ? block.closeLine : lines.length
    for (let line = block.line; line <= last; line++) lines[line - 1] = ''
    if (!block.closed) unclosed.push(block.line)
  }
  for (const { line, start, end } of comments) {
    const current = lines[line - 1]
    lines[line - 1] = current.slice(0, start) + ' '.repeat(end - start) + current.slice(end)
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
 * 見出し（ATX と Setext）を読む。コードブロックと HTML のコメントの中は読まない。
 *
 * @param {string} text 文書の内容
 * @returns {{ level: number, title: string, line: number }[]} 見出しの深さ、名前、行（1 から。Setext の見出しは段落の最初の行）。出る順
 */
export function readHeadings(text) {
  const headings = []
  const { lines } = readDocument(text)
  lines.forEach((line, index) => {
    const match = /^ {0,3}(#{1,6})[ \t]+(.+?)[ \t]*$/.exec(line)
    if (match) {
      headings.push({ level: match[1].length, title: match[2].replace(/[ \t]+#+$/, ''), line: index + 1 })
      return
    }
    const underline = SETEXT_UNDERLINE.exec(line)
    if (!underline) return
    // 下線の直前から上へ、段落の行を集める
    let first = index
    while (first > 0 && !NOT_PARAGRAPH.test(lines[first - 1]) && !SETEXT_UNDERLINE.test(lines[first - 1])) first--
    if (first === index) return
    // 止まった行が空行・ATX の見出し・区切り線でなければ（箇条・引用・HTML のブロックの続きの行）、段落でない。最初の行が 4 個以上の字下げならコード
    if (first > 0 && !PARAGRAPH_START.test(lines[first - 1])) return
    if (/^(?: {4}|\t)/.test(lines[first])) return
    const title = lines.slice(first, index).map((item) => item.trim()).join(' ')
    headings.push({ level: underline[1][0] === '=' ? 1 : 2, title, line: first + 1 })
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
 * @param {{ head: string, number: string | null, listElement: RegExp }} options
 *   head：範囲の端に書ける ID の前半（直前の ID の、連番の前まで）。
 *   number：`、7-32` の形で読む spec の番号（null なら読まない）。listElement：番号だけの列挙の要素
 * @returns {{ elements: { from: string, to: string | null }[], end: number }} 番号の組（to は範囲の端。なければ null）と、読み終えた位置
 */
function readTail(line, position, first, { head, number, listElement }) {
  const elements = []
  let end = position
  const start = { from: first, to: null }
  const range = rangeEnd(head).exec(line.slice(end))
  // 範囲の端は、`〜` の後ろの数なので、常に読む（数字・`-`・`.` が続く形は rangeEnd が除く）。列挙の要素は ENUM_END が続くときだけ読む
  if (range) {
    start.to = range[1]
    end += range[0].length
  }
  elements.push(start)
  for (;;) {
    const rest = line.slice(end)
    const withNumber = LIST_ELEMENT_WITH_NUMBER.exec(rest)
    const plain = listElement.exec(rest)
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
      const options = { head: `${match[1]}-${match[2]}-`, number: match[2], listElement: LIST_ELEMENT }
      const { elements, end } = readTail(line, match.index + match[0].length, match[3], options)
      const written = line.slice(match.index, end)
      const numbers = expand(elements, written, lineNumber, problems, (digits) => /^0\d/.test(digits), '連番を 0 で埋めている')
      for (const seq of numbers) references.push({ id: `${match[1]}-${match[2]}-${seq}`, number: match[2], line: lineNumber, text: written })
    }
    for (const match of line.matchAll(TASK_HEAD)) {
      const options = { head: `T${match[1]}-${match[2]}-`, number: null, listElement: TASK_LIST_ELEMENT }
      const { elements, end } = readTail(line, match.index + match[0].length, match[3], options)
      const written = line.slice(match.index, end)
      const wide = match[2].length !== 2 || elements.some((element) => element.from.length !== 2 || (element.to !== null && element.to.length !== 2))
      if (wide) {
        problems.push(`${lineNumber} 行：タスクの段階と連番が 2 桁でない（${written}）`)
        continue
      }
      const numbers = expand(elements, written, lineNumber, problems, () => false, '')
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
