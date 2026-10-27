// 統合テストが、テストの補助だけが呼んでよい操作を、補助の外で呼んでいないことを確認する（テストの書き方 第 3.2 節）。
// 規則は 2 種類ある。
// - イベントの発行の記録（EventPublicationRegistry）を読む操作（find で始まる操作）は、非同期のイベントの補助（AsyncEvents、EventPublications）だけが呼ぶ。
//   テストは、記録を読んで待たずに、補助の待つ操作を呼ぶ。補助を作るために記録を注入して渡すだけのテストは、違反にしない。
// - テストの書き方 第 3.2 節の 2 つ目の表の全ての行（在庫の作成、税区分・カテゴリの登録の要求、応答から ID を取り出す処理、支払の作成の操作）は、
//   表の「補助」と「その操作そのものを確認するテスト」の列のクラスだけが書く。この 2 つの列は、実行時に文書から読む（コードに一覧を重ねて書かない）。
//   表の書式が読めないとき（表がない、列が違う、知らない手順の行がある、手順の行が欠けている、クラスのファイルが見つからない）は失敗にする。
// Java のコメントは読まない。構文は解析しない（宣言した変数の名前や、文字列の定数から、呼び出しを探す）。
// 規則の表は loadRules が作る（イベントの規則に、文書の表の行を足す）。表の手順を探す関数は STEP_FINDERS の表に追加する。
// 使い方：node scripts/ci/test-helpers.mjs（リポジトリのルートで）
import { readFileSync } from 'node:fs'
import { exitIfNoFiles, listGitFiles } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'
import { createLineLocator } from './lib/line-number.mjs'
import { stripJavaComments } from './lib/strip-comments.mjs'

/** 検査する統合テストの Java のファイル（テストの補助も含む） */
export const TARGET_FILE = /^backend\/ecsite\/src\/integrationTest\/java\/com\/ecsite\/.*\.java$/

/** 補助と、操作そのもののテストの表を持つ文書（リポジトリのルートからのパス） */
export const TEST_WRITING_DOC = 'docs/rules/test-writing.md'

/** イベントの発行の記録を読んでよい、非同期のイベントの補助 */
export const ASYNC_EVENT_HELPERS = [
  'backend/ecsite/src/integrationTest/java/com/ecsite/AsyncEvents.java',
  'backend/ecsite/src/integrationTest/java/com/ecsite/config/event/EventPublications.java',
]

/** 記録の型の名前 */
const REGISTRY_TYPE = 'EventPublicationRegistry'

/**
 * コメントを除いたコードから、指定した型の変数の、指定した名前の操作の呼び出しの位置を探す。<br />
 * 型で宣言した変数（フィールド、引数、`getBean` で得た変数）の名前を求め、`名前.操作(` と `名前::操作` を探す。
 * `getBean(型.class).操作(` も探す。
 *
 * @param {string} code コメントを除いた Java のコード
 * @param {string} type 型の名前
 * @param {string} method 操作の名前の正規表現（例：`find\\w*`、`create\\b`）
 * @returns {number[]} 呼び出しの位置
 */
function findCallsOnType(code, type, method) {
  const names = new Set()
  for (const match of code.matchAll(new RegExp(`(?<!\\w)${type}\\s+(\\w+)`, 'g'))) names.add(match[1])
  for (const match of code.matchAll(new RegExp(`(\\w+)\\s*=\\s*[^;]*${type}\\.class`, 'g'))) names.add(match[1])
  const positions = []
  for (const name of names) {
    for (const match of code.matchAll(new RegExp(`(?<!\\w)${name}\\s*(?:\\.|::)\\s*${method}`, 'g'))) positions.push(match.index)
  }
  for (const match of code.matchAll(new RegExp(`${type}\\.class\\s*\\)\\s*\\.\\s*${method}`, 'g'))) positions.push(match.index)
  return positions
}

/**
 * イベントの発行の記録を読む操作（`find` で始まる操作）の呼び出しの位置を探す。
 *
 * @param {string} code コメントを除いた Java のコード
 * @returns {number[]} 呼び出しの位置
 */
function findRegistryReads(code) {
  return findCallsOnType(code, REGISTRY_TYPE, 'find\\w*')
}

