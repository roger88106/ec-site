// port-stop.mjs のテスト（node --test scripts/dev/lib/port-stop.test.mjs）
import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findListeners, parseListeners, portBusyMessage, queryFailedMessage, stopFailedMessage, stopListeners, stoppedMessage } from './port-stop.mjs'

/** spawnSync の代わり。呼ばれた指令と引数を記録し、決めた出力を返す */
function fakeSpawnSync(stdout = '') {
  const calls = []
  const spawnSync = (command, args) => {
    calls.push([command, args])
    return { status: 0, stdout }
  }
  return { calls, spawnSync }
}

describe('待ち受けるプロセスの出力の読み方（Windows）', () => {
  const ports = [18080, 19080]

  test('待ち受けの行だけを読み、接続中や TIME_WAIT の行を読まない', () => {
    const text = ['18080 1234 Listen', '18080 5555 Established', '19080 0 TimeWait', '19080 7777 CloseWait'].join('\r\n')
    assert.deepEqual(parseListeners(text, { platform: 'win32', ports, ownPid: 1 }), [{ port: 18080, pid: 1234 }])
  })

  test('スロットのポート以外の行を読まない', () => {
    const text = ['8080 1111 Listen', '28080 2222 Listen', '19080 3333 Listen'].join('\r\n')
    assert.deepEqual(parseListeners(text, { platform: 'win32', ports, ownPid: 1 }), [{ port: 19080, pid: 3333 }])
  })

  test('自分のプロセスと ID 0 の行を読まない', () => {
    const text = ['18080 0 Listen', '19080 42 Listen'].join('\r\n')
    assert.deepEqual(parseListeners(text, { platform: 'win32', ports, ownPid: 42 }), [])
  })

  test('IPv4 と IPv6 で同じポートとプロセスが 2 行あっても、1 つにする', () => {
    const text = ['18080 1234 Listen', '18080 1234 Listen', '19080 1234 Listen'].join('\r\n')
    assert.deepEqual(parseListeners(text, { platform: 'win32', ports, ownPid: 1 }), [
      { port: 18080, pid: 1234 },
      { port: 19080, pid: 1234 },
    ])
  })

  test('出力の順によらず、渡したポートの順に並べる', () => {
    const text = ['19080 1234 Listen', '18080 1234 Listen'].join('\r\n')
    assert.deepEqual(parseListeners(text, { platform: 'win32', ports, ownPid: 1 }), [
      { port: 18080, pid: 1234 },
      { port: 19080, pid: 1234 },
    ])
  })

  test('空の出力や形の違う行からは、何も読まない', () => {
    assert.deepEqual(parseListeners('', { platform: 'win32', ports, ownPid: 1 }), [])
    assert.deepEqual(parseListeners('警告：何か\r\n18080 abc Listen', { platform: 'win32', ports, ownPid: 1 }), [])
  })
})

describe('待ち受けるプロセスの出力の読み方（Windows 以外の lsof）', () => {
  const ports = [18080, 19080]

  test('プロセスの行とアドレスの行を組にして、待ち受けのアドレスだけを読む', () => {
    const text = ['p1234', 'f12', 'n*:18080', 'f13', 'n[::1]:19080', 'p5555', 'f9', 'n127.0.0.1:18080->127.0.0.1:50000'].join('\n')
    assert.deepEqual(parseListeners(text, { platform: 'linux', ports, ownPid: 1 }), [
      { port: 18080, pid: 1234 },
      { port: 19080, pid: 1234 },
    ])
  })

  test('スロットのポート以外と、自分のプロセスを読まない', () => {
    const text = ['p42', 'n*:18080', 'p777', 'n*:8080', 'p888', 'n*:19080'].join('\n')
    assert.deepEqual(parseListeners(text, { platform: 'darwin', ports, ownPid: 42 }), [{ port: 19080, pid: 888 }])
  })
})

