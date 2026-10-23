// 文言をキーで引く所に、表示する内容のコメントがあることを確認する（コメントの書き方 第 6.1 節）。
// キーだけでは画面に何が出るかを読めないため、`t('…')` の呼び出しの始めから、閉じ括弧のある行の終わりまでに、
// コメント（/* */ か //）を求める。キーを組み立てる呼び出し（t(`…${x}`)）は、表示が値で変わるので見ない。
// 使い方：node scripts/ci/message-comments.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { listGitFiles } from './lib/git-files.mjs'

/** 文言の関数（t、tCommon など）を、キーを文字で書いて呼ぶ所。名前の途中（format( など）は除く */
const MESSAGE_CALL = /(?<![\w.$])t(?:[A-Z]\w*)?\(\s*(['"])([^'"]+)\1/g

/**
 * 呼び出しの閉じ括弧の位置を返す。文字列の中の括弧は数えない。
 *
 * @param {string} text  ソース
 * @param {number} open  開き括弧の位置
 * @returns {number} 閉じ括弧の位置。見つからなければソースの終わり
 */
function closingParen(text, open) {
  let depth = 0
  let quote = null
  for (let i = open; i < text.length; i += 1) {
    const ch = text[i]
    if (quote) {
      if (ch === '\\') i += 1
      else if (ch === quote) quote = null
    } else if (ch === "'" || ch === '"' || ch === '`') {
      quote = ch
    } else if (ch === '(') {
      depth += 1
    } else if (ch === ')') {
      depth -= 1
      if (depth === 0) return i
    }
  }
  return text.length
}

/**
 * 呼び出しを含む JSX の式（{…}）が閉じ、後ろに別の JSX（式、要素、属性）が続く位置を返す。<br />
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
 * 呼び出しを包む、いちばん内側の JSX の式（子の `{` か、属性の `={`）の範囲を返す。<br />
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
      const isJsx = /[>}]$/.test(before) || /[\w-]=$/.test(text.slice(0, i))
      // 引数のオブジェクトや関数の本体の括弧なら、外側へ探し続ける
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

/** 範囲にコメント（/* *\/ か //）があるか */
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
  const lineOf = (index) => text.slice(0, index).split('\n').length
  const missing = []
  for (const match of text.matchAll(MESSAGE_CALL)) {
    const close = closingParen(text, match.index + match[0].indexOf('('))
    const lineEnd = text.indexOf('\n', close)
    // 同じ行の別の JSX の式のコメントを、この呼び出しのコメントにしない。
    // 三元演算子の両方の枝を 1 つのコメントで書く形（{a ? t('x') : t('y') /* … */}）は通す
    const end = jsxExpressionEnd(text, close, lineEnd === -1 ? text.length : lineEnd)
    if (hasComment(text.slice(match.index, end))) continue
    const expression = enclosingJsxExpression(text, match.index)
    if (expression && hasComment(text.slice(match.index, expression.close))) continue
    missing.push({ line: lineOf(match.index), key: match[2] })
  }
  return missing
}

/**
 * 調べるファイルかを返す。
 * 画面の本番のソースの定義（lib/screen-sources.mjs）より狭く、2 つの画面の .tsx だけを見る（共有パッケージ、src/ui/、generated/ は見ない）。
 * 定義に合わせて対象を広げるのは別の作業で行うので、今は今の対象のままにしている（今のコードの違反は、広げても 0 件）
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} 2 つの画面の src の .tsx（テストと shadcn/ui の生成物を除く）なら true
 */
export function isTargetFile(file) {
  return (
    /^frontend\/(?:admin|store)\/src\/.*\.tsx$/.test(file) &&
    !/\.test\.tsx$/.test(file) &&
    !file.includes('/components/ui/')
  )
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const files = listGitFiles().filter((file) => isTargetFile(file))
  let count = 0
  for (const file of files) {
    for (const hit of findMissingComments(readFileSync(file, 'utf8'))) {
      console.log(`${file}:${hit.line} ${hit.key}`)
      count += 1
    }
  }
  console.log(`コメントのない文言のキー：${count}`)
  process.exit(count ? 1 : 0)
}
