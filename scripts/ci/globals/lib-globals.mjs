// TypeScript の lib（ES と DOM）が宣言するグローバルの値の名前の一覧（lib-globals.json）を、生成して確認する。
// 一覧は、フロントのテストと E2E で、組み込みの名前と同じ名前の const・let を禁じる検査（frontend-rules）が読む。手で書かない。
// - 名前は、lib の宣言のファイル（lib.*.d.ts）の最上位の declare var・let・const・function・class・namespace・enum から読む。
//   型だけの名前（interface・type）は、値の名前をシャドーイングしないので入れない。
// - 宣言のファイルは、TypeScript のネイティブの実装（7 系）では、typescript のパッケージでなく、プラットフォームごとのパッケージ
//   （@typescript/typescript-<OS>-<CPU>）の lib/ にある。typescript の getExePath が返す実行ファイルと同じディレクトリを読む。
// - 起点の lib は、店舗画面・共有の tsconfig の lib（esnext、dom、dom.iterable）に dom.asynciterable を加えた範囲にし、/// <reference lib="…" /> をたどる。
//   管理画面と E2E の lib（ES2023、DOM）は、この範囲に含まれる。
// 使い方（リポジトリのルートで。pnpm install の後）：
//   node scripts/ci/globals/lib-globals.mjs --write   一覧を書き直す（TypeScript を上げたとき）
//   node scripts/ci/globals/lib-globals.mjs           一覧が今の lib と一致するかを確認する（違えば失敗）
// scripts/ci/ の直下には置かない（check-all.sh が直下を全て実行する。これは依存を入れた job だけで流す）。
import { readFileSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { isMain } from '../lib/is-main.mjs'
import { stripComments } from '../lib/strip-comments.mjs'

/** 一覧のファイル */
export const LIST_FILE = path.join(import.meta.dirname, 'lib-globals.json')

/** typescript を読み込む基準のパッケージ（フロントの TypeScript を入れるパッケージ） */
const PACKAGE_JSON = 'frontend/shared/package.json'

/** 起点の lib（店舗画面・共有の tsconfig の lib（esnext、dom、dom.iterable）に dom.asynciterable を加えた範囲。管理画面と E2E の lib（ES2023、DOM）を含む） */
const ENTRY_LIBS = ['esnext', 'dom', 'dom.iterable', 'dom.asynciterable']

/** 最上位の値の宣言（行の頭から始まるもの。入れ子の中は字下げされているので読まない） */
const VALUE_DECLARATION = /^declare\s+(?:var|let|const|function\*?|class|namespace|enum)\s+([\w$]+)/gm

/** 別の lib の参照 */
const LIB_REFERENCE = /^\/\/\/\s*<reference\s+lib="([^"]+)"\s*\/>/gm

/**
 * 宣言のファイルの文字から、グローバルの値の名前を読む。
 *
 * @param {string} text 宣言のファイル
 * @returns {string[]} 名前（重複なし、名前の順）
 */
export function extractGlobalNames(text) {
  const code = stripComments(text)
  return [...new Set([...code.matchAll(VALUE_DECLARATION)].map((match) => match[1]))].sort()
}

/**
 * 宣言のファイルが参照する別の lib の名前を読む。
 *
 * @param {string} text 宣言のファイル
 * @returns {string[]} lib の名前（lib.<名前>.d.ts の <名前>。書いた順）
 */
export function referencedLibs(text) {
  return [...text.matchAll(LIB_REFERENCE)].map((match) => match[1].toLowerCase())
}

/**
 * 一覧と今の lib の名前の差を求める。
 *
 * @param {string[]} listed 一覧のファイルの名前
 * @param {string[]} actual 今の lib の名前
 * @returns {{ missing: string[], extra: string[] }} missing は lib にあって一覧にない名前、extra は一覧にあって lib にない名前（名前の順）
 */
export function diffNames(listed, actual) {
  const listedSet = new Set(listed)
  const actualSet = new Set(actual)
  return {
    missing: actual.filter((name) => !listedSet.has(name)).sort(),
    extra: listed.filter((name) => !actualSet.has(name)).sort(),
  }
}

/**
 * TypeScript の lib のディレクトリを返す（実行ファイルと同じディレクトリ）。
 *
 * @param {string} packageJson typescript を読み込む基準の package.json
 * @returns {Promise<string>} lib のディレクトリ
 */
async function libDirectory(packageJson) {
  const typescriptDir = path.dirname(createRequire(packageJson).resolve('typescript/package.json'))
  const { default: getExePath } = await import(pathToFileURL(path.join(typescriptDir, 'lib', 'getExePath.js')).href)
  return path.dirname(getExePath())
}

/**
 * 起点の lib から参照をたどり、グローバルの値の名前を全て読む。
 *
 * @param {string} dir lib のディレクトリ
 * @returns {string[]} 名前（重複なし、名前の順）
 */
export function readLibNames(dir) {
  const names = new Set()
  const seen = new Set()
  const pending = [...ENTRY_LIBS]
  while (pending.length > 0) {
    const lib = pending.shift()
    if (seen.has(lib)) continue
    seen.add(lib)
    const text = readFileSync(path.join(dir, `lib.${lib}.d.ts`), 'utf8')
    for (const name of extractGlobalNames(text)) names.add(name)
    pending.push(...referencedLibs(text))
  }
  return [...names].sort()
}

/**
 * 一覧の書式（1 行に 1 つの名前。差分を読みやすくする）。
 *
 * @param {string[]} names 名前
 * @returns {string} ファイルの中身
 */
function render(names) {
  return `${JSON.stringify(names, null, 2)}\n`
}

if (isMain(import.meta.url)) {
  const actual = readLibNames(await libDirectory(path.resolve(PACKAGE_JSON)))
  if (process.argv.includes('--write')) {
    writeFileSync(LIST_FILE, render(actual))
    console.log(`書き直した：${path.relative(process.cwd(), LIST_FILE)}（${actual.length} 個）`)
    process.exit(0)
  }
  const listed = JSON.parse(readFileSync(LIST_FILE, 'utf8'))
  const { missing, extra } = diffNames(listed, actual)
  for (const name of missing) console.log(`lib にあり、一覧にない名前：${name}`)
  for (const name of extra) console.log(`一覧にあり、lib にない名前：${name}`)
  const problems = missing.length + extra.length
  if (problems > 0) console.log('TypeScript の lib の名前が変わった。node scripts/ci/globals/lib-globals.mjs --write で一覧を書き直し、差分を確認する')
  console.log(`組み込みの名前の一覧の食い違い：${problems}（lib の名前 ${actual.length} 個）`)
  process.exit(problems > 0 ? 1 : 0)
}
