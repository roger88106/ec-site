import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
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
