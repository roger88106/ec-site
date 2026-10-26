// slot-lock.mjs のテスト（node --test scripts/dev/lib/slot-lock.test.mjs）
import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { formatLock } from './e2e-lock.mjs'
import { acquireLock, acquireOnce, busyLockMessage, ownsLock } from './slot-lock.mjs'

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

  test('所有者の PID のプロセスがあれば、時間が経っていても取らず、所有者を返す', () => {
    const lock = fakeLock(formatLock(other))
    assert.deepEqual(acquireOnce({ io: lock.io, owner: me, isRunning: alive(200) }), { action: 'busy', owner: other })
    assert.equal(lock.lock, formatLock(other))
    assert.deepEqual(lock.events, [])
  })

  test('所有者の PID のプロセスがなければ、退かして捨て、取り直す', () => {
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

  test('所有者が動いている間は待ち、上限を過ぎたら所有者を返す', async () => {
    const lock = fakeLock(formatLock(other))
    const c = clock()
    const result = await acquireLock({ io: lock.io, owner: me, isRunning: alive(200), waitMs: 1000, now: () => c.now, sleep: c.sleep })
    assert.deepEqual(result, { acquired: false, owner: other })
    assert.ok(c.now >= 1000)
    assert.equal(lock.lock, formatLock(other))
  })

  test('所有者が放せば、待っている側が取る', async () => {
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

describe('スロットのロックの所有者', () => {
  test('自分の PID と開始の時刻のロックだけを自分のものとする', () => {
    assert.equal(ownsLock(formatLock(me), me), true)
    assert.equal(ownsLock(formatLock(other), me), false)
    assert.equal(ownsLock(formatLock({ ...me, startedAt: other.startedAt }), me), false)
    assert.equal(ownsLock(null, me), false)
    assert.equal(ownsLock('broken', me), false)
  })
})

describe('ロックを取れないときの待ちと案内', () => {
  /** 古いロックをいつまでも退かせない io（Windows で他方が読み続けているときなど） */
  const stuckIo = () => ({
    create: () => false,
    read: () => formatLock(other),
    moveAside: () => null,
    restore: () => {},
    discard: () => {},
  })

  test('古いロックを退かせない状態が続いても、間を置いて待ち、上限を過ぎたら所有者なしで終わる', async () => {
    const c = { now: 0 }
    const sleep = async (ms) => {
      c.now += ms
    }
    const result = await acquireLock({ io: stuckIo(), owner: me, isRunning: alive(), waitMs: 1000, now: () => c.now, sleep })
    assert.deepEqual(result, { acquired: false, owner: null })
    assert.ok(c.now >= 1000)
  })

  test('上限が 0 でも、古いロックを取り除いた直後の取り直しはできる', async () => {
    const lock = fakeLock(formatLock(other))
    const sleep = async () => assert.fail('待ってはいけない')
    const result = await acquireLock({ io: lock.io, owner: me, isRunning: alive(), waitMs: 0, now: () => 0, sleep })
    assert.deepEqual(result, { acquired: true })
  })

  test('上限が 0 で古いロックを退かせない状態が続くときも、終わる', async () => {
    const c = { now: 0 }
    const sleep = async (ms) => {
      c.now += ms
    }
    const result = await acquireLock({ io: stuckIo(), owner: me, isRunning: alive(), waitMs: 0, now: () => c.now, sleep })
    assert.deepEqual(result, { acquired: false, owner: null })
  })

  test('上限が 0 なら、所有者が動いているとき待たずに所有者を返す', async () => {
    const lock = fakeLock(formatLock(other))
    const sleep = async () => assert.fail('待ってはいけない')
    const result = await acquireLock({ io: lock.io, owner: me, isRunning: alive(200), waitMs: 0, now: () => 0, sleep })
    assert.deepEqual(result, { acquired: false, owner: other })
  })

  test('sleep を渡さないときは、既定の待ちで間隔を置く', async (t) => {
    t.mock.timers.enable({ apis: ['setTimeout'] })
    const lock = fakeLock(formatLock(other))
    const c = { now: 0 }
    let done = false
    const waiting = acquireLock({ io: lock.io, owner: me, isRunning: alive(200), waitMs: 1000, now: () => c.now, intervalMs: 200 })
    waiting.then(() => {
      done = true
    })
    // 待ちが setTimeout で置かれたら、時計と一緒に進める
    for (let i = 0; i < 20 && !done; i++) {
      await new Promise((resolve) => setImmediate(resolve))
      c.now += 200
      t.mock.timers.tick(200)
    }
    assert.deepEqual(await waiting, { acquired: false, owner: other })
    assert.ok(c.now >= 1000)
  })

  test('案内は、所有者、ロックのファイルのパス、所有者のプロセスがなければ削除してよいことを示す', () => {
    const message = busyLockMessage({ lock: '/repo/.git/x.lock', owner: other })
    assert.match(message, /\/wt\/other/)
    assert.match(message, /PID 200/)
    assert.match(message, /\/repo\/\.git\/x\.lock/)
    assert.match(message, /プロセスがなければ削除してよい/)
  })

  test('所有者が分からないときも、ロックのファイルのパスと削除の案内を示す', () => {
    const message = busyLockMessage({ lock: '/repo/.git/x.lock', owner: null })
    assert.match(message, /\/repo\/\.git\/x\.lock/)
    assert.match(message, /削除してよい/)
  })
})
