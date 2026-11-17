// AC 一覧と ③ の生成の表を読み書きする（ac-check の一覧の読み書き）。進行中の spec の AC の定義の一覧もここで作る（行の読み方は scripts/shared/docs/ac-definitions.mjs）。
//
// 読むもの：
//   - AC 一覧：生成のマーカー（<!-- generated:ac-list --> とその閉じ）の範囲の表。改行コードは LF にそろえて読む
//   - AC 一覧の列：AC・モジュール・状態・元の spec・変えた spec・今の内容。
//     --write は、進行中の spec の AC について、列 AC・状態（有効）・元の spec・今の内容を転記し、変えた spec を「—」にする。
//     手動で書くのは、モジュールと、前の spec の AC を変えた・廃止したときの状態・変えた spec・今の内容。
//     転記した行の今の内容も、spec の中だけで通じる参照を書き換えるときと、転記の後に spec の AC の文を直したときは手動で修正する
//     （手順は docs/05-開発規則/issues.md「AC 一覧の操作」）
//   - 元の spec・変えた spec の値：形は SPEC_NUMBER。変えた spec が 2 つ以上なら SPEC_NUMBER_SEPARATOR で区切る。
//     spec のディレクトリとの照合は match.mjs
//   - ③ のモジュールの名前：listModuleNames と、common（共通。③ の表に生成しない）
//   - ③ の表：構造の検査の対象の一覧（lib/docs/functional-specs.mjs の FUNCTIONAL_SPECS）にある ③ の、マーカーの範囲。
//     マーカーは「今有効な AC」の節の <!-- generated:module-ac --> とその閉じ。中身は renderModuleTable で作る
//   - ③ の表の比べ方：範囲の先頭と末尾の空行を除き、改行コードを LF にそろえて、完全一致で比べる。
//     全角と半角などは正規化しない
//   - 進行中の spec：docs/08-spec/ の直下の spec.md の、定義の行（- **AC-…**）。spec.md のないディレクトリは飛ばす。
//     コードブロック・HTML のコメント・1 行のコードの中は読まない
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { readCodeBlocks } from '../../../shared/docs/code-block.mjs'
import { readCodeSpans, replaceCodeSpans } from '../../../shared/source/code-span.mjs'
import { ARCHIVE, listSpecDirs, SPECS_DIR } from '../../../shared/docs/spec-dirs.mjs'
import { parseSpecAcDefinitions, toAcKey } from '../../../shared/docs/ac-definitions.mjs'
import { compareAc } from './test-names.mjs'

/** AC 一覧の状態 */
export const STATUSES = ['有効', '変更', '廃止']

/** ③ の表に入れる状態 */
const ACTIVE = ['有効', '変更']

/** 共通（どのモジュールにも属さない AC）を表す、モジュールの列の値。AC 一覧だけに置き、③ の表には生成しない */
export const COMMON = 'common'

/** AC 一覧の文書（リポジトリのルートから） */
export const AC_LIST = 'docs/03-機能仕様/acceptance-criteria.md'

/** ③ の文書の置き場所（リポジトリのルートから） */
export const MODULE_DOCS_DIR = 'docs/03-機能仕様'

/** バックエンドのモジュールのディレクトリの置き場所（リポジトリのルートから） */
export const BACKEND_MODULES_DIR = 'backend/ecsite/src/main/java/com/ecsite/module'

/** AC 一覧の表の列 */
const LIST_COLUMNS = ['AC', 'モジュール', '状態', '元の spec', '変えた spec', '今の内容']

/** ③ の生成の表の列 */
export const MODULE_COLUMNS = ['AC', '今の内容', '元の spec']

/** ③ に生成する表の、見出しと区切りの行 */
const MODULE_TABLE_HEAD = [
  `| ${MODULE_COLUMNS.join(' | ')} |`,
  // 区切りの - の数は、見出しのセルの表示の幅（全角は 2）と前後の空白に合わせる
  `|${MODULE_COLUMNS.map((column) => '-'.repeat([...column].reduce((width, char) => width + (char.codePointAt(0) > 0xff ? 2 : 1), 2))).join('|')}|`,
]