describe('待ち受けるプロセスの探し方', () => {
  test('Windows では、PowerShell の Get-NetTCPConnection に渡したポートだけを問い合わせる', () => {
    const { calls, spawnSync } = fakeSpawnSync('18080 1234 Listen\r\n')
    const result = findListeners([18080, 19080], { platform: 'win32', spawnSync, ownPid: 1 })
    assert.deepEqual(result, { ok: true, listeners: [{ port: 18080, pid: 1234 }] })
    assert.equal(calls.length, 1)
    const [command, args] = calls[0]
    assert.equal(command, 'powershell')
    const script = args.at(-1)
    assert.match(script, /Get-NetTCPConnection -LocalPort 18080,19080 -ErrorAction Stop /)
  })

  test('Windows では、一致する接続がない誤り（ObjectNotFound）だけを待ち受けなしとし、他の誤りを隠さない', () => {
    const { calls, spawnSync } = fakeSpawnSync()
    findListeners([18080], { platform: 'win32', spawnSync, ownPid: 1 })
    const script = calls[0][1].at(-1)
    assert.doesNotMatch(script, /SilentlyContinue/)
    assert.match(script, /catch \{ if \(\$_\.CategoryInfo\.Category -eq 'ObjectNotFound'\) \{ exit 0 \}/)
    assert.match(script, /exit 1 \}$/)
  })

  test('Windows 以外では、lsof に渡したポートの待ち受けだけを問い合わせる', () => {
    const { calls, spawnSync } = fakeSpawnSync('p1234\nn*:19080\n')
    const result = findListeners([18080, 19080], { platform: 'linux', spawnSync, ownPid: 1 })
    assert.deepEqual(result, { ok: true, listeners: [{ port: 19080, pid: 1234 }] })
    assert.deepEqual(calls, [['lsof', ['-nP', '-w', '-sTCP:LISTEN', '-Fpn', '-iTCP:18080', '-iTCP:19080']]])
  })

  test('lsof が終了コード 1 で終わり、誤りの出力がない（一致なし）ときは、待ち受けなしとする', () => {
    const spawnSync = () => ({ status: 1, stdout: '', stderr: '' })
    assert.deepEqual(findListeners([18080], { platform: 'linux', spawnSync, ownPid: 1 }), { ok: true, listeners: [] })
  })

  test('lsof が終了コード 1 で終わり、誤りの出力があるときは、待ち受けなしとせず、失敗を返す', () => {
    const spawnSync = () => ({ status: 1, stdout: '', stderr: 'lsof: status error on /proc: Permission denied\n' })
    const result = findListeners([18080], { platform: 'linux', spawnSync, ownPid: 1 })
    assert.equal(result.ok, false)
    assert.match(result.reason, /Permission denied/)
  })

  test('問い合わせの指令を流せないときは、待ち受けなしとせず、失敗を返す', () => {
    const spawnSync = () => ({ status: null, stdout: null, error: new Error('spawnSync lsof ENOENT') })
    const result = findListeners([18080], { platform: 'linux', spawnSync, ownPid: 1 })
    assert.equal(result.ok, false)
    assert.match(result.reason, /ENOENT/)
  })

  for (const [platform, status] of [
    ['win32', 1],
    ['linux', 2],
  ]) {
    test(`${platform} で問い合わせの指令が終了コード ${status} で終わったときは、失敗を返す`, () => {
      const spawnSync = () => ({ status, stdout: '', stderr: 'アクセスが拒否された' })
      const result = findListeners([18080], { platform, spawnSync, ownPid: 1 })
      assert.equal(result.ok, false)
      assert.match(result.reason, /アクセスが拒否された/)
    })
  }
})

