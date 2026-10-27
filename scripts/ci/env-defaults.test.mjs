import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { describe, test } from 'node:test'
import { compareAdminIdp, compareDefaults, normalizeValue, parseEnvTable, readAllProperties, readProperties } from './env-defaults.mjs'

const JAVA = `
@ConfigurationProperties("ecsite.payment")
public record PaymentProperties(
        @DefaultValue("30m") @NotNull Duration deadline,
        @DefaultValue("1m") @NotNull Duration expiryInterval,
        @NotBlank String returnUrl,
        @DefaultValue @Valid Stripe stripe) {

    public record Stripe(@DefaultValue("5s") Duration connectTimeout, Map<String, List<String>> headers) {
    }
}
`

const DOC = [
  '| 環境変数 | 内容 | 既定 |',
  '|----------|------|------|',
  '| `ECSITE_PAYMENT_DEADLINE`、`_EXPIRY_INTERVAL` | 期限、間隔 | 30m、1m |',
  '| `ECSITE_PAYMENT_RETURN_URL` | 戻り先 | 必須 |',
  '| `ECSITE_PAYMENT_STRIPE_CONNECT_TIMEOUT` | 接続 | 5s |',
  '| `MANAGEMENT_SERVER_PORT` | 運用ポート | 9080（E2E は 9090） |',
].join('\n')

describe('環境変数の文書と既定値の検査', () => {
  test('設定の型の record から、環境変数の名前と既定値を作る（入れ子の record を含む）', () => {
    assert.deepEqual(readProperties(JAVA).properties, [
      { env: 'ECSITE_PAYMENT_DEADLINE', value: '30m' },
      { env: 'ECSITE_PAYMENT_EXPIRY_INTERVAL', value: '1m' },
      { env: 'ECSITE_PAYMENT_RETURN_URL', value: null },
      { env: 'ECSITE_PAYMENT_STRIPE_CONNECT_TIMEOUT', value: '5s' },
      { env: 'ECSITE_PAYMENT_STRIPE_HEADERS', value: null },
    ])
  })

  test('文書の表を読み、省略形の名前を、知っている名前に合うように補う', () => {
    const known = new Set(['ECSITE_PAYMENT_DEADLINE', 'ECSITE_PAYMENT_EXPIRY_INTERVAL'])
    assert.deepEqual(parseEnvTable(DOC, known), [
      { env: 'ECSITE_PAYMENT_DEADLINE', value: '30m' },
      { env: 'ECSITE_PAYMENT_EXPIRY_INTERVAL', value: '1m' },
      { env: 'ECSITE_PAYMENT_RETURN_URL', value: '必須' },
      { env: 'ECSITE_PAYMENT_STRIPE_CONNECT_TIMEOUT', value: '5s' },
      { env: 'MANAGEMENT_SERVER_PORT', value: '9080' },
    ])
  })

  test('時間の書き方をそろえる（1m と PT1M、7d と PT168H）', () => {
    assert.equal(normalizeValue('1m'), normalizeValue('PT1M'))
    assert.equal(normalizeValue('7d'), normalizeValue('PT168H'))
    assert.notEqual(normalizeValue('1m'), normalizeValue('1h'))
    assert.equal(normalizeValue('1000'), '1000')
  })

  test('既定値の食い違い、文書にない既定値、コードにない既定値を見つける', () => {
    const code = [
      { env: 'ECSITE_PAYMENT_DEADLINE', value: '30m' },
      { env: 'ECSITE_PAYMENT_EXPIRY_INTERVAL', value: '1m' },
      { env: 'ECSITE_PAYMENT_RETURN_URL', value: null },
      { env: 'ECSITE_PAYMENT_LIMIT', value: '10' },
    ]
    const doc = [
      { env: 'ECSITE_PAYMENT_DEADLINE', value: '20m' },
      { env: 'ECSITE_PAYMENT_EXPIRY_INTERVAL', value: 'PT1M' },
      { env: 'ECSITE_PAYMENT_RETURN_URL', value: '5s' },
      { env: 'ECSITE_SCHEDULING_ENABLED', value: 'true' },
    ]
    assert.deepEqual(compareDefaults(code, doc), [
      'ECSITE_PAYMENT_DEADLINE：文書の既定「20m」が、コードの既定「30m」と違う',
      'ECSITE_PAYMENT_LIMIT：コードの既定「10」が、文書にない',
      'ECSITE_PAYMENT_RETURN_URL：文書の既定「5s」が、コードにない（コードは既定を持たない）',
    ])
  })
})

