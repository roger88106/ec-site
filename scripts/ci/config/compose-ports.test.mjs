import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { describeUnreadableHostPort, findPortProblems, readHostPort } from './compose-ports.mjs'

/** サービスの ports を compose の形にする */
const compose = (services) =>
  ['services:', ...Object.entries(services).flatMap(([name, ports]) => [`  ${name}:`, '    ports:', ...ports.map((port) => `      - ${port}`)])].join('\n')

/** 問題の項目を「サービス 項目」で並べる */
const problemsOf = (text) => findPortProblems(text).problems.map((port) => `${port.service} ${port.entry}`)

describe('compose のポートの検査', () => {
  test('待ち受けるアドレスのないポートと、変数を使わずに書いたアドレスを見つける', () => {
    const text = compose({ postgres: ['"5432:5432"', '"0.0.0.0:9411:9411"', '"127.0.0.1:8333:8333"'] })
    assert.deepEqual(problemsOf(text), ['postgres 5432:5432', 'postgres 0.0.0.0:9411:9411', 'postgres 127.0.0.1:8333:8333'])
  })

  test('既定が 127.0.0.1 でないもの、別の変数を使うものを見つける', () => {
    const text = compose({ app: ['"${LOCAL_BIND_ADDRESS:-0.0.0.0}:5432:5432"', '"${BIND_ADDRESS:-127.0.0.1}:8025:8025"'] })
    assert.deepEqual(problemsOf(text), ['app ${LOCAL_BIND_ADDRESS:-0.0.0.0}:5432:5432', 'app ${BIND_ADDRESS:-127.0.0.1}:8025:8025'])
  })

  test('決まった変数で書いたポートは、ホストのポートが変数でも、引用符がなくても通す', () => {
    const text = compose({ mailpit: ['"${LOCAL_BIND_ADDRESS:-127.0.0.1}:${MAILPIT_SMTP_PORT:-2525}:1025"', '${LOCAL_BIND_ADDRESS:-127.0.0.1}:8025:8025'] })
    assert.deepEqual(problemsOf(text), [])
    assert.equal(findPortProblems(text).ports.length, 2)
  })

  test('1 行で書いた ports（フロー形式）も読む', () => {
    const text = ['services:', '  app:', '    ports: ["8080:8080"]'].join('\n')
    assert.deepEqual(problemsOf(text), ['app 8080:8080'])
  })

  test('文字列でない項目（長い書き方、数字）は、決まった形でない項目にする', () => {
    const text = ['services:', '  app:', '    ports:', '      - target: 80', '        published: 8080', '      - 8081'].join('\n')
    assert.deepEqual(problemsOf(text), ['app {"target":80,"published":8080}', 'app 8081'])
  })

  test('ホスト側のポートは、数字か既定値付きの変数から読み、それ以外（既定値のない変数、範囲）は読めない', () => {
    assert.equal(readHostPort('${LOCAL_BIND_ADDRESS:-127.0.0.1}:5432:5432'), 5432)
    assert.equal(readHostPort('${LOCAL_BIND_ADDRESS:-127.0.0.1}:${MAILPIT_SMTP_PORT:-2525}:1025'), 2525)
    assert.equal(readHostPort('5432:5432'), 5432)
    for (const entry of ['${LOCAL_BIND_ADDRESS:-127.0.0.1}:${NEW_PORT}:80', '${LOCAL_BIND_ADDRESS:-127.0.0.1}:9000-9001:80', '${LOCAL_BIND_ADDRESS:-127.0.0.1}:80']) {
      assert.equal(readHostPort(entry), null, entry)
    }
  })

  test('ホスト側のポートを読めない項目は、サービスの名前を付けて失敗にする', () => {
    const text = compose({
      extra: ['"${LOCAL_BIND_ADDRESS:-127.0.0.1}:${NEW_PORT}:80"', '"${LOCAL_BIND_ADDRESS:-127.0.0.1}:9000-9001:80"', '"${LOCAL_BIND_ADDRESS:-127.0.0.1}:${MAILPIT_SMTP_PORT:-2525}:1025"'],
    })
    const { problems, unreadable } = findPortProblems(text)
    assert.deepEqual(problems, [])
    assert.deepEqual(
      unreadable.map((port) => describeUnreadableHostPort('docker/compose.yaml', port)),
      [
        'docker/compose.yaml extra：${LOCAL_BIND_ADDRESS:-127.0.0.1}:${NEW_PORT}:80（ホスト側のポートを読めない。数字か ${変数:-数字} で 1 つのポートを書く）',
        'docker/compose.yaml extra：${LOCAL_BIND_ADDRESS:-127.0.0.1}:9000-9001:80（ホスト側のポートを読めない。数字か ${変数:-数字} で 1 つのポートを書く）',
      ],
    )
  })
})

describe('script として実行する', () => {
  const script = fileURLToPath(new URL('./compose-ports.mjs', import.meta.url))

  /** 一時のディレクトリに docker/compose.yaml を置いて、script を実行する */
  function run(text) {
    const dir = mkdtempSync(path.join(tmpdir(), 'compose-ports-'))
    try {
      mkdirSync(path.join(dir, 'docker'), { recursive: true })
      writeFileSync(path.join(dir, 'docker/compose.yaml'), text)
      return spawnSync(process.execPath, [script], { cwd: dir, encoding: 'utf8' })
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  }

  test('全てのポートが決まった形なら、終了コード 0 で、ポートの数を出して終わる', () => {
    const result = run(compose({ app: ['"${LOCAL_BIND_ADDRESS:-127.0.0.1}:8080:8080"'] }))
    assert.equal(result.status, 0, result.stdout)
    assert.match(result.stdout, /compose のポート：1 件、決まった形でないもの：0 件、ホスト側のポートを読めないもの：0 件/)
  })

  test('決まった形でないポートと、ホスト側を読めないポートがあれば、サービスと項目を出し、終了コード 1 で終わる', () => {
    const result = run(compose({ app: ['"8080:8080"'], extra: ['"${LOCAL_BIND_ADDRESS:-127.0.0.1}:9000-9001:80"'] }))
    assert.equal(result.status, 1, result.stdout)
    assert.match(result.stdout, /docker\/compose\.yaml app：8080:8080/)
    assert.match(result.stdout, /docker\/compose\.yaml extra：.*9000-9001:80（ホスト側のポートを読めない/)
  })

  test('ポートが 0 件なら、何も見ずに通らず、原因を出して終了コード 1 で終わる', () => {
    const result = run('services:\n  app:\n    image: x\n')
    assert.equal(result.status, 1, result.stdout)
    assert.match(result.stdout, /docker\/compose\.yaml にポートが 1 件も見つからない/)
    assert.match(result.stdout, /compose のポート：0 件/)
  })
})
