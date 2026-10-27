// 統合テストが、テストの補助だけが呼んでよい操作を、補助の外で呼んでいないことを確認する（テストの書き方 第 3.2 節）。
// 今の規則：イベントの発行の記録（EventPublicationRegistry）を読む操作（find で始まる操作）は、非同期のイベントの補助（AsyncEvents、EventPublications）だけが呼ぶ。
// テストは、記録を読んで待たずに、補助の待つ操作を呼ぶ。補助を作るために記録を注入して渡すだけのテストは、違反にしない。
// Java のコメントは読まない。構文は解析しない（宣言した変数の名前から、呼び出しを探す）。
// 規則は RULES の表に追加する（名前、補助のファイル、呼び出しを探す関数）。
// 使い方：node scripts/ci/test-helpers.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'
import { createLineLocator } from './lib/line-number.mjs'
import { stripJavaComments } from './lib/strip-comments.mjs'

/** 検査する統合テストの Java のファイル（テストの補助も含む） */
export const TARGET_FILE = /^backend\/ecsite\/src\/integrationTest\/java\/com\/ecsite\/.*\.java$/

/** イベントの発行の記録を読んでよい、非同期のイベントの補助 */
export const ASYNC_EVENT_HELPERS = [
  'backend/ecsite/src/integrationTest/java/com/ecsite/AsyncEvents.java',
  'backend/ecsite/src/integrationTest/java/com/ecsite/config/event/EventPublications.java',
]

/** 記録の型の名前 */
const REGISTRY_TYPE = 'EventPublicationRegistry'

/**
 * コメントを除いたコードから、イベントの発行の記録を読む操作（`find` で始まる操作）の呼び出しの位置を探す。<br />
 * 記録の型で宣言した変数（フィールド、引数、`getBean` で得た変数）の名前を求め、`名前.find…(` と `名前::find…` を探す。
 * `getBean(EventPublicationRegistry.class).find…(` も探す。
 *
 * @param {string} code コメントを除いた Java のコード
 * @returns {number[]} 呼び出しの位置
 */
function findRegistryReads(code) {
  const names = new Set()
  for (const match of code.matchAll(new RegExp(`${REGISTRY_TYPE}\\s+(\\w+)`, 'g'))) names.add(match[1])
  for (const match of code.matchAll(new RegExp(`(\\w+)\\s*=\\s*[^;]*${REGISTRY_TYPE}\\.class`, 'g'))) names.add(match[1])
  const positions = []
  for (const name of names) {
    for (const match of code.matchAll(new RegExp(`(?<!\\w)${name}\\s*(?:\\.|::)\\s*find\\w*`, 'g'))) positions.push(match.index)
  }
  for (const match of code.matchAll(new RegExp(`${REGISTRY_TYPE}\\.class\\s*\\)\\s*\\.\\s*find\\w*`, 'g'))) positions.push(match.index)
  return positions
}

/**
 * 規則。
 * - name：違反の報告に添える規則の名前
 * - helpers：この規則の操作を呼んでよい補助のファイル（リポジトリのルートからのパス）
 * - find：コメントを除いたコードから、禁じる呼び出しの位置を返す関数
 *
 * @typedef {{ name: string, helpers: string[], find: (code: string) => number[] }} HelperRule
 */

/** 統合テストが、イベントの発行の記録を直接読んで待たない */
export const EVENT_PUBLICATION_READS = {
  name: 'イベントの発行の記録を読む操作',
  helpers: ASYNC_EVENT_HELPERS,
  find: findRegistryReads,
}

/** 検査する規則の表 */
export const RULES = [EVENT_PUBLICATION_READS]

/**
 * ファイルが、規則の補助かを返す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @param {HelperRule} rule 規則
 * @returns {boolean} 補助なら true
 */
export function isHelperFile(file, rule) {
  return rule.helpers.includes(file)
}

/**
 * 補助の外で、禁じる操作を呼ぶ行を探す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @param {string} source Java のソース
 * @param {HelperRule[]} [rules] 規則の表
 * @returns {{ line: number, rule: string, text: string }[]} 違反（行の順）。text は元の行の文字（前後の空白を除く）
 */
export function findHelperViolations(file, source, rules = RULES) {
  const lines = source.split('\n')
  const hits = []
  const applicable = rules.filter((rule) => !isHelperFile(file, rule))
  if (applicable.length === 0) return hits
  const code = stripJavaComments(source)
  const lineOf = createLineLocator(code)
  for (const rule of applicable) {
    for (const position of new Set(rule.find(code))) {
      const line = lineOf(position)
      hits.push({ line, rule: rule.name, text: (lines[line - 1] ?? '').trim() })
    }
  }
  return hits.sort((a, b) => a.line - b.line)
}

if (isMain(import.meta.url)) {
  const files = listGitFiles().filter((file) => TARGET_FILE.test(file))
  exitIfNoFiles(files, '統合テストのファイル')
  let count = 0
  for (const file of files) {
    for (const hit of findHelperViolations(file, readFileSync(file, 'utf8'))) {
      console.log(`${file}:${hit.line} ${hit.rule}（${hit.text}）`)
      count += 1
    }
  }
  console.log(`補助の外の呼び出し：${count}`)
  process.exit(count ? 1 : 0)
}
