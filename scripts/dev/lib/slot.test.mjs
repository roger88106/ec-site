import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { describe, test } from 'node:test'
import {
  E2E,
  SHARED_CONTAINERS,
  SHARED_PORTS,
  SLOT_COUNT,
  assignSlot,
  checkShared,
  currentSlotOf,
  formatSlotRecord,
  isMainWorktree,
  parseSlotRecord,
  parseWorktrees,
  pickSlot,
  slotEnv,
  slotPorts,
  slotValues,
} from './slot.mjs'

/** git worktree list --porcelain の出力を組み立てる */
const porcelain = (...blocks) => blocks.map((block) => block.join('\n')).join('\n\n') + '\n'

const MAIN = ['worktree /repo', 'HEAD aaa', 'branch refs/heads/develop']
const PRUNABLE = 'prunable gitdir file points to non-existent location'
const worktree = (path, ...extra) => ['worktree ' + path, 'HEAD bbb', 'branch refs/heads/x', ...extra]

/** リポジトリのファイルを読む（lib は純粋なままにし、読み取りはテストで行う） */
const repoFile = (relative) => readFileSync(new URL(`../../../${relative}`, import.meta.url), 'utf8').replace(/\r/g, '')

/** 一覧を小さい順に並べる */
const sorted = (values) => [...values].sort((a, b) => (a < b ? -1 : a > b ? 1 : 0))

