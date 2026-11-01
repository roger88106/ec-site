// child-stop.mjs のテスト（node --test scripts/dev/lib/child-stop.test.mjs）
import assert from 'node:assert/strict'
import { EventEmitter } from 'node:events'
import { describe, test } from 'node:test'
import { STOP_SIGNALS, stopChild, stopOnSignals } from './child-stop.mjs'

/** 子のプロセスの代わり。kill で受けたシグナルを記録する */
function fakeChild(pid) {
  const kills = []
  return { pid, kills, kill: (signal) => kills.push(signal) }
}

/** spawnSync の代わり。呼ばれたコマンドと引数を記録する */
function fakeSpawnSync() {
  const calls = []
  const spawnSync = (command, args) => {
    calls.push([command, args])
    return { status: 0 }
  }
  return { calls, spawnSync }
}

describe('子のプロセスの停止', () => {
  test('Windows では、taskkill で子と子孫を強制終了し、シグナルを送らない', () => {
    const child = fakeChild(1234)
    const { calls, spawnSync } = fakeSpawnSync()
    stopChild(child, 'SIGINT', { platform: 'win32', spawnSync })
    assert.deepEqual(calls, [['taskkill', ['/pid', '1234', '/T', '/F']]])
    assert.deepEqual(child.kills, [])
  })

  test('Windows 以外では、受けたシグナルを子に送り、taskkill を呼ばない', () => {
    const child = fakeChild(1234)
    const { calls, spawnSync } = fakeSpawnSync()
    stopChild(child, 'SIGTERM', { platform: 'linux', spawnSync })
    assert.deepEqual(child.kills, ['SIGTERM'])
    assert.deepEqual(calls, [])
  })

  test('taskkill が失敗しても（子がすでに終わっていても）、例外を投げない', () => {
    const child = fakeChild(1234)
    const spawnSync = () => ({ status: 128 })
    assert.doesNotThrow(() => stopChild(child, 'SIGINT', { platform: 'win32', spawnSync }))
  })

  for (const platform of ['win32', 'linux']) {
    test(`pid がない（起動できなかった）子には、${platform} でも何もしない`, () => {
      const child = fakeChild(undefined)
      const { calls, spawnSync } = fakeSpawnSync()
      stopChild(child, 'SIGINT', { platform, spawnSync })
      assert.deepEqual(calls, [])
      assert.deepEqual(child.kills, [])
    })
  }
})

describe('停止のシグナルを受けたときの止め方', () => {
  /** シグナルの受け取りを差し替えて、止め方を組み立てる */
  function setup(current) {
    const proc = new EventEmitter()
    const stops = []
    const exits = []
    const state = stopOnSignals(
      { current: () => current.child, stop: (child, signal) => stops.push([child, signal]), exit: (code) => exits.push(code) },
      proc,
    )
    return { proc, stops, exits, state }
  }

  test('受け取るシグナルは SIGINT、SIGTERM、SIGHUP', () => {
    assert.deepEqual(STOP_SIGNALS, ['SIGINT', 'SIGTERM', 'SIGHUP'])
    const { proc } = setup({ child: fakeChild(1234) })
    for (const signal of STOP_SIGNALS) assert.equal(proc.listenerCount(signal), 1)
  })

  for (const signal of ['SIGINT', 'SIGTERM', 'SIGHUP']) {
    test(`1 回目の ${signal} では、実行中の子を同じシグナルで停止し、終了せずに子が終わるのを待つ`, () => {
      const child = fakeChild(1234)
      const { proc, stops, exits, state } = setup({ child })
      assert.equal(state.isStopping(), false)
      proc.emit(signal)
      assert.deepEqual(stops, [[child, signal]])
      assert.deepEqual(exits, [])
      assert.equal(state.isStopping(), true)
    })
  }

  test('2 回目のシグナルでは、子の終わりを待たずに終了コード 1 で終了する', () => {
    const child = fakeChild(1234)
    const { proc, stops, exits } = setup({ child })
    proc.emit('SIGINT')
    proc.emit('SIGTERM')
    assert.equal(stops.length, 1)
    assert.deepEqual(exits, [1])
  })

  test('実行中の子がないときは、1 回目のシグナルで終了コード 1 で終了する', () => {
    const { proc, stops, exits } = setup({ child: null })
    proc.emit('SIGINT')
    assert.deepEqual(stops, [])
    assert.deepEqual(exits, [1])
  })
})
