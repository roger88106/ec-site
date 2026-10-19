import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { describe, test } from 'node:test'
import { findPortProblems } from './compose-ports.mjs'

/** 問題の項目を「行 項目」で並べる */
const problemsOf = (text) => findPortProblems(text).problems.map((port) => `${port.line} ${port.entry}`)

describe('compose のポートの検査', () => {
  test('待ち受けるアドレスのないポートと、変数を使わずに書いたアドレスを見つける', () => {
    const text = [
      'services:',
      '  postgres:',
      '    ports:',
      '      - "5432:5432"',
      '      - "0.0.0.0:9411:9411"',
      '      - "127.0.0.1:8333:8333"',
    ].join('\n')
    assert.deepEqual(problemsOf(text), ['4 5432:5432', '5 0.0.0.0:9411:9411', '6 127.0.0.1:8333:8333'])
  })

  test('既定が 127.0.0.1 でないもの、別の変数を使うものを見つける', () => {
    const text = [
      '    ports:',
      '      - "${LOCAL_BIND_ADDRESS:-0.0.0.0}:5432:5432"',
      '      - "${BIND_ADDRESS:-127.0.0.1}:8025:8025"',
    ].join('\n')
    assert.deepEqual(problemsOf(text), [
      '2 ${LOCAL_BIND_ADDRESS:-0.0.0.0}:5432:5432',
      '3 ${BIND_ADDRESS:-127.0.0.1}:8025:8025',
    ])
  })

  test('決まった変数で書いたポートは、ホストのポートが変数でも通す', () => {
    const text = [
      '    ports:',
      '      - "${LOCAL_BIND_ADDRESS:-127.0.0.1}:${MAILPIT_SMTP_PORT:-2525}:1025"',
      "      - '${LOCAL_BIND_ADDRESS:-127.0.0.1}:8025:8025'",
    ].join('\n')
    assert.deepEqual(problemsOf(text), [])
    assert.equal(findPortProblems(text).ports.length, 2)
  })

  test('ports の外のリスト（volumes、environment など）は見ない', () => {
    const text = [
      '    ports:',
      '      - "${LOCAL_BIND_ADDRESS:-127.0.0.1}:5432:5432"',
      '    volumes:',
      '      - postgres_data:/var/lib/postgresql/data',
      '    command:',
      '      - "8080:8080"',
    ].join('\n')
    assert.deepEqual(problemsOf(text), [])
  })

  test('ports の中のコメントと空行は飛ばす', () => {
    const text = ['    ports:', '      # SMTP', '', '      - "5432:5432"', '    restart: always'].join('\n')
    assert.deepEqual(problemsOf(text), ['4 5432:5432'])
  })

  test('リポジトリの compose のすべてのポートが、決まった形で書いてある', () => {
    const { ports, problems } = findPortProblems(readFileSync('docker/compose.yaml', 'utf8'))
    assert.ok(ports.length > 0)
    assert.deepEqual(problems, [])
  })
})
