import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { load } from 'js-yaml'
import {
  checkContract,
  checkDocument,
  parseRealmRoles,
  parseOperations,
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
  '      summary: 税区分の登録',
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
  '      summary: 商品の一覧',
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
      { method: 'POST', path: '/tax-categories', summary: '税区分の登録', permission: 'tax:write', security: null },
      { method: 'GET', path: '/me', summary: 'ログインしている運営者', permission: 'authenticated', security: null },
    ])
    assert.deepEqual(
      parseOperations(STORE).map((operation) => operation.security),
      [[], ['', 'memberToken'], ['memberToken']],
    )
  })

  test('項目のない security: は、認証が不要（[]）と読まずに、書いていないもの（null）として読む', () => {
    const [operation] = parseOperations(['paths:', '  /a:', '    get:', '      security:', '      summary: x'].join('\n'))
    assert.equal(operation.security, null)
  })

  test('フロー形式と引用符で囲んだパスも読み、パスの parameters と知らないメソッドは操作にしない', () => {
    const text = ['paths:', "  '/a': {get: {summary: a, x-required-permission: tax:read}}", '  /b:', '    parameters: []', '    head: {summary: b}'].join('\n')
    assert.deepEqual(parseOperations(text), [{ method: 'GET', path: '/a', summary: 'a', permission: 'tax:read', security: null }])
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

  test('ロールの表はロールと説明の 2 列、権限の表はロールを列にして権限を行にする。管理 API の表は 3 列', () => {
    const { markdown, problems } = renderTables({
      admin: parseOperations(ADMIN),
      store: parseOperations(STORE),
      realm: parseRealmRoles(REALM),
    })
    assert.deepEqual(problems, [])
    assert.match(markdown, /^\| ロール \| 説明 \|$/m)
    assert.match(markdown, /^\| `administrator` \| 管理者：すべての権限 \|$/m)
    assert.match(markdown, /^\| `viewer` \| 閲覧者：参照の権限すべて \|$/m)
    assert.match(markdown, /^\| 権限 \| `administrator` \| `viewer` \|$/m)
    assert.match(markdown, /^\| `tax:read` \| ○ \| ○ \|$/m)
    assert.match(markdown, /^\| `tax:write` \| ○ \| {2}\|$/m)
    assert.match(markdown, /^\| 操作 \| 内容 \| 権限 \|$/m)
    assert.match(markdown, /^\| `POST \/tax-categories` \| 税区分の登録 \| `tax:write` \|$/m)
    assert.match(markdown, /^\| `GET \/me` \| ログインしている運営者 \| ログインだけ \|$/m)
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
    const store = parseOperations(
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
    const { markdown, problems } = renderTables({ admin: [], store, realm: parseRealmRoles(REALM) })
    assert.deepEqual(problems, ['GET /forgotten：security がない。token が要らない操作は security: [] と書く'])
    assert.match(markdown, /\| `GET \/open` \| 公開 \| 不要 \|/)
    assert.doesNotMatch(markdown, /GET \/forgotten/)
    assert.match(markdown, /\| `POST \/forgotten` \| 同じパスの別の操作 \| 会員 \|/)
  })

  describe('契約の書き方の検査', () => {
    const topSecurity = (value) => ['openapi: 3.0.3', `security:${value}`, 'paths:', '  /a:', '    get:', '      security: []'].join('\n')
    const topSecurityProblem = '最上位の security：最上位に書かず、操作ごとに書く'

    test('店舗の契約の最上位の security を失敗にする（値あり、空の配列、値のない security:）', () => {
      assert.deepEqual(checkContract(topSecurity('\n  - memberToken: []'), { store: true }), [topSecurityProblem])
      assert.deepEqual(checkContract(topSecurity(' []'), { store: true }), [topSecurityProblem])
      assert.deepEqual(checkContract(topSecurity(''), { store: true }), [topSecurityProblem])
    })

    test('管理の契約の最上位の security は失敗にしない', () => {
      assert.deepEqual(checkContract(ADMIN, { store: false }), [])
      assert.deepEqual(checkContract(topSecurity('\n  - adminToken: []'), { store: false }), [])
    })

    test('店舗の契約の、操作ごとの security と、最上位のない契約は失敗にしない', () => {
      assert.deepEqual(checkContract(STORE, { store: true }), [])
    })

    test('パスの下の $ref を、店舗と管理のどちらの契約でも失敗にする', () => {
      const text = ['paths:', '  /carts:', "    $ref: './paths/carts.yaml'", '  /ok:', '    get:', '      summary: x'].join('\n')
      const problem = '/carts：パスの下に $ref がある。$ref の Path Item は読まないので、パスの下に直接書く'
      assert.deepEqual(checkContract(text, { store: true }), [problem])
      assert.deepEqual(checkContract(text, { store: false }), [problem])
    })

    test('契約のファイルを渡すと、問題の文の先頭にファイル名を付ける（どの契約を直すかが分かる）', () => {
      const file = 'api/openapi/store.yaml'
      assert.deepEqual(checkContract(topSecurity(''), { store: true, file }), [`${file}：${topSecurityProblem}`])
      const ref = ['paths:', '  /carts:', "    $ref: './paths/carts.yaml'"].join('\n')
      assert.deepEqual(checkContract(ref, { store: false, file: 'api/openapi/admin.yaml' }), [
        'api/openapi/admin.yaml：/carts：パスの下に $ref がある。$ref の Path Item は読まないので、パスの下に直接書く',
      ])
    })

    test('読んだ後の契約（オブジェクト）も受け取り、文字列と同じ結果にする（YAML を 1 回だけ読むため）', () => {
      const text = topSecurity('\n  - memberToken: []')
      assert.deepEqual(checkContract(load(text), { store: true }), checkContract(text, { store: true }))
      assert.deepEqual(parseOperations(load(STORE)), parseOperations(STORE))
    })

    test('パスの共通の parameters の下の $ref と、応答の $ref は失敗にしない', () => {
      const text = [
        'paths:',
        '  /carts/{cartId}:',
        '    parameters:',
        "      - $ref: '#/components/parameters/CartId'",
        '    post:',
        '      responses:',
        "        '201':",
        "          $ref: '#/components/responses/Created'",
      ].join('\n')
      assert.deepEqual(checkContract(text, { store: true }), [])
      assert.deepEqual(checkContract(text, { store: false }), [])
    })
  })

  test('文書の生成の範囲が、生成し直した結果と違えば失敗にし、書き直し方を示す', () => {
    const doc = '# 表\n\n<!-- generated:role-permissions -->\n古い表\n<!-- /generated:role-permissions -->\n'
    assert.deepEqual(checkDocument(doc, '古い表\n'), [])
    assert.deepEqual(checkDocument(doc, '新しい表\n'), [
      '文書の対応表が、契約と realm の設定から作った表と違う。node scripts/ci/config/role-permissions.mjs --write で書き直し、差分を確認する',
    ])
    assert.deepEqual(checkDocument('# 表\n', 'x\n'), ['文書に <!-- generated:role-permissions --> の範囲がない'])
    assert.equal(
      replaceGenerated(doc, '新しい表\n'),
      '# 表\n\n<!-- generated:role-permissions -->\n新しい表\n<!-- /generated:role-permissions -->\n',
    )
  })

  test('生成の範囲の終わりが開きより前にあれば、範囲がないものとして失敗にし、書き直しでは例外にする', () => {
    const doc = '<!-- /generated:role-permissions -->\n表\n<!-- generated:role-permissions -->\n'
    assert.deepEqual(checkDocument(doc, '表\n'), ['文書に <!-- generated:role-permissions --> の範囲がない'])
    assert.throws(() => replaceGenerated(doc, '表\n'), /範囲がない/)
  })

  test('生成の範囲の開きだけがあって、終わりがなければ、書き直しでは例外にする', () => {
    assert.throws(() => replaceGenerated('<!-- generated:role-permissions -->\n表\n', '表\n'), /範囲がない/)
  })
})

describe('script として実行する', () => {
  const script = fileURLToPath(new URL('./role-permissions.mjs', import.meta.url))
  const forgotten = `${STORE}\n  /forgotten:\n    get:\n      summary: 書き忘れ`

  /**
   * 一時のディレクトリに契約と realm と文書を置いて、script を実行する。
   *
   * @param {string} store 店舗 API の契約
   * @param {string[]} args script の引数
   * @param {string} [table] 文書の生成の範囲に置く表
   * @returns {{ result: import('node:child_process').SpawnSyncReturns<string>, document: string }} 実行の結果と、実行の後の文書
   */
  function run(store, args, table = '古い表\n') {
    const dir = mkdtempSync(path.join(tmpdir(), 'role-permissions-'))
    try {
      const files = {
        'api/openapi/admin.yaml': ADMIN,
        'api/openapi/store.yaml': store,
        'docker/keycloak/realms/ec-admin-realm.json': JSON.stringify(REALM),
        'docs/02-設計/role-permissions.md': `# 表\n\n<!-- generated:role-permissions -->\n${table}<!-- /generated:role-permissions -->\n`,
      }
      for (const [file, text] of Object.entries(files)) {
        mkdirSync(path.dirname(path.join(dir, file)), { recursive: true })
        writeFileSync(path.join(dir, file), text)
      }
      const result = spawnSync(process.execPath, [script, ...args], { cwd: dir, encoding: 'utf8' })
      return { result, document: readFileSync(path.join(dir, 'docs/02-設計/role-permissions.md'), 'utf8') }
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  }

  const current = renderTables({ admin: parseOperations(ADMIN), store: parseOperations(STORE), realm: parseRealmRoles(REALM) }).markdown

  test('文書の表が契約と realm から作った表と同じなら、終了コード 0 で、食い違いの数を出して終わる', () => {
    const { result } = run(STORE, [], current)
    assert.equal(result.status, 0, result.stdout)
    assert.match(result.stdout, /対応表の食い違い：0/)
  })

  test('文書の表が違えば、書き直し方を出し、終了コード 1 で終わる', () => {
    const { result } = run(STORE, [])
    assert.equal(result.status, 1, result.stdout)
    assert.match(result.stdout, /--write で書き直し/)
  })

  test('--write は、問題がなければ表を書き直し、終了コード 0 で終わる', () => {
    const { result, document } = run(STORE, ['--write'])
    assert.equal(result.status, 0, result.stdout)
    assert.match(result.stdout, /書き直した：/)
    assert.ok(document.includes(current), document)
  })

  test('店舗の契約の最上位の security と、パスの下の $ref は、終了コード 1 で終わる。管理の契約の最上位の security は通る', () => {
    const top = run(`${STORE}\nsecurity:\n  - memberToken: []`, [], current)
    assert.equal(top.result.status, 1, top.result.stdout)
    assert.match(top.result.stdout, /最上位に書かず、操作ごとに書く/)
    const ref = run(`${STORE}\n  /ref:\n    $ref: './ref.yaml'`, [], current)
    assert.equal(ref.result.status, 1, ref.result.stdout)
    assert.match(ref.result.stdout, /\/ref：パスの下に \$ref がある/)
    const admin = run(STORE, [], current)
    assert.equal(admin.result.status, 0, admin.result.stdout)
  })

  test('--write は、契約に問題があれば、表を書かず、書かなかったことを出して終了コード 1 で終わる', () => {
    const { result, document } = run(forgotten, ['--write'])
    assert.equal(result.status, 1, result.stdout)
    assert.match(result.stdout, /GET \/forgotten：security がない/)
    assert.match(result.stdout, /書き直さなかった/)
    assert.ok(document.includes('古い表'), document)
  })
})
