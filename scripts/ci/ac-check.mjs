// AC 一覧（docs/03-機能仕様/acceptance-criteria.md）を、③ の表、進行中の spec、テスト名と照合する。
// AC 一覧は AC の正本で、③ の表は「今有効な AC」の節の表。テスト名の規則は、テストの書き方の「AC 番号を入れる」。
// 目的：AC の今の内容を AC 一覧の 1 か所に置き、③ の表とテストとの対応を、AC 一覧と食い違わせない。
//   AC の内容を得るために、アーカイブの spec は読まない。
// 読むもの：
//   - AC 一覧：生成のマーカー（<!-- generated:ac-list --> とその閉じ）の範囲の表。改行コードは LF にそろえて読む
//   - AC 一覧の列：AC・モジュール・状態・元の spec・変えた spec・今の内容。
//     --write は、進行中の spec の AC について、列 AC・状態（有効）・元の spec・今の内容を転記し、変えた spec を「—」にする。
//     手動で書くのは、モジュールと、前の spec の AC を変えた・廃止したときの状態・変えた spec・今の内容。
//     転記した行の今の内容も、spec の中だけで通じる参照を書き換えるときと、転記の後に spec の AC の文を直したときは手動で修正する
//     （手順は docs/05-開発規則/issues.md「AC 一覧の操作」）
//   - 元の spec・変えた spec の値：spec の番号だけ（001、227）。変えた spec が 2 つ以上なら読点で区切る（003、016）。
//     番号に対応する spec のディレクトリは、docs/08-spec/ の直下とアーカイブのまとまりの下から探す（lib/spec-dirs.mjs）。
//     番号は、ディレクトリの名前の先頭の <番号>- と文字列として比べる（001 と 1 は別の番号）
//   - AC：MVP の AC-n と、1→10 の AC-s-n（s は spec の番号、n は連番）。2 つの形は別の AC として扱う。
//     AC-s-n を AC-s と読まない。番号は、文書の記号と番号の「spec の番号」
//   - ③ のモジュールの名前：次の 2 つが両方ある名前と、common（共通。③ の表に生成しない）。
//     backend/ecsite/src/main/java/com/ecsite/module/<名前>/ と docs/03-機能仕様/<名前>.md
//   - ③ の表：構造の検査の対象の一覧（spec-structure.mjs の FUNCTIONAL_SPECS）にある ③ の、マーカーの範囲。
//     マーカーは「今有効な AC」の節の <!-- generated:module-ac --> とその閉じ
//   - ③ の表の中身：AC 一覧から作る。列は AC、今の内容、元の spec。0 件なら「なし。」の 1 行。
//     状態が有効か変更で、列「モジュール」がその ③ の名前の AC を、AC の順に置く
//   - ③ の表の比べ方：範囲の先頭と末尾の空行を除き、改行コードを LF にそろえて、完全一致で比べる。
//     全角と半角などは正規化しない
//   - 進行中の spec：docs/08-spec/ の直下の spec.md の、定義の行（- **AC-…**）。spec.md のないディレクトリは飛ばす。
//     コードブロック・HTML のコメント・1 行のコードの中は読まない
//   - テスト名：Java のメソッドの @DisplayName（src/test、src/integrationTest）。
//     Vitest・Playwright の test・it の第 1 引数（*.test.ts(x)、*.spec.ts）。Git の対象のファイルだけを読む
//   - 読まないテスト名：まとまりの名前（describe、クラス）、skip・fixme のテスト、skip・fixme のまとまりの中のテスト。
//     コメント・文字列の中の呼び出しも読まない
// 次のときに失敗する：
//   - AC 一覧の範囲：マーカーの範囲がない、2 組ある、閉じが前にある
//   - AC 一覧の形：範囲に表の見出しの行がない（空、空行だけ）・列が違う・行のセルの数が違う・表の行でない行がある（読み飛ばさない）・AC の番号の形が違う。
//     表の中に空行がある（GFM の表はそこで切れる。表より前と、表より後の空行は許す）
//   - AC 一覧の行：同じ AC の行が 2 つある、状態が有効・変更・廃止でない
//   - モジュール：有効・変更の AC で、③ の名前か common の 1 つでない。廃止の AC は空でもよい
//   - 変えた spec：有効の AC で「—」以外、変更・廃止の AC で空か「—」。
//     変更・廃止の AC の各番号が、番号の形でない、その番号の spec のディレクトリがない、2 つある
//   - 元の spec：番号の形でない、その番号の spec のディレクトリがない、2 つある
//   - 今の内容：有効・変更の AC で、空か仮の値（—、なし、<…>）。<br /> がある
//   - 今の内容の参照：spec の中だけで通じる参照（※1、事実 2、US の番号、spec の「…」）がある
//   - 今の内容の根拠：変更と common の AC で、根拠（リンク [<文書の名前>](<パス>) と、その直後の「の「<見出し>」」）がない。
//     リンクのない「…の「…」」は根拠と読まない（doc-refs が見出しの実在を確認できないため）
//   - ③ のマーカー：一覧にある ③ のマーカーの組がない、1 組でない、閉じが前にある、「今有効な AC」の節の外にある、節をまたぐ
//   - ③ の節と表：一覧にある ③ に「今有効な AC」の節がない、表が古い、節の中でマーカーの範囲の外に AC の表の行（| AC- で始まる行）がある
//   - ③ の名前：一覧にある ③ の名前に、バックエンドのモジュールのディレクトリがない。
//     バックエンドのモジュールのディレクトリと ③ の文書の両方がある名前の ③ が、一覧にない（追加し忘れ）
//   - その他のマーカー：一覧にない ③ にマーカーがある。既知のマーカー（generated:ac-list、generated:module-ac）でない generated: のコメントがある
//   - 進行中の spec：AC が AC 一覧にない、AC 一覧の元の spec がその spec でない（番号の重なり）。
//     定義の行が崩れている（箇条の先頭の太字の AC が、- **AC-<番号>** <文> の形でない）。
//     崩れた形には、* や + の箇条、字下げ、番号付きの箇条を含む。
//     spec から消した AC が、AC 一覧に有効・変更で残っている
//   - テスト名の AC：AC 一覧にない AC 番号がある、廃止の AC を参照する
//   - テスト名の書き方：範囲（AC-n〜m）、新しい形の後の数字だけの番号（AC-s-n、m）、空白の区切り（AC-n AC-m）
//   - テストのファイル：テスト名を読めない test・it の呼び出しがある、ファイルが 0 件。
//     読めない呼び出しは、名前が文字列でない、each の括弧が閉じない、知らない修飾が付いている、のどれか
//   - テスト名に出ない有効・変更の AC：CI（環境変数 CI=true）でだけ失敗にし、手元では警告にする。
//     spec を書いてからテストを書くまでの間も commit できるようにし、テストの漏れは CI で防ぐため。
//     手元で CI と同じに確認するとき：CI=true node scripts/ci/ac-check.mjs
// 失敗したとき：
//   - AC 一覧の行を修正する。進行中の spec の AC がないときは --write で転記し、モジュールの列を書く。
//     転記した行はモジュールが空なので、書くまで失敗する
//   - ③ の表が古いときは、--write で書き換えて差分を確認する。
//     マーカーと節の誤りは手動で修正する（そのとき --write は何も書かずに失敗する）
//   - テストを追加するか、テスト名に AC を書く。AC は読点で 1 つずつ書く（AC-n、AC-m）。
//     読めない呼び出しは ファイル:行 を示すので、テスト名を文字列で書く
// 除外：ac-exclusions.json に {キー: 理由}。キーは AC 一覧の有効・変更の AC（AC-n）。
//   テストがある AC、AC 一覧にない AC、廃止の AC の除外は失敗にする
// 使い方（リポジトリのルートで）：
//   - node scripts/ci/ac-check.mjs：照合する
//   - node scripts/ci/ac-check.mjs --write：AC 一覧にない進行中の spec の AC を転記し、③ のマーカーの範囲を書き換える。
//     範囲の外と、一覧にない ③ は変えない
//   - node scripts/ci/ac-check.mjs --tests：AC とテストの対応を表示する（対応は Git に置かない）
import { existsSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { extractTestNames, findTestFiles, scanTsTests } from './lib/test-files.mjs'
import { createLineLocator } from './lib/line-number.mjs'
import { describeEmptyReason, describeStaleExclusion, exclusionsPath, findEmptyReasons, readExclusions } from './lib/exclusions.mjs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { ARCHIVE, listSpecDirs } from './lib/spec-dirs.mjs'
import { isMain } from './lib/is-main.mjs'
import { describeUnreadableLine } from './lib/unreadable-line.mjs'
import { readCodeBlocks } from './lib/code-block.mjs'
import { readCodeSpans, replaceCodeSpans } from './lib/code-span.mjs'
import { readDocument, readHeadings } from './lib/doc-structure.mjs'
import { FUNCTIONAL_SPECS } from './spec-structure.mjs'

/** 除外の一覧のファイルの名前 */
const EXCLUSIONS_NAME = 'ac-exclusions.json'

/** 範囲を表す記号（全角の波ダッシュ、全角のチルダ、半角のチルダ） */
const RANGE = '[〜～~]'

/** AC の番号の書き方。MVP の `12` と、1→10 の `7-1`（spec の番号 ＋ 連番）の 2 つの形 */
const AC_NUMBER = '[0-9]+(?:-[0-9]+)?'

/** テスト名の中の AC の並び（`AC-n、m`、`AC-n〜m` など）に合う正規表現の元。1 つ目の括弧が、`AC-` の後の並び全体 */
const AC_LIST_SOURCE = `AC-(${AC_NUMBER}(?:\\s*(?:[、,]\\s*(?:AC-)?|${RANGE}\\s*)${AC_NUMBER})*)`

/** AC の番号だけに合う正規表現（`AC-` の後の全体） */
const AC_NUMBER_ONLY = new RegExp(`^(${AC_NUMBER})$`)

/**
 * AC の番号の文字列を、照合に使う値にする。MVP の `12` は数、1→10 の `7-1` は文字列のまま。
 * 形で型を分けるので、`AC-s` と `AC-s-n` は別の AC になる。
 *
 * @param text AC- の後の番号（`12`、`7-1`）
 * @returns 数（MVP）か文字列（1→10）
 */
export function toAcKey(text) {
  return text.includes('-') ? text : Number(text)
}

/** AC の値を並べるための組。MVP は番号の順、その後に 1→10 の spec の番号、連番の順 */
function acOrder(ac) {
  if (typeof ac === 'number') return [0, ac, 0]
  const [spec, seq] = ac.split('-').map(Number)
  return [1, spec, seq]
}

/** AC の値の比較（並べ替え用） */
export function compareAc(a, b) {
  const left = acOrder(a)
  const right = acOrder(b)
  return left[0] - right[0] || left[1] - right[1] || left[2] - right[2]
}

/**
 * テスト名から AC 番号を取り出す。AC- の後に読点・カンマで並べた番号（AC- は先頭だけでもよい）を読む。
 *
 * @param name テスト名
 * @returns AC の番号と、範囲の表記（範囲の両端は番号に含める）
 */
export function parseAcRefs(name) {
  const acs = []
  const ranges = []
  const list = new RegExp(AC_LIST_SOURCE, 'g')
  const item = new RegExp(`(${AC_NUMBER})\\s*(${RANGE})\\s*(${AC_NUMBER})|(${AC_NUMBER})`, 'g')
  for (const [, numbers] of name.matchAll(list)) {
    for (const [, from, sign, to, single] of numbers.matchAll(item)) {
      if (single !== undefined) {
        acs.push(toAcKey(single))
      } else {
        // 新しい形の範囲は終わりの連番だけでもよい（AC-s-n〜m は AC-s-n と AC-s-m）
        const end = from.includes('-') && !to.includes('-') ? `${from.split('-')[0]}-${to}` : to
        acs.push(toAcKey(from), toAcKey(end))
        ranges.push(`${from}${sign}${to}`)
      }
    }
  }
  return { acs, ranges }
}

/**
 * テスト名から、曖昧な AC の並べ方を取り出す。新しい形（AC-s-n）の後に、読点・カンマと数字だけの番号（AC-s-n、m）が続く書き方。<br />
 * 理由：MVP の AC-m と読め、MVP 側の抜けを隠すため。新しい形は AC-s-n、AC-s-m と書く。
 *
 * @param name テスト名
 * @returns 曖昧な並べ方の文字列
 */
export function findAmbiguousAcLists(name) {
  const list = new RegExp(AC_LIST_SOURCE, 'g')
  const found = []
  for (const match of name.matchAll(list)) {
    // 範囲を含まない並びの区切りで分け、AC- が付かない数字だけの項目が、新しい形の直後にあるかを見る
    const items = match[0].split(/\s*[、,]\s*/)
    const bad = items.some((item, i) => i > 0 && /^[0-9]+(?!\d|-)/.test(item) && /^(?:AC-)?[0-9]+-[0-9]+/.test(items[i - 1]))
    if (bad) found.push(match[0])
  }
  return found
}

/**
 * テスト名から、空白（半角・全角）で区切った AC の並び（AC-n AC-m）を取り出す。<br />
 * 理由：複数の AC は読点で 1 つずつ並べる決まりで（AC-n、AC-m）、空白の並びは読み方が人によって分かれるため。
 *
 * @param name テスト名
 * @returns 空白で区切った並びの文字列
 */
export function findSpacedAcLists(name) {
  const list = new RegExp(`AC-${AC_NUMBER}(?:[\\s\\u3000]+AC-${AC_NUMBER})+`, 'g')
  return [...name.matchAll(list)].map((match) => match[0])
}

/**
 * AC とテスト名を照合する。
 *
 * @param input.specAcs    AC の番号（AC 一覧と進行中の spec の AC）
 * @param input.tests      テスト（ファイルと名前）
 * @param input.exclusions 除外の一覧（"AC-n" → 理由）
 * @param input.deprecated 廃止の AC の番号。テストが要らず、テストが参照すると失敗。除外の一覧のキーにもできない
 * @param input.strict     テスト名に出ない AC を失敗にするか。false なら警告にする（missing には出す）
 * @returns 照合した結果。ok が false なら失敗
 */
export function check({ specAcs, tests, exclusions, deprecated = new Set(), strict = true }) {
  const covered = new Set()
  const ranges = []
  const ambiguous = []
  const spaced = []
  const unknown = []
  const deprecatedRefs = []
  for (const test of tests) {
    const refs = parseAcRefs(test.name)
    if (refs.ranges.length > 0) {
      ranges.push(test)
    }
    if (findAmbiguousAcLists(test.name).length > 0) {
      ambiguous.push(test)
    }
    if (findSpacedAcLists(test.name).length > 0) {
      spaced.push(test)
    }
    for (const ac of refs.acs) {
      if (!specAcs.has(ac)) {
        unknown.push({ ...test, ac })
      } else if (deprecated.has(ac)) {
        deprecatedRefs.push({ ...test, ac })
      } else {
        covered.add(ac)
      }
    }
  }

  const excluded = new Set()
  const invalidExclusions = []
  const emptyReasons = findEmptyReasons(exclusions)
  for (const key of Object.keys(exclusions)) {
    const match = key.startsWith('AC-') ? AC_NUMBER_ONLY.exec(key.slice(3)) : null
    if (!match || !specAcs.has(toAcKey(match[1])) || deprecated.has(toAcKey(match[1]))) {
      invalidExclusions.push(key)
    } else if (!emptyReasons.includes(key)) {
      excluded.add(toAcKey(match[1]))
    }
  }

  const sorted = (values) => [...values].sort(compareAc)
  const missing = sorted([...specAcs].filter((ac) => !covered.has(ac) && !excluded.has(ac) && !deprecated.has(ac)))
  const staleExclusions = sorted([...excluded].filter((ac) => covered.has(ac)))
  const ok =
    (!strict || missing.length === 0) &&
    ranges.length === 0 &&
    ambiguous.length === 0 &&
    spaced.length === 0 &&
    unknown.length === 0 &&
    deprecatedRefs.length === 0 &&
    staleExclusions.length === 0 &&
    invalidExclusions.length === 0 &&
    emptyReasons.length === 0
  return { ok, missing, ranges, ambiguous, spaced, unknown, deprecatedRefs, staleExclusions, invalidExclusions, emptyReasons }
}

/** AC 一覧の状態 */
const STATUSES = ['有効', '変更', '廃止']

/** ③ の表に入れる状態 */
const ACTIVE = ['有効', '変更']

/** 共通（どのモジュールにも属さない AC）を表す、モジュールの列の値。AC 一覧だけに置き、③ の表には生成しない */
const COMMON = 'common'

/** AC 一覧の文書（リポジトリのルートから） */
const AC_LIST = 'docs/03-機能仕様/acceptance-criteria.md'

/** ③ の文書の置き場所（リポジトリのルートから） */
const MODULE_DOCS_DIR = 'docs/03-機能仕様'

/** バックエンドのモジュールのディレクトリの置き場所（リポジトリのルートから） */
const BACKEND_MODULES_DIR = 'backend/ecsite/src/main/java/com/ecsite/module'

/** spec の置き場所（リポジトリのルートから） */
const SPECS_DIR = 'docs/08-spec'

/** AC 一覧の表の列 */
const LIST_COLUMNS = ['AC', 'モジュール', '状態', '元の spec', '変えた spec', '今の内容']

/** ③ の生成の表の列 */
const MODULE_COLUMNS = ['AC', '今の内容', '元の spec']

/** ③ に生成する表の、見出しと区切りの行 */
const MODULE_TABLE_HEAD = ['| AC | 今の内容 | 元の spec |', '|----|----------|-----------|']

/** 対応する AC が 0 件のモジュールの、マーカーの範囲の中の行 */
const NO_ACS = 'なし。'

/** ③ の生成の表を置く節。雛形の骨組みの ## にあることを、テストで確認する */
export const MODULE_AC_SECTION = '今有効な AC'

/** 生成のマーカーの名前（AC 一覧の表と、③ の表） */
const LIST_MARK = 'ac-list'
const MODULE_MARK = 'module-ac'

/** 生成のマーカーの形（開きは `<!-- generated:<名前> -->`、閉じは `<!-- /generated:<名前> -->`） */
const MARK = /^(\/?)generated:(ac-list|module-ac)$/

/** 表の区切りの行 */
const SEPARATOR_ROW = /^\|(?:\s*:?-+:?\s*\|)+$/

/** AC の番号のセル（0 で埋めない） */
const AC_CELL = /^AC-([1-9][0-9]*(?:-[1-9][0-9]*)?)$/

/** 元の spec・変えた spec の列の、spec の番号の形（0 埋めの有無は変えずに、そのまま比べる） */
const SPEC_NUMBER = /^[0-9]+$/

/** 変えた spec の列で、2 つ以上の番号を区切る記号 */
const SPEC_NUMBER_SEPARATOR = '、'

/** 今の内容に残してはいけない、spec の中だけで通じる参照（※1、事実 2、US の番号、spec の節） */
const SPEC_ONLY_REFS = [/※[0-9]/, /事実\s*[0-9]/, /US-[0-9]/, /spec\s*の「/]

/**
 * 今の内容の根拠の形：リンク（[<文書の名前>](<パス>)）と、その直後の「の「<見出し>」」。リンクのない「…の「…」」は当たらない。
 * 理由：doc-refs が見出しの実在を確認できるのは、リンクの後の「の「…」」だけのため
 */
const BASIS = /\[[^\]]+\]\([^()\s]+\)の「[^「」]+」/

