// lock.mjs（E2E のロック）のテスト（node --test scripts/dev/lib/lock.test.mjs）
import assert from 'node:assert/strict'
import { mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, describe, test } from 'node:test'
import { acquireLock, busyLockMessage, e2eLockFile, formatLock, isE2eRunningIn, isProcessRunning, ownsLock, parseLock, readLock } from './lock.mjs'

const dir = mkdtempSync(path.join(tmpdir(), 'lock-'))
after(() => rmSync(dir, { recursive: true, force: true }))

/** テストごとのロックのファイル */
let count = 0
const newLock = () => path.join(dir, `e2e-${++count}.lock`)

const ME = { worktree: '/wt/me', pid: 100, startedAt: '2026-11-17T00:00:00.000Z' }
const OTHER = { worktree: '/wt/other', pid: 200, startedAt: '2026-11-17T00:00:01.000Z' }

/** 動いているプロセスを決めた確認 */
const running =
  (...pids) =>
  (pid) =>
    pids.includes(pid)

describe('ロックの中身', () => {
  test('書き出した中身を、作業ツリーのパス・PID・開始の時刻に読み戻せる', () => {
    assert.deepEqual(parseLock(formatLock(ME)), ME)
  })

  test('壊れた中身は null になる', () => {
    for (const content of [
      '',
      '{',
      'null',
      '1',
      '{"worktree":"/a","pid":"1","startedAt":"x"}',
      '{"worktree":1,"pid":1,"startedAt":"x"}',
      '{"worktree":"/a","pid":1,"startedAt":0}',
    ]) {
      assert.equal(parseLock(content), null, content)
    }
  })
})

describe('ロックのファイルの読み取り', () => {
  test('E2E のロックのファイルは、git の共通のディレクトリの ec-site-e2e.lock', () => {
    assert.equal(e2eLockFile(path.join('repo', '.git')), path.join('repo', '.git', 'ec-site-e2e.lock'))
  })

  test('ロックがなければ readLock は null を返す', () => {
    assert.equal(readLock(newLock()), null)
  })
})

describe('ロックの取得', () => {
  test('ロックがなければ、自分の中身を書いて取り、一時のファイルを残さない', () => {
    const file = newLock()
    assert.deepEqual(acquireLock({ file, owner: ME, isRunning: running() }), { acquired: true })
    assert.deepEqual(parseLock(readLock(file)), ME)
    assert.deepEqual(
      readdirSync(dir).filter((name) => name.startsWith(path.basename(file)) && name !== path.basename(file)),
      [],
    )
  })

  test('所有者のプロセスが動いていれば取らず、所有者を返す', () => {
    const file = newLock()
    writeFileSync(file, formatLock(OTHER))
    assert.deepEqual(acquireLock({ file, owner: ME, isRunning: running(OTHER.pid) }), { acquired: false, owner: OTHER })
    assert.deepEqual(parseLock(readFileSync(file, 'utf8')), OTHER)
  })

  test('所有者のプロセスがない古いロックと、壊れたロックは、取り除いて再取得する', () => {
    for (const content of [formatLock(OTHER), '壊れた中身']) {
      const file = newLock()
      writeFileSync(file, content)
      assert.deepEqual(acquireLock({ file, owner: ME, isRunning: running() }), { acquired: true })
      assert.deepEqual(parseLock(readLock(file)), ME)
    }
  })
})

describe('ロックの所有者と案内', () => {
  test('自分の PID と開始の時刻のロックだけを自分のものとする', () => {
    assert.equal(ownsLock(formatLock(ME), ME), true)
    assert.equal(ownsLock(formatLock({ ...ME, startedAt: 'other' }), ME), false)
    assert.equal(ownsLock(formatLock(OTHER), ME), false)
    assert.equal(ownsLock(null, ME), false)
  })

  test('案内は、所有者、ロックのファイルのパス、所有者のプロセスがなければ削除してよいことを示す', () => {
    const message = busyLockMessage({ lock: '/repo/.git/ec-site-e2e.lock', owner: OTHER })
    assert.match(message, /\/wt\/other（PID 200/)
    assert.match(message, /\/repo\/\.git\/ec-site-e2e\.lock/)
    assert.match(message, /削除してよい/)
    assert.doesNotMatch(busyLockMessage({ lock: '/l', owner: null }), /所有者：/)
  })
})

describe('作業ツリーの E2E が実行中かの判定', () => {
  test('所有者が自分の作業ツリーで、プロセスが動いていれば実行中', () => {
    assert.equal(isE2eRunningIn({ content: formatLock(ME), worktree: ME.worktree, isRunning: running(ME.pid) }), true)
  })

  test('他の作業ツリー、プロセスがない、ロックがない、壊れたロックなら、実行中とみなさない', () => {
    assert.equal(isE2eRunningIn({ content: formatLock(OTHER), worktree: ME.worktree, isRunning: running(OTHER.pid) }), false)
    assert.equal(isE2eRunningIn({ content: formatLock(ME), worktree: ME.worktree, isRunning: running() }), false)
    assert.equal(isE2eRunningIn({ content: null, worktree: ME.worktree, isRunning: running(ME.pid) }), false)
    assert.equal(isE2eRunningIn({ content: '{', worktree: ME.worktree, isRunning: running(ME.pid) }), false)
  })
})

describe('プロセスが実行中かの判定', () => {
  test('自分のプロセスは実行中で、存在しない PID は実行中でない', () => {
    assert.equal(isProcessRunning(process.pid), true)
    assert.equal(isProcessRunning(2 ** 30), false)
  })
})
