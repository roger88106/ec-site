import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import {
  E2E,
  SHARED_PORTS,
  SLOT_COUNT,
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

  test('全スロットのポートは、互いに、共有の基盤と E2E のポートに重ならない（DC-104-10）', () => {
    const all = [...slots.flatMap((n) => slotPorts(n)), ...SHARED_PORTS, ...E2E.ports]
    assert.equal(new Set(all).size, all.length)
    assert.deepEqual(SHARED_PORTS, [5432, 9411, 8333, 8081, 8180, 8181, 2525, 8025])
    assert.deepEqual(E2E.ports, [8090, 9090, 4173, 3100])
  })

  test('スロットは 4 つのポートを持つ', () => {
    assert.deepEqual(slotPorts(1), [18080, 19080, 13000, 15173])
  })

  test('全スロットのポートは、Windows の動的なポートの範囲（49152〜）より下に収まる', () => {
    assert.ok(slots.flatMap((n) => slotPorts(n)).every((port) => port < 49152))
  })

  test('E2E のデータベースとバケットは、全スロットの名前と重ならない（DC-104-20）', () => {
    assert.equal(E2E.database, 'ec_site_e2e')
    assert.deepEqual(E2E.buckets, ['ec-site-e2e-images', 'ec-site-e2e-images-incoming'])
    const databases = slots.map((n) => slotValues(n).database)
    const buckets = slots.flatMap((n) => [slotValues(n).publicBucket, slotValues(n).incomingBucket])
    assert.ok(!databases.includes(E2E.database))
    for (const bucket of E2E.buckets) assert.ok(!buckets.includes(bucket))
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

describe('空きのスロットの選び方', () => {
  test('使われていなければ 1 を選ぶ（DC-104-2）', () => {
    const text = porcelain(MAIN, worktree('/wt/a'))
    assert.deepEqual(pickSlot(text, {}), { slot: 1 })
  })

  test('使われている番号を飛ばして、最小の空きを選ぶ（DC-104-4）', () => {
    const text = porcelain(MAIN, worktree('/wt/a'), worktree('/wt/b'), worktree('/wt/c'))
    assert.deepEqual(pickSlot(text, { '/wt/a': '1\n', '/wt/c': '3\n' }), { slot: 2 })
    assert.deepEqual(pickSlot(text, { '/wt/b': '1\n' }), { slot: 2 })
  })

  test('メインの作業ツリーの記録は数えない', () => {
    const text = porcelain(MAIN, worktree('/wt/a'))
    assert.deepEqual(pickSlot(text, { '/repo': '1\n' }), { slot: 1 })
  })

  test('消えた作業ツリー（prunable）の記録は数えず、そのスロットは空きになる（DC-104-4）', () => {
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

  test('1〜3 が全て使われていれば、使っている作業ツリーのパスを返す（DC-104-7）', () => {
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
