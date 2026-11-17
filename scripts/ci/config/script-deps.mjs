// scripts/ci/・scripts/dev/・scripts/shared/・e2e/ の間の import の向きが、scripts の設計（docs/02-設計/scripts.md）の「設計の判断」にある依存の向きに合うことを確認する。
// - scripts/ci/ と scripts/dev/ は、互いに import しない
// - scripts/shared/ は、scripts/ci/ と scripts/dev/ を import しない
// - e2e/ は、scripts/ の下では scripts/shared/ だけを import する
//
// 相対パスの import・export・require・import()・new URL(…, import.meta.url) を読む（テストのファイルも同じ規則）。コメントの中は読まない。
// 対象のファイルが 0 件のときも失敗にする。
//
// 失敗したとき：2 つ以上のフォルダが使うモジュールは scripts/shared/ に移し、両方からそこを import する。
//
// 除外：なし
//
// 使い方：node scripts/ci/config/script-deps.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { exitIfNoFiles, listGitFiles } from '../../shared/git/git-files.mjs'
import { stripComments } from '../../shared/source/strip-comments.mjs'

/** 読むファイル（JavaScript と TypeScript） */
const SOURCE_FILE = /\.(?:mjs|cjs|js|ts|mts)$/

/** 読む指定（相対パスの文字列）。from・import()・import の文・require()・new URL() */
const SPECIFIER = /(?:\bfrom\s*|\bimport\s*\(\s*|\bimport\s+|\brequire\s*\(\s*|\bnew\s+URL\s*\(\s*)(['"])(\.{1,2}\/[^'"]*)\1/g

/** 置き場所の区分。前から順に当てる */
const AREAS = [
  ['ci', 'scripts/ci/'],
  ['dev', 'scripts/dev/'],
  ['shared', 'scripts/shared/'],
  ['e2e', 'e2e/'],
]

/** 区分ごとの、import してよくない区分 */
const FORBIDDEN = {
  ci: ['dev', 'e2e'],
  dev: ['ci', 'e2e'],
  shared: ['ci', 'dev', 'e2e'],
  e2e: ['ci', 'dev'],
}

/**
 * ファイルの置き場所の区分を返す。
 *
 * @param {string} file リポジトリのルートからのパス（/ 区切り）
 * @returns {string | null} 区分（ci・dev・shared・e2e）。どれでもなければ null
 */
export function areaOf(file) {
  return AREAS.find(([, prefix]) => file.startsWith(prefix))?.[0] ?? null
}

/**
 * ソースから、相対パスの指定と行番号を読む。
 *
 * @param {string} text ソース
 * @returns {{ specifier: string, line: number }[]} 指定（書いた順）
 */
export function readSpecifiers(text) {
  const code = stripComments(text)
  return [...code.matchAll(SPECIFIER)].map((match) => ({ specifier: match[2], line: code.slice(0, match.index).split('\n').length }))
}

/**
 * 依存の向きの違反を返す。
 *
 * @param {string[]} files 読むファイル（リポジトリのルートからのパス）
 * @param {(file: string) => string} readFile 中身を返す
 * @returns {{ file: string, line: number, target: string }[]} 違反（ファイルと行の順）
 */
export function findDependencyProblems(files, readFile) {
  const problems = []
  for (const file of files) {
    const from = areaOf(file)
    if (from === null) continue
    for (const { specifier, line } of readSpecifiers(readFile(file))) {
      const target = path.posix.normalize(path.posix.join(path.posix.dirname(file), specifier))
      const to = areaOf(target)
      if (to !== null && FORBIDDEN[from].includes(to)) problems.push({ file, line, target })
    }
  }
  return problems
}

if (import.meta.main) {
  const files = listGitFiles(['scripts', 'e2e']).filter((file) => SOURCE_FILE.test(file))
  exitIfNoFiles(files, 'scripts と e2e のソース')
  const problems = findDependencyProblems(files, (file) => readFileSync(file, 'utf8'))
  for (const { file, line, target } of problems) console.log(`${file}:${line}：${areaOf(file)} から ${areaOf(target)} を読む（${target}）`)
  console.log(`依存の向きの違反：${problems.length}`)
  process.exit(problems.length > 0 ? 1 : 0)
}
