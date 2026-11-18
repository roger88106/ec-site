// 文言をキーで引く所に、表示する内容のコメントがあることを確認する（コメントの書き方の「文言をキーにした所」）。
// キーだけでは画面に何が出るかを読めないため、`t('…')`・`translate('…')` の呼び出しの始めから、閉じ括弧のある行の終わりまでに、
// コメント（/* */ か //）を求める。キーを組み立てる呼び出し（t(`…${x}`)）は、表示が値で変わるので見ない。
//
// 対象は、店舗画面・管理画面・共有パッケージの本番ソース（.ts、.tsx）と、店舗画面・管理画面の src/test/ 内のテスト補助と、generated/ 内の .tsx。
//
// コメントを式の終わりに書く形のため、呼び出しを包む JSX の式（子の `{…}`、属性の `={…}`）の中のコメントでも通す。
// 関数の本体（アロー関数、戻り値の型を書いた関数、クラスとメソッド）の `{` は JSX の式と読まず、外側へ探し続ける。
// 外側に JSX の式があれば、その式の中のコメントで通る。
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
import { keepTsComments } from '../../shared/source/strip-comments.mjs'

/** 文言の関数（t、tCommon、translate など）を、キーを文字で書いて呼ぶ所。名前の途中（format( など）は除く */
const MESSAGE_CALL = /(?<![\w.$])(?:t(?:[A-Z]\w*)?|translate)\(\s*(['"])([^'"]+)\1/g

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
 * `{` の直前の文字列が、JSX の要素のタグの終わり（`<p className="x">`、`</b>`、`<br />`）かを返す。
 *
 * `>` で終わっていても、アロー関数の `=>` と、型引数の終わり（`Promise<string>`、`Component<Props>`）は、関数やクラスの本体の前なので違う。
 * 型引数は、対応する `<` が名前の直後にあることで、タグと見分ける。
 * 閉じタグ（`</b>`）と自己閉じタグ（`<br />`）は、型引数に書けない形なので、名前の直後でもタグである。
 *
 * @param {string} before `{` の直前までのソース（末尾の空白は除く）
 * @returns {boolean} タグの終わりなら true
 */
function endsWithJsxTag(before) {
  if (!before.endsWith('>') || before.endsWith('=>')) return false
  if (before.endsWith('/>') || /<\/[\w.$-]+\s*>$/.test(before)) return true
  let depth = 0
  for (let i = before.length - 1; i >= 0; i -= 1) {
    if (before[i] === '>' && before[i - 1] !== '=') {
      depth += 1
    } else if (before[i] === '<') {
      depth -= 1
      if (depth === 0) return !/[\w$]/.test(before[i - 1] ?? '')
    }
  }
  return true
}

/**
 * `{` の直前の文字列が、前の JSX の式の終わり（`{a}{b}` の `}`）かを返す。
 *
 * 戻り値の型がオブジェクトの型（`): { inclTax: string } {`）のときの `}` は、関数の本体の前なので違う。
 * 対応する `{` の直前が、引数の閉じ括弧の後の `:`（`): {`）なら、型である。
 * テキストの末尾の `:`（`合計: {a}{b}`）は、型ではない。
 *
 * @param {string} before `{` の直前までのソース（末尾の空白は除く）
 * @returns {boolean} 前の JSX の式の終わりなら true
 */
function endsWithJsxExpression(before) {
  if (!before.endsWith('}')) return false
  let depth = 0
  for (let i = before.length - 1; i >= 0; i -= 1) {
    if (before[i] === '}') {
      depth += 1
    } else if (before[i] === '{') {
      depth -= 1
      if (depth === 0) return !/\)\s*:$/.test(before.slice(0, i).trimEnd())
    }
  }
  return true
}

/**
 * 呼び出しを包む、いちばん内側の JSX の式（子の `{` か、属性の `={`）の範囲を返す。
 *
 * 式が複数行にわたり、コメントを式の終わりに書く形（`{ a ? t('x') : b /* … *\/ }`）のため。
 *
 * @param {string} text  ソース
 * @param {number} index 呼び出しの位置
 * @returns {{ open: number, close: number } | null} 式の括弧の位置。JSX の式に包まれていなければ null
 */
function enclosingJsxExpression(text, index) {
  let depth = 0
  for (let i = index - 1; i >= 0; i -= 1) {
    if (text[i] === '}') {
      depth += 1
    } else if (text[i] === '{') {
      if (depth > 0) {
        depth -= 1
        continue
      }
      // JSX の子の式は、直前（空白を除く）が要素の終わり（>）か前の式（}）。属性の値は、名前の直後の ={
      const before = text.slice(0, i).trimEnd()
      const isJsx = endsWithJsxTag(before) || endsWithJsxExpression(before) || /[\w-]=$/.test(text.slice(0, i))
      // 引数のオブジェクトや関数の本体（アロー関数、戻り値の型を書いた関数、クラスとメソッド）の括弧なら、外側へ探し続ける
      if (!isJsx) continue
      let inner = 0
      for (let j = i + 1; j < text.length; j += 1) {
        if (text[j] === '{') inner += 1
        else if (text[j] === '}') {
          if (inner === 0) return { open: i, close: j }
          inner -= 1
        }
      }
      return null
    }
  }
  return null
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
  const missing = []
  for (const match of text.matchAll(MESSAGE_CALL)) {
    const close = closingParen(text, match.index + match[0].indexOf('('))
    const lineEnd = text.indexOf('\n', close)
    // 同じ行の別の JSX の式のコメントを、この呼び出しのコメントにしない。
    // 三元演算子の両方の枝を 1 つのコメントで書く形（{a ? t('x') : t('y') /* … */}）は通す
    const end = jsxExpressionEnd(text, close, lineEnd === -1 ? text.length : lineEnd)
    if (hasComment(comments.slice(match.index, end))) continue
    const expression = enclosingJsxExpression(text, match.index)
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
