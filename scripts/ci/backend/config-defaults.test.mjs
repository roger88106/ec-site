import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { compareDefaults, normalizeValue, parseEnvTable, readMetadata } from './config-defaults.mjs'

/** spring-configuration-metadata.json の小さな例（入れ子の record の項目を含む） */
const METADATA = JSON.stringify({
  groups: [{ name: 'ecsite.payment' }],
  properties: [
    { name: 'ecsite.payment.deadline', type: 'java.time.Duration', defaultValue: '30m' },
    { name: 'ecsite.payment.expiry-interval', type: 'java.time.Duration', defaultValue: '1m' },
    { name: 'ecsite.payment.return-url', type: 'java.lang.String' },
    { name: 'ecsite.payment.stripe.connect-timeout', type: 'java.time.Duration', defaultValue: '5s' },
    { name: 'ecsite.payment.limit', type: 'java.lang.Integer', defaultValue: 10 },
    { name: 'ecsite.payment.enabled', type: 'java.lang.Boolean', defaultValue: false },
  ],
})

const DOC = [
  '| 環境変数 | 内容 | 既定 |',
  '|----------|------|------|',
  '| `ECSITE_PAYMENT_DEADLINE`、`_EXPIRY_INTERVAL` | 期限、間隔 | 30m、1m |',
  '| `ECSITE_PAYMENT_RETURN_URL` | 戻り先 | 必須 |',
  '| `ECSITE_PAYMENT_STRIPE_CONNECT_TIMEOUT` | 接続 | 5s |',
  '| `MANAGEMENT_SERVER_PORT` | 運用ポート | 9080（E2E は 9090） |',
].join('\n')

describe('設定の一覧の読み方', () => {
  test('プロパティの名前から環境変数の名前を作り、既定値を文字にする（ないものは null）', () => {
    assert.deepEqual(readMetadata(METADATA), [
      { env: 'ECSITE_PAYMENT_DEADLINE', value: '30m' },
      { env: 'ECSITE_PAYMENT_EXPIRY_INTERVAL', value: '1m' },
      { env: 'ECSITE_PAYMENT_RETURN_URL', value: null },
      { env: 'ECSITE_PAYMENT_STRIPE_CONNECT_TIMEOUT', value: '5s' },
      { env: 'ECSITE_PAYMENT_LIMIT', value: '10' },
      { env: 'ECSITE_PAYMENT_ENABLED', value: 'false' },
    ])
  })

  test('プロパティが 1 つもない一覧は、失敗にする', () => {
    assert.throws(() => readMetadata('{"groups":[]}'), /1 つもない/)
  })
})

describe('環境変数の文書と既定値の照合', () => {
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
