// lock-io.mjs のテスト（node --test scripts/dev/lib/lock-io.test.mjs）
import assert from 'node:assert/strict'
import { mkdtempSync, readFileSync, readdirSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { afterEach, beforeEach, describe, test } from 'node:test'
import { createLockIo, isProcessRunning } from './lock-io.mjs'

let dir
let lock
beforeEach(() => {
  dir = mkdtempSync(path.join(tmpdir(), 'lock-io-'))
  lock = path.join(dir, 'x.lock')
})
afterEach(() => rmSync(dir, { recursive: true, force: true }))

describe('ロックのファイルの作成', () => {
  test('中身を書いたロックを作り、一時のファイルを残さない', () => {
    assert.equal(createLockIo(lock).create('content'), true)
    assert.equal(readFileSync(lock, 'utf8'), 'content')
    assert.deepEqual(readdirSync(dir), ['x.lock'])
  })

  test('すでにロックがあれば、上書きせずに false を返す', () => {
    const io = createLockIo(lock)
    io.create('first')
    assert.equal(io.create('second'), false)
    assert.equal(readFileSync(lock, 'utf8'), 'first')
    assert.deepEqual(readdirSync(dir), ['x.lock'])
  })

  test('ロックのファイルには、中身の書き込みを済ませた一時のファイルを link で置き、空のロックを見せない', () => {
    const events = []
    const fs = {
      writeFileSync: (file, content) => events.push(['write', path.basename(file) === 'x.lock' ? 'lock' : 'temp', content]),
      linkSync: (from, to) => events.push(['link', path.basename(from) === 'x.lock' ? 'lock' : 'temp', path.basename(to)]),
      rmSync: () => {},
    }
    createLockIo(lock, fs).create('content')
    assert.deepEqual(events, [
      ['write', 'temp', 'content'],
      ['link', 'temp', 'x.lock'],
    ])
  })
})

describe('古いロックの退避', () => {
  test('退かすと、ロックがなくなり、退かした中身を返す', () => {
    const io = createLockIo(lock)
    io.create('old')
    assert.equal(io.moveAside(), 'old')
    assert.equal(io.read(), null)
    io.discard()
    assert.deepEqual(readdirSync(dir), [])
  })

  test('ロックがなければ null を返す', () => {
    assert.equal(createLockIo(lock).moveAside(), null)
  })

  test('Windows で他方が読んでいる間の失敗（EPERM、EBUSY、EACCES）は、例外にせず null を返す', () => {
    for (const code of ['EPERM', 'EBUSY', 'EACCES']) {
      const fs = {
        renameSync: () => {
          throw Object.assign(new Error(code), { code })
        },
      }
      assert.equal(createLockIo(lock, fs).moveAside(), null, code)
    }
  })

  test('退かしたロックを戻すとき、新しいロックがあれば上書きしない', () => {
    const io = createLockIo(lock)
    io.create('old')
    io.moveAside()
    io.create('new')
    io.restore()
    assert.equal(readFileSync(lock, 'utf8'), 'new')
    assert.deepEqual(readdirSync(dir), ['x.lock'])
  })
})

describe('プロセスが実行中かの判定', () => {
  test('自分のプロセスは実行中', () => {
    assert.equal(isProcessRunning(process.pid), true)
  })

  test('存在しない PID は実行中でない', () => {
    assert.equal(isProcessRunning(2 ** 22 + 12345), false)
  })
})
