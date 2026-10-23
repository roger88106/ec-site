import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { expandedPorts, findPortMismatches, readComposeHostPorts, readConfigPorts, readDocPorts, readPowerShellPorts } from './local-ports.mjs'

/** 小さな一覧（共有 2 件、スロットでずらす 1 件、E2E 1 件、スロット 3 つ） */
const LIST = {
  slotCount: 3,
  slotOffset: 10000,
  ports: [
    { key: 'postgres', port: 5432, name: 'PostgreSQL', group: 'shared', slotted: false },
    { key: 'mailpit-smtp', port: 2525, name: 'Mailpit の SMTP', group: 'shared', slotted: false },
    { key: 'backend', port: 8080, name: 'バックエンド', group: 'app', slotted: true },
    { key: 'backend-e2e', port: 8090, name: 'バックエンド（E2E）', group: 'e2e', slotted: false },
  ],
}

const COMPOSE = [
  'services:',
  '  postgres:',
  '    ports:',
  '      - "${LOCAL_BIND_ADDRESS:-127.0.0.1}:5432:5432"',
  '  mailpit:',
  '    ports:',
  '      - "${LOCAL_BIND_ADDRESS:-127.0.0.1}:${MAILPIT_SMTP_PORT:-2525}:1025"',
].join('\n')

const PS1 = [
  '$ports = [ordered]@{',
  "  5432 = 'PostgreSQL'",
  "  2525 = 'Mailpit'",
  "  8080 = 'バックエンド'",
  "  8090 = 'E2E'",
  "  18080 = 'スロット 1'",
  "  28080 = 'スロット 2'",
  '}',
].join('\n')

const DOC = [
  '| http://localhost:8080/api | API |',
  'DB は localhost:5432、SMTP は :2525',
  'E2E は :8090 を使う。カード 4242 4242 4242 4242',
  '| 0 | 8080 |',
  '| 1 | 18080 |',
  '| 2 | 28080 |',
].join('\n')

const INPUT = {
  list: LIST,
  compose: COMPOSE,
  powershell: PS1,
  docs: { 'docs/guides/setup.md': DOC },
  sources: { 'e2e/src/urls.ts': "export const BACKEND_URL = 'http://localhost:8090'" },
}

