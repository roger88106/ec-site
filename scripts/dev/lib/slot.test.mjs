import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { describe, test } from 'node:test'
import {
  E2E,
  SHARED_CONTAINERS,
  SHARED_PORTS,
  SLOT_COUNT,
  assignSlot,
  checkBackendStopped,
  checkShared,
  currentSlotOf,
  dropBucketsScript,
  generateApiDir,
  formatSlotRecord,
  isBackendDown,
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

  test('スロットのポートは、バックエンド、運用、店舗画面、管理画面の順に返す', () => {
    assert.deepEqual(slotPorts(2), [28080, 29080, 23000, 25173])
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

describe('起動の前の API の型の生成', () => {
  test('画面は起動の前に API の型を生成するディレクトリを返す', () => {
    assert.equal(generateApiDir('store'), 'frontend/store')
    assert.equal(generateApiDir('admin'), 'frontend/admin')
  })

  test('バックエンドは生成しない', () => {
    assert.equal(generateApiDir('backend'), null)
  })
})

describe('バックエンドの稼働の確認', () => {
  test('動いていれば、スロットとバックエンドのポートと操作の名前を示して、終了コード 1', () => {
    const problem = checkBackendStopped({ slot: 2, running: true, action: '解く' })
    assert.equal(problem.code, 1)
    assert.match(problem.message, /スロット 2/)
    assert.match(problem.message, /:28080/)
    assert.match(problem.message, /止めてから解く/)
  })

  test('動いていなければ、何も示さない', () => {
    assert.equal(checkBackendStopped({ slot: 2, running: false, action: '解く' }), null)
  })
})

describe('バケットの削除の script', () => {
  /** 偽の aws を定義して script を流す。aws は与えた出力を標準エラー出力へ書き、与えた終了コードで終わる */
  function runScript(buckets, awsStderr, awsStatus) {
    const fake = `aws() { echo '${awsStderr}' >&2; return ${awsStatus}; }\n`
    return spawnSync('sh', ['-c', fake + dropBucketsScript(buckets)], { encoding: 'utf8' })
  }

  test('削除に成功すれば、終了コード 0', () => {
    assert.equal(runScript(['b1', 'b2'], '', 0).status, 0)
  })

  test('バケットがない誤り（NoSuchBucket）だけは無視して、終了コード 0', () => {
    const result = runScript(['b1', 'b2'], 'An error occurred (NoSuchBucket) when calling the ListObjectsV2 operation', 255)
    assert.equal(result.status, 0)
  })

  test('他の誤り（権限や接続）は、出力して失敗にする', () => {
    const result = runScript(['b1', 'b2'], 'An error occurred (AccessDenied) when calling the DeleteBucket operation', 255)
    assert.notEqual(result.status, 0)
    assert.match(result.stderr, /AccessDenied/)
  })

  test('指定のバケットをそれぞれ削除する', () => {
    const script = dropBucketsScript(['b1', 'b2'])
    assert.match(script, /s3:\/\/b1\b/)
    assert.match(script, /s3:\/\/b2\b/)
  })
})

describe('スロットの値を書き写したファイル', () => {
  const slots = [1, 2, 3]
  const readJson = (relative) => JSON.parse(repoFile(relative))

  /** realm の中で、スロット 0 の画面の origin を許す client */
  const clientAllowing = (relative, origin) => {
    const client = readJson(relative).clients.find((c) => c.redirectUris?.includes(`${origin}/*`))
    assert.ok(client, `${relative} に ${origin} を許す client がない`)
    return client
  }

  for (const [label, relative, origin, port] of [
    ['店舗画面', 'docker/keycloak/realms/ec-store-realm.json', 'http://localhost:3000', 'storePort'],
    ['管理画面', 'docker/keycloak/realms/ec-admin-realm.json', 'http://localhost:5173', 'adminPort'],
  ]) {
    test(`${label}の realm は、スロット 1〜3 の origin を redirectUris、webOrigins、post.logout.redirect.uris に含む`, () => {
      const client = clientAllowing(relative, origin)
      for (const slot of slots) {
        const o = `http://localhost:${slotValues(slot)[port]}`
        assert.ok(client.redirectUris.includes(`${o}/*`), `redirectUris に ${o}/* がない`)
        assert.ok(client.webOrigins.includes(o), `webOrigins に ${o} がない`)
        assert.ok(client.attributes['post.logout.redirect.uris'].split('##').includes(`${o}/*`), `post.logout.redirect.uris に ${o}/* がない`)
      }
    })
  }

  test('.claude/launch.json は、スロットごとの画面の起動に、スロットのポートと番号を渡す', () => {
    const configs = readJson('.claude/launch.json').configurations
    for (const slot of slots) {
      for (const [target, port] of [
        ['admin', 'adminPort'],
        ['store', 'storePort'],
      ]) {
        const config = configs.find((c) => c.name === `${target}-${slot}`)
        assert.ok(config, `${target}-${slot} がない`)
        assert.equal(config.port, slotValues(slot)[port])
        assert.deepEqual(config.runtimeArgs, ['scripts/dev/slot.mjs', 'run', target, String(slot)])
      }
    }
  })

  test('compose の imgproxy は、全スロットの受付のバケットを読み元として許す', () => {
    const line = /IMGPROXY_ALLOWED_SOURCES:\s*(\S+)/.exec(repoFile('docker/compose.yaml'))
    assert.ok(line, 'IMGPROXY_ALLOWED_SOURCES がない')
    const sources = line[1].split(',')
    for (const slot of [0, ...slots]) assert.ok(sources.includes(`s3://${slotValues(slot).incomingBucket}/`), `スロット ${slot}`)
  })

  test('SeaweedFS の imgproxy の identity は、全スロットの受付のバケットを読める', () => {
    const identity = readJson('docker/seaweedfs/s3.json').identities.find((i) => i.name === 'ec-site-imgproxy')
    for (const slot of [0, ...slots]) assert.ok(identity.actions.includes(`Read:${slotValues(slot).incomingBucket}`), `スロット ${slot}`)
  })

  test('Windows のポートの予約の表は、全スロットのポートを、スロットの名前を付けて含む', () => {
    const script = repoFile('scripts/dev/windows/exclude-ports.ps1')
    const listed = [...script.matchAll(/^\s+(\d+) = '([^']*)'/gm)].map((m) => ({ port: Number(m[1]), label: m[2] }))
    for (const slot of slots) {
      for (const port of slotPorts(slot)) {
        const entry = listed.find((e) => e.port === port)
        assert.ok(entry, `${port} がない`)
        assert.ok(entry.label.includes(`スロット ${slot}`), `${port} の名前にスロット ${slot} がない`)
      }
    }
  })
})

describe('バックエンドが止まっているかの判定（fetch の誤りから）', () => {
  /** undici の接続拒否の誤りの形（localhost が IPv4 と IPv6 に引ける場合は AggregateError） */
  const failed = (cause) => Object.assign(new TypeError('fetch failed'), { cause })

  test('接続が拒まれたときだけ止まっている', () => {
    assert.equal(isBackendDown(failed(Object.assign(new Error('x'), { code: 'ECONNREFUSED' }))), true)
    assert.equal(isBackendDown(failed(Object.assign(new AggregateError([]), { code: 'ECONNREFUSED' }))), true)
  })

  test('タイムアウトは動いているとみなす（安全な側）', () => {
    assert.equal(isBackendDown(new DOMException('timed out', 'TimeoutError')), false)
  })

  test('その他の誤りや形の違う値も動いているとみなす', () => {
    assert.equal(isBackendDown(failed(Object.assign(new Error('x'), { code: 'ECONNRESET' }))), false)
    assert.equal(isBackendDown(new TypeError('fetch failed')), false)
    assert.equal(isBackendDown(null), false)
    assert.equal(isBackendDown('x'), false)
  })
})
