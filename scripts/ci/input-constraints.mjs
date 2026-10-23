// 画面の入力の制約（長さ・範囲）に、数字を直接書いた所がないことを確認する（フロントの書き方 第 2.3 節）。
// 制約は API の契約から書き出した定数（src/api/generated/constraints.ts）を使う。
// 使い方：node scripts/ci/input-constraints.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isScreenSource, SCREEN_APPS } from './lib/screen-sources.mjs'
import { isMain } from './lib/is-main.mjs'

/** src/test/ のテストの補助（.tsx）。テストそのものは含まない */
const TEST_HELPER = new RegExp(`^frontend/(?:${SCREEN_APPS.join('|')})/src/test/.*(?<!\\.test)\\.tsx$`)

/**
 * 検査の対象かを返す。
 * 画面の本番のソースの定義に加えて src/test/ のテストの補助も読む。
 * テストの補助にも画面の部品があり、数字を直接書くと本番の部品の写しになるため。
 *
 * @param {string} file リポジトリのルートからのパス（/ 区切り）
 * @returns {boolean} 対象なら true
 */
export function isTarget(file) {
  return isScreenSource(file) || TEST_HELPER.test(file)
}

/** 制約の属性に、数字を直接書いた所（例：maxLength={50}、min="0"） */
const LITERAL = /\b(maxLength|minLength|max|min)=(\{\s*-?\d|"-?\d)/g

/**
 * ファイルの中の、数字を直接書いた制約の属性を探す。
 *
 * @param {string} text ファイルの中身
 * @returns {{ line: number, text: string }[]} 見つけた行
 */
export function findLiterals(text) {
  return text.split('\n').flatMap((line, index) => (line.match(LITERAL) ? [{ line: index + 1, text: line.trim() }] : []))
}

if (isMain(import.meta.url)) {
  const targets = listGitFiles(['frontend']).filter(isTarget)
  exitIfNoFiles(targets, '入力の制約を調べるファイル')
  const found = targets
    .flatMap((file) => findLiterals(readFileSync(file, 'utf8')).map((hit) => `${file}:${hit.line}  ${hit.text}`))
  // 他の検査と同じ形：違反を標準出力に出し、最後の行に要約を出す。直すときは src/api/generated/constraints.ts の定数を使う
  for (const line of found) console.log(line)
  console.log(`入力の制約に数字を直接書いた所：${found.length}`)
  process.exit(found.length > 0 ? 1 : 0)
}
