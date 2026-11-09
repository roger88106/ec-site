// slot-ports.mjs のテスト（node --test scripts/dev/lib/slot-ports.test.mjs）
import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { checkBackendPorts, releaseSlot, stopSlot, stopTargetPorts } from './slot-ports.mjs'

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

describe('停止の対象のポート', () => {
  test('backend はバックエンドと運用のポート', () => {
    assert.deepEqual(stopTargetPorts(1, ['backend']), { ports: [18080, 19080] })
  })

  test('store は店舗画面、admin は管理画面のポート', () => {
    assert.deepEqual(stopTargetPorts(1, ['store']), { ports: [13000] })
    assert.deepEqual(stopTargetPorts(1, ['admin']), { ports: [15173] })
  })

  test('省略したときは 4 つ全て', () => {
    assert.deepEqual(stopTargetPorts(1, []), { ports: PORTS })
  })

  test('スロット 0 は既定のポート', () => {
    assert.deepEqual(stopTargetPorts(0, []), { ports: [8080, 9080, 3000, 5173] })
  })

  test('知らない対象は、対象を示す理由を返す', () => {
    const result = stopTargetPorts(1, ['api'])
    assert.equal(result.ports, undefined)
    assert.match(result.error, /backend、store、admin のどれか：api/)
  })

  test('対象が 2 つ以上なら、黙って無視せず、理由を返す', () => {
    const result = stopTargetPorts(1, ['store', 'admin'])
    assert.equal(result.ports, undefined)
    assert.match(result.error, /1 つだけ/)
    assert.match(result.error, /store admin/)
  })
})

describe('スロットのプロセスの停止', () => {
  /**
   * 停止の依存を差し替える。find は呼ばれるたびに answers を順に返す。
   *
   * @param {object[]} answers 問い合わせの結果の並び
   */
  function stopDeps(answers) {
    const calls = { find: [], stop: [], log: [] }
    let index = 0
    return {
      calls,
      deps: {
        ports: PORTS,
        platform: 'win32',
        find: (ports) => {
          calls.find.push(ports)
          return answers[index++]
        },
        stop: (listeners) => calls.stop.push(listeners),
        log: (line) => calls.log.push(line),
      },
    }
  }

  test('待ち受けがなければ、止めずに、ないことを示して 0', () => {
    const { calls, deps } = stopDeps([{ ok: true, listeners: [] }])
    assert.deepEqual(stopSlot(deps), { code: 0 })
    assert.deepEqual(calls.stop, [])
    assert.equal(calls.log.length, 1)
    assert.match(calls.log[0], /待ち受けるプロセスはない/)
  })

  test('待ち受けがあれば、止め、止めたことを示し、もう一度問い合わせて残りがなければ 0', () => {
    const listeners = [{ port: 18080, pid: 1234 }]
    const { calls, deps } = stopDeps([{ ok: true, listeners }, { ok: true, listeners: [] }])
    assert.deepEqual(stopSlot(deps), { code: 0 })
    assert.deepEqual(calls.stop, [listeners])
    assert.equal(calls.log.length, 1)
    assert.match(calls.log[0], /1234（ポート 18080）/)
    assert.deepEqual(calls.find, [PORTS, PORTS])
  })

  test('最初の問い合わせが失敗したら、止めずに 1 と、理由と手で止めるコマンドを示す', () => {
    const { calls, deps } = stopDeps([{ ok: false, reason: 'lsof がない' }])
    const result = stopSlot(deps)
    assert.equal(result.code, 1)
    assert.match(result.message, /lsof がない/)
    assert.match(result.message, /Stop-Process/)
    assert.deepEqual(calls.stop, [])
  })

  test('止めた後の問い合わせが失敗したら 1', () => {
    const { deps } = stopDeps([{ ok: true, listeners: [{ port: 18080, pid: 1234 }] }, { ok: false, reason: '誤り' }])
    const result = stopSlot(deps)
    assert.equal(result.code, 1)
    assert.match(result.message, /誤り/)
  })

  test('止めた後も残れば 1 と、残ったプロセスと手で止めるコマンドを示し、release の文にしない', () => {
    const { deps } = stopDeps([
      { ok: true, listeners: [{ port: 18080, pid: 1234 }] },
      { ok: true, listeners: [{ port: 18080, pid: 5678 }] },
    ])
    const result = stopSlot(deps)
    assert.equal(result.code, 1)
    assert.match(result.message, /5678（ポート 18080）/)
    assert.match(result.message, /Stop-Process -Id 5678 -Force/)
    assert.doesNotMatch(result.message, /release|解かない/)
  })
})

describe('バックエンドの起動の前のポートの確認', () => {
  const ports = [18080, 19080]

  test('ポートが空いていれば、警告なしで起動する', () => {
    assert.deepEqual(checkBackendPorts({ ports, platform: 'win32', find: () => ({ ok: true, listeners: [] }) }), {
      start: true,
      warning: null,
    })
  })

  test('ポートが使われていれば、起動せず、終了コード 1 でプロセスの ID と止めるコマンドを示す', () => {
    const result = checkBackendPorts({
      ports,
      platform: 'win32',
      find: () => ({ ok: true, listeners: [{ port: 19080, pid: 4321 }] }),
    })
    assert.equal(result.start, false)
    assert.equal(result.code, 1)
    assert.match(result.message, /4321（ポート 19080）/)
    assert.match(result.message, /node scripts\/dev\/slot\.mjs stop backend/)
    assert.doesNotMatch(result.message, /release/)
    assert.match(result.message, /Stop-Process -Id 4321 -Force/)
  })

  test('問い合わせが失敗したら、理由を警告にして起動する', () => {
    const result = checkBackendPorts({ ports, platform: 'linux', find: () => ({ ok: false, reason: 'lsof がない' }) })
    assert.equal(result.start, true)
    assert.match(result.warning, /lsof がない/)
    assert.match(result.warning, /確かめずに起動する/)
  })

  test('問い合わせるのは渡したポートだけ', () => {
    const asked = []
    checkBackendPorts({ ports, platform: 'win32', find: (p) => (asked.push(p), { ok: true, listeners: [] }) })
    assert.deepEqual(asked, [ports])
  })
})
