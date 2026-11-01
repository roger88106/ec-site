// 画面のコードに、表示の文言（日本語の文字）を直接書いた所がないことを確認する（フロントの書き方の「表示の文言は文言ファイルに置く」）。
// 文言は各画面の messages/ja.json（共通は frontend/shared/src/messages/ja.json）に置き、キーで引く。
// コメントと、開発者だけが見る文（ログ、プログラムの誤りの例外）は見ない。テスト、生成物、shadcn/ui の部品、除外の一覧（hardcoded-text-exclusions.json。リポジトリのルートからのパスと理由）のファイルも見ない。
// 使い方：node scripts/ci/hardcoded-text.mjs [--count]（リポジトリのルートで。--count は数だけを出し、失敗にしない）
import { readFileSync } from 'node:fs'
import { checkExclusions, exclusionsPath, readExclusions } from './lib/exclusions.mjs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isScreenSource } from './lib/screen-sources.mjs'
import { stripComments } from './lib/strip-comments.mjs'
import { isMain } from './lib/is-main.mjs'

/** 開発者だけが見る文（ログ、プログラムの誤りの例外）の行。利用者に見せる文ではないので見ない */
const DEVELOPER_ONLY = /\b(log|console)\.(debug|info|warn|error)\(|throw new Error\(/

/** 日本語の文字（ひらがな、カタカナ、漢字、全角の記号と句読点） */
const JAPANESE = /[　-ヿ㐀-鿿＀-￯]/

/**
 * 表示の文言を直接書いた行を探す。
 *
 * @param {string} text ファイルの中身
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findHardcodedText(text) {
  const original = text.split('\n')
  return stripComments(text)
    .split('\n')
    .flatMap((line, index) =>
      JAPANESE.test(line) && !DEVELOPER_ONLY.test(line) ? [{ line: index + 1, text: original[index].trim() }] : [],
    )
}

/**
 * ファイルごとに、文言を直接書いた行を集める。
 *
 * @param {string[]} files 調べるファイル
 * @param {(file: string) => string} read ファイルの中身を読む関数
 * @returns {Map<string, { line: number, text: string }[]>} 文言を直接書いた行があるファイルと、その行（ない場合はファイルを含めない）
 */
export function scanSources(files, read) {
  const hitsByFile = new Map()
  for (const file of files) {
    const hits = findHardcodedText(read(file))
    if (hits.length > 0) hitsByFile.set(file, hits)
  }
  return hitsByFile
}

if (isMain(import.meta.url)) {
  const exclusions = readExclusions(exclusionsPath('hardcoded-text-exclusions.json'))
  const files = listGitFiles(['frontend']).filter(isScreenSource)
  exitIfNoFiles(files, '画面のソース')
  // 除外のファイルも読む。除外が必要なのは、文言を直接書いた行があるファイルだけ
  const hitsByFile = scanSources(files, (file) => readFileSync(file, 'utf8'))
  const found = [...hitsByFile].filter(([file]) => !(file in exclusions)).flatMap(([file, hits]) => hits.map((hit) => ({ file, ...hit })))
  const problems = checkExclusions('hardcoded-text-exclusions.json', exclusions, (file) => hitsByFile.has(file))
  const countOnly = process.argv.includes('--count')
  if (countOnly) {
    // --count は数だけを出し、失敗にしない。除外の誤りは警告として出して、数も出す
    for (const problem of problems) console.log(`警告：${problem}`)
    const byFile = Object.groupBy(found, (hit) => hit.file)
    for (const [file, hits] of Object.entries(byFile)) console.log(`${hits.length}\t${file}`)
    console.log(`合計 ${found.length} 行、${Object.keys(byFile).length} ファイル`)
  } else {
    // 他の検査と同じ形：違反と誤りを標準出力に出し、最後の行に要約を出す。違反があれば失敗
    for (const problem of problems) console.log(problem)
    for (const hit of found) console.log(`${hit.file}:${hit.line}  ${hit.text}`)
    console.log(`表示の文言を直接書いた所：${found.length}、除外の誤り：${problems.length}`)
    process.exit(found.length > 0 || problems.length > 0 ? 1 : 0)
  }
}
