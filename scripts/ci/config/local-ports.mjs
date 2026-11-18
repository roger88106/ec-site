// ローカルのポートの一覧（scripts/shared/slot/ports.json）と、一覧を読めない所のポートが食い違わないことを確認する。
// 読めない所は、compose のホスト側のポート（COMPOSE_FILE）、文書（DOC_FILES）、E2E の設定（SOURCE_FILES）、
// ポートを直接書く設定と script（CONFIG_FILES）、Keycloak の realm の redirect URI（REALM_FILES）。一覧を読むスロットの script は対象にしない。
//
// ポートは、`:ポート`、`--port ポート`、`${変数:-ポート}` の形で読む。`${変数:-ポート}` は、変数の名前が `PORT` か `_PORT` で終わり（大文字小文字を区別する）、
// 既定値が 4〜5 桁の数字のときだけ読む（`:=`・`-`・`=` の既定値の書き方と、引用符で囲んだ既定値も読む。時間や行数の既定値と、`REPORT_ROWS` のように名前の途中に PORT を含む変数は読まない）。
//
// 一覧にないポート、書かれていない一覧のポート、一覧の中で重なるポートを失敗にする。
// E2E の設定は、E2E のポートと、E2E が使う共有のサービスのポートだけを許す（開発の画面のポートに替えると失敗にする）。
// realm には、一覧の画面のポートが、スロットごとの分と E2E を含めて全てあることを確認する。
// compose の待ち受けるアドレスは compose-ports が確認する。テストのコードの中のポートは見ない（URL の例で、一覧と同じである必要がない）。
//
// 失敗したとき：一覧と、食い違う所のどちらかを修正する。ポートを加えるときは、一覧と、上の読めない所を同時に修正する。
//
// 除外：なし
//
// 使い方：node scripts/ci/config/local-ports.mjs（リポジトリのルートで）
import { existsSync, readFileSync } from 'node:fs'
import { exitIfNoFiles } from '../../shared/git/git-files.mjs'
import { describeUnreadableHostPort, findPortProblems, parseHostPort } from './compose-ports.mjs'

/** ポートの一覧 */
const LIST_FILE = 'scripts/shared/slot/ports.json'

/** compose のファイル */
const COMPOSE_FILE = 'docker/compose.yaml'

/** ポートを書く文書 */
export const DOC_FILES = ['docs/06-手順書/setup.md', 'docs/06-手順書/running-tests.md']

/** E2E のポートを書く設定（E2E のサーバーの URL、画面のサーバーの `--port`、Playwright の `port:`。読み方は CONFIG_FILES と同じ readConfigPorts） */
export const SOURCE_FILES = ['e2e/src/urls.ts', 'e2e/playwright.config.ts']

/** E2E の設定が書いてよい共有のサービスの key（E2E 用の IdP、メールを確認する Mailpit の画面、店舗画面の CSP が許す画像の配信元） */
const E2E_SHARED_KEYS = ['keycloak-e2e', 'mailpit-ui', 'seaweedfs']

/** ポートを直接書く設定とスクリプト（一覧にあるポートだけが書かれていることを確認する） */
export const CONFIG_FILES = [
  'backend/ecsite/src/main/resources/application.yml',
  'backend/ecsite/src/main/resources/application-local.yml',
  'backend/ecsite/src/main/resources/application-e2e.yml',
  'backend/ecsite/src/main/resources/db/seed/local/R__payment_seed.sql',
  '.claude/launch.json',
  '.github/workflows/ci.yml',
  'frontend/store/src/auth/config.ts',
  'frontend/admin/src/auth/oidc.ts',
  'frontend/admin/vite.config.ts',
  'e2e/src/showcase-urls.ts',
  'docker/seaweedfs/init.sh',
  'scripts/dev/check-frontend.sh',
  'scripts/dev/keycloak.sh',
  'scripts/dev/reset.sh',
]

/** Keycloak の realm の redirect URI。値は、そのクライアントの画面の、一覧の key（スロットごとの分と E2E を含む） */
const REALM_FILES = {
  'docker/keycloak/realms/ec-store-realm.json': ['store', 'store-e2e'],
  'docker/keycloak/realms/ec-admin-realm.json': ['admin', 'admin-e2e'],
}

