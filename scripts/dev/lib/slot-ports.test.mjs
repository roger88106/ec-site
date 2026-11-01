// slot-ports.mjs のテスト（node --test scripts/dev/lib/slot-ports.test.mjs）
import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { checkBackendPorts, releaseSlot } from './slot-ports.mjs'

/** スロット 1 の 4 つのポート */
const PORTS = [18080, 19080, 13000, 15173]

/**
 * 解除の依存を差し替える。find は呼ばれるたびに answers を順に返し、他は呼ばれたことを記録する。
 *
 * @param {{ answers: object[], running?: boolean }} options 問い合わせの結果の並びと、health が応答するか
 */
function releaseDeps({ answers, running = false }) {
  const calls = { find: [], stop: [], backendRunning: 0, drop: 0, log: [] }
  let index = 0
  return {
    calls,
    deps: {
      slot: 1,
      ports: PORTS,
      platform: 'win32',
      find: (ports) => {
        calls.find.push(ports)
        return answers[index++]
      },
      stop: (listeners) => calls.stop.push(listeners),
      backendRunning: async () => {
        calls.backendRunning++
        return running
      },
      drop: () => {
        calls.drop++
      },
      log: (line) => calls.log.push(line),
    },
  }
}

describe('スロットの解除', () => {
  test('待ち受けるプロセスがなく、バックエンドが応答しなければ、何も止めずにデータを消す', async () => {
    const { calls, deps } = releaseDeps({ answers: [{ ok: true, listeners: [] }] })
    assert.deepEqual(await releaseSlot(deps), { code: 0 })
    assert.deepEqual(calls.find, [PORTS])
    assert.deepEqual(calls.stop, [])
    assert.equal(calls.backendRunning, 1)
    assert.equal(calls.drop, 1)
  })

  test('待ち受けるプロセスがあれば止め、止めたことを 1 行で示し、空いたことを確認してからデータを消す', async () => {
    const listeners = [{ port: 18080, pid: 1234 }]
    const { calls, deps } = releaseDeps({ answers: [{ ok: true, listeners }, { ok: true, listeners: [] }] })
    assert.deepEqual(await releaseSlot(deps), { code: 0 })
    assert.deepEqual(calls.stop, [listeners])
    assert.equal(calls.log.length, 1)
    assert.match(calls.log[0], /1234（ポート 18080）/)
    assert.deepEqual(calls.find, [PORTS, PORTS])
    assert.equal(calls.drop, 1)
  })

  test('最初の問い合わせが失敗したら、止めず、データを消さずに終了コード 1 で終わり、止めるコマンドを示す', async () => {
    const { calls, deps } = releaseDeps({ answers: [{ ok: false, reason: 'lsof がない' }] })
    const result = await releaseSlot(deps)
    assert.equal(result.code, 1)
    assert.match(result.message, /lsof がない/)
    assert.match(result.message, /Stop-Process/)
    assert.deepEqual(calls.stop, [])
    assert.equal(calls.drop, 0)
  })

  test('止めた後の問い合わせが失敗したら、データを消さずに終了コード 1 で終わる', async () => {
    const { calls, deps } = releaseDeps({
      answers: [{ ok: true, listeners: [{ port: 18080, pid: 1234 }] }, { ok: false, reason: '誤り' }],
    })
    const result = await releaseSlot(deps)
    assert.equal(result.code, 1)
    assert.match(result.message, /誤り/)
    assert.equal(calls.drop, 0)
  })

  test('止めた後も待ち受けが残れば、データを消さずに終了コード 1 で終わり、残ったプロセスを示す', async () => {
    const { calls, deps } = releaseDeps({
      answers: [
        { ok: true, listeners: [{ port: 18080, pid: 1234 }] },
        { ok: true, listeners: [{ port: 18080, pid: 5678 }] },
      ],
    })
    const result = await releaseSlot(deps)
    assert.equal(result.code, 1)
    assert.match(result.message, /5678（ポート 18080）/)
    assert.equal(calls.drop, 0)
  })

  test('待ち受けがなくても、バックエンドが応答すれば、データを消さずに終了コード 1 で終わる', async () => {
    const { calls, deps } = releaseDeps({ answers: [{ ok: true, listeners: [] }], running: true })
    const result = await releaseSlot(deps)
    assert.equal(result.code, 1)
    assert.match(result.message, /スロット 1 のバックエンド/)
    assert.equal(calls.drop, 0)
  })

  test('止めて待ち受けが空いても、バックエンドが応答すれば、データを消さずに終了コード 1 で終わる', async () => {
    const { calls, deps } = releaseDeps({
      answers: [{ ok: true, listeners: [{ port: 18080, pid: 1234 }] }, { ok: true, listeners: [] }],
      running: true,
    })
    const result = await releaseSlot(deps)
    assert.equal(result.code, 1)
    assert.equal(calls.backendRunning, 1)
    assert.equal(calls.drop, 0)
  })
})

describe('バックエンドの起動の前のポートの確認', () => {
  const ports = [18080, 19080]

  test('ポートが空いていれば、警告なしで起動する', () => {
    assert.deepEqual(checkBackendPorts({ ports, platform: 'win32', canRelease: true, find: () => ({ ok: true, listeners: [] }) }), {
      start: true,
      warning: null,
    })
  })

  test('ポートが使われていれば、起動せず、終了コード 1 でプロセスの ID と止めるコマンドを示す', () => {
    const result = checkBackendPorts({
      ports,
      platform: 'win32',
      canRelease: true,
      find: () => ({ ok: true, listeners: [{ port: 19080, pid: 4321 }] }),
    })
    assert.equal(result.start, false)
    assert.equal(result.code, 1)
    assert.match(result.message, /4321（ポート 19080）/)
    assert.match(result.message, /node scripts\/dev\/slot\.mjs release/)
    assert.match(result.message, /Stop-Process -Id 4321 -Force/)
  })

  test('問い合わせが失敗したら、理由を警告にして起動する', () => {
    const result = checkBackendPorts({ ports, platform: 'linux', canRelease: true, find: () => ({ ok: false, reason: 'lsof がない' }) })
    assert.equal(result.start, true)
    assert.match(result.warning, /lsof がない/)
    assert.match(result.warning, /確かめずに起動する/)
  })

  test('問い合わせるのは渡したポートだけ', () => {
    const asked = []
    checkBackendPorts({ ports, platform: 'win32', canRelease: false, find: (p) => (asked.push(p), { ok: true, listeners: [] }) })
    assert.deepEqual(asked, [ports])
  })
})
