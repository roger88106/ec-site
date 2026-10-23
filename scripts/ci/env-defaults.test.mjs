import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { compareDefaults, normalizeValue, parseEnvTable, readAllProperties, readProperties } from './env-defaults.mjs'

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