/**
 * 一覧が使うポートを、スロットごとにずらした分も含めて返す。
 *
 * @param {{ slotCount: number, slotOffset: number, ports: { port: number, slotted: boolean }[] }} list ポートの一覧
 * @returns {Set<number>} 全てのポート
 */
export function expandedPorts(list) {
  return new Set(list.ports.flatMap((entry) => slotPorts(entry, list)))
}

/**
 * 一覧の 1 つの項目のポートを、スロットごとにずらした分も含めて返す（スロットのない項目は 1 つ）。
 *
 * @param {{ port: number, slotted: boolean }} entry 一覧の項目
 * @param {{ slotCount: number, slotOffset: number }} list ポートの一覧
 * @returns {number[]} スロットの順のポート
 */
function slotPorts(entry, list) {
  const slots = entry.slotted ? list.slotCount : 1
  return Array.from({ length: slots }, (_, slot) => entry.port + list.slotOffset * slot)
}

/**
 * compose の `ports` の項目から、ホスト側のポートを読む（読み方は compose-ports の readHostPort）。
 *
 * `${LOCAL_BIND_ADDRESS:-127.0.0.1}:` の後ろが、数字か `${変数:-数字}` の書き方のもの。
 * それ以外の書き方の項目（既定値のない変数、範囲など）は、読み飛ばすと一覧にないポートを見逃すので、`unreadable` に入れる。
 *
 * @param {string} text compose のファイル
 * @returns {{ ports: number[], unreadable: { service: string, entry: string }[] }} ホスト側のポートと、読めない項目（書いた順）
 */
export function parseComposeHostPorts(text) {
  const ports = []
  const unreadable = []
  for (const port of findPortProblems(text).ports) {
    const host = parseHostPort(port.entry)
    if (host === null) {
      unreadable.push(port)
    } else {
      ports.push(host)
    }
  }
  return { ports, unreadable }
}

/**
 * 文書や設定のポートの書き方。
 * `:ポート`・`--port ポート`（1 番目の組）と、`${変数:-ポート}` の形（2 番目の組）。
 * 後者は、変数の名前が `PORT` か `_PORT` で終わる（大文字小文字を区別する）、既定値が 4〜5 桁の数字のときだけ読む
 * （時間や行数の既定値、`REPORT_ROWS` のように名前の途中に PORT を含むものを読まないため）。
 * 既定値の書き方は `:-`・`:=`・`-`・`=`、引用符で囲んだ既定値も読む。
 * `--port` は大文字小文字を区別しない。
 */
