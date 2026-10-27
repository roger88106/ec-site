import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { readFileSync } from 'node:fs'
import {
  checkDocument,
  parseRealmRoles,
  readOperations,
  renderTables,
  replaceGenerated,
} from './role-permissions.mjs'

const ADMIN = [
  'openapi: 3.0.3',
  'security:',
  '  - adminToken: []',
  'paths:',
  '  /tax-categories:',
  '    get:',
  '      tags: [tax]',
  '      operationId: listTaxCategories',
  '      summary: 税区分の一覧（税率を含む）',
  '      x-required-permission: tax:read',
  '      responses:',
  "        '200':",
  '          description: 一覧',
  '    post:',
  '      operationId: createTaxCategory',
  '      summary: 税区分の登録（AC-1）',
  '      x-required-permission: tax:write',
  '  /me:',
  '    get:',
  '      operationId: getMe',
  '      summary: ログインしている運営者',
  '      x-required-permission: authenticated',
  'components:',
  '  schemas:',
  '    Foo:',
  '      type: object',
].join('\n')

const STORE = [
  'openapi: 3.0.3',
  'paths:',
  '  /products:',
  '    get:',
  '      operationId: listProducts',
  '      security: []',
  '      summary: 商品の一覧（AC-50）',
  '  /carts/{cartId}:',
  '    parameters:',
  "      - $ref: '#/components/parameters/CartId'",
  '    get:',
  '      operationId: getCart',
  '      security:',
  '        - {}',
  '        - memberToken: []',
  '      summary: カートの表示',
  '  /orders:',
  '    get:',
  '      operationId: listOrders',
  '      security:',
  '        - memberToken: []',
  '      summary: 自分の注文の一覧',
].join('\n')

const REALM = {
  roles: {
    realm: [
      {
        name: 'administrator',
        description: '管理者：すべての権限',
        composite: true,
        composites: { client: { 'ec-admin-api': ['tax:read', 'tax:write'] } },
      },
      {
        name: 'viewer',
        description: '閲覧者：参照の権限すべて',
        composite: true,
        composites: { client: { 'ec-admin-api': ['tax:read'] } },
      },
      { name: 'offline_access', description: '既定のロール' },
    ],
    client: { 'ec-admin-api': [{ name: 'tax:read' }, { name: 'tax:write' }] },
  },
}

