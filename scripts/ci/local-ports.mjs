// ローカルのポートの一覧（scripts/dev/ports.json）と、一覧を読めない所のポートが食い違わないことを確認する。
// 一覧を読む所（スロットの script `scripts/dev/lib/slot.mjs`）は検査の対象にしない。
// 一覧を読めない所：compose（docker/compose.yaml）、Windows の予約の script、Playwright の設定（E2E のポート）、開発環境の起動の文書、
// そして、ポートを直接書く設定とスクリプト（Keycloak の realm の redirect URI、バックエンドの application*.yml、.claude/launch.json、
// 画面の認証の設定、起動の補助の script、CI の workflow など。CONFIG_FILES）。
// 設定のポートは、一覧から決まる値（スロットでずらした値を含む）であることを確認する。realm は、一覧の画面のポートが全て redirect URI にあることも確認する。
// 対象外：テストコードの中のポート（単体テストの URL の例で、一覧のポートと同じである必要がない）、
// 開発の文書のうち DOC_FILES 以外（ポートの一覧を書く文書ではない）。
// compose の待ち受けるアドレス（LOCAL_BIND_ADDRESS）は compose-ports が確認する。この検査は、ポートの番号だけを照合する。
// 使い方：node scripts/ci/local-ports.mjs（リポジトリのルートで）
import { existsSync, readFileSync } from 'node:fs'
import { isMain } from './lib/is-main.mjs'
import { exitIfNoFiles } from './lib/git-files.mjs'
import { findPortProblems, readHostPort } from './compose-ports.mjs'

/** ポートの一覧 */
const LIST_FILE = 'scripts/dev/ports.json'

/** compose のファイル */
const COMPOSE_FILE = 'docker/compose.yaml'

/** Windows の予約の script */
const POWERSHELL_FILE = 'scripts/dev/windows/exclude-ports.ps1'

/** ポートを書く文書 */
const DOC_FILES = ['docs/guides/setup.md', 'docs/guides/running-tests.md']

/** E2E のポートを書く設定（E2E のサーバーの URL、画面のサーバーの `--port`、Playwright の `port:`。読み方は CONFIG_FILES と同じ readConfigPorts） */
const SOURCE_FILES = ['e2e/src/urls.ts', 'e2e/playwright.config.ts']

/** E2E の設定が書いてよい共有のサービスの key（E2E 用の IdP と、メールを確認する Mailpit の画面） */
const E2E_SHARED_KEYS = ['keycloak-e2e', 'mailpit-ui']

/** ポートを直接書く設定とスクリプト（一覧にあるポートだけが書かれていることを確認する） */
const CONFIG_FILES = [
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
  const result = new Set()
  for (const entry of list.ports) {
    const slots = entry.slotted ? list.slotCount : 1
    for (let slot = 0; slot < slots; slot++) {
      result.add(entry.port + list.slotOffset * slot)
    }
  }
  return result
}

/**
 * compose の `ports` の項目から、ホスト側のポートを読む（読み方は compose-ports の readHostPort）。<br />
 * `${LOCAL_BIND_ADDRESS:-127.0.0.1}:` の後ろが、数字か `${変数:-数字}` の書き方のもの。
 * それ以外の書き方の項目（既定値のない変数、範囲など）は、読み飛ばすと一覧にないポートを見逃すので、`unreadable` に入れる。
 *
 * @param {string} text compose のファイル
 * @returns {{ ports: number[], unreadable: { line: number, entry: string }[] }} ホスト側のポートと、読めない項目（行の順）
 */
export function readComposeHostPorts(text) {
  const ports = []
  const unreadable = []
  for (const port of findPortProblems(text).ports) {
    const host = readHostPort(port.entry)
    if (host === null) {
      unreadable.push(port)
    } else {
      ports.push(host)
    }
  }
  return { ports, unreadable }
}

/**
 * Windows の予約の script の表（`$ports = [ordered]@{ 5432 = '…' }`）から、ポートを読む。
 *
 * @param {string} text script
 * @returns {number[]} 表のポート（行の順）
 */