const DOC_PORT = /(?::|--[Pp][Oo][Rr][Tt]\s+)(\d{4,5})(?!\d)|\$\{(?:\w*_)?PORT:?[-=]\s*["']?(\d{4,5})(?!\d)/g

/**
 * 文書や設定の中の、`:ポート` の書き方（`localhost:8080`、`:8090`）、`--port ポート`、`${変数:-ポート}` から、ポートを読む。
 *
 * 数字だけの書き方（カード番号など）は、ポートか分からないので読まない。
 *
 * @param {string} text 文書や設定
 * @returns {number[]} ポート（出てくる順）
 */
export function parseDocPorts(text) {
  return [...text.matchAll(DOC_PORT)].map((match) => Number(match[1] ?? match[2]))
}

/**
 * 設定とスクリプトのポートを読む。
 *
 * `:ポート`（URL）、`${変数:-ポート}` と `--port ポート` に加え、
 * yml の `port: ポート`・`port: ${変数:ポート}`、JSON の `"port": ポート`、起動の引数の `"--port", "ポート"` を読む。
 *
 * @param {string} text 設定やスクリプト
 * @returns {number[]} ポート（種類ごとに出てくる順。重複は除く）
 */
export function parseConfigPorts(text) {
  const found = [
    ...parseDocPorts(text),
    ...[...text.matchAll(/^\s*port:\s*(?:\$\{\w+:)?(\d{4,5})(?!\d)/gm)].map((match) => Number(match[1])),
    ...[...text.matchAll(/"port"\s*:\s*(\d{4,5})(?!\d)/g)].map((match) => Number(match[1])),
    ...[...text.matchAll(/"--port"\s*,\s*"(\d{4,5})"/g)].map((match) => Number(match[1])),
  ]
  return [...new Set(found)].sort((a, b) => a - b)
}

/**
 * 文書のスロットの表（`| 番号 | ポート… | …` の行）を読む。
 *
 * @param {string} text 文書
 * @returns {{ slot: number, ports: number[] }[]} 行ごとの、スロットの番号と、続く数字だけのセル
 */
function parseSlotRows(text) {
  const rows = []
  for (const line of text.split('\n')) {
    const cells = line.split('|').slice(1, -1).map((cell) => cell.replace(/（.*?）/g, '').trim())
    if (cells.length < 2 || !/^\d$/.test(cells[0])) {
      continue
    }
    const ports = []
    for (const cell of cells.slice(1)) {
      if (!/^\d{2,5}$/.test(cell)) {
        break
      }
      ports.push(Number(cell))
    }
    if (ports.length > 0) {
      rows.push({ slot: Number(cells[0]), ports })
    }
  }
  return rows
}

/**
 * 2 つの集合の差を、「あり・なし」の問題の文にする。
 *
 * @param {number[]} actual 読んだポート
 * @param {Set<number>} expected 一覧から決まるポート
 * @param {string} where 読んだ場所
 * @param {string} place 「ない」ときの言い方の場所
 * @returns {string[]} 問題
 */
function diffProblems(actual, expected, where, place) {
  const problems = []
  const actualSet = new Set(actual)
  for (const port of [...actualSet].sort((a, b) => a - b)) {
    if (!expected.has(port)) {
      problems.push(`${where} に一覧にないポート ${port} がある（${LIST_FILE} に加えるか、${where} を修正する）`)
    }
  }
  for (const port of [...expected].sort((a, b) => a - b)) {
    if (!actualSet.has(port)) {
      problems.push(`一覧のポート ${port} が ${place} にない（${where} に加える）`)
    }
  }
  return problems
}

/**
 * 一覧と、一覧を読めない所のポートの食い違いを探す。
 *
 * @param {{ list: { slotCount: number, slotOffset: number, ports: { key: string, port: number, group: string, slotted: boolean }[] },
 *   compose: string, docs: Record<string, string>, sources: Record<string, string>, configs?: Record<string, string>,
 *   realms?: Record<string, { text: string, keys: string[] }> }} input
 *   docs・sources・configs は、ファイルのパス → 内容。realms は、ファイルのパス → { text: 内容, keys: redirect URI に全て書く画面の一覧の key }
 * @returns {string[]} 問題の文
 */
export function findPortMismatches({ list, compose, docs, sources, configs = {}, realms = {} }) {
  const problems = []

  const used = new Map()
  for (const entry of list.ports) {
    for (const port of slotPorts(entry, list)) {
      if (used.has(port)) {
        problems.push(`${LIST_FILE} でポート ${port} が重なる（${used.get(port)} と ${entry.key}）`)
      }
      used.set(port, entry.key)
    }
  }

  const shared = new Set(list.ports.filter((entry) => entry.group === 'shared').map((entry) => entry.port))
  const composePorts = parseComposeHostPorts(compose)
  for (const port of composePorts.unreadable) {
    problems.push(describeUnreadableHostPort(COMPOSE_FILE, port))
  }
  problems.push(...diffProblems(composePorts.ports, shared, COMPOSE_FILE, 'compose'))

  const all = expandedPorts(list)
  const docsText = Object.values(docs).join('\n')
  for (const [file, text] of Object.entries(docs)) {
    for (const port of new Set(parseDocPorts(text))) {
      if (!all.has(port)) {
        problems.push(`${file} に一覧にないポート ${port} がある（${LIST_FILE} に加えるか、文書を修正する）`)
      }
    }
  }
  for (const entry of list.ports) {
    if (!new RegExp(`(?<!\\d)${entry.port}(?!\\d)`).test(docsText)) {
      problems.push(`一覧のポート ${entry.port} が文書（${Object.keys(docs).join('、')}）のどこにも書かれていない`)
    }
  }

  const slotted = list.ports.filter((entry) => entry.slotted)
  const rows = Object.entries(docs).flatMap(([file, text]) => parseSlotRows(text).map((row) => ({ ...row, file })))
  if (rows.length === 0) {
    problems.push('文書にスロットのポートの表が見つからない')
  }
  for (let slot = 0; slot < list.slotCount; slot++) {
    if (!rows.some((row) => row.slot === slot)) {
      problems.push(`文書のスロットのポートの表に、スロット ${slot} の行がない`)
    }
  }
  for (const row of rows) {
    const expected = slotted.map((entry) => entry.port + list.slotOffset * row.slot)
    if (row.slot >= list.slotCount || row.ports.join() !== expected.join()) {
      problems.push(`${row.file} のスロット ${row.slot} の行のポート（${row.ports.join('、')}）が一覧から決まる値（${expected.join('、')}）と違う`)
    }
  }

  const e2e = new Set(list.ports.filter((entry) => entry.group === 'e2e').map((entry) => entry.port))
  // E2E の設定が書いてよいのは、E2E のポートと、E2E が使う共有のサービス（E2E_SHARED_KEYS）だけ。開発の画面のポートなどに替えても、一覧にあるだけで通さない
  const allowed = new Set([...e2e, ...list.ports.filter((entry) => E2E_SHARED_KEYS.includes(entry.key)).map((entry) => entry.port)])
  const sourcePorts = new Set()
  for (const [file, text] of Object.entries(sources)) {
    for (const port of parseConfigPorts(text)) {
      sourcePorts.add(port)
      if (!all.has(port)) {
        problems.push(`${file} に一覧にないポート ${port} がある（${LIST_FILE} に加えるか、設定を修正する）`)
      } else if (!allowed.has(port)) {
        problems.push(`${file} のポート ${port} は、一覧の E2E のポート（group が e2e）でも E2E が使う共有のサービスでもない（E2E の設定を修正する）`)
      }
    }
  }
  for (const port of [...e2e].sort((a, b) => a - b)) {
    if (!sourcePorts.has(port)) {
      problems.push(`一覧の E2E のポート ${port} が E2E の設定（${Object.keys(sources).join('、')}）にない`)
    }
  }

  for (const [file, text] of Object.entries(configs)) {
    for (const port of parseConfigPorts(text)) {
      if (!all.has(port)) {
        problems.push(`${file} に一覧にないポート ${port} がある（${LIST_FILE} に加えるか、${file} を修正する）`)
      }
    }
  }
  for (const [file, { text, keys }] of Object.entries(realms)) {
    const ports = parseConfigPorts(text)
    const written = new Set(ports)
    for (const port of ports) {
      if (!all.has(port)) {
        problems.push(`${file} に一覧にないポート ${port} がある（${LIST_FILE} に加えるか、${file} を修正する）`)
      }
    }
    for (const entry of list.ports.filter((item) => keys.includes(item.key))) {
      for (const port of slotPorts(entry, list)) {
        if (!written.has(port)) {
          problems.push(`一覧のポート ${port}（${entry.key}）が ${file} の redirect URI にない（${file} に加える）`)
        }
      }
    }
  }
  return problems
}

if (import.meta.main) {
  const files = [LIST_FILE, COMPOSE_FILE, ...DOC_FILES, ...SOURCE_FILES, ...CONFIG_FILES, ...Object.keys(REALM_FILES)]
  const missing = files.filter((file) => !existsSync(file))
  for (const file of missing) {
    console.log(`${file} がない（置き場所が変わっていないか確認する）`)
  }
  exitIfNoFiles(files.filter((file) => existsSync(file)), '照合するファイル')
  if (missing.length) {
    process.exit(2)
  }
  const read = (file) => readFileSync(file, 'utf8')
  const list = JSON.parse(read(LIST_FILE))
  const problems = findPortMismatches({
    list,
    compose: read(COMPOSE_FILE),
    docs: Object.fromEntries(DOC_FILES.map((file) => [file, read(file)])),
    sources: Object.fromEntries(SOURCE_FILES.map((file) => [file, read(file)])),
    configs: Object.fromEntries(CONFIG_FILES.map((file) => [file, read(file)])),
    realms: Object.fromEntries(Object.entries(REALM_FILES).map(([file, keys]) => [file, { text: read(file), keys }])),
  })
  for (const problem of problems) {
    console.log(problem)
  }
  console.log(`ローカルのポート：${list.ports.length} 件（スロットの展開後 ${expandedPorts(list).size} 件）、問題：${problems.length} 件`)
  process.exit(problems.length ? 1 : 0)
}