describe('待ち受けるプロセスの停止', () => {
  test('Windows では、プロセスごとに 1 回だけ taskkill でツリーごと強制終了し、終わるのを待つ', () => {
    const { calls, spawnSync } = fakeSpawnSync()
    const kills = []
    stopListeners(
      [
        { port: 18080, pid: 1234 },
        { port: 19080, pid: 1234 },
        { port: 13000, pid: 5678 },
      ],
      { platform: 'win32', spawnSync, kill: (pid, signal) => kills.push([pid, signal]) },
    )
    assert.deepEqual(calls.slice(0, 2), [
      ['taskkill', ['/pid', '1234', '/T', '/F']],
      ['taskkill', ['/pid', '5678', '/T', '/F']],
    ])
    assert.equal(calls.length, 3)
    assert.equal(calls[2][0], 'powershell')
    assert.match(calls[2][1].at(-1), /Wait-Process -Id 1234,5678 /)
    assert.deepEqual(kills, [])
  })

  test('Windows 以外では、プロセスごとに 1 回だけ SIGKILL を送る', () => {
    const { calls, spawnSync } = fakeSpawnSync()
    const kills = []
    stopListeners(
      [
        { port: 18080, pid: 1234 },
        { port: 19080, pid: 1234 },
      ],
      { platform: 'linux', spawnSync, kill: (pid, signal) => kills.push([pid, signal]) },
    )
    assert.deepEqual(kills, [[1234, 'SIGKILL']])
    assert.deepEqual(calls, [])
  })

  test('すでに終わっていて kill が失敗しても、例外を投げない', () => {
    const kill = () => {
      throw Object.assign(new Error('kill ESRCH'), { code: 'ESRCH' })
    }
    assert.doesNotThrow(() => stopListeners([{ port: 18080, pid: 1234 }], { platform: 'linux', spawnSync: () => ({}), kill }))
  })

  for (const platform of ['win32', 'linux']) {
    test(`待ち受けるプロセスがなければ、${platform} でも何もしない`, () => {
      const { calls, spawnSync } = fakeSpawnSync()
      const kills = []
      stopListeners([], { platform, spawnSync, kill: (pid, signal) => kills.push([pid, signal]) })
      assert.deepEqual(calls, [])
      assert.deepEqual(kills, [])
    })
  }
})

describe('示す文', () => {
  const listeners = [
    { port: 18080, pid: 1234 },
    { port: 19080, pid: 1234 },
    { port: 13000, pid: 5678 },
  ]

  test('停止したときは、プロセスとポートを 1 行で示す', () => {
    const message = stoppedMessage(listeners)
    assert.equal(message.includes('\n'), false)
    assert.match(message, /1234（ポート 18080、19080）/)
    assert.match(message, /5678（ポート 13000）/)
  })

  test('ポートが使われているときは、プロセスの ID と、release と手動の停止の指令を示す（Windows）', () => {
    const message = portBusyMessage(listeners, { platform: 'win32', canRelease: true })
    assert.match(message, /1234（ポート 18080、19080）/)
    assert.match(message, /node scripts\/dev\/slot\.mjs release/)
    assert.match(message, /Stop-Process -Id 1234,5678 -Force/)
  })

  test('ポートが使われているときの手動の停止の指令は、Windows 以外では kill', () => {
    assert.match(portBusyMessage(listeners, { platform: 'linux', canRelease: true }), /kill 1234 5678/)
  })

  test('停止しても待ち受けが残ったときは、プロセスの ID と手動の停止の指令、release の流し直しを示す', () => {
    const message = stopFailedMessage(listeners, 'win32')
    assert.match(message, /5678（ポート 13000）/)
    assert.match(message, /Stop-Process -Id 1234,5678 -Force/)
    assert.match(message, /もう一度 node scripts\/dev\/slot\.mjs release/)
  })

  test('問い合わせが失敗したときは、理由と、ポートで探して止める指令を示す（Windows）', () => {
    const message = queryFailedMessage([18080, 19080], 'ENOENT', 'win32')
    assert.match(message, /ENOENT/)
    assert.match(message, /Stop-Process -Id \(Get-NetTCPConnection -LocalPort 18080,19080 -State Listen\)\.OwningProcess -Force/)
  })

  test('問い合わせが失敗したときの止める指令は、Windows 以外では lsof と kill', () => {
    assert.match(queryFailedMessage([18080, 19080], 'ENOENT', 'linux'), /kill \$\(lsof -t -sTCP:LISTEN -iTCP:18080 -iTCP:19080\)/)
  })

  test('解除できない（メインの作業ツリーの）ときは、release を示さず、手動の停止の指令だけを示す', () => {
    const message = portBusyMessage(listeners, { platform: 'win32', canRelease: false })
    assert.doesNotMatch(message, /release/)
    assert.match(message, /Stop-Process -Id 1234,5678 -Force/)
  })
})