export function readPowerShellPorts(text) {
  return [...text.matchAll(/^\s*(\d{2,5})\s*=\s*'/gm)].map((match) => Number(match[1]))
}

/**
 * 文書や設定の中の、`:ポート` の書き方（`localhost:8080`、`:8090`）と `--port ポート` から、ポートを読む。<br />
 * 数字だけの書き方（カード番号など）は、ポートか分からないので読まない。
 *
 * @param {string} text 文書や設定
 * @returns {number[]} ポート（出てくる順）
 */
export function readDocPorts(text) {
  return [...text.matchAll(/(?::|--port\s+)(\d{4,5})(?!\d)/g)].map((match) => Number(match[1]))
}

/**
 * 設定とスクリプトのポートを読む。`:ポート`（URL、`${変数:-ポート}`）と `--port ポート` に加え、
 * yml の `port: ポート`・`port: ${変数:ポート}`、JSON の `"port": ポート`、起動の引数の `"--port", "ポート"` を読む。
 *
 * @param {string} text 設定やスクリプト
 * @returns {number[]} ポート（種類ごとに出てくる順。重複は除く）
 */
export function readConfigPorts(text) {
  const found = [
    ...readDocPorts(text),
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
function readSlotRows(text) {
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
 *   compose: string, powershell: string, docs: Record<string, string>, sources: Record<string, string> }} input
 *   docs・sources・configs は、ファイルのパス → 内容。realms は、ファイルのパス → { text: 内容, keys: redirect URI に全て書く画面の一覧の key }
 * @returns {string[]} 問題の文
 */
export function findPortMismatches({ list, compose, powershell, docs, sources, configs = {}, realms = {} }) {
  const problems = []

  const used = new Map()
  for (const entry of list.ports) {
    const slots = entry.slotted ? list.slotCount : 1
    for (let slot = 0; slot < slots; slot++) {
      const port = entry.port + list.slotOffset * slot
      if (used.has(port)) {
        problems.push(`${LIST_FILE} でポート ${port} が重なる（${used.get(port)} と ${entry.key}）`)
      }
      used.set(port, entry.key)
    }
  }

  const shared = new Set(list.ports.filter((entry) => entry.group === 'shared').map((entry) => entry.port))
  const composePorts = readComposeHostPorts(compose)
  for (const port of composePorts.unreadable) {
    problems.push(`${COMPOSE_FILE}:${port.line} ${port.entry} のホスト側のポートを読めない（数字か \${変数:-数字} で 1 つのポートを書く）`)
  }
  problems.push(...diffProblems(composePorts.ports, shared, COMPOSE_FILE, 'compose'))

  problems.push(...diffProblems(readPowerShellPorts(powershell), expandedPorts(list), POWERSHELL_FILE, 'Windows の予約の script'))

  const all = expandedPorts(list)
  const docsText = Object.values(docs).join('\n')
  for (const [file, text] of Object.entries(docs)) {
    for (const port of new Set(readDocPorts(text))) {
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
  const rows = Object.entries(docs).flatMap(([file, text]) => readSlotRows(text).map((row) => ({ ...row, file })))
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
  // E2E の設定が書いてよいのは、E2E のポートと、E2E が使う共有のサービス（IdP・Mailpit）だけ。開発の画面のポートなどに替えても、一覧にあるだけで通さない
  const allowed = new Set([...e2e, ...list.ports.filter((entry) => E2E_SHARED_KEYS.includes(entry.key)).map((entry) => entry.port)])
  const sourcePorts = new Set()
  for (const [file, text] of Object.entries(sources)) {
    for (const port of readConfigPorts(text)) {
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
    for (const port of readConfigPorts(text)) {
      if (!all.has(port)) {
        problems.push(`${file} に一覧にないポート ${port} がある（${LIST_FILE} に加えるか、${file} を修正する）`)
      }
    }
  }
  for (const [file, { text, keys }] of Object.entries(realms)) {
    const written = new Set(readConfigPorts(text))
    for (const port of readConfigPorts(text)) {
      if (!all.has(port)) {
        problems.push(`${file} に一覧にないポート ${port} がある（${LIST_FILE} に加えるか、${file} を修正する）`)
      }
    }
    for (const entry of list.ports.filter((item) => keys.includes(item.key))) {
      const slots = entry.slotted ? list.slotCount : 1
      for (let slot = 0; slot < slots; slot++) {
        const port = entry.port + list.slotOffset * slot
        if (!written.has(port)) {
          problems.push(`一覧のポート ${port}（${entry.key}）が ${file} の redirect URI にない（${file} に加える）`)
        }
      }
    }
  }
  return problems
}

if (isMain(import.meta.url)) {
  const files = [LIST_FILE, COMPOSE_FILE, POWERSHELL_FILE, ...DOC_FILES, ...SOURCE_FILES, ...CONFIG_FILES, ...Object.keys(REALM_FILES)]
  const missing = files.filter((file) => !existsSync(file))
  for (const file of missing) {
    console.log(`${file} がない（置き場所が変わっていないか確認する）`)
  }
  exitIfNoFiles(files.filter((file) => existsSync(file)), '照合するファイル')
  if (missing.length) {
    process.exit(1)
  }
  const read = (file) => readFileSync(file, 'utf8')
  const list = JSON.parse(read(LIST_FILE))
  const problems = findPortMismatches({
    list,
    compose: read(COMPOSE_FILE),
    powershell: read(POWERSHELL_FILE),
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
