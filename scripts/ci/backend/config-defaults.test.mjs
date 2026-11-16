import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
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

  test('コードに既定値がなく、文書が「必須」や「—」のような具体的な値でない書き方なら、食い違いにしない', () => {
    const code = [
      { env: 'ECSITE_A', value: null },
      { env: 'ECSITE_B', value: null },
    ]
    const doc = [
      { env: 'ECSITE_A', value: '必須' },
      { env: 'ECSITE_B', value: '—' },
    ]
    assert.deepEqual(compareDefaults(code, doc), [])
  })
})

describe('script として実行する', () => {
  const script = fileURLToPath(new URL('./config-defaults.mjs', import.meta.url))
  const metadata = JSON.stringify({
    properties: [
      { name: 'ecsite.payment.deadline', defaultValue: '30m' },
      { name: 'ecsite.payment.return-url' },
    ],
  })
  const doc = ['| 環境変数 | 内容 | 既定 |', '|----------|------|------|', '| `ECSITE_PAYMENT_DEADLINE` | 期限 | 30m |', '| `ECSITE_PAYMENT_RETURN_URL` | 戻り先 | 必須 |'].join('\n')

  /**
   * 一時のディレクトリに文書と設定の一覧を置いて、script を実行する。
   *
   * @param {{ doc?: string, metadata?: string | null }} [options] 文書と設定の一覧（null は一覧を置かない）
   * @returns {import('node:child_process').SpawnSyncReturns<string>} 実行の結果
   */
  function run({ doc: docText = doc, metadata: metadataText = metadata } = {}) {
    const dir = mkdtempSync(path.join(tmpdir(), 'config-defaults-'))
    try {
      mkdirSync(path.join(dir, 'docs/06-手順書'), { recursive: true })
      writeFileSync(path.join(dir, 'docs/06-手順書/environment.md'), docText)
      if (metadataText !== null) writeFileSync(path.join(dir, 'metadata.json'), metadataText)
      return spawnSync(process.execPath, [script, 'metadata.json'], { cwd: dir, encoding: 'utf8' })
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  }

  test('既定値が一致していれば、終了コード 0 で、食い違いの数を出して終わる', () => {
    const result = run()
    assert.equal(result.status, 0, result.stdout)
    assert.match(result.stdout, /設定の既定値の食い違い：0/)
  })

  test('既定値が食い違っていれば、その環境変数を出し、終了コード 1 で終わる', () => {
    const result = run({ doc: doc.replace('30m', '20m') })
    assert.equal(result.status, 1, result.stdout)
    assert.match(result.stdout, /ECSITE_PAYMENT_DEADLINE：文書の既定「20m」が、コードの既定「30m」と違う/)
    assert.match(result.stdout, /設定の既定値の食い違い：1/)
  })

  test('設定の一覧がなければ、原因を出して終了コード 2 で終わり、食い違いの要約の行は出さない', () => {
    const result = run({ metadata: null })
    assert.equal(result.status, 2, result.stdout)
    assert.match(result.stdout, /設定の一覧（metadata\.json）がない/)
    assert.doesNotMatch(result.stdout, /食い違い：/)
  })
})
