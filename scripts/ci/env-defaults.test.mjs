import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { compareDefaults, normalizeValue, parseEnvTable, parseProperties } from './env-defaults.mjs'

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
    assert.deepEqual(parseProperties(JAVA), [
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
