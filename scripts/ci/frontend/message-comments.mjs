// 文言をキーで引く所に、表示する内容のコメントがあることを確認する（コメントの書き方の「文言をキーにした所」）。
// キーだけでは画面に何が出るかを読めないため、`t('…')`・`translate('…')` の呼び出しの始めから、閉じ括弧のある行の終わりまでに、
// コメント（/* */ か //）を求める。キーを組み立てる呼び出し（t(`…${x}`)）は、表示が値で変わるので見ない。
//
// 対象は、店舗画面・管理画面・共有パッケージの本番ソース（.ts、.tsx）と、店舗画面・管理画面の src/test/ 内のテスト補助と、generated/ 内の .tsx。
//
// コメントを式の終わりに書く形のため、呼び出しを包む JSX の式（子の `{…}`、属性の `={…}`）の中のコメントでも通す。
// 関数の本体（アロー関数、戻り値の型を書いた関数、クラスとメソッド）の `{` は JSX の式と読まず、外側へ探し続ける。
// 戻り値の型には、型引数（`Promise<string>`）、オブジェクトの型（`): { a: string } {`）、交差・合併の最後のオブジェクトの型（`): Base & { a: string } {`）がある。
// 外側に JSX の式があれば、その式の中のコメントで通る。
// 波括弧は、scan-code の読み分けで 1 回だけ読み、文字列・コメントの中の波括弧は数えない。`{` が JSX の式かは、直前のタグ・式の範囲だけで決める。
//
// 失敗したとき：呼び出しの後に `/* 表示する内容 */` か `// 表示する内容` を書く。
//
// 除外：なし
//
// 使い方：node scripts/ci/frontend/message-comments.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { closingParen } from '../../shared/source/closing-paren.mjs'
import { exitIfNoFiles, listGitFiles } from '../../shared/git/git-files.mjs'
import { isScreenSource } from '../lib/docs/screen-sources.mjs'
import { createLineLocator } from '../lib/source/line-number.mjs'
import { scanCode } from '../../shared/source/scan-code.mjs'
import { keepTsComments } from '../../shared/source/strip-comments.mjs'

