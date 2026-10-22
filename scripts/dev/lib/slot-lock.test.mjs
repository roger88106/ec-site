// slot-lock.mjs のテスト（node --test scripts/dev/lib/slot-lock.test.mjs）
import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { formatLock } from './e2e-lock.mjs'
import { acquireLock, acquireOnce, ownsLock } from './slot-lock.mjs'

const me = { worktree: '/wt/me', pid: 100, startedAt: '2026-10-22T10:00:00.000Z' }
const other = { worktree: '/wt/other', pid: 200, startedAt: '2026-10-22T09:00:00.000Z' }
const third = { worktree: '/wt/third', pid: 300, startedAt: '2026-10-22T10:00:01.000Z' }

/**
 * ロックのファイルを真似る。beforeMove は、退かす直前に他のプロセスがすることを真似る。
 *
 * @param {string | null} initial ロックのファイルの中身
 * @param {{ beforeMove?: (state: { lock: string | null }) => void }} [hooks]
 */
function fakeLock(initial, hooks = {}) {
  const state = { lock: initial, aside: null, events: [] }
  state.io = {
    create(content) {
      if (state.lock !== null) return false
      state.lock = content
      state.events.push('create')
      return true
    },
    read() {
      return state.lock
    },
    moveAside() {
      hooks.beforeMove?.(state)
      if (state.lock === null) return null
      state.aside = state.lock
      state.lock = null
      state.events.push('move')
      return state.aside
    },
    restore() {
      if (state.lock === null) state.lock = state.aside
      state.aside = null
      state.events.push('restore')
    },
    discard() {
      state.aside = null
      state.events.push('discard')
    },
  }
  return state
}

const alive = (...pids) => (pid) => pids.includes(pid)

describe('スロットのロックの 1 回の取得', () => {
  test('ロックがなければ、自分の PID と開始の時刻を書いて取る', () => {
    const lock = fakeLock(null)
    assert.deepEqual(acquireOnce({ io: lock.io, owner: me, isRunning: alive() }), { action: 'acquired' })
    assert.equal(lock.lock, formatLock(me))
  })

  test('持ち主の PID のプロセスがあれば、時間が経っていても取らず、持ち主を返す', () => {
    const lock = fakeLock(formatLock(other))
    assert.deepEqual(acquireOnce({ io: lock.io, owner: me, isRunning: alive(200) }), { action: 'busy', owner: other })
    assert.equal(lock.lock, formatLock(other))
    assert.deepEqual(lock.events, [])
  })

  test('持ち主の PID のプロセスがなければ、退かして捨て、取り直す', () => {
    const lock = fakeLock(formatLock(other))
    assert.deepEqual(acquireOnce({ io: lock.io, owner: me, isRunning: alive() }), { action: 'retry' })
    assert.deepEqual(lock.events, ['move', 'discard'])
    assert.equal(lock.lock, null)
  })

  test('壊れたロックは、古いロックとして退かす', () => {
    const lock = fakeLock('broken')
    assert.deepEqual(acquireOnce({ io: lock.io, owner: me, isRunning: alive(100, 200) }), { action: 'retry' })
    assert.deepEqual(lock.events, ['move', 'discard'])
  })

  test('退かした中身が判定に使った中身と違えば（他方が先に取り直した）、元に戻して判定からやり直す', () => {
    const lock = fakeLock(formatLock(other), {
      beforeMove: (state) => {
        // 他方が古いロックを退かして、自分のロックを書いた
        state.lock = formatLock(third)
      },
    })
    assert.deepEqual(acquireOnce({ io: lock.io, owner: me, isRunning: alive(300) }), { action: 'retry' })
    assert.deepEqual(lock.events, ['move', 'restore'])
    assert.equal(lock.lock, formatLock(third))
  })

  test('退かす前にロックが消えていれば、判定からやり直す', () => {
    const lock = fakeLock(formatLock(other), { beforeMove: (state) => (state.lock = null) })
    assert.deepEqual(acquireOnce({ io: lock.io, owner: me, isRunning: alive() }), { action: 'retry' })
    assert.deepEqual(lock.events, [])
  })
})

describe('スロットのロックの取得（待つ）', () => {
  /** 時計を進める sleep */
  const clock = () => {
    const c = { now: 0 }
    c.sleep = async (ms) => {
      c.now += ms
    }
    return c
  }

  test('古いロックを取り除いてから取る', async () => {
    const lock = fakeLock(formatLock(other))
    const c = clock()
    const result = await acquireLock({ io: lock.io, owner: me, isRunning: alive(), waitMs: 1000, now: () => c.now, sleep: c.sleep })
    assert.deepEqual(result, { acquired: true })
    assert.equal(lock.lock, formatLock(me))
  })

  test('持ち主が動いている間は待ち、上限を過ぎたら持ち主を返す', async () => {
    const lock = fakeLock(formatLock(other))
    const c = clock()
    const result = await acquireLock({ io: lock.io, owner: me, isRunning: alive(200), waitMs: 1000, now: () => c.now, sleep: c.sleep })
    assert.deepEqual(result, { acquired: false, owner: other })
    assert.ok(c.now > 1000)
    assert.equal(lock.lock, formatLock(other))
  })

  test('持ち主が放せば、待っている側が取る', async () => {
    const lock = fakeLock(formatLock(other))
    const c = clock()
    const sleep = async (ms) => {
      await c.sleep(ms)
      if (c.now >= 400) lock.lock = null
    }
    const result = await acquireLock({ io: lock.io, owner: me, isRunning: alive(200), waitMs: 1000, now: () => c.now, sleep })
    assert.deepEqual(result, { acquired: true })
  })
})

describe('スロットのロックの持ち主', () => {
  test('自分の PID と開始の時刻のロックだけを自分のものとする', () => {
    assert.equal(ownsLock(formatLock(me), me), true)
    assert.equal(ownsLock(formatLock(other), me), false)
    assert.equal(ownsLock(formatLock({ ...me, startedAt: other.startedAt }), me), false)
    assert.equal(ownsLock(null, me), false)
    assert.equal(ownsLock('broken', me), false)
  })
})
