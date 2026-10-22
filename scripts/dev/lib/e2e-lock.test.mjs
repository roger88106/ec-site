// e2e-lock.mjs のテスト（node --test scripts/dev/lib/e2e-lock.test.mjs）
import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { decideLock, decideTakeover, formatLock, parseLock } from './e2e-lock.mjs'

const owner = { worktree: '/work/a', pid: 4321, startedAt: '2026-10-22T10:00:00.000Z' }

describe('ロックの中身', () => {
  test('書き出した中身を、作業ツリーのパス・PID・開始の時刻に読み戻せる', () => {
    assert.deepEqual(parseLock(formatLock(owner)), owner)
  })

  test('壊れた中身は null になる', () => {
    assert.equal(parseLock('not json'), null)
    assert.equal(parseLock(JSON.stringify({ worktree: '/work/a' })), null)
    assert.equal(parseLock(JSON.stringify({ ...owner, pid: 'x' })), null)
  })
})

describe('ロックの判定', () => {
  test('ロックのファイルがなければ取得できる', () => {
    assert.deepEqual(decideLock({ content: null, isRunning: () => true }), { action: 'acquire' })
  })

  test('PID のプロセスが実行中なら、所有者を示して取得できない', () => {
    const decision = decideLock({ content: formatLock(owner), isRunning: (pid) => pid === 4321 })
    assert.deepEqual(decision, { action: 'busy', owner })
  })

  test('PID のプロセスがなければ、古いロックとして取得し直す', () => {
    const decision = decideLock({ content: formatLock(owner), isRunning: () => false })
    assert.deepEqual(decision, { action: 'acquire', stale: owner })
  })

  test('壊れたロックは古いロックとして取得し直す', () => {
    const decision = decideLock({ content: 'broken', isRunning: () => true })
    assert.deepEqual(decision, { action: 'acquire', stale: null })
  })
})

describe('古いロックの取り直し', () => {
  test('退かしたファイルの内容が判定に使った内容と同じなら、取り直す', () => {
    const content = formatLock(owner)
    assert.equal(decideTakeover({ judged: content, moved: content }), 'take')
  })

  test('退かしたファイルの内容が判定に使った内容と違えば、元に戻す', () => {
    assert.equal(decideTakeover({ judged: formatLock(owner), moved: formatLock({ ...owner, pid: 9999 }) }), 'restore')
  })

  test('判定に使ったロックが壊れた中身でも、同じ内容なら取り直す', () => {
    assert.equal(decideTakeover({ judged: 'broken', moved: 'broken' }), 'take')
  })
})