describe('設定の値のクラスを読めないときの失敗', () => {
  test('record の形の設定のクラスは、読めない理由がない', () => {
    assert.deepEqual(readProperties(JAVA).problems, [])
  })

  test('record でないクラス（class）に付けた注釈は、読めない理由にする', () => {
    const java = ['@ConfigurationProperties("ecsite.x")', 'public class XProperties {', '  private int a;', '}'].join('\n')
    assert.equal(readProperties(java).problems.length, 1)
    assert.match(readProperties(java).problems[0], /record/)
  })

  test('接頭辞が文字列リテラルでない注釈（prefix = 定数）は、読めない理由にする', () => {
    const java = ['@ConfigurationProperties(prefix = PREFIX)', 'public record XProperties(int a) {}'].join('\n')
    assert.equal(readProperties(java).problems.length, 1)
  })

  test('prefix = "..." の書き方も読む', () => {
    const java = '@ConfigurationProperties(prefix = "a.b")\nrecord X(@DefaultValue("1") int c) {}'
    assert.deepEqual(readProperties(java), { properties: [{ env: 'A_B_C', value: '1' }], problems: [] })
  })

  test('注釈の後の record でなく、離れた別の record を読み違えない', () => {
    const java = ['@ConfigurationProperties("ecsite.x")', 'public class X {', '  record Other(@DefaultValue("1") int a) {}', '}'].join('\n')
    const { properties, problems } = readProperties(java)
    assert.deepEqual(properties, [])
    assert.equal(problems.length, 1)
  })

  test('注釈と修飾子を飛ばす部分で、重なる読み方があっても指数的に遅くならない', () => {
    const java = `@ConfigurationProperties("!")@${'.final @'.repeat(40)}`
    const start = Date.now()
    assert.equal(readProperties(java).problems.length, 1)
    assert.ok(Date.now() - start < 1000)
  })

  test('引数が閉じていない record と、引数が 0 件の record は、読めない理由にする', () => {
    assert.equal(readProperties('@ConfigurationProperties("a.b")\nrecord X(int a').problems.length, 1)
    const empty = readProperties('@ConfigurationProperties("a.b")\nrecord X() {}')
    assert.equal(empty.problems.length, 1)
    assert.match(empty.problems[0], /引数/)
  })

  test('引数の名前を読めない行は、読めない理由にする（例外にしない）', () => {
    const java = '@ConfigurationProperties("a.b")\nrecord X(int a, @DefaultValue("1") ) {}'
    assert.doesNotThrow(() => readProperties(java))
    assert.equal(readProperties(java).problems.length, 1)
  })

  test('コメントの中の注釈は、設定のクラスとして読まない', () => {
    const java = ['// @ConfigurationProperties("a.b") は使わない', '/* @ConfigurationProperties("c.d") */', 'class Z {}'].join('\n')
    assert.deepEqual(readProperties(java), { properties: [], problems: [] })
  })

  test('設定のクラスが 1 つもないときは、ファイルごとでなく全体の理由を返す', () => {
    const result = readAllProperties([])
    assert.equal(result.code.length, 0)
    assert.equal(result.problems.length, 1)
    assert.match(result.problems[0], /1 つも/)
  })

  test('読めないファイルの理由にファイルの名前を付け、読めるファイルの既定値は残す', () => {
    const result = readAllProperties([
      { file: 'A.java', source: JAVA },
      { file: 'B.java', source: '@ConfigurationProperties("b.c")\npublic class B {}' },
    ])
    assert.equal(result.code.length, 5)
    assert.equal(result.problems.length, 1)
    assert.match(result.problems[0], /^B\.java：/)
  })

  test('注釈のないファイルは、対象にしない', () => {
    const result = readAllProperties([
      { file: 'A.java', source: JAVA },
      { file: 'C.java', source: 'class C {}' },
    ])
    assert.deepEqual(result.problems, [])
  })
})