/** 文言の関数（t、tCommon、translate など）を、キーを文字で書いて呼ぶ所。名前の途中（format( など）は除く */
const MESSAGE_CALL = /(?<![\w.$])(?:t(?:[A-Z]\w*)?|translate)\(\s*(['"])([^'"]+)\1/g

/** 戻り値の型の中（`):` の後、または `):` の後の型の交差・合併の `&`・`|` の後）で終わるか。型の波括弧と本体の前を見分ける */
const RETURN_TYPE_END = /\)\s*:(?:[^(){};=]*[&|])?$/

/** 型引数の前の名前が、型の位置にあると分かる語（`class A<T> {`、`extends B<T> {`） */
const TYPE_KEYWORD_END = /\b(?:extends|implements|class|interface)$/

/** 遡って読む字数の上限（直前の数語だけを見る判定用） */
const LOOKBEHIND = 200

/**
 * 呼び出しを含む JSX の式（{…}）が閉じ、後ろに別の JSX（式、要素、属性）が続く位置を返す。
 *
 * 閉じ括弧の後ろがコメント・カンマ・文字列などなら、同じ式の続きとして見続ける（`{ title: t('x') } // …`、
 * `${t('x')}${y}` など）。
 *
 * @param {string} text  ソース
 * @param {number} from  呼び出しの閉じ括弧の位置
 * @param {number} limit 見る範囲の終わり
 * @returns {number} 位置。なければ limit
 */
function jsxExpressionEnd(text, from, limit) {
  let depth = 0
  for (let i = from + 1; i < limit; i += 1) {
    if (text[i] === '{') {
      depth += 1
    } else if (text[i] === '}') {
      if (depth > 0) {
        depth -= 1
      } else if (/^\s*(?:\{|<|\/?>|[\w-]+=)/.test(text.slice(i + 1, limit))) {
        return i
      }
    }
  }
  return limit
}

/**
 * 位置の前の、空白でない最後の字の位置を返す。
 *
 * @param {string} text ソース
 * @param {number} from 見始める位置（この位置から前へ）
 * @returns {number} 位置。なければ -1
 */
function lastNonSpace(text, from) {
  let i = from
  while (i >= 0 && /\s/.test(text[i])) i -= 1
  return i
}

/**
 * `<` が、型引数の始まり（`Promise<string>`、`Component<Props>`）かを返す。
 *
 * `<` の直前が名前なら、型引数かタグ（`Total<b>` の `<b>`。JSX のテキストの直後の開きタグ）かが決まらない。
 * 名前の前が、型の位置を示すもの（戻り値の型の `):`、交差・合併の `&`・`|`、`extends`・`implements`・`class`・`interface`）なら、型引数と読む。
 *
 * @param {string} text ソース
 * @param {number} start `<` の位置
 * @returns {boolean} 型引数なら true
 */
function isTypeArgumentsStart(text, start) {
  if (!/[\w$]/.test(text[start - 1] ?? '')) return false
  let nameStart = start - 1
  while (nameStart > 0 && /[\w$.]/.test(text[nameStart - 1])) nameStart -= 1
  const before = lastNonSpace(text, nameStart - 1)
  if (before < 0) return false
  const near = text.slice(Math.max(0, before - LOOKBEHIND), before + 1)
  return RETURN_TYPE_END.test(near) || TYPE_KEYWORD_END.test(near)
}

/**
 * `>` で終わる直前のタグの `<` の位置を返す。属性の式の波括弧、文字列、コメントの中は、skip で飛ばす。
 *
 * @param {string} text ソース
 * @param {number} end `>` の位置
 * @param {Map<number, number>} skip 飛ばす区間の、終わりの字の位置 → 始まりの字の位置
 * @returns {number} `<` の位置。なければ -1
 */
function tagStart(text, end, skip) {
  let depth = 0
  for (let i = end; i >= 0; i -= 1) {
    if (skip.has(i)) {
      i = skip.get(i)
    } else if (text[i] === '>' && text[i - 1] !== '=') {
      depth += 1
    } else if (text[i] === '<') {
      depth -= 1
      if (depth === 0) return i
    }
  }
  return -1
}

/**
 * 位置の字が、JSX の要素のタグの終わり（`<p className="x">`、`</b>`、`<br />`）かを返す。
 *
 * `>` で終わっていても、アロー関数の `=>` と、型引数の終わり（`Promise<string>`、`Component<Props>`）は、関数やクラスの本体の前なので違う。
 * 閉じタグ（`</b>`）と自己閉じタグ（`<br />`）は、型引数に書けない形なので、名前の直後でもタグである。
 *
 * @param {string} text ソース
 * @param {number} end `{` の直前の、空白でない字の位置
 * @param {Map<number, number>} skip 飛ばす区間の、終わりの字の位置 → 始まりの字の位置
 * @returns {boolean} タグの終わりなら true
 */
function endsWithJsxTag(text, end, skip) {
  if (text[end] !== '>' || text[end - 1] === '=') return false
  if (text[end - 1] === '/') return true
  const start = tagStart(text, end, skip)
  if (start < 0) return true
  if (text[start + 1] === '/') return true
  return !isTypeArgumentsStart(text, start)
}

/**
 * 位置の字が、前の JSX の式の終わり（`{a}{b}` の `}`）かを返す。
 *
 * 戻り値の型がオブジェクトの型（`): { inclTax: string } {`、`): Base & { a: string } {`、`): null | { a: string } {`）のときの `}` は、関数の本体の前なので違う。
 * 対応する `{` の直前が、戻り値の型の中（引数の閉じ括弧の後の `:`、または型の交差・合併の `&`・`|`）なら、型である。
 * テキストの末尾の `:`（`合計: {a}{b}`）は、型ではない。
 *
 * @param {string} text ソース
 * @param {number} end `{` の直前の、空白でない字の位置
 * @param {Map<number, number>} skip 飛ばす区間の、終わりの字の位置 → 始まりの字の位置
 * @returns {boolean} 前の JSX の式の終わりなら true
 */
function endsWithJsxExpression(text, end, skip) {
  if (text[end] !== '}') return false
  const open = skip.get(end)
  if (open === undefined) return true
  const before = lastNonSpace(text, open - 1)
  return !RETURN_TYPE_END.test(text.slice(Math.max(0, before - LOOKBEHIND), before + 1))
}

/**
 * `{` が、JSX の式（子の `{`、属性の `={`）の始まりかを返す。
 *
 * 子の式は、直前（空白を除く）が要素の終わり（>）か前の式（}）。属性の値は、名前の直後の ={。
 * 引数のオブジェクトや関数の本体（アロー関数、戻り値の型を書いた関数、クラスとメソッド）の括弧は、JSX の式ではない。
 *
 * @param {string} text ソース
 * @param {number} open `{` の位置
 * @param {Map<number, number>} skip 飛ばす区間の、終わりの字の位置 → 始まりの字の位置
 * @returns {boolean} JSX の式なら true
 */
function opensJsxExpression(text, open, skip) {
  if (text[open - 1] === '=' && /[\w-]/.test(text[open - 2] ?? '')) return true
  const end = lastNonSpace(text, open - 1)
  if (end < 0) return false
  return endsWithJsxTag(text, end, skip) || endsWithJsxExpression(text, end, skip)
}

/**
 * 呼び出しごとに、包むいちばん内側の JSX の式（子の `{…}`、属性の `={…}`）の範囲を返す。
 *
 * 式が複数行にわたり、コメントを式の終わりに書く形（`{ a ? t('x') : b /* … *\/ }`）のため。
 * ソースを scan-code で前から 1 回だけ読み、文字列・コメントの中の波括弧は数えない。
 *
 * @param {string} text  ソース
 * @param {number[]} indexes 呼び出しの位置（昇順）
 * @returns {({ open: number, close: number } | null)[]} 呼び出しごとの式の括弧の位置。JSX の式に包まれていない（か閉じていない）なら null
 */
function enclosingJsxExpressions(text, indexes) {
  const result = indexes.map(() => null)
  // 閉じた波括弧の `}` の位置 → 対応する `{` の位置と、文字列・コメント・正規表現の終わりの字の位置 → 始まりの字の位置
  const skip = new Map()
  const stack = []
  let next = 0
  scanCode(text, {}, (type, from, to) => {
    while (next < indexes.length && indexes[next] < to) {
      result[next] = stack.findLast((entry) => entry.isJsx) ?? null
      next += 1
    }
    if (type !== 'code') {
      skip.set(to - 1, from)
    } else if (text[from] === '{') {
      stack.push({ open: from, close: -1, isJsx: opensJsxExpression(text, from, skip) })
    } else if (text[from] === '}') {
      const entry = stack.pop()
      if (entry) {
        entry.close = from
        skip.set(from, entry.open)
      }
    }
  })
  return result.map((entry) => (entry && entry.close >= 0 ? entry : null))
}

/** 範囲にコメント（/* *\/ か //）があるか。範囲は、コメントの外を空白にしたソース（keepTsComments）から取る */
function hasComment(range) {
  return range.includes('/*') || range.includes('//')
}

/**
 * コメントのない文言の呼び出しを探す。
 *
 * @param {string} text ソース
 * @returns {{ line: number, key: string }[]} 見つけた呼び出しの行とキー（行の順）
 */
export function findMissingComments(text) {
  const lineOf = createLineLocator(text)
  // 文字列の中の // や /*（URL など）をコメントにしないよう、コメントの有無はコメントだけを残したソースで見る（位置は元と同じ）
  const comments = keepTsComments(text)
  const matches = [...text.matchAll(MESSAGE_CALL)]
  const expressions = enclosingJsxExpressions(
    text,
    matches.map((match) => match.index),
  )
  const missing = []
  for (const [n, match] of matches.entries()) {
    const close = closingParen(text, match.index + match[0].indexOf('('))
    const lineEnd = text.indexOf('\n', close)
    // 同じ行の別の JSX の式のコメントを、この呼び出しのコメントにしない。
    // 三元演算子の両方の枝を 1 つのコメントで書く形（{a ? t('x') : t('y') /* … */}）は通す
    const end = jsxExpressionEnd(text, close, lineEnd === -1 ? text.length : lineEnd)
    if (hasComment(comments.slice(match.index, end))) continue
    const expression = expressions[n]
    if (expression && hasComment(comments.slice(match.index, expression.close))) continue
    missing.push({ line: lineOf(match.index), key: match[2] })
  }
  return missing
}

/** 2 つの画面の src の .tsx */
const SCREEN_TSX = /^frontend\/(?:admin|store)\/src\/.*\.tsx$/

/**
 * 調べるファイルかを返す。
 *
 * 画面の本番のソースの定義（lib/docs/screen-sources.mjs）に加えて、
 * 2 つの画面の src/test/ のテストの補助と generated/ の .tsx も見る（テストと shadcn/ui の部品は除く）。
 * 理由：テストの補助や生成物にも画面の文言を書けるので、共通の定義に合わせて対象を狭めない。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} 調べるなら true
 */
export function isTargetFile(file) {
  return isScreenSource(file) || (SCREEN_TSX.test(file) && !file.endsWith('.test.tsx') && !file.includes('/components/ui/'))
}

if (import.meta.main) {
  const files = listGitFiles().filter((file) => isTargetFile(file))
  exitIfNoFiles(files, '文言を読むファイル')
  let count = 0
  for (const file of files) {
    for (const hit of findMissingComments(readFileSync(file, 'utf8'))) {
      console.log(`${file}:${hit.line} ${hit.key} 表示する内容のコメントがない（呼び出しの後に /* 内容 */ を書く。コメントの書き方の「文言をキーにした所」）`)
      count += 1
    }
  }
  console.log(`コメントのない文言のキー：${count}`)
  process.exit(count ? 1 : 0)
}
