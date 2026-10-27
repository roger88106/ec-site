// 検査の script が、直接実行されたかの判定を共通の関数（lib/is-main.mjs の isMain）だけで行うことを確認する。
// 判定の書き方が複数あると、シンボリックリンクや Windows のパスの違いで、直接実行しても何も検査せずに成功する script ができる。
// 対象は scripts/ と frontend/shared/scripts/ の下の、テストでない .mjs と .cjs（scripts/dev/ も含む。テストは直接実行しない）。コメントと文字列の中は読まない。
// argv の 2 つ目の要素を取り出す書き方（argv[1]、argv.at(1)、分割代入）、import.meta.url・filename の比較、import.meta.main、require.main があると失敗にする。対象のファイルが 0 件のときも失敗にする。
// 使い方：node scripts/ci/main-entry.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'
import { createLineLocator } from './lib/line-number.mjs'
import { stripComments } from './lib/strip-comments.mjs'

/** 判定の定義そのもの。ここだけが argv[1] を読んでよい */
const DEFINITION = 'scripts/ci/lib/is-main.mjs'

/**
 * 共通の関数を使わない、直接実行の判定の書き方（コメントと文字列を除いたコードに当てる）。
 * - argv の 2 つ目の要素（実行した script のパス）を取り出す：argv[1]、argv.at(1)、`[, entry] = process.argv`。3 つ目以降（引数）は対象にしない
 * - import.meta の url・filename を、比べる（===、!==）か endsWith などで調べる。import.meta.main
 * - require.main（CommonJS の判定）。filename・url を、ファイルの位置を求めるために使うのは対象にしない
 */
const JUDGMENT = new RegExp(
  [
    String.raw`\bargv\s*\[\s*1\s*\]`,
    String.raw`\bargv\s*\.\s*at\s*\(\s*1\s*\)`,
    String.raw`\[\s*[^\],]*,\s*[^\s\],][^\]]*\]\s*=\s*process\s*\.\s*argv\b(?!\s*\.)`,
    String.raw`\bimport\s*\.\s*meta\s*\.\s*main\b`,
    String.raw`\bimport\s*\.\s*meta\s*\.\s*(?:url|filename)\s*(?:[!=]==?|\.\s*(?:endsWith|startsWith|includes)\s*\()`,
    String.raw`[!=]==?\s*import\s*\.\s*meta\s*\.\s*(?:url|filename)\b`,
    String.raw`\brequire\s*\.\s*main\b`,
  ].join('|'),
  'g',
)

/**
 * 見つけたときの直し方を、ファイルの種類に合わせて返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {string} 直し方の文
 */
export function adviceOf(file) {
  return file.endsWith('.cjs') ? '直接実行の判定は、.mjs にして lib/is-main.mjs の isMain(import.meta.url) を使う（CommonJS では import.meta を使えない）' : '直接実行の判定は lib/is-main.mjs の isMain(import.meta.url) を使う'
}

/**
 * 検査の対象のファイルか。
 *
 * @param {string} file リポジトリのルートからのパス（/ 区切り）
 * @returns {boolean} scripts/ と frontend/shared/scripts/ の下の、テストでない .mjs・.cjs（判定の定義を除く）なら true
 */
export function isMainTarget(file) {
  return /^(?:frontend\/shared\/)?scripts\/.*\.[mc]js$/.test(file) && !/\.test\.[mc]js$/.test(file) && file !== DEFINITION
}

/**
 * 直接実行の判定を、共通の関数を使わずに書いた行を探す。
 *
 * @param {string} text ソース
 * @returns {{ line: number, call: string }[]} 見つけた行と書き方（行の順）
 */
export function findMainJudgments(text) {
  const code = stripComments(text, { blankStrings: true })
  const lineOf = createLineLocator(code)
  return [...code.matchAll(JUDGMENT)].map((match) => ({ line: lineOf(match.index), call: match[0].replace(/\s+/g, ' ').replace(/^[!=]==?\s*/, '') }))
}

if (isMain(import.meta.url)) {
  const files = listGitFiles(['scripts', 'frontend/shared/scripts']).filter((file) => isMainTarget(file))
  exitIfNoFiles(files, '検査の script')
  const found = files.flatMap((file) => findMainJudgments(readFileSync(file, 'utf8')).map((hit) => ({ file, ...hit })))
  for (const hit of found) {
    console.log(`${hit.file}:${hit.line} ${hit.call}（${adviceOf(hit.file)}）`)
  }
  console.log(`共通の関数を使わない判定：${found.length}`)
  process.exit(found.length > 0 ? 1 : 0)
}