describe('管理画面の IdP の既定値の検査', () => {
  const AUTHORITY = 'http://localhost:8180/realms/ec-admin'
  const CLIENT_ID = 'ec-admin-web'

  /** 5 つの置き場所の文。値は引数で変える */
  const texts = ({ oidc = [AUTHORITY, CLIENT_ID], knip = [AUTHORITY, CLIENT_ID], script = [AUTHORITY, CLIENT_ID], workflow = [AUTHORITY, CLIENT_ID], doc = [AUTHORITY, CLIENT_ID] } = {}) => ({
    'frontend/admin/src/auth/oidc.ts': [
      '// authority: import.meta.env.VITE_OIDC_AUTHORITY ?? "http://comment.example"',
      "  authority: import.meta.env.VITE_OIDC_AUTHORITY ?? '" + oidc[0] + "',",
      "  client_id: import.meta.env.VITE_OIDC_CLIENT_ID ?? '" + oidc[1] + "',",
    ].join('\n'),
    'knip.ts': [
      "process.env.VITE_OIDC_AUTHORITY ??= '" + knip[0] + "'",
      "process.env.VITE_OIDC_CLIENT_ID ??= '" + knip[1] + "'",
    ].join('\n'),
    'scripts/dev/check-frontend.sh': [
      '# export VITE_OIDC_AUTHORITY="${VITE_OIDC_AUTHORITY:-http://comment.example}"',
      'export VITE_OIDC_AUTHORITY="${VITE_OIDC_AUTHORITY:-' + script[0] + '}"',
      'export VITE_OIDC_CLIENT_ID="${VITE_OIDC_CLIENT_ID:-' + script[1] + '}"',
    ].join('\n'),
    '.github/workflows/ci.yml': [
      '        env:',
      '          # VITE_OIDC_AUTHORITY: http://comment.example',
      '          VITE_OIDC_AUTHORITY: ' + workflow[0],
      '          VITE_OIDC_CLIENT_ID: ' + workflow[1],
    ].join('\n'),
    'docs/guides/environment.md': [
      '| 環境変数 | 内容 | 既定 |',
      '|----------|------|------|',
      '| `VITE_OIDC_AUTHORITY` | IdP（運営者の realm） | `' + doc[0] + '` |',
      '| `VITE_OIDC_CLIENT_ID` | client ID | `' + doc[1] + '` |',
    ].join('\n'),
  })

  test('全ての置き場所の値が一致すれば、問題はない（コメントの行は読まない）', () => {
    assert.deepEqual(compareAdminIdp(texts()), [])
  })

  test('管理画面の認証の設定を正とし、ローカルの検査の script、CI、環境変数の文書、knip の値が違えば、置き場所と名前を示して失敗にする', () => {
    const wrong = [AUTHORITY.replace('8180', '9999'), 'ec-other']
    for (const place of ['knip', 'script', 'workflow', 'doc']) {
      const problems = compareAdminIdp(texts({ [place]: wrong }))
      assert.equal(problems.length, 2, place)
      assert.match(problems[0], /^VITE_OIDC_AUTHORITY：.*「http:\/\/localhost:9999\/realms\/ec-admin」.*「http:\/\/localhost:8180\/realms\/ec-admin」/, place)
      assert.match(problems[1], /^VITE_OIDC_CLIENT_ID：.*「ec-other」.*「ec-admin-web」/, place)
    }
  })

  test('どれか 1 つの項目だけが違うときは、その項目だけを失敗にする', () => {
    const problems = compareAdminIdp(texts({ script: [AUTHORITY, 'ec-other'] }))
    assert.equal(problems.length, 1)
    assert.match(problems[0], /scripts\/dev\/check-frontend\.sh/)
    assert.match(problems[0], /^VITE_OIDC_CLIENT_ID：/)
  })

  test('管理画面の認証の設定の既定値を読めないときは、他の値と比べずに失敗にする', () => {
    const input = texts()
    input['frontend/admin/src/auth/oidc.ts'] = 'export const oidcSettings = {}'
    const problems = compareAdminIdp(input)
    assert.equal(problems.length, 2)
    assert.match(problems[0], /^VITE_OIDC_AUTHORITY：frontend\/admin\/src\/auth\/oidc\.ts の既定値を読めない/)
  })

  test('他の置き場所に値がない（読めない）ときも、失敗にする', () => {
    for (const file of ['knip.ts', 'scripts/dev/check-frontend.sh', '.github/workflows/ci.yml', 'docs/guides/environment.md']) {
      const input = texts()
      input[file] = ''
      const problems = compareAdminIdp(input)
      assert.equal(problems.length, 2, file)
      assert.ok(problems.every((problem) => problem.includes(`${file} の`)), file)
    }
  })

  test('CI の値が複数あるときは、全ての値が一致していなければ失敗にする', () => {
    const input = texts()
    input['.github/workflows/ci.yml'] += '\n          VITE_OIDC_AUTHORITY: http://localhost:1/realms/x'
    const problems = compareAdminIdp(input)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /http:\/\/localhost:1\/realms\/x/)
  })

  test('リポジトリの今のファイルで、値が一致している', () => {
    const files = Object.keys(texts())
    const actual = Object.fromEntries(files.map((file) => [file, readFileSync(file, 'utf8')]))
    assert.deepEqual(compareAdminIdp(actual), [])
  })
})