/** docker/compose.yaml の ports のホストのポート（`${MAILPIT_SMTP_PORT:-2525}` のような既定値も読む） */
function composeHostPorts() {
  const mappings = repoFile('docker/compose.yaml')
    .split('\n')
    .filter((line) => /^\s*-\s*"\$\{LOCAL_BIND_ADDRESS/.test(line))
  const ports = mappings.map((line) => {
    const match = /^\s*-\s*"\$\{LOCAL_BIND_ADDRESS:-[^}]*\}:(?:\$\{[A-Z_]+:-(\d+)\}|(\d+)):\d+"/.exec(line)
    assert.ok(match, `ホストのポートを読めない：${line}`)
    return Number(match[1] ?? match[2])
  })
  assert.ok(ports.length > 0, 'compose のポートがない')
  return ports
}

/** e2e/src/urls.ts の URL のポート */
function e2eUrlPorts() {
  const ports = [...repoFile('e2e/src/urls.ts').matchAll(/localhost:(\d+)/g)].map((match) => Number(match[1]))
  assert.ok(ports.length > 0, 'e2e/src/urls.ts のポートがない')
  return ports
}

/** application-e2e.yml の中身 */
const e2eYaml = () => repoFile('backend/ecsite/src/main/resources/application-e2e.yml')

/** application-e2e.yml の業務ポートと運用ポート */
function e2eBackendPorts() {
  const server = /^server:\n\s+port:\s*(\d+)/m.exec(e2eYaml())
  const management = /^management:\n\s+server:\n(?:\s+#.*\n)*\s+port:\s*(\d+)/m.exec(e2eYaml())
  assert.ok(server && management, 'application-e2e.yml のポートを読めない')
  return [Number(server[1]), Number(management[1])]
}

/** application-e2e.yml のデータベースの名前 */
function e2eDatabase() {
  const match = /url:\s*jdbc:postgresql:\/\/[^/\s]+\/(\w+)/.exec(e2eYaml())
  assert.ok(match, 'application-e2e.yml のデータベースを読めない')
  return match[1]
}

/** application-e2e.yml のバケット */
function e2eBackendBuckets() {
  const buckets = [...e2eYaml().matchAll(/^\s+(?:incoming|public)-bucket:\s*(\S+)/gm)].map((match) => match[1])
  assert.equal(buckets.length, 2, 'application-e2e.yml のバケットを読めない')
  return buckets
}

/** docker/compose.yaml の E2E のバケット（seaweedfs-init の E2E_*_BUCKET） */
function composeE2eBuckets() {
  const buckets = [...repoFile('docker/compose.yaml').matchAll(/^\s+E2E_[A-Z_]*BUCKET:\s*(\S+)/gm)].map((match) => match[1])
  assert.equal(buckets.length, 2, 'compose の E2E のバケットを読めない')
  return buckets
}

describe('スロットの値', () => {
  test('スロット 0 は今の既定の値で、Cookie の接頭辞がない（DC-104-1）', () => {
    assert.deepEqual(slotValues(0), {
      slot: 0,
      backendPort: 8080,
      managementPort: 9080,
      storePort: 3000,
      adminPort: 5173,
      database: 'ec_site',
      publicBucket: 'ec-site-images',
      incomingBucket: 'ec-site-images-incoming',
      cookiePrefix: '',
    })
  })

  test('スロット 1〜3 は、ポートを 10000 ずつずらし、名前の末尾に番号を付ける（DC-104-10）', () => {
    assert.deepEqual(slotValues(2), {
      slot: 2,
      backendPort: 28080,
      managementPort: 29080,
      storePort: 23000,
      adminPort: 25173,
      database: 'ec_site_2',
      publicBucket: 'ec-site-images-2',
      incomingBucket: 'ec-site-images-incoming-2',
      cookiePrefix: 's2-',
    })
    assert.equal(slotValues(3).adminPort, 35173)
    assert.equal(slotValues(1).database, 'ec_site_1')
  })

  test('スロットの数は 4 で、範囲外の番号は拒む', () => {
    assert.equal(SLOT_COUNT, 4)
    assert.throws(() => slotValues(-1))
    assert.throws(() => slotValues(4))
    assert.throws(() => slotValues(1.5))
    assert.throws(() => slotValues('1'))
  })
})

describe('ポートと名前が重ならないこと', () => {
  const slots = Array.from({ length: SLOT_COUNT }, (_, n) => n)
  const slotPortList = slots.flatMap((n) => slotPorts(n))
  const databases = slots.map((n) => slotValues(n).database)
  const buckets = slots.flatMap((n) => [slotValues(n).publicBucket, slotValues(n).incomingBucket])

  test('全スロットのポートは、互いに重ならない（DC-104-10）', () => {
    assert.equal(new Set(slotPortList).size, slotPortList.length)
  })

  test('全スロットのポートは compose の共有の基盤のホストのポートと重ならず、SHARED_PORTS はそれと一致する（DC-104-10）', () => {
    const shared = composeHostPorts()
    for (const port of slotPortList) assert.ok(!shared.includes(port), String(port))
    assert.deepEqual(sorted(SHARED_PORTS), sorted(shared))
  })

  test('全スロットのポートは E2E の URL とバックエンドのポートと重ならず、E2E.ports はそのうち共有の基盤でないものと一致する（DC-104-10、DC-104-20）', () => {
    const e2e = [...new Set([...e2eUrlPorts(), ...e2eBackendPorts()])]
    for (const port of slotPortList) assert.ok(!e2e.includes(port), String(port))
    const shared = composeHostPorts()
    assert.deepEqual(sorted(E2E.ports), sorted(e2e.filter((port) => !shared.includes(port))))
  })

  test('スロットは 4 つのポートを持つ', () => {
    assert.deepEqual(slotPorts(1), [18080, 19080, 13000, 15173])
  })

  test('全スロットのポートは、Windows の動的なポートの範囲（49152〜）より下に収まる', () => {
    assert.ok(slotPortList.every((port) => port < 49152))
  })

  test('E2E のデータベースは全スロットの名前と重ならず、E2E.database は application-e2e.yml と一致する（DC-104-20）', () => {
    const database = e2eDatabase()
    assert.ok(!databases.includes(database))
    assert.equal(E2E.database, database)
  })

  test('E2E のバケットは全スロットの名前と重ならず、E2E.buckets は compose と application-e2e.yml と一致する（DC-104-20）', () => {
    const fromCompose = composeE2eBuckets()
    for (const bucket of fromCompose) assert.ok(!buckets.includes(bucket), bucket)
    assert.deepEqual(sorted(E2E.buckets), sorted(fromCompose))
    assert.deepEqual(sorted(E2E.buckets), sorted(e2eBackendBuckets()))
    assert.equal(new Set([...databases, ...buckets]).size, databases.length + buckets.length)
  })
})

describe('作業ツリーの一覧の読み取り', () => {
  test('最初をメインの作業ツリーとし、prunable を印す', () => {
    const text = porcelain(MAIN, worktree('/wt/a'), worktree('/wt/gone', PRUNABLE))
    assert.deepEqual(parseWorktrees(text), [
      { path: '/repo', main: true, prunable: false },
      { path: '/wt/a', main: false, prunable: false },
      { path: '/wt/gone', main: false, prunable: true },
    ])
  })

  test('CRLF の出力と、空の出力を読める', () => {
    assert.equal(parseWorktrees(porcelain(MAIN, worktree('/wt/a')).replaceAll('\n', '\r\n')).length, 2)
    assert.deepEqual(parseWorktrees(''), [])
  })

  test('空白を含むパスをそのまま読む', () => {
    assert.equal(parseWorktrees(porcelain(MAIN, worktree('/my wt/a')))[1].path, '/my wt/a')
  })
})

describe('スロットの記録の読み取り', () => {
  test('番号を読み、空白や改行を除く', () => {
    assert.equal(parseSlotRecord('2\n'), 2)
    assert.equal(parseSlotRecord(' 3 \r\n'), 3)
  })

  test('ないもの、範囲外、数字でないものは、割り当てなしにする', () => {
    for (const content of [undefined, null, '', 'x', '0', '4', '-1', '1.5', '12']) {
      assert.equal(parseSlotRecord(content), null, String(content))
    }
  })
})

describe('スロットの記録の書き出し', () => {
  test('番号と改行を書き、読み戻せる（DC-104-3）', () => {
    for (const slot of [1, 2, 3]) {
      assert.equal(formatSlotRecord(slot), `${slot}\n`)
      assert.equal(parseSlotRecord(formatSlotRecord(slot)), slot)
    }
  })

  test('スロット 0 と範囲外は書かない', () => {
    for (const slot of [0, 4, -1, 1.5]) assert.throws(() => formatSlotRecord(slot), String(slot))
  })
})

describe('メインの作業ツリーの判定', () => {
  test('git-dir と git-common-dir が同じなら、メインの作業ツリー（DC-104-1）', () => {
    assert.equal(isMainWorktree('/repo/.git', '/repo/.git'), true)
    assert.equal(isMainWorktree('/repo/.git/', '/repo/sub/../.git'), true)
  })

  test('git-dir が共通のディレクトリの worktrees の下なら、追加の作業ツリー', () => {
    assert.equal(isMainWorktree('/repo/.git/worktrees/a', '/repo/.git'), false)
  })

  test('メインの作業ツリーは記録によらずスロット 0、追加の作業ツリーは記録の番号（DC-104-1、DC-104-3）', () => {
    assert.equal(currentSlotOf({ main: true, record: null }), 0)
    assert.equal(currentSlotOf({ main: true, record: '2\n' }), 0)
    assert.equal(currentSlotOf({ main: false, record: '2\n' }), 2)
    assert.equal(currentSlotOf({ main: false, record: null }), null)
  })
})

describe('共有の基盤の確認', () => {
  test('確かめるのは PostgreSQL と SeaweedFS のコンテナ', () => {
    assert.deepEqual(SHARED_CONTAINERS, ['ec-site-postgres', 'ec-site-seaweedfs'])
  })

  test('全て動いていれば、何も示さない', () => {
    assert.equal(checkShared({ 'ec-site-postgres': true, 'ec-site-seaweedfs': true }, '/repo'), null)
  })

  test('止まっているものがあれば、それとメインの作業ツリーのパスと docker compose up -d を示して、終了コード 2（DC-104-14、DC-104-15）', () => {
    const result = checkShared({ 'ec-site-postgres': true, 'ec-site-seaweedfs': false }, '/repo main')
    assert.equal(result.code, 2)
    assert.deepEqual(result.stopped, ['ec-site-seaweedfs'])
    assert.match(result.message, /ec-site-seaweedfs/)
    assert.doesNotMatch(result.message, /ec-site-postgres/)
    assert.ok(result.message.includes('cd /repo main\n'))
    assert.match(result.message, /docker compose up -d/)
  })

  test('状態の分からない（ない）コンテナは、止まっているものとする', () => {
    assert.deepEqual(checkShared({}, '/repo').stopped, ['ec-site-postgres', 'ec-site-seaweedfs'])
  })
})

describe('割り当ての順序', () => {
  /** 割り当ての依存を真似て、呼ばれた順を記録する */
  function fakeAssign({ main = false, own = null, records = {}, lateOwn = null, failRecreate = false } = {}) {
    const events = []
    const mark = (event) => events.push(inLock ? `${event}（ロックの中）` : event)
    let inLock = false
    let ownRecord = own
    const deps = {
      main,
      readOwnRecord: () => ownRecord,
      listWorktrees: () => {
        mark('list')
        return porcelain(MAIN, worktree('/wt/me'), ...Object.keys(records).map((p) => worktree(p)))
      },
      readRecord: (p) => (p === '/wt/me' ? ownRecord : (records[p] ?? null)),
      withLock: async (action) => {
        events.push('lock')
        inLock = true
        // ロックを待つ間に、同じ作業ツリーの別のセッションが割り当てた
        if (lateOwn !== null) ownRecord = lateOwn
        try {
          return action()
        } finally {
          inLock = false
          events.push('unlock')
        }
      },
      writeRecord: (slot) => {
        mark(`write ${slot}`)
        ownRecord = formatSlotRecord(slot)
      },
      recreate: (slot) => {
        mark(`recreate ${slot}`)
        if (failRecreate) throw new Error('docker が失敗した')
      },
    }
    return { deps, events, record: () => ownRecord }
  }

  test('メインの作業ツリーでは何もしない（DC-104-1）', async () => {
    const f = fakeAssign({ main: true })
    assert.deepEqual(await assignSlot(f.deps), { kind: 'main' })
    assert.deepEqual(f.events, [])
  })

  test('割り当て済みなら、番号を返して何もしない', async () => {
    const f = fakeAssign({ own: '2\n' })
    assert.deepEqual(await assignSlot(f.deps), { kind: 'already', slot: 2 })
    assert.deepEqual(f.events, [])
  })

  test('ロックの中で一覧を読んで空きを選び記録を書き、ロックを放した後にデータを作り直す（DC-104-2、DC-104-5）', async () => {
    const f = fakeAssign({ records: { '/wt/other': '1\n' } })
    assert.deepEqual(await assignSlot(f.deps), { kind: 'assigned', slot: 2 })
    assert.deepEqual(f.events, ['lock', 'list（ロックの中）', 'write 2（ロックの中）', 'unlock', 'recreate 2'])
  })

  test('ロックを待つ間に同じ作業ツリーへ割り当てられていれば、何もしない', async () => {
    const f = fakeAssign({ lateOwn: '3\n' })
    assert.deepEqual(await assignSlot(f.deps), { kind: 'already', slot: 3 })
    assert.deepEqual(f.events, ['lock', 'unlock'])
  })

  test('空きがなければ使っている作業ツリーを返し、記録もデータも作らない（DC-104-4）', async () => {
    const f = fakeAssign({ records: { '/wt/a': '1', '/wt/b': '2', '/wt/c': '3' } })
    const result = await assignSlot(f.deps)
    assert.equal(result.kind, 'full')
    assert.deepEqual(
      result.users.map((user) => user.slot),
      [1, 2, 3],
    )
    assert.deepEqual(f.events, ['lock', 'list（ロックの中）', 'unlock'])
    assert.equal(f.record(), null)
  })

  test('データの作り直しに失敗しても記録を残して失敗を返す（他の割り当てが同じ番号を選ばない）', async () => {
    const f = fakeAssign({ failRecreate: true })
    const result = await assignSlot(f.deps)
    assert.equal(result.kind, 'recreateFailed')
    assert.equal(result.slot, 1)
    assert.match(result.error.message, /docker/)
    assert.equal(f.record(), '1\n')
  })
})

describe('空きのスロットの選び方', () => {
  test('使われていなければ 1 を選ぶ（DC-104-2）', () => {
    const text = porcelain(MAIN, worktree('/wt/a'))
    assert.deepEqual(pickSlot(text, {}), { slot: 1 })
  })

  test('使われている番号を飛ばして、最小の空きを選ぶ（DC-104-2）', () => {
    const text = porcelain(MAIN, worktree('/wt/a'), worktree('/wt/b'), worktree('/wt/c'))
    assert.deepEqual(pickSlot(text, { '/wt/a': '1\n', '/wt/c': '3\n' }), { slot: 2 })
    assert.deepEqual(pickSlot(text, { '/wt/b': '1\n' }), { slot: 2 })
  })

  test('メインの作業ツリーの記録は数えない', () => {
    const text = porcelain(MAIN, worktree('/wt/a'))
    assert.deepEqual(pickSlot(text, { '/repo': '1\n' }), { slot: 1 })
  })

  test('消えた作業ツリー（prunable）の記録は数えず、そのスロットは空きになる（DC-104-7）', () => {
    const text = porcelain(MAIN, worktree('/wt/gone', PRUNABLE), worktree('/wt/a'))
    assert.deepEqual(pickSlot(text, { '/wt/gone': '1\n', '/wt/a': '2\n' }), { slot: 1 })
  })

  test('一覧にない作業ツリーの記録は数えない', () => {
    const text = porcelain(MAIN, worktree('/wt/a'))
    assert.deepEqual(pickSlot(text, { '/removed': '1\n' }), { slot: 1 })
  })

  test('壊れた記録のスロットは空きとして扱う', () => {
    const text = porcelain(MAIN, worktree('/wt/a'))
    assert.deepEqual(pickSlot(text, { '/wt/a': 'x' }), { slot: 1 })
  })

  test('1〜3 が全て使われていれば、使っている作業ツリーのパスを返す（DC-104-4）', () => {
    const text = porcelain(MAIN, worktree('/wt/a'), worktree('/wt/b'), worktree('/wt/c'), worktree('/wt/d'))
    assert.deepEqual(pickSlot(text, { '/wt/a': '1', '/wt/b': '2', '/wt/c': '3' }), {
      full: true,
      users: [
        { slot: 1, path: '/wt/a' },
        { slot: 2, path: '/wt/b' },
        { slot: 3, path: '/wt/c' },
      ],
    })
  })
})

describe('環境変数の組み立て', () => {
  test('スロット 0 は、どの対象でも何も上書きしない（DC-104-1）', () => {
    for (const target of ['backend', 'store', 'admin']) assert.deepEqual(slotEnv(0, target), {})
  })

  test('バックエンドの環境変数', () => {
    assert.deepEqual(slotEnv(1, 'backend'), {
      SERVER_PORT: '18080',
      MANAGEMENT_SERVER_PORT: '19080',
      SPRING_DATASOURCE_URL: 'jdbc:postgresql://localhost:5432/ec_site_1',
      ECSITE_MEDIA_STORAGE_INCOMING_BUCKET: 'ec-site-images-incoming-1',
      ECSITE_MEDIA_STORAGE_PUBLIC_BUCKET: 'ec-site-images-1',
      ECSITE_MEDIA_STORAGE_PUBLIC_BASE_URL: 'http://localhost:8333/ec-site-images-1',
      ECSITE_PAYMENT_RETURN_URL: 'http://localhost:13000/orders/{orderId}?payment=returned',
      ECSITE_PAYMENT_MOCK_BASE_URL: 'http://localhost:18080',
    })
  })

  test('店舗画面の環境変数', () => {
    assert.deepEqual(slotEnv(2, 'store'), {
      STORE_API_BASE_URL: 'http://localhost:28080/api/store',
      AUTH_URL: 'http://localhost:23000',
      STORE_COOKIE_PREFIX: 's2-',
    })
  })

  test('管理画面の環境変数', () => {
    assert.deepEqual(slotEnv(3, 'admin'), { ADMIN_API_PROXY_TARGET: 'http://localhost:38080' })
  })

  test('知らない対象は拒む', () => {
    assert.throws(() => slotEnv(1, 'db'))
  })
})