/** 今の内容の仮の値（`<…>`。英字で始まる HTML のタグは除く） */
const PLACEHOLDER = /<[^\x00-\x7F][^<>]*>/

/**
 * 改行コードを LF にそろえる。
 *
 * @param {string} text 文書
 * @returns {string} LF にした文書
 */
function toLf(text) {
  return text.replace(/\r\n?/g, '\n')
}

/**
 * 文書を、改行コードを付けたままの行に分ける（書き換えるときに、範囲の外の改行コードを変えないため）。
 *
 * @param {string} text 文書
 * @returns {string[]} 行（改行コードを含む）。つなぐと元の文書になる
 */
function splitKeepingEol(text) {
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
function readPair(marks, name) {
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
 * @returns {{ rows: { ac: number | string, label: string, module: string, status: string, origin: string, changedBy: string, content: string, line: number }[], problems: string[], pair: { open: number, close: number } | null }}
 *   rows：AC の行（書いた順。line は 1 から）。problems：問題（行を付ける）。pair：マーカーの範囲（読めなければ null）
 */
export function readAcList(document) {
  const text = toLf(document)
  const { marks, problems } = findMarks(text)
  if (marks.some((mark) => mark.name === MODULE_MARK)) problems.push(`AC 一覧に ③ の生成の印（generated:${MODULE_MARK}）がある`)
  const { pair, problems: pairProblems } = readPair(marks, LIST_MARK)
  problems.push(...pairProblems)
  if (!pair && pairProblems.length === 0) problems.push(`<!-- generated:${LIST_MARK} --> と <!-- /generated:${LIST_MARK} --> の範囲がない`)
  if (!pair) return { rows: [], problems, pair: null }
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
function specNumberOf(nameOrRel) {
  return /^[0-9]+/.exec(nameOrRel.split('/').pop())[0]
}

/**
 * 元の spec・変えた spec の 1 つの番号を、spec のディレクトリと照合する。
 *
 * @param {string} number 列の値（変えた spec は読点で区切った 1 つ）
 * @param {{ name: string, rel: string }[]} specDirs spec のディレクトリ
 * @param {string} column 列の名前
 * @returns {string | null} 問題。なければ null
 */
function checkSpecNumber(number, specDirs, column) {
  if (!SPEC_NUMBER.test(number)) return `${column}が spec の番号（001、227）の形でない（${number}）`
  const found = specDirs.filter((dir) => specNumberOf(dir.name) === number)
  if (found.length === 0) return `${column}の番号 ${number} の spec のディレクトリがない（${SPECS_DIR} の直下とアーカイブの下を探した）`
  if (found.length > 1) return `${column}の番号 ${number} の spec のディレクトリが ${found.length} つある（${found.map((dir) => dir.rel).join('、')}。番号から 1 つに決められない）`
  return null
}

/**
 * AC 一覧の行の、重複・状態・モジュール・変えた spec・元の spec・今の内容を調べる。
 *
 * @param {object} input
 * @param {ReturnType<typeof readAcList>['rows']} input.rows AC 一覧の行
 * @param {Set<string>} input.modules ③ のモジュールの名前
 * @param {{ name: string, rel: string }[]} input.specDirs spec のディレクトリ（listSpecDirs の結果）
 * @returns {string[]} 問題（行を付ける）
 */
export function checkAcRows({ rows, modules, specDirs }) {
  const problems = []
  const seen = new Map()
  for (const row of rows) {
    const where = `${row.line} 行：${row.label}：`
    const add = (problem) => problems.push(`${where}${problem}`)
    const key = String(row.ac)
    if (seen.has(key)) add(`同じ AC の行が ${seen.get(key)} 行にもある`)
    else seen.set(key, row.line)
    const knownModule = modules.has(row.module) || row.module === COMMON
    const originProblem = checkSpecNumber(row.origin, specDirs, '元の spec')
    if (originProblem) add(originProblem)
    // 変更・廃止の AC の変えた spec の各番号（空と「—」は、状態ごとの検査が見る）
    const checkChangedBy = () => {
      for (const number of row.changedBy.split(SPEC_NUMBER_SEPARATOR)) {
        const problem = checkSpecNumber(number.trim(), specDirs, '変えた spec')
        if (problem) add(problem)
      }
    }
    if (row.content !== '') {
      for (const pattern of SPEC_ONLY_REFS) {
        if (pattern.test(row.content)) add(`今の内容に、spec の中だけで通じる参照（${pattern.exec(row.content)[0]}）がある。意味を補って書き換えるか、削る`)
      }
      if (/<br\s*\/?>/i.test(row.content)) add('今の内容を複数行にしない（<br /> を使わない）')
    }
    if (!STATUSES.includes(row.status)) {
      add(`状態が有効・変更・廃止のどれでもない（${row.status}）`)
      continue
    }
    if (row.status === '廃止') {
      if (row.module !== '' && !knownModule) add(`廃止の AC のモジュールが、空でも ③ の名前でも ${COMMON} でもない（${row.module}）`)
      if (row.changedBy === '' || row.changedBy === '—') add('廃止の AC の変えた spec がない')
      else checkChangedBy()
      continue
    }
    if (!knownModule) add(`モジュールが、③ の名前か ${COMMON} の 1 つでない（「${row.module}」）。名前は ${[...modules].sort().join('、')}`)
    if (row.status === '有効' && row.changedBy !== '—') add(`有効の AC の変えた spec は「—」にする（${row.changedBy}）`)
    if (row.status === '変更') {
      if (row.changedBy === '' || row.changedBy === '—') add('変更の AC の変えた spec がない')
      else checkChangedBy()
    }
    if (['', '—', '-', 'なし'].includes(row.content) || PLACEHOLDER.test(row.content)) add(`今の内容が空か仮の値（${row.content}）`)
    else if ((row.status === '変更' || row.module === COMMON) && !BASIS.test(row.content)) {
      add('今の内容に、根拠を、リンク（[<文書の名前>](<パス>)）と、その直後の「の「<見出し>」」の形で添える')
    }
  }
  return problems
}

/**
 * 進行中の spec の AC と、AC 一覧を照合する。AC 一覧の元の spec は、spec のディレクトリの名前の先頭の番号と文字列として比べる。
 * AC の本文は比べない（転記の後は AC 一覧を手動で修正する）。
 *
 * @param {object} input
 * @param {ReturnType<typeof readAcList>['rows']} input.rows AC 一覧の行
 * @param {{ rel: string, definitions: { ac: number | string }[] }[]} input.specs 進行中の spec（docs/08-spec からのパスと AC の定義）
 * @returns {string[]} 問題
 */
export function checkInProgress({ rows, specs }) {
  const problems = []
  const byKey = new Map(rows.map((row) => [String(row.ac), row]))
  for (const spec of specs) {
    const number = specNumberOf(spec.rel)
    const defined = new Set(spec.definitions.map((definition) => String(definition.ac)))
    for (const key of defined) {
      const row = byKey.get(key)
      if (!row) problems.push(`進行中の spec（${spec.rel}）の AC-${key} が AC 一覧にない。node scripts/ci/ac-check.mjs --write で転記し、モジュールを書く`)
      else if (row.origin !== number) problems.push(`AC 一覧の AC-${key} の元の spec が、その AC を定義する進行中の spec（${spec.rel}）でない（番号が重なっていないかも確かめる）`)
    }
    for (const row of rows) {
      if (row.origin === number && row.status !== '廃止' && !defined.has(String(row.ac))) {
        problems.push(`${row.line} 行：${row.label} は、元の spec（${spec.rel}）にない。AC 一覧の行を消すか、spec に戻す`)
      }
    }
  }
  return problems
}

/**
 * spec の本文から、AC の定義を読む。コードブロック・HTML のコメント・1 行のコードの中は読まない。
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
  // 箇条（- * + と番号。字下げしてもよい）の先頭の太字の AC。定義の形でなければ、読み飛ばされて検査を通らないよう、崩れた行として報告する
  const attempt = /^\s*(?:[-*+]|\d+[.)])\s*\*\*AC-/
  plain.forEach((line, index) => {
    if (!attempt.test(line)) return
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
  const { rows, problems, pair, lastLine } = readAcList(document)
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
 * ③ の文書の名前を、バックエンドのモジュールのディレクトリと、構造の検査の対象の一覧（FUNCTIONAL_SPECS）に照合する。<br />
 * 一覧にある ③ にディレクトリがない名前と、ディレクトリがあるモジュールの ③ が一覧にない名前を、問題にする。
 * 後者を許すと、追加し忘れた ③ が、節の構造の検査と「今有効な AC」の表の照合から外れる。
 *
 * @param {object} input
 * @param {{ file: string, module: string, required: boolean }[]} input.docs ③ の置き場所の文書（required は構造の検査の対象の一覧にあるか）
 * @param {Set<string>} input.modules ③ のモジュールの名前（listModuleNames の結果）
 * @returns {string[]} 問題（ファイルのパスを付ける）
 */
export function checkModuleDocNames({ docs, modules }) {
  const problems = []
  for (const doc of docs) {
    if (doc.required && !modules.has(doc.module)) problems.push(`${doc.file}：構造の検査の対象の ③ の名前（${doc.module}）に、バックエンドのモジュールのディレクトリがない`)
    if (!doc.required && modules.has(doc.module)) problems.push(`${doc.file}：モジュールの ③ が構造の検査の対象の一覧（spec-structure.mjs の FUNCTIONAL_SPECS）にない。固定の節に書いて一覧に追加する`)
  }
  return problems
}

/**
 * ③ の「今有効な AC」の表を、AC 一覧から作る。有効・変更の、そのモジュールの AC を AC の順に置く。
 *
 * @param {ReturnType<typeof readAcList>['rows']} rows AC 一覧の行
 * @param {string} module モジュールの名前
 * @returns {string} マーカーの範囲の中に置く行（LF。末尾に改行を付けない）。0 件なら「なし。」
 */
export function renderModuleTable(rows, module) {
  const selected = rows.filter((row) => ACTIVE.includes(row.status) && row.module === module && module !== COMMON).sort((a, b) => compareAc(a.ac, b.ac))
  if (selected.length === 0) return NO_ACS
  return [...MODULE_TABLE_HEAD, ...selected.map((row) => `| ${row.label} | ${row.content} | ${row.origin} |`)].join('\n')
}

/**
 * ③ の文書の、生成のマーカーと「今有効な AC」の表を調べる。
 * 構造の検査の対象の一覧にある ③ は、「今有効な AC」の節の中にマーカーの組が必須で、範囲の中が生成した表と同じでなければならない。
 * 一覧にない ③ は、マーカーを置かない。
 *
 * @param {object} input
 * @param {string} input.text ③ の文書（改行は LF でも CRLF でもよい）
 * @param {string} input.module モジュールの名前
 * @param {ReturnType<typeof readAcList>['rows']} input.rows AC 一覧の行
 * @param {boolean} input.required 構造の検査の対象の一覧にあるか
 * @returns {{ problems: string[], stale: boolean, updated: string | null }}
 *   problems：問題（行を付ける）。stale：表が古いだけか（--write で修正できる）。updated：範囲の中を書き換えた文書（書き換えられないときは null。古くなければ元のまま）
 */
export function checkModuleDoc({ text, module, rows, required }) {
  const lf = toLf(text)
  const { marks, problems } = findMarks(lf)
  if (marks.some((mark) => mark.name === LIST_MARK)) problems.push(`③ に AC 一覧の生成の印（generated:${LIST_MARK}）がある`)
  const own = marks.filter((mark) => mark.name === MODULE_MARK)
  if (!required) {
    if (own.length > 0) problems.push(`${own[0].line} 行：構造の検査の対象の一覧（spec-structure.mjs の FUNCTIONAL_SPECS）にない ③ に、生成の印がある。固定の節に書き換えて一覧に加えてから置く`)
    return { problems, stale: false, updated: null }
  }
  const { pair, problems: pairProblems } = readPair(marks, MODULE_MARK)
  problems.push(...pairProblems)
  if (!pair && pairProblems.length === 0) problems.push(`「${MODULE_AC_SECTION}」の節に <!-- generated:${MODULE_MARK} --> と <!-- /generated:${MODULE_MARK} --> の組がない`)
  const h2 = readHeadings(lf).filter((heading) => heading.level <= 2)
  const sections = h2.filter((heading) => heading.level === 2 && heading.title === MODULE_AC_SECTION)
  if (sections.length !== 1) problems.push(`「${MODULE_AC_SECTION}」の節が ${sections.length} 個ある（1 つにする）`)
  else if (pair) {
    const next = h2.find((heading) => heading.line > sections[0].line)?.line ?? Infinity
    if (pair.open < sections[0].line || pair.close > next) problems.push(`生成の印（${pair.open} 行、${pair.close} 行）が「${MODULE_AC_SECTION}」の節の外にあるか、節をまたぐ`)
    else {
      // 節の中で、範囲の外の AC の表の行。手動で書いた行が、AC 一覧にない AC が ③ に残ったまま検査を通らないようにする
      const { plain } = readDocument(lf)
      for (let number = sections[0].line + 1; number < Math.min(next, plain.length + 1); number++) {
        if (number >= pair.open && number <= pair.close) continue
        if (plain[number - 1].trimStart().startsWith('| AC-')) problems.push(`${number} 行：「${MODULE_AC_SECTION}」の節の、生成の印の範囲の外に、AC の表の行がある。AC 一覧に書き、--write で範囲の中に生成する`)
      }
    }
  }
  if (problems.length > 0) return { problems, stale: false, updated: null }

  const expected = renderModuleTable(rows, module)
  const inner = lf.split('\n').slice(pair.open, pair.close - 1)
  while (inner.length > 0 && inner[0].trim() === '') inner.shift()
  while (inner.length > 0 && inner.at(-1).trim() === '') inner.pop()
  if (inner.join('\n') === expected) return { problems: [], stale: false, updated: text }
  const lines = splitKeepingEol(text)
  const eol = lines[pair.open - 1].endsWith('\r\n') ? '\r\n' : '\n'
  const updated = [...lines.slice(0, pair.open), ...expected.split('\n').map((line) => `${line}${eol}`), ...lines.slice(pair.close - 1)].join('')
  return {
    problems: [`${pair.open} 行：「${MODULE_AC_SECTION}」の表が AC 一覧と違う（列は ${MODULE_COLUMNS.join('、')}）。node scripts/ci/ac-check.mjs --write で書き換え、差分を確認する`],
    stale: true,
    updated,
  }
}

/**
 * AC とテストの対応を、表示する行にする（--tests）。
 *
 * @param {object} input
 * @param {ReturnType<typeof readAcList>} input.list AC 一覧を読んだ結果
 * @param {Map<number | string, string[]>} input.testFiles AC → その AC をテスト名に持つテストのファイル（リポジトリのルートからのパス）
 * @param {Record<string, unknown>} input.exclusions 除外の一覧（"AC-n" → 理由）
 * @returns {{ lines: string[], ok: boolean }} 表示する行（最後は要約の行）。AC 一覧を読めなければ ok が false で、行は問題
 */
export function renderTestMap({ list, testFiles, exclusions }) {
  if (list.problems.length > 0) return { lines: [...list.problems, 'NG：AC 一覧を読めない'], ok: false }
  const lines = list.rows.map((row) => {
    const files = [...new Set(testFiles.get(row.ac) ?? [])].sort()
    if (files.length > 0) return `${row.label}：${files.join('、')}`
    if (exclusions[row.label]) return `${row.label}：除外（${exclusions[row.label]}）`
    return `${row.label}：なし${row.status === '廃止' ? '（廃止）' : ''}`
  })
  return { lines: [...lines, `AC とテストの対応：AC ${list.rows.length} 件`], ok: true }
}

/**
 * テスト名に出ない AC を失敗にするかを、環境から決める。CI では失敗にし、手元では警告にする。<br />
 * 理由：spec を書いてからテストを書くまでの間も、手元で commit できるようにするため。
 * テストの漏れは、CI（push と pull request）で止める。
 *
 * @param env 環境変数
 * @returns CI なら true
 */
export function isStrict(env) {
  return env.CI === 'true'
}

/** 照合した結果を、標準出力に書く */
function report(result, root, strict) {
  const where = (test) => `${path.relative(root, test.file).replaceAll('\\', '/')}：${test.name}`
  if (result.missing.length > 0) {
    // 手元では警告。check-all.sh は「警告：」で始まる行を、通ったときも出す
    const prefix = strict ? '' : '警告：'
    console.log(`${prefix}テスト名に出ない AC：${result.missing.map((ac) => `AC-${ac}`).join('、')}`)
    console.log('  テストを追加するか、テストで確認できない理由を scripts/ci/ac-exclusions.json に書く。')
  }
  for (const test of result.ranges) {
    console.log(`範囲の表記（AC 番号を 1 つずつ書く）：${where(test)}`)
  }
  for (const test of result.ambiguous) {
    console.log(`曖昧な AC の並べ方（新しい形は AC-7-1、AC-7-2 と書く）：${where(test)}`)
  }
  for (const test of result.spaced) {
    console.log(`空白で区切った AC の並び（AC-1、AC-2 と読点で 1 つずつ書く）：${where(test)}`)
  }
  for (const test of result.unknown) {
    console.log(`AC 一覧にない AC-${test.ac}：${where(test)}`)
  }
  for (const test of result.deprecatedRefs) {
    console.log(`廃止した AC-${test.ac} を参照するテスト（AC 番号を消すか、AC 一覧の状態を直す）：${where(test)}`)
  }
  for (const ac of result.staleExclusions) {
    console.log(describeStaleExclusion(EXCLUSIONS_NAME, `AC-${ac}`))
  }
  for (const key of result.emptyReasons) {
    console.log(describeEmptyReason(EXCLUSIONS_NAME, key))
  }
  if (result.invalidExclusions.length > 0) {
    console.log(`${EXCLUSIONS_NAME}：AC 一覧にない AC か、廃止の AC の除外：${result.invalidExclusions.join('、')}`)
  }
}

/**
 * テスト名を読めない呼び出しを、失敗の文にして返す。読めないテストは AC の照合から消えるので、読み飛ばさない。
 *
 * @param {string} source Vitest・Playwright のテストのソース
 * @param {string} rel 表示するファイルのパス
 * @param {{ index: number, reason: string }[]} unreadable scanTsTests が返した、読めない呼び出し
 * @returns {string[]} `ファイル:行 内容（理由）`
 */
export function describeUnreadableTests(source, rel, unreadable) {
  const lineOf = createLineLocator(source)
  const lines = source.split('\n')
  return unreadable.map(({ index, reason }) => {
    const line = lineOf(index)
    return describeUnreadableLine({ file: rel, line, text: lines[line - 1], reason })
  })
}

/**
 * Git の対象のテストを読み、テスト名と、AC → テストのファイルの対応を返す。
 *
 * @param {string} root リポジトリのルート
 * @param {string[]} gitFiles Git の対象のファイル
 * @returns {{ tests: { file: string, name: string }[], unreadable: string[], testFiles: Map<number | string, string[]> }}
 */
function readTests(root, gitFiles) {
  const testSources = findTestFiles(root, gitFiles)
  exitIfNoFiles(testSources, 'テストのファイル')
  const unreadable = []
  const tests = testSources.flatMap(({ file, kind }) => {
    const source = readFileSync(file, 'utf8')
    if (kind === 'ts') {
      // 1 回の走査で、テスト名と読めない呼び出しの両方を得る
      const scan = scanTsTests(source)
      unreadable.push(...describeUnreadableTests(source, path.relative(root, file).replaceAll('\\', '/'), scan.unreadable))
      return scan.tests.filter((test) => !test.skipped).map((test) => ({ file, name: test.name }))
    }
    return extractTestNames(source, kind).map((name) => ({ file, name }))
  })
  const testFiles = new Map()
  for (const test of tests) {
    for (const ac of parseAcRefs(test.name).acs) {
      testFiles.set(ac, [...(testFiles.get(ac) ?? []), path.relative(root, test.file).replaceAll('\\', '/')])
    }
  }
  return { tests, unreadable, testFiles }
}

/**
 * ③ の置き場所の文書（AC 一覧を除く）を読む。
 *
 * @param {string} root リポジトリのルート
 * @returns {{ file: string, module: string, text: string, required: boolean }[]} リポジトリのルートからのパス、モジュールの名前（ファイル名から .md を除く）、内容、構造の検査の対象の一覧にあるか
 */
function readModuleDocs(root) {
  if (!existsSync(path.join(root, MODULE_DOCS_DIR))) return []
  return readdirSync(path.join(root, MODULE_DOCS_DIR))
    .filter((name) => name.endsWith('.md') && `${MODULE_DOCS_DIR}/${name}` !== AC_LIST)
    .sort()
    .map((name) => {
      const file = `${MODULE_DOCS_DIR}/${name}`
      return { file, module: name.slice(0, -3), text: readFileSync(path.join(root, file), 'utf8'), required: FUNCTIONAL_SPECS.includes(file) }
    })
}

/** AC 一覧、③、進行中の spec、テストを読んで照合する。--write は転記と ③ の表の書き換え、--tests は対応の表示 */
function main() {
  // 他の検査と同じく、リポジトリのルートで実行する（今のディレクトリを、読む対象のルートにする）
  const root = process.cwd()
  const writing = process.argv.includes('--write')
  // 対象のファイルが 0 件なら、何も見ずに通らないよう、先に失敗にする
  const gitFiles = listGitFiles([], root)
  exitIfNoFiles(gitFiles, 'Git のファイル')
  const { tests, unreadable, testFiles } = writing ? { tests: [], unreadable: [], testFiles: new Map() } : readTests(root, gitFiles)
  const listFile = path.join(root, AC_LIST)
  const document = existsSync(listFile) ? readFileSync(listFile, 'utf8') : ''
  let list = readAcList(document)
  const exclusions = readExclusions(exclusionsPath(EXCLUSIONS_NAME))

  if (process.argv.includes('--tests')) {
    const { lines, ok } = renderTestMap({ list, testFiles, exclusions })
    for (const line of lines) console.log(line)
    process.exit(ok ? 0 : 1)
  }

  const inProgress = readInProgressSpecs(path.join(root, SPECS_DIR))
  const docs = readModuleDocs(root)
  const backendDir = path.join(root, BACKEND_MODULES_DIR)
  const backendDirs = existsSync(backendDir) ? readdirSync(backendDir, { withFileTypes: true }).filter((entry) => entry.isDirectory()).map((entry) => entry.name) : []
  const modules = listModuleNames({ backendDirs, docFiles: docs.map((doc) => path.basename(doc.file)) })
  const listProblems = [...list.problems, ...inProgress.problems]

  if (writing) {
    if (listProblems.length > 0) {
      for (const problem of listProblems) console.log(`${AC_LIST}：${problem}`)
      console.log('NG：AC 一覧か進行中の spec を読めないので、書き換えない')
      process.exit(1)
    }
    const written = addAcsToList(document, inProgress.specs.flatMap((spec) => spec.definitions))
    if (written !== document) list = readAcList(written)
    const results = docs.map((doc) => ({ doc, result: checkModuleDoc({ text: doc.text, module: doc.module, rows: list.rows, required: doc.required }) }))
    const blocking = results.flatMap(({ doc, result }) => (result.stale ? [] : result.problems.map((problem) => `${doc.file}：${problem}`)))
    if (blocking.length > 0) {
      for (const problem of blocking) console.log(problem)
      console.log('NG：③ の生成の印か節を修正してから、もう一度実行する（何も書き換えていない）')
      process.exit(1)
    }
    if (written !== document) {
      writeFileSync(listFile, written)
      console.log(`転記した：${AC_LIST}`)
    }
    for (const { doc, result } of results.filter(({ result }) => result.stale)) {
      writeFileSync(path.join(root, doc.file), result.updated)
      console.log(`書き換えた：${doc.file}`)
    }
    console.log('OK：書き換えた後に、モジュールの列と差分を確認する')
    process.exit(0)
  }

  const specDirs = listSpecDirs(path.join(root, SPECS_DIR))
  listProblems.push(...checkAcRows({ rows: list.rows, modules, specDirs }), ...checkInProgress({ rows: list.rows, specs: inProgress.specs }))
  const docProblems = checkModuleDocNames({ docs, modules })
  for (const doc of docs) {
    docProblems.push(...checkModuleDoc({ text: doc.text, module: doc.module, rows: list.rows, required: doc.required }).problems.map((problem) => `${doc.file}：${problem}`))
  }

  const specAcs = new Set([...list.rows.map((row) => row.ac), ...inProgress.specs.flatMap((spec) => spec.definitions.map((definition) => definition.ac))])
  const deprecated = new Set(list.rows.filter((row) => row.status === '廃止').map((row) => row.ac))
  const strict = isStrict(process.env)
  const result = check({ specAcs, tests, exclusions, deprecated, strict })
  report(result, root, strict)
  for (const problem of listProblems) console.log(`${AC_LIST}：${problem}`)
  for (const problem of docProblems) console.log(problem)
  for (const problem of unreadable) console.log(problem)
  const ok = result.ok && listProblems.length === 0 && docProblems.length === 0 && unreadable.length === 0
  const generated = docs.filter((doc) => doc.required).length
  console.log(`${ok ? 'OK' : 'NG'}：AC ${list.rows.length} 件（廃止 ${deprecated.size} 件）、③ の生成の表 ${generated} 件、テスト名 ${tests.length} 件、除外 ${Object.keys(exclusions).length} 件`)
  process.exit(ok ? 0 : 1)
}

if (isMain(import.meta.url)) {
  main()
}