/**
 * 在庫の作成を探す。<br />
 * 在庫は Repository の `createIfAbsent` で作る（InventoryTestData がそうしている）ので、その呼び出しを探す。
 * Repository を通さず、jOOQ の `insertInto(STOCK_ITEM)` や SQL の `insert into inventory.stock_item` で行を入れる書き方も、同じ作成なので探す。
 * 在庫の読み取りと削除（`selectFrom`、`deleteFrom`、`fetchCount`）は作成でないので探さない。
 *
 * @param {string} code コメントを除いた Java のコード
 * @returns {number[]} 呼び出しの位置
 */
function findStockCreations(code) {
  const patterns = [/\.\s*createIfAbsent\s*\(/g, /insertInto\s*\(\s*(?:\w+\s*\.\s*)*STOCK_ITEM\b/g, /insert\s+into\s+inventory\.stock_item\b/gi]
  return patterns.flatMap((pattern) => [...code.matchAll(pattern)].map((match) => match.index))
}

/**
 * 指定した path を値に持つ文字列の定数の名前を求める（`String BASE = "/api/admin/…";`）。
 *
 * @param {string} code コメントを除いた Java のコード
 * @param {string} path API の path
 * @returns {string[]} 定数の名前
 */
function constantsOf(code, path) {
  return [...code.matchAll(new RegExp(`(\\w+)\\s*=\\s*"${path}"\\s*;`, 'g'))].map((match) => match[1])
}

/**
 * 作成の要求（指定した path そのものへの POST）を探す。<br />
 * 登録の API（税区分、カテゴリ）は、一覧と同じ path への POST で作る。補助 CatalogTestData は `post("/api/admin/…")` と書く。
 * テストは path を定数（`BASE` など）に置くことが多いので、path の文字列そのものと、その path を値に持つ定数を、`post(` の最初の引数として探す。
 * RestClient・WebTestClient の `.post().uri("…")` と `.post().uri(定数)` も、同じ規則で探す（統合テストの今の要求の書き方は、MockMvc の `post(…)` と RestClient の `.post().uri(…)`）。
 * 限界：他のクラスの定数（`Other.BASE`）、文字列の連結（`BASE + "/x"`）、変数に入れた path は探さない（同じファイルの定数と文字列そのものだけ）。
 * path に ID や `/rates` を足した要求（取得、税率やカテゴリの変更）は、作った後の変更の要求で、表の手順でないので探さない。
 *
 * @param {string} code コメントを除いた Java のコード
 * @param {string} path API の path
 * @returns {number[]} `post(` の位置
 */
function findPostsTo(code, path) {
  const targets = [`"${path}"`, ...constantsOf(code, path)]
  const alternatives = targets.map((target) => target.replace(/[.*+?^${}()|[\]\\]/g, (char) => `\\${char}`)).join('|')
  const mockMvc = [...code.matchAll(new RegExp(`(?<!\\w)post\\s*\\(\\s*(?:${alternatives})\\s*[),]`, 'g'))].map((match) => match.index)
  // RestClient・WebTestClient の `.post().uri(…)`。`.post()` から次の `;` までの間の `.uri(` の最初の引数を見る
  const uri = [...code.matchAll(new RegExp(`\\.\\s*post\\s*\\(\\s*\\)[^;]*?\\.\\s*uri\\s*\\(\\s*(?:${alternatives})\\s*[),]`, 'g'))].map((match) => match.index)
  return [...mockMvc, ...uri]
}

/**
 * 作成の応答の JSON から ID を取り出す処理を探す。<br />
 * 補助 TestJson は `JsonPath.read(body, "$.id")` と読む。テストが同じ読み取りを書くのは、`JsonPath.read(…, "$.id")` と、
 * 解析した文書の `.read("$.id")` の 2 つ。型引数（`JsonPath.<String>read(…)`）と、path の後の引数（`, String.class`）があっても探す。`jsonPath("$.id")`（MockMvc の応答の検証）は取り出す処理でなく確認なので探さない。
 * 一覧の応答の読み取り（`$.items`、`$.images[*].id`）は表の手順に含めない（テストの書き方）ので、path が `$.id` のものだけを探す。
 *
 * @param {string} code コメントを除いた Java のコード
 * @returns {number[]} 呼び出しの位置
 */
function findIdExtractions(code) {
  const patterns = [/JsonPath\s*\.\s*(?:<[^>;]*>\s*)?read\s*\([^;]*?,\s*"\$\.id"\s*[),]/g, /\.\s*(?:<[^>;]*>\s*)?read\s*\(\s*"\$\.id"\s*[),]/g]
  return patterns.flatMap((pattern) => [...code.matchAll(pattern)].map((match) => match.index))
}

/**
 * 支払の API の作成の操作を探す。<br />
 * 支払は、支払の API（PaymentApi）の `create` で作る（PaymentTestData がそうしている）ので、その型の変数の `create` を探す。
 * 集約を直接作る `Payment.create`（Repository のテスト）は、PaymentApi の変数でないので探さない。
 *
 * @param {string} code コメントを除いた Java のコード
 * @returns {number[]} 呼び出しの位置
 */
function findPaymentCreations(code) {
  return findCallsOnType(code, 'PaymentApi', 'create\\b')
}

/**
 * テストの書き方の表の手順の名前（表の「手順」の列の文字）→ その手順を探す関数。<br />
 * 表に行を足すときは、ここにも足す（足さないと、知らない手順として失敗する）。
 *
 * @type {Record<string, (code: string) => number[]>}
 */
export const STEP_FINDERS = {
  在庫の作成: findStockCreations,
  税区分の登録の要求: (code) => findPostsTo(code, '/api/admin/tax-categories'),
  '作成の応答から ID を取り出す処理': findIdExtractions,
  カテゴリの登録の要求: (code) => findPostsTo(code, '/api/admin/categories'),
  '支払の API の作成の操作': findPaymentCreations,
}

/** 表の見出しの行（手順、補助、その操作そのものを確認するテスト） */
const TABLE_HEADER = /^\|\s*手順\s*\|\s*補助\s*\|\s*その操作そのものを確認するテスト\s*\|\s*$/

/**
 * 表のセルから、バッククォートで囲んだクラス名を読む。括弧の注記など、バッククォートの外の文字は読まない。
 *
 * @param {string} cell セルの文字
 * @param {string} where 失敗の文に添える場所
 * @returns {string[]} クラス名
 */
function classNamesOf(cell, where) {
  return [...cell.matchAll(/`([^`]*)`/g)].map((match) => {
    if (!/^[A-Z]\w*$/.test(match[1])) throw new Error(`${where}：クラス名の形が違う（${match[1]}）`)
    return match[1]
  })
}

/**
 * テストの書き方の文書から、補助の表を読む。
 *
 * @param {string} markdown 文書の本文
 * @returns {{ step: string, helpers: string[], exceptions: string[] }[]} 行（手順、補助のクラス名、操作そのものを確認するテストのクラス名）
 * @throws {Error} 表がない、列が違う、行の形が違う、補助がないとき
 */
export function parseHelperTable(markdown) {
  const lines = markdown.split(/\r?\n/)
  const start = lines.findIndex((line) => TABLE_HEADER.test(line))
  if (start < 0 || !/^\|[\s|:-]+\|\s*$/.test(lines[start + 1] ?? '')) {
    throw new Error(`${TEST_WRITING_DOC} の表の形が違う（見出し「| 手順 | 補助 | その操作そのものを確認するテスト |」の表がない、または列が違う）`)
  }
  const rows = []
  for (const line of lines.slice(start + 2)) {
    if (!line.startsWith('|')) break
    const cells = line.replace(/^\|/, '').replace(/\|\s*$/, '').split('|').map((cell) => cell.trim())
    const step = cells[0]
    if (cells.length !== 3 || !step) throw new Error(`${TEST_WRITING_DOC} の表の行の形が違う（列が 3 つでない：${line}）`)
    const helpers = classNamesOf(cells[1], `手順「${step}」の補助`)
    if (helpers.length === 0) throw new Error(`手順「${step}」の補助の列に、バッククォートで囲んだクラス名がない`)
    rows.push({ step, helpers, exceptions: classNamesOf(cells[2], `手順「${step}」の除外`) })
  }
  if (rows.length === 0) throw new Error(`${TEST_WRITING_DOC} の表に行がない`)
  return rows
}

/**
 * クラス名から、統合テストのファイルを特定する。
 *
 * @param {string} className クラス名
 * @param {string[]} files リポジトリのファイルの一覧
 * @returns {string} リポジトリのルートからのパス
 * @throws {Error} ファイルが 0 件か 2 件以上のとき
 */
function fileOfClass(className, files) {
  const found = files.filter((file) => TARGET_FILE.test(file) && file.endsWith(`/${className}.java`))
  if (found.length !== 1) throw new Error(`クラス ${className} の統合テストのファイルが${found.length === 0 ? '見つからない' : `${found.length} 件ある（${found.join('、')}）`}`)
  return found[0]
}

/**
 * 規則。
 * - name：違反の報告に添える規則の名前
 * - helpers：この規則の操作を呼んでよい補助のファイル（リポジトリのルートからのパス）
 * - exceptions：その操作そのものを確認するので、呼んでよいテストのファイル（なければ省く）
 * - find：コメントを除いたコードから、禁じる呼び出しの位置を返す関数
 *
 * @typedef {{ name: string, helpers: string[], exceptions?: string[], find: (code: string) => number[] }} HelperRule
 */

/**
 * テストの書き方の表から、表の全ての行の規則を作る。
 *
 * @param {string} markdown テストの書き方の本文
 * @param {string[]} files リポジトリのファイルの一覧（クラスのファイルを特定する）
 * @returns {HelperRule[]} 規則（表の行の順）
 * @throws {Error} 表が読めない、知らない手順の行がある、手順の行が欠けている、クラスのファイルが見つからないとき
 */
export function buildTableRules(markdown, files) {
  const rows = parseHelperTable(markdown)
  const rules = rows.map(({ step, helpers, exceptions }) => {
    if (!Object.hasOwn(STEP_FINDERS, step)) throw new Error(`表に知らない手順の行がある（${step}）。STEP_FINDERS に探す関数を足す`)
    return {
      name: step,
      helpers: helpers.map((className) => fileOfClass(className, files)),
      exceptions: exceptions.map((className) => fileOfClass(className, files)),
      find: STEP_FINDERS[step],
    }
  })
  const missing = Object.keys(STEP_FINDERS).filter((step) => !rows.some((row) => row.step === step))
  if (missing.length > 0) throw new Error(`表に手順の行がない（${missing.join('、')}）`)
  return rules
}

/** 統合テストが、イベントの発行の記録を直接読んで待たない */
export const EVENT_PUBLICATION_READS = {
  name: 'イベントの発行の記録を読む操作',
  helpers: ASYNC_EVENT_HELPERS,
  find: findRegistryReads,
}

/** 作った規則の表（loadRules が持つ） */
let cachedRules = null

/**
 * 検査する規則の表（イベントの発行の記録の規則と、テストの書き方の表の全ての行）を返す。<br />
 * 文書とファイルの一覧を読むので、読み込みの時でなく、検査の始めに呼ぶ（対象のファイルが 0 件のときの失敗の文を先に出すため）。
 *
 * @returns {HelperRule[]} 規則
 * @throws {Error} テストの書き方の表が読めないとき
 */
export function loadRules() {
  cachedRules ??= [EVENT_PUBLICATION_READS, ...buildTableRules(readFileSync(TEST_WRITING_DOC, 'utf8'), listGitFiles())]
  return cachedRules
}

/**
 * ファイルが、規則の補助か、その操作そのものを確認するテストかを返す（どちらも、規則の操作を書いてよい）。
 *
 * @param {string} file リポジトリのルートからのパス
 * @param {HelperRule} rule 規則
 * @returns {boolean} 補助か除外のテストなら true
 */
export function isHelperFile(file, rule) {
  return rule.helpers.includes(file) || (rule.exceptions ?? []).includes(file)
}

/**
 * 補助の外で、禁じる操作を呼ぶ行を探す。
 *
 * @param {string} file リポジトリのルートからのパス
 * @param {string} source Java のソース
 * @param {HelperRule[]} [rules] 規則の表（なければ loadRules の規則）
 * @returns {{ line: number, rule: string, text: string }[]} 違反（行の順）。text は元の行の文字（前後の空白を除く）
 */
export function findHelperViolations(file, source, rules = loadRules()) {
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