/** 対応する AC が 0 件のモジュールの、マーカーの範囲の中の行 */
const NO_ACS = 'なし。'

/** ③ の生成の表を置く節。雛形の骨組みの ## にあることを、テストで確認する */
export const MODULE_AC_SECTION = '今有効な AC'

/** AC 一覧の表の、生成のマーカーの名前 */
export const LIST_MARK = 'ac-list'

/** ③ の表の、生成のマーカーの名前 */
export const MODULE_MARK = 'module-ac'

/** 生成のマーカーの形（開きは `<!-- generated:<名前> -->`、閉じは `<!-- /generated:<名前> -->`） */
const MARK = new RegExp(`^(/?)generated:(${LIST_MARK}|${MODULE_MARK})$`)

/** 表の区切りの行 */
const SEPARATOR_ROW = /^\|(?:\s*:?-+:?\s*\|)+$/

/** AC の番号のセル（0 で埋めない） */
const AC_CELL = /^AC-([1-9][0-9]*(?:-[1-9][0-9]*)?)$/

/** MVP の形（AC-n）の番号の上限。MVP は終わっていて、AC-n は増えない */
export const MVP_AC_LIMIT = 312

/**
 * 元の spec・変えた spec の列の、spec の番号の形。MVP の spec は MVP-<3 桁の番号>、それ以外は番号だけ（227）。
 *
 * MVP-<番号> は MVP_ARCHIVE_PREFIX の下と、番号だけの値は docs/08-spec/ の直下と MVP 以外のまとまりの下と照合する。
 * 番号は、ディレクトリの名前の先頭の <番号>- と文字列として比べる（0 埋めの有無は変えない。007 と 7 は別の番号）
 */
export const SPEC_NUMBER = /^(?:MVP-(?<mvp>[0-9]{3})|(?<plain>[0-9]+))$/

/** MVP の spec のディレクトリの置き場所（docs/08-spec/ から） */
export const MVP_ARCHIVE_PREFIX = `${ARCHIVE}/mvp/`

/** 変えた spec の列で、2 つ以上の番号を区切る記号 */
export const SPEC_NUMBER_SEPARATOR = '、'

/** 今の内容に残してはいけない、spec の中だけで通じる参照（※1、事実 2、US の番号、spec の節） */
export const SPEC_ONLY_REFS = [/※[0-9]/, /事実\s*[0-9]/, /US-[0-9]/, /spec\s*の「/]

/**
 * 今の内容の根拠の形：リンク（[<文書の名前>](<パス>)）と、その直後の「の「<見出し>」」。リンクのない「…の「…」」は当たらない。
 *
 * 理由：doc-refs が見出しの実在を確認できるのは、リンクの後の「の「…」」だけのため
 */
export const BASIS = /\[[^\]]+\]\([^()\s]+\)の「[^「」]+」/

/** 今の内容の仮の値（`<…>`。英字で始まる HTML のタグは除く） */
export const PLACEHOLDER = /<[^\x00-\x7F][^<>]*>/

/**
 * 改行コードを LF にそろえる。
 *
 * @param {string} text 文書
 * @returns {string} LF にした文書
 */
export function toLf(text) {
  return text.replace(/\r\n?/g, '\n')
}

/**
 * 文書を、改行コードを付けたままの行に分ける（書き換えるときに、範囲の外の改行コードを変えないため）。
 *
 * @param {string} text 文書
 * @returns {string[]} 行（改行コードを含む）。つなぐと元の文書になる
 */
export function splitKeepingEol(text) {
  return text.split(/(?<=\n)/)
}

/**
 * 表の行をセルに分ける。1 行のコードの中の縦線と、エスケープした縦線（\|）では分けない。
 *
 * @param {string} line 行（`|` で始まる）
 * @returns {string[] | null} セル（前後の空白を除く）。表の行でなければ null
 */