describe('ロールと権限の対応表', () => {
  test('OpenAPI の操作から、メソッド、パス、要約、権限、security を読む。components の後は読まない', () => {
    assert.deepEqual(readOperations(ADMIN).operations, [
      { method: 'GET', path: '/tax-categories', summary: '税区分の一覧（税率を含む）', permission: 'tax:read', security: null },
      { method: 'POST', path: '/tax-categories', summary: '税区分の登録（AC-1）', permission: 'tax:write', security: null },
      { method: 'GET', path: '/me', summary: 'ログインしている運営者', permission: 'authenticated', security: null },
    ])
    assert.deepEqual(
      readOperations(STORE).operations.map((operation) => operation.security),
      [[], ['', 'memberToken'], ['memberToken']],
    )
  })

  describe('契約の知らない書き方', () => {
    /** 知らない書き方の行を「行 内容」で並べる */
    const unknownOf = (lines) => readOperations(lines.join('\n')).unknown.map((entry) => `${entry.line} ${entry.text}`)

    test('今の書き方（2 字ずつの字下げ、コメント、空行、引用符）は、知らない書き方にならない', () => {
      assert.deepEqual(unknownOf(ADMIN.split('\n')), [])
      assert.deepEqual(unknownOf(STORE.split('\n')), [])
      assert.deepEqual(
        unknownOf([
          'paths:',
          '  # 運営者',
          '',
          '  /a:',
          '    get:',
          "      summary: 'a'",
          '      x-required-permission: "tax:read"',
          '      security: []',
        ]),
        [],
      )
    })

    test('paths の 1 行の書き方（フロー形式）は、読み飛ばさずに失敗にする', () => {
      assert.deepEqual(unknownOf(['paths: {}']), ['1 paths: {}'])
      assert.deepEqual(unknownOf(['paths:', '  /a: {get: {summary: a}}']), ['2   /a: {get: {summary: a}}'])
      assert.deepEqual(unknownOf(['paths:', '  /a:', '    get: {summary: a}']), ['3     get: {summary: a}'])
    })

    test('字下げが 2 字ずつでない（4 字）パスと、操作の中身は、読み飛ばさずに失敗にする', () => {
      assert.deepEqual(unknownOf(['paths:', '    /a:', '        get:', '            summary: a']), ['2     /a:'])
      assert.deepEqual(unknownOf(['paths:', '  /a:', '    get:', '        summary: a']), ['4         summary: a'])
      assert.deepEqual(unknownOf(['paths:', '  /a:', '        get:']), ['3         get:'])
    })

    test('引用符で囲んだパスと、知らないメソッドの行は失敗にする', () => {
      assert.deepEqual(unknownOf(['paths:', "  '/a':", '    get:']), ["2   '/a':"])
      assert.deepEqual(unknownOf(['paths:', '  /a:', '    head:', '    GET:']), ['3     head:', '4     GET:'])
    })

    test('summary、権限の値が空か、ブロックやフロー形式のときは失敗にする', () => {
      assert.deepEqual(
        unknownOf([
          'paths:',
          '  /a:',
          '    get:',
          '      summary: >',
          '      x-required-permission:',
          '      security: [{}, memberToken: []]',
        ]),
        ['4       summary: >', '5       x-required-permission:', '6       security: [{}, memberToken: []]'],
      )
    })

    test('リポジトリの管理 API と店舗 API の契約に、知らない書き方の行がない', () => {
      for (const file of ['api/openapi/admin.yaml', 'api/openapi/store.yaml']) {
        const { operations, unknown } = readOperations(readFileSync(file, 'utf8'))
        assert.ok(operations.length > 0)
        assert.deepEqual(unknown, [])
      }
    })

    test('security の項目の字下げが違う、知らない書き方の項目は失敗にし、読み飛ばして不要と読まない', () => {
      assert.deepEqual(
        unknownOf(['paths:', '  /a:', '    get:', '      security:', '      - memberToken: []', '        - {}']),
        ['5       - memberToken: []'],
      )
      assert.deepEqual(
        unknownOf(['paths:', '  /a:', '    get:', '      security:', '        - memberToken: [x]']),
        ['5         - memberToken: [x]'],
      )
    })

    test('security の項目は字下げ 8 だけを読み、5・6・7・9・10 の字下げは読み飛ばさずに失敗にする', () => {
      const head = ['paths:', '  /a:', '    get:', '      summary: x', '      security:']
      const item = (indent) => `${' '.repeat(indent)}- memberToken: []`
      const ok = readOperations([...head, item(8)].join('\n'))
      assert.deepEqual(ok.unknown, [])
      assert.deepEqual(ok.operations[0].security, ['memberToken'])
      for (const indent of [5, 6, 7, 9, 10]) {
        assert.deepEqual(unknownOf([...head, item(indent)]), [`6 ${item(indent)}`], `字下げ ${indent}`)
      }
    })

    test('項目のない security: は、認証が不要（[]）と読まずに失敗にする', () => {
      assert.deepEqual(unknownOf(['paths:', '  /a:', '    get:', '      security:', '      summary: x']), ['4       security:'])
      assert.deepEqual(unknownOf(['paths:', '  /a:', '    get:', '      security:']), ['4       security:'])
    })

    test('操作のほかの項目（responses など）の中身は、字下げ 8 以上でも読まない', () => {
      assert.deepEqual(unknownOf(['paths:', '  /a:', '    get:', '      responses:', "        '200':", '          description: ok']), [])
    })
  })

  test('realm から、管理 API の権限を持つロールと、その権限を読む（権限のないロールは除く）', () => {
    assert.deepEqual(parseRealmRoles(REALM), {
      roles: [
        { name: 'administrator', description: '管理者：すべての権限', permissions: ['tax:read', 'tax:write'] },
        { name: 'viewer', description: '閲覧者：参照の権限すべて', permissions: ['tax:read'] },
      ],
      permissions: ['tax:read', 'tax:write'],
    })
  })

  test('管理 API はロール × 操作の表、店舗 API は認証の要否ごとの表にする。要約から AC の番号を除く', () => {
    const { markdown, problems } = renderTables({
      admin: readOperations(ADMIN).operations,
      store: readOperations(STORE).operations,
      realm: parseRealmRoles(REALM),
    })
    assert.deepEqual(problems, [])
    assert.match(markdown, /\| ロール \| 説明 \| tax:read \| tax:write \|/)
    assert.match(markdown, /\| `administrator` \| 管理者：すべての権限 \| ○ \| ○ \|/)
    assert.match(markdown, /\| `viewer` \| 閲覧者：参照の権限すべて \| ○ \| {2}\|/)
    assert.match(markdown, /\| `POST \/tax-categories` \| 税区分の登録 \| `tax:write` \| ○ \| {2}\|/)
    assert.match(markdown, /\| `GET \/me` \| ログインしている運営者 \| ログインだけ \| ○ \| ○ \|/)
    assert.match(markdown, /\| `GET \/orders` \| 自分の注文の一覧 \| 会員 \|/)
    assert.match(markdown, /\| `GET \/carts\/\{cartId\}` \| カートの表示 \| 任意 \|/)
    assert.match(markdown, /\| `GET \/products` \| 商品の一覧 \| 不要 \|/)
  })

  test('権限のない管理 API の操作と、realm にない権限を見つける', () => {
    const admin = [
      { method: 'GET', path: '/a', summary: 'a', permission: null, security: null },
      { method: 'GET', path: '/b', summary: 'b', permission: 'unknown:read', security: null },
    ]
    const { problems } = renderTables({ admin, store: [], realm: parseRealmRoles(REALM) })
    assert.deepEqual(problems, [
      'GET /a：x-required-permission がない',
      'GET /b：権限 unknown:read が運営者の realm にない',
    ])
  })

  test('security のない店舗 API の操作は、「不要」にせず問題にする。security: [] は token 不要を明示した操作で、「不要」になる', () => {
    const store = readOperations(
      [
        'paths:',
        '  /open:',
        '    get:',
        '      security: []',
        '      summary: 公開',
        '  /forgotten:',
        '    get:',
        '      summary: security を書き忘れた',
        '    post:',
        '      summary: 同じパスの別の操作',
        '      security:',
        '        - memberToken: []',
      ].join('\n'),
    )
    assert.equal(store.unknown.length, 0)
    const { markdown, problems } = renderTables({ admin: [], store: store.operations, realm: parseRealmRoles(REALM) })
    assert.deepEqual(problems, ['GET /forgotten：security がない。token が要らない操作は security: [] と書く'])
    assert.match(markdown, /\| `GET \/open` \| 公開 \| 不要 \|/)
    assert.doesNotMatch(markdown, /GET \/forgotten/)
    assert.match(markdown, /\| `POST \/forgotten` \| 同じパスの別の操作 \| 会員 \|/)
  })

  test('文書の生成の範囲が、生成し直した結果と違えば失敗にし、書き直し方を示す', () => {
    const doc = '# 表\n\n<!-- generated:role-permissions -->\n古い表\n<!-- /generated:role-permissions -->\n'
    assert.deepEqual(checkDocument(doc, '古い表\n'), [])
    assert.deepEqual(checkDocument(doc, '新しい表\n'), [
      '文書の対応表が、契約と realm の設定から作った表と違う。node scripts/ci/role-permissions.mjs --write で書き直し、差分を確認する',
    ])
    assert.deepEqual(checkDocument('# 表\n', 'x\n'), ['文書に <!-- generated:role-permissions --> の範囲がない'])
    assert.equal(
      replaceGenerated(doc, '新しい表\n'),
      '# 表\n\n<!-- generated:role-permissions -->\n新しい表\n<!-- /generated:role-permissions -->\n',
    )
  })
})
