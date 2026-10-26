// 検査の script が、直接実行されたかの判定を共通の関数（lib/is-main.mjs の isMain）だけで行うことを確認する。
// 判定の書き方が複数あると、シンボリックリンクや Windows のパスの違いで、直接実行しても何も検査せずに成功する script ができる。
// 対象は scripts/ の下の、テストでない .mjs と .cjs（scripts/dev/ も含む。テストは直接実行しない）。コメントの中は読まない。
// argv[1] を直接読む書き方、import.meta.url の比較、import.meta.main、require.main があると失敗にする。対象のファイルが 0 件のときも失敗にする。
// 使い方：node scripts/ci/main-entry.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'
import { createLineLocator } from './lib/line-number.mjs'
import { stripComments } from './lib/strip-comments.mjs'

/** 判定の定義そのもの。ここだけが argv[1] を読んでよい */
const DEFINITION = 'scripts/ci/lib/is-main.mjs'

/** 共通の関数を使わない、直接実行の判定の書き方 */
const JUDGMENT = /\bargv\[1\]|\bimport\.meta\.main\b|\bimport\.meta\.url\s*[!=]==?|[!=]==?\s*import\.meta\.url\b|\brequire\.main\b/g

/**
 * 検査の対象のファイルか。
 *
 * @param {string} file リポジトリのルートからのパス（/ 区切り）
 * @returns {boolean} scripts/ の下の、テストでない .mjs・.cjs（判定の定義を除く）なら true
 */
export function isMainTarget(file) {
  return /^scripts\/.*\.[mc]js$/.test(file) && !/\.test\.[mc]js$/.test(file) && file !== DEFINITION
}

/**
 * 直接実行の判定を、共通の関数を使わずに書いた行を探す。
 *
 * @param {string} text ソース
 * @returns {{ line: number, call: string }[]} 見つけた行と書き方（行の順）
 */
export function findMainJudgments(text) {
  const code = stripComments(text)
  const lineOf = createLineLocator(code)
  return [...code.matchAll(JUDGMENT)].map((match) => ({ line: lineOf(match.index), call: match[0].replace(/\s+/g, ' ').replace(/^[!=]==?\s*/, '') }))
}

if (isMain(import.meta.url)) {
  const files = listGitFiles(['scripts']).filter((file) => isMainTarget(file))
  exitIfNoFiles(files, '検査の script')
  const found = files.flatMap((file) => findMainJudgments(readFileSync(file, 'utf8')).map((hit) => ({ file, ...hit })))
  for (const hit of found) {
    console.log(`${hit.file}:${hit.line} ${hit.call}（直接実行の判定は lib/is-main.mjs の isMain(import.meta.url) を使う）`)
  }
  console.log(`直接実行の判定の検査：script ${files.length} 件、共通の関数を使わない判定：${found.length} 件`)
  process.exit(found.length > 0 ? 1 : 0)
}