export function splitRow(line) {
  const text = line.trim()
  if (!text.startsWith('|')) return null
  const spans = readCodeSpans(text).spans
  const cells = []
  let start = 1
  for (let i = 1; i < text.length; i++) {
    if (text[i] !== '|' || text[i - 1] === '\\' || spans.some((span) => i > span.start && i < span.end)) continue
    cells.push(text.slice(start, i).trim())
    start = i + 1
  }
  // 末尾の縦線がない行は、最後のセルも数える
  if (start < text.length) cells.push(text.slice(start).trim())
  return cells
}

/**
 * 文書から、生成のマーカーを読む。コードブロックと 1 行のコードの中は読まない。
 *
 * `generated:` を含むコメントで、既知のマーカーの形でないもの（綴りの誤り）と、行に単独で書いていないマーカーは問題にする。
 *
 * @param {string} text 文書（LF）
 * @returns {{ marks: { name: string, close: boolean, line: number }[], problems: string[] }} マーカー（出る順。line は 1 から）と問題
 */
export function findMarks(text) {
  const lines = text.split('\n')
  const inCode = new Set()
  for (const block of readCodeBlocks(text)) {
    const last = block.closed ? block.closeLine : lines.length
    for (let line = block.line; line <= last; line++) inCode.add(line)
  }
  const marks = []
  const problems = []
  lines.forEach((raw, index) => {
    const number = index + 1
    if (inCode.has(number)) return
    const line = replaceCodeSpans(raw, () => ' ')
    for (const comment of line.matchAll(/<!--([\s\S]*?)-->/g)) {
      const body = comment[1].trim()
      if (!body.includes('generated:')) continue
      const match = MARK.exec(body)
      if (!match) problems.push(`${number} 行：知らない生成の印（${comment[0]}）。<!-- generated:${LIST_MARK} --> か <!-- generated:${MODULE_MARK} --> と、その閉じだけを書く`)
      else if (line.trim() !== comment[0]) problems.push(`${number} 行：生成の印は、1 行に単独で書く`)
      else marks.push({ name: match[2], close: match[1] === '/', line: number })
    }
  })
  return { marks, problems }
}

/**
 * 1 つの名前のマーカーの組を、ただ 1 組として読む。
 *
 * @param {{ name: string, close: boolean, line: number }[]} marks findMarks のマーカー
 * @param {string} name マーカーの名前
 * @returns {{ pair: { open: number, close: number } | null, problems: string[] }} 組（開きと閉じの行）と問題。マーカーが 1 つもなければ pair も problems も空
 */
export function readPair(marks, name) {
  const own = marks.filter((mark) => mark.name === name)
  if (own.length === 0) return { pair: null, problems: [] }
  const opens = own.filter((mark) => !mark.close)
  const closes = own.filter((mark) => mark.close)
  if (opens.length !== 1 || closes.length !== 1) {
    return { pair: null, problems: [`生成の印 generated:${name} の開きが ${opens.length} 個、閉じが ${closes.length} 個ある（1 組だけにする）`] }
  }
  if (closes[0].line < opens[0].line) return { pair: null, problems: [`生成の印 generated:${name} の閉じ（${closes[0].line} 行）が開き（${opens[0].line} 行）より前にある`] }
  return { pair: { open: opens[0].line, close: closes[0].line }, problems: [] }
}

/**
 * AC 一覧の文書から、生成のマーカーの範囲の表を読む。行を読み飛ばさず、読めない行は問題にする。
 *
 * @param {string} document AC 一覧の文書（改行は LF でも CRLF でもよい）
 * @returns {{ rows: { ac: number | string, label: string, module: string, status: string, origin: string, changedBy: string, content: string, line: number }[], problems: string[], pair: { open: number, close: number } | null, lastLine: number }}
 *   rows：AC の行（書いた順。line は 1 から）。problems：問題（行を付ける）。pair：マーカーの範囲（読めなければ null）。lastLine：表の最後の行（1 から。行がなければ 0）
 */
