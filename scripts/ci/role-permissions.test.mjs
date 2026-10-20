import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { checkDocument, parseOperations, parseRealmRoles, renderTables, replaceGenerated } from './role-permissions.mjs'

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
    assert.deepEqual(parseOperations(ADMIN), [
      { method: 'GET', path: '/tax-categories', summary: '税区分の一覧（税率を含む）', permission: 'tax:read', security: null },
      { method: 'POST', path: '/tax-categories', summary: '税区分の登録（AC-1）', permission: 'tax:write', security: null },
      { method: 'GET', path: '/me', summary: 'ログインしている運営者', permission: 'authenticated', security: null },
    ])
    assert.deepEqual(
      parseOperations(STORE).map((operation) => operation.security),
      [null, ['', 'memberToken'], ['memberToken']],
    )
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
      admin: parseOperations(ADMIN),
      store: parseOperations(STORE),
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

  test('文書の生成の範囲が、生成し直した結果と違えば失敗にし、書き直し方を示す', () => {
    const doc = '# 表\n\n<!-- generated:role-permissions -->\n古い表\n<!-- /generated:role-permissions -->\n'
    assert.deepEqual(checkDocument(doc, '古い表\n'), [])
    assert.deepEqual(checkDocument(doc, '新しい表\n'), [
      '文書の対応表が、契約と realm の設定から作った表と違う。node scripts/ci/role-permissions.mjs --write で書き直し、差分を確かめる',
    ])
    assert.deepEqual(checkDocument('# 表\n', 'x\n'), ['文書に <!-- generated:role-permissions --> の範囲がない'])
    assert.equal(
      replaceGenerated(doc, '新しい表\n'),
      '# 表\n\n<!-- generated:role-permissions -->\n新しい表\n<!-- /generated:role-permissions -->\n',
    )
  })
})
