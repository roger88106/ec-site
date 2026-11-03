// 統合テストが、テストの補助だけが呼んでよい操作を、補助の外で呼んでいないことを確認する（バックエンドのテストの書き方の「テストデータは補助のクラスで作る」）。
// 規則は 2 種類ある。
// - イベントの発行の記録（EventPublicationRegistry）を読む操作（find で始まる操作）は、非同期のイベントの補助（AsyncEvents、EventPublications）だけが呼ぶ。
//   テストは、記録を読んで待たずに、補助の待つ操作を呼ぶ。補助を作るために記録を注入して渡すだけのテストは、違反にしない。
// - 補助の手順：HELPER_STEPS の全ての手順は、そこに挙げたクラスだけが書く。
//   手順は、在庫の作成、税区分・カテゴリの登録のリクエスト、応答から ID を取得する処理、支払の作成の操作。
//   クラスは、「補助」と「その操作そのものを確認するテスト」の 2 種類。クラス名からファイルを特定し、見つからないとき（0 件、2 件以上）は失敗にする。
// Java のコメントは読まない。構文は解析しない（宣言した変数の名前や、文字列の定数から、呼び出しを探す）。
// 税区分とカテゴリの登録のリクエストは、MockMvc の post(…) と RestClient などの .post().uri(…) の、最初の引数（文字列そのもの、同じファイルの定数）を探す。他のクラスの定数と文字列の連結は探さない（限界）。
// 規則の表は loadRules が作る（イベントの規則に、HELPER_STEPS の手順を追加する）。手順の名前、クラス、探す関数の対応は、HELPER_STEPS の 1 か所にある。
// 失敗したとき：手順を補助に移し、テストは補助の操作を呼ぶ。操作そのものを確認するテストを増やすときは、HELPER_STEPS の exceptions にクラスを追加する。
// 除外：なし。
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
 * path に ID や `/rates` を足したリクエスト（取得、税率やカテゴリの変更）は、作った後の変更のリクエストで、`HELPER_STEPS` の手順でないので探さない。
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
 * 一覧の応答の読み取り（`$.items`、`$.images[*].id`）は手順に含めない（バックエンドのテストの書き方の「テストデータは補助のクラスで作る」）ので、path が `$.id` のものだけを探す。
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
 * 補助の手順。
 * - step：手順の名前（違反の報告に添える）
 * - helpers：この手順を書いてよい補助のクラス名
 * - exceptions：その操作そのものを確認するので、書いてよいテストのクラス名
 * - find：コメントを除いたコードから、手順の位置を返す関数
 *
 * @typedef {{ step: string, helpers: string[], exceptions: string[], find: (code: string) => number[] }} HelperStep
 */

/**
 * 手順と、手順を書いてよい補助とテストの対応（バックエンドのテストの書き方の「テストデータは補助のクラスで作る」の規則の正本）。<br />
 * 手順を増やすときは、探す関数と一緒にここに追加する。
 *
 * @type {HelperStep[]}
 */
export const HELPER_STEPS = [
  { step: '在庫の作成', helpers: ['InventoryTestData'], exceptions: ['JooqStockItemRepositoryTest'], find: findStockCreations },
  {
    step: '税区分の登録の要求',
    helpers: ['CatalogTestData'],
    exceptions: ['TaxCategoryApiTest', 'AuthorizationTest', 'KeycloakIntegrationTest'],
    find: (code) => findPostsTo(code, '/api/admin/tax-categories'),
  },
  {
    step: '作成の応答から ID を取り出す処理',
    helpers: ['TestJson', 'CatalogTestData'],
    // PlaceOrderConcurrencyTest は、確認する API の作成の応答から ID を取り出す
    exceptions: ['TaxCategoryApiTest', 'CategoryApiTest', 'ProductApiTest', 'PlaceOrderConcurrencyTest'],
    find: findIdExtractions,
  },
  { step: 'カテゴリの登録の要求', helpers: ['CatalogTestData'], exceptions: ['CategoryApiTest'], find: (code) => findPostsTo(code, '/api/admin/categories') },
  { step: '支払の API の作成の操作', helpers: ['PaymentTestData'], exceptions: ['PaymentApiTest'], find: findPaymentCreations },
]

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
 * 手順の一覧から、全ての手順の規則を作る。
 *
 * @param {HelperStep[]} steps 手順の一覧
 * @param {string[]} files リポジトリのファイルの一覧（クラスのファイルを特定する）
 * @returns {HelperRule[]} 規則（手順の順）
 * @throws {Error} クラスのファイルが見つからないとき、2 件以上あるとき
 */
export function buildStepRules(steps, files) {
  return steps.map(({ step, helpers, exceptions, find }) => ({
    name: step,
    helpers: helpers.map((className) => fileOfClass(className, files)),
    exceptions: exceptions.map((className) => fileOfClass(className, files)),
    find,
  }))
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
 * 検査する規則の表（イベントの発行の記録の規則と、HELPER_STEPS の全ての手順）を返す。<br />
 * ファイルの一覧を読むので、読み込みの時でなく、検査の始めに呼ぶ（対象のファイルが 0 件のときの失敗の文を先に出すため）。
 *
 * @returns {HelperRule[]} 規則
 * @throws {Error} 手順のクラスのファイルが見つからないとき
 */
export function loadRules() {
  cachedRules ??= [EVENT_PUBLICATION_READS, ...buildStepRules(HELPER_STEPS, listGitFiles())]
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