export function parseAcList(document) {
  const text = toLf(document)
  const { marks, problems } = findMarks(text)
  if (marks.some((mark) => mark.name === MODULE_MARK)) problems.push(`AC 一覧に ③ の生成の印（generated:${MODULE_MARK}）がある`)
  const { pair, problems: pairProblems } = readPair(marks, LIST_MARK)
  problems.push(...pairProblems)
  if (!pair && pairProblems.length === 0) problems.push(`<!-- generated:${LIST_MARK} --> と <!-- /generated:${LIST_MARK} --> の範囲がない`)
  if (!pair) return { rows: [], problems, pair: null, lastLine: 0 }
  const lines = text.split('\n')
  const rows = []
  let tableLine = 0
  // 表の最後の行（見出し、区切り、AC の行のうち最後のもの。1 から）。転記する行を、表が切れない位置に入れるために返す
  let lastLine = 0
  // 空行は、表より前と表より後だけ許す。GFM の表は空行で切れ、後ろの行が表にならないため
  const filled = []
  for (let number = pair.open + 1; number < pair.close; number++) {
    if (lines[number - 1].trim() !== '') filled.push(number)
  }
  for (let number = pair.open + 1; number < pair.close; number++) {
    const line = lines[number - 1]
    if (line.trim() === '') {
      if (number > filled[0] && number < filled.at(-1)) problems.push(`${number} 行：AC 一覧の表の途中に空行がある（表がそこで切れる）`)
      continue
    }
    const cells = splitRow(line)
    tableLine++
    lastLine = number
    if (cells === null) {
      problems.push(`${number} 行：AC 一覧の範囲の中に、表の行でない行がある（セルの中で改行しない）`)
      continue
    }
    if (tableLine === 1) {
      if (cells.join('|') !== LIST_COLUMNS.join('|')) problems.push(`${number} 行：AC 一覧の列が ${LIST_COLUMNS.join('、')} でない`)
      continue
    }
    if (tableLine === 2) {
      if (!SEPARATOR_ROW.test(line.trim()) || cells.length !== LIST_COLUMNS.length) problems.push(`${number} 行：AC 一覧の表の区切りの行が ${LIST_COLUMNS.length} 列でない`)
      continue
    }
    const match = AC_CELL.exec(cells[0])
    if (!match) {
      problems.push(`${number} 行：AC の番号の形が違う（${cells[0]}。AC-<番号> か AC-<spec の番号>-<連番> の形で、0 で埋めない）`)
      continue
    }
    if (cells.length !== LIST_COLUMNS.length) {
      problems.push(`${number} 行：${cells[0]} の行のセルが ${cells.length} 個ある（${LIST_COLUMNS.length} 個にする。今の内容の縦線は ｜ にする）`)
      continue
    }
    const [label, module, status, origin, changedBy, content] = cells
    rows.push({ ac: toAcKey(match[1]), label, module, status, origin, changedBy, content, line: number })
  }
  if (tableLine === 0) problems.push(`${pair.open} 行：AC 一覧の範囲に、表の見出しの行がない（列は ${LIST_COLUMNS.join('、')}）`)
  return { rows, problems, pair, lastLine }
}

/**
 * spec のディレクトリの名前、または docs/08-spec からのパスから、spec の番号を取り出す。
 *
 * @param {string} nameOrRel ディレクトリの名前（5-x）か、パス（archive/1to10/5-x）
 * @returns {string} 先頭の番号（0 埋めはそのまま）
 */
export function specNumberOf(nameOrRel) {
  return /^[0-9]+/.exec(nameOrRel.split('/').pop())[0]
}

/**
 * 進行中の spec（docs/08-spec/ の直下）の AC の定義を読む。アーカイブの spec は読まない。spec.md のないディレクトリは飛ばす。
 *
 * @param {string} specsDir docs/08-spec のパス
 * @returns {{ specs: { rel: string, definitions: object[] }[], problems: string[] }} spec ごとの定義と、崩れた定義の行（spec.md のパスを付ける）
 */