describe('ローカルのポートの一覧の検査', () => {
  test('一覧とそろっていれば問題がない', () => {
    assert.deepEqual(findPortMismatches(INPUT), [])
  })

  test('スロットでずらすポートは、スロットの数だけ展開する', () => {
    assert.deepEqual([...expandedPorts(LIST)].sort((a, b) => a - b), [2525, 5432, 8080, 8090, 18080, 28080])
  })

  test('compose の既定値付きの変数のポートを読む', () => {
    assert.deepEqual(readComposeHostPorts(COMPOSE), { ports: [5432, 2525], unreadable: [] })
  })

  test('compose のホスト側のポートを読めない項目（既定値のない変数、範囲）は、ファイルと行を出して失敗にする', () => {
    const extra = ['  extra:', '    ports:', '      - "${LOCAL_BIND_ADDRESS:-127.0.0.1}:${NEW_PORT}:80"', '      - "${LOCAL_BIND_ADDRESS:-127.0.0.1}:9000-9001:80"']
    const problems = findPortMismatches({ ...INPUT, compose: [COMPOSE, ...extra].join('\n') })
    assert.equal(problems.length, 2)
    assert.match(problems[0], /docker\/compose\.yaml:10 .*NEW_PORT/)
    assert.match(problems[1], /docker\/compose\.yaml:11 .*9000-9001/)
  })

  test('PowerShell の表のポートを読む', () => {
    assert.deepEqual(readPowerShellPorts(PS1), [5432, 2525, 8080, 8090, 18080, 28080])
  })

  test('文書のポートは、コロンの後の数字だけを読む（カード番号などは読まない）', () => {
    assert.deepEqual(readDocPorts('http://localhost:8080/api と :8090、4242 4242、12:3456'), [8080, 8090, 3456])
  })

  test('一覧にないポートが compose にあれば失敗にする', () => {
    const compose = `${COMPOSE}\n  extra:\n    ports:\n      - "\${LOCAL_BIND_ADDRESS:-127.0.0.1}:6379:6379"`
    const problems = findPortMismatches({ ...INPUT, compose })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /compose.*6379/)
  })

  test('一覧の共有のポートが compose にないときも失敗にする', () => {
    const compose = COMPOSE.replace('5432:5432', '5433:5432')
    const problems = findPortMismatches({ ...INPUT, compose })
    assert.equal(problems.length, 2)
    assert.ok(problems.some((problem) => /5433/.test(problem)))
    assert.ok(problems.some((problem) => /5432/.test(problem)))
  })

  test('Windows の予約の表が、一覧とスロットの展開に足りない・余るとき失敗にする', () => {
    const missing = findPortMismatches({ ...INPUT, powershell: PS1.replace("  28080 = 'スロット 2'\n", '') })
    assert.equal(missing.length, 1)
    assert.match(missing[0], /28080/)
    const extra = findPortMismatches({ ...INPUT, powershell: PS1.replaceAll('}', "  7777 = 'x'\n}") })
    assert.equal(extra.length, 1)
    assert.match(extra[0], /7777/)
  })

  test('文書に一覧にないポートがあれば失敗にする', () => {
    const problems = findPortMismatches({ ...INPUT, docs: { 'docs/guides/setup.md': `${DOC}\nhttp://localhost:9999` } })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /setup\.md.*9999/)
  })

  test('スロットの表の行が、スロットごとの値と違えば失敗にする', () => {
    const problems = findPortMismatches({ ...INPUT, docs: { 'docs/guides/setup.md': DOC.replace('| 1 | 18080 |', '| 1 | 18081 |') } })
    assert.ok(problems.some((problem) => /18081/.test(problem)))
  })

  test('一覧のポートがどの文書にも書かれていなければ失敗にする', () => {
    const problems = findPortMismatches({ ...INPUT, docs: { 'docs/guides/setup.md': DOC.replace(':8090', '') } })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /8090.*文書/)
  })

  test('E2E の設定のポートが、一覧の E2E のものと違えば失敗にする', () => {
    const problems = findPortMismatches({ ...INPUT, sources: { 'e2e/src/urls.ts': "'http://localhost:8091'" } })
    assert.ok(problems.some((problem) => /urls\.ts.*8091/.test(problem)))
    assert.ok(problems.some((problem) => /8090.*E2E/.test(problem)))
  })

  test('E2E の設定は、Playwright の webServer の port: も読み、一覧にないポートを失敗にする', () => {
    const sources = { 'e2e/src/urls.ts': "'http://localhost:8090'", 'e2e/playwright.config.ts': 'webServer: {\n  port: 4174,\n}' }
    const problems = findPortMismatches({ ...INPUT, sources })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /playwright\.config\.ts.*4174/)
  })

  test('E2E の設定のポートが、一覧にあっても E2E のポートでなければ（開発の画面のポートなどは）失敗にする', () => {
    const sources = { 'e2e/src/urls.ts': "'http://localhost:8090'", 'e2e/playwright.config.ts': 'command: pnpm preview --port 8080' }
    const problems = findPortMismatches({ ...INPUT, sources })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /playwright\.config\.ts.*8080.*E2E/)
  })

  test('設定のポートは、yml の port、JSON の port、起動の引数の --port から読む', () => {
    const yml = 'server:\n  port: 8090\nmanagement:\n  server:\n    port: ${MANAGEMENT_SERVER_PORT:9080}\nurl: http://localhost:5432/x'
    assert.deepEqual(readConfigPorts(yml), [5432, 8090, 9080])
    assert.deepEqual(readConfigPorts('{ "port": 5173, "args": ["--port", "3000"] }'), [3000, 5173])
  })

  test('設定（realm、yml、起動の設定）に一覧にないポートがあれば失敗にする', () => {
    const configs = { 'backend/application.yml': 'server:\n  port: 8081', 'docker/realm.json': '"http://localhost:8080/*"' }
    const problems = findPortMismatches({ ...INPUT, configs })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /application\.yml.*8081/)
  })

  test('スロットでずらした値は、一覧から決まる値なら通り、ずれていれば失敗にする', () => {
    assert.deepEqual(findPortMismatches({ ...INPUT, configs: { 'x.json': '"port": 18080' } }), [])
    assert.ok(findPortMismatches({ ...INPUT, configs: { 'x.json': '"port": 18081' } }).some((problem) => /18081/.test(problem)))
  })

  test('realm の redirect URI に、一覧のスロットごとのポートが足りなければ失敗にする', () => {
    const realm = (ports) => ({ text: ports.map((port) => `http://localhost:${port}/*`).join(' '), keys: ['backend', 'backend-e2e'] })
    assert.deepEqual(findPortMismatches({ ...INPUT, realms: { 'docker/r.json': realm([8080, 18080, 28080, 8090]) } }), [])
    const problems = findPortMismatches({ ...INPUT, realms: { 'docker/r.json': realm([8080, 18080, 8090]) } })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /28080.*r\.json/)
  })

  test('一覧の中で同じポートを重ねて書くと失敗にする', () => {
    const list = { ...LIST, ports: [...LIST.ports, { key: 'dup', port: 8090, name: 'x', group: 'e2e', slotted: false }] }
    assert.ok(findPortMismatches({ ...INPUT, list }).some((problem) => /8090.*重なる/.test(problem)))
  })
})