export function readInProgressSpecs(specsDir) {
  const specs = []
  const problems = []
  for (const { rel, dir } of listSpecDirs(specsDir).filter((spec) => !spec.rel.startsWith(`${ARCHIVE}/`))) {
    const file = path.join(dir, 'spec.md')
    if (!existsSync(file)) continue
    const parsed = parseSpecAcDefinitions(readFileSync(file, 'utf8'), rel)
    specs.push({ rel, definitions: parsed.definitions })
    problems.push(...parsed.problems.map((problem) => `${SPECS_DIR}/${rel}/spec.md：${problem}`))
  }
  return { specs, problems }
}

/**
 * AC 一覧の行を作る（転記する進行中の spec の AC）。モジュールは空にし、書くまで検査を失敗にする。
 *
 * @param {{ ac: number | string, specRel: string, content: string }} definition AC の定義
 * @returns {string} 表の行
 */
function renderListRow({ ac, specRel, content }) {
  return `| AC-${ac} |  | 有効 | ${specNumberOf(specRel)} | — | ${content} |`
}

/**
 * AC 一覧にない AC を、AC の順の位置に転記する。既存の行と、範囲の外は変えない。
 *
 * @param {string} document AC 一覧の文書
 * @param {{ ac: number | string, specRel: string, content: string }[]} definitions 進行中の spec の AC の定義
 * @returns {string} 転記した文書（転記する AC がなければ元のまま）
 * @throws {Error} AC 一覧を読めないとき
 */
export function addAcsToList(document, definitions) {
  const { rows, problems, pair, lastLine } = parseAcList(document)
  if (problems.length > 0) throw new Error(`AC 一覧を読めないので転記しない：${problems.join(' / ')}`)
  const known = new Set(rows.map((row) => String(row.ac)))
  const added = []
  for (const definition of definitions) {
    if (known.has(String(definition.ac))) continue
    known.add(String(definition.ac))
    added.push(definition)
  }
  if (added.length === 0) return document
  const lines = splitKeepingEol(document)
  const eol = lines[pair.open - 1].endsWith('\r\n') ? '\r\n' : '\n'
  // 行の前に入れる行（1 から）→ 入れる行
  const inserts = new Map()
  for (const definition of added.sort((a, b) => compareAc(a.ac, b.ac))) {
    // 全ての行より後ろの AC は、閉じのマーカーでなく表の最後の行の直後に入れる（範囲の末尾の空行で、表が切れないように）
    const before = rows.find((row) => compareAc(row.ac, definition.ac) > 0)?.line ?? lastLine + 1
    inserts.set(before, [...(inserts.get(before) ?? []), `${renderListRow(definition)}${eol}`])
  }
  return lines.map((line, index) => [...(inserts.get(index + 1) ?? []), line].join('')).join('')
}

/**
 * ③ のモジュールの名前を決める。バックエンドのモジュールのディレクトリと、③ の文書の両方がある名前。
 *
 * @param {{ backendDirs: string[], docFiles: string[] }} input バックエンドのモジュールのディレクトリの名前と、③ の置き場所のファイルの名前
 * @returns {Set<string>} モジュールの名前
 */
export function listModuleNames({ backendDirs, docFiles }) {
  const docs = new Set(docFiles.filter((file) => file.endsWith('.md')).map((file) => file.slice(0, -3)))
  return new Set(backendDirs.filter((name) => docs.has(name)))
}

/**
 * ③ の「今有効な AC」の表を、AC 一覧から作る。有効・変更の、そのモジュールの AC を AC の順に置く。
 *
 * @param {ReturnType<typeof parseAcList>['rows']} rows AC 一覧の行
 * @param {string} module モジュールの名前
 * @returns {string} マーカーの範囲の中に置く行（LF。末尾に改行を付けない）。0 件なら「なし。」
 */
export function renderModuleTable(rows, module) {
  const selected = rows.filter((row) => ACTIVE.includes(row.status) && row.module === module && module !== COMMON).sort((a, b) => compareAc(a.ac, b.ac))
  if (selected.length === 0) return NO_ACS
  return [...MODULE_TABLE_HEAD, ...selected.map((row) => `| ${row.label} | ${row.content} | ${row.origin} |`)].join('\n')
}
