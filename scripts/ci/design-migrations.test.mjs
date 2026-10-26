import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { checkDesignMigrations, moduleOfMigration, parseDataModelMigrations } from './design-migrations.mjs'

const DIR = '../../../backend/ecsite/src/main/resources/db/migration/'

/** データモデルの節だけにマイグレーションを挙げる設計書 */
function design(...names) {
  const links = names.map((name) => `[${name}](${DIR}${name})`).join('、')
  return ['# 設計', '## 概要', '本文は V999__other_x.sql に触れない', '## データモデル', links, '## 次の節', ''].join('\n')
}

describe('設計書のデータモデルとマイグレーションの照合', () => {
  test('ファイル名の接頭辞からモジュールを決める。ordering は order の設計書、common はどのモジュールでもない', () => {
    assert.equal(moduleOfMigration('V202610091000__tax_create_tax_category.sql'), 'tax')
    assert.equal(moduleOfMigration('V202610111200__ordering_create_order.sql'), 'order')
    assert.equal(moduleOfMigration('V202610090000__common_create_schemas.sql'), null)
    assert.equal(moduleOfMigration('V1__nomodule.sql'), undefined)
  })

  test('データモデルの節だけから、挙げたマイグレーションのファイル名を読む（リンクの文字とパスの両方に出ても 1 回）', () => {
    assert.deepEqual(parseDataModelMigrations(design('V1__tax_a.sql', 'V2__tax_b.sql')), ['V1__tax_a.sql', 'V2__tax_b.sql'])
  })

  test('データモデルの節がなければ null', () => {
    assert.equal(parseDataModelMigrations('# 設計\n## 概要\n'), null)
  })

  test('全て挙げていれば問題がない', () => {
    const problems = checkDesignMigrations({
      migrations: ['V1__tax_a.sql', 'V2__ordering_b.sql', 'V3__common_c.sql'],
      designs: { tax: design('V1__tax_a.sql'), order: design('V2__ordering_b.sql'), storefront: '# 設計\n' },
    })
    assert.deepEqual(problems, [])
  })

  test('設計書に挙げていないマイグレーションを失敗にする', () => {
    const problems = checkDesignMigrations({
      migrations: ['V1__tax_a.sql', 'V2__tax_b.sql'],
      designs: { tax: design('V1__tax_a.sql') },
    })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /docs\/design\/modules\/tax\.md/)
    assert.match(problems[0], /V2__tax_b\.sql/)
  })

  test('設計書が挙げたマイグレーションが実在しないとき（ファイル名の誤り）を失敗にする', () => {
    const problems = checkDesignMigrations({
      migrations: ['V1__tax_a.sql'],
      designs: { tax: design('V1__tax_a.sql', 'V9__tax_typo.sql') },
    })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /V9__tax_typo\.sql/)
    assert.match(problems[0], /実在しない/)
  })

  test('他のモジュールのマイグレーションを挙げたときを失敗にする', () => {
    const problems = checkDesignMigrations({
      migrations: ['V1__tax_a.sql', 'V2__cart_b.sql'],
      designs: { tax: design('V1__tax_a.sql', 'V2__cart_b.sql'), cart: design('V2__cart_b.sql') },
    })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /V2__cart_b\.sql/)
    assert.match(problems[0], /cart/)
  })

  test('モジュールの設計書がない、または設計書にデータモデルの節がないマイグレーションを失敗にする', () => {
    const problems = checkDesignMigrations({
      migrations: ['V1__tax_a.sql', 'V2__ghost_b.sql'],
      designs: { tax: '# 設計\n## 概要\n' },
    })
    assert.equal(problems.length, 2)
    assert.match(problems[0], /V1__tax_a\.sql/)
    assert.match(problems[1], /V2__ghost_b\.sql/)
  })

  test('モジュールが読めないファイル名を失敗にする', () => {
    const problems = checkDesignMigrations({ migrations: ['V1__nomodule.sql'], designs: {} })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /V1__nomodule\.sql/)
  })

  test('データモデルの節のある設計書は、マイグレーションが 1 つもなくても問題がない', () => {
    assert.deepEqual(checkDesignMigrations({ migrations: ['V1__tax_a.sql'], designs: { tax: design('V1__tax_a.sql'), media: design() } }), [])
  })
})

describe('マイグレーションが SQL で触るスキーマのモジュール', () => {
  test('common のマイグレーションは、SQL のスキーマ付きの表のスキーマに当たるモジュールの設計書に挙げる', () => {
    const problems = checkDesignMigrations({
      migrations: ['V1__common_checks.sql'],
      sqls: { 'V1__common_checks.sql': 'ALTER TABLE payment.payment ADD CONSTRAINT c CHECK (x);\nALTER TABLE shipping.shipment ADD CONSTRAINT d CHECK (y);' },
      designs: { payment: design('V1__common_checks.sql'), shipping: design(), tax: design() },
    })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /docs\/design\/modules\/shipping\.md/)
    assert.match(problems[0], /V1__common_checks\.sql/)
  })

  test('スキーマ common と CREATE SCHEMA と SQL のコメントは、どのモジュールにも当てない', () => {
    const problems = checkDesignMigrations({
      migrations: ['V1__common_schemas.sql'],
      sqls: { 'V1__common_schemas.sql': '-- docs/modules/tax.md、tax.category\nCREATE SCHEMA IF NOT EXISTS tax;\nCREATE TABLE common.event (id int);' },
      designs: { tax: design() },
    })
    assert.deepEqual(problems, [])
  })

  test('接頭辞が別のモジュールのマイグレーションが、他のモジュールの表に触るなら、その設計書にも挙げる（挙げていてもよい）', () => {
    const migrations = ['V1__ordering_x.sql']
    const sqls = { 'V1__ordering_x.sql': 'ALTER TABLE ordering.checkout ADD x int;\nUPDATE inventory.stock_reservation SET y = 1;' }
    const missing = checkDesignMigrations({ migrations, sqls, designs: { order: design('V1__ordering_x.sql'), inventory: design() } })
    assert.equal(missing.length, 1)
    assert.match(missing[0], /inventory\.md/)
    const listed = { order: design('V1__ordering_x.sql'), inventory: design('V1__ordering_x.sql') }
    assert.deepEqual(checkDesignMigrations({ migrations, sqls, designs: listed }), [])
  })

  test('SQL に出ないスキーマのモジュールの設計書が common のマイグレーションを挙げると、他のモジュールのものとして失敗にする', () => {
    const problems = checkDesignMigrations({
      migrations: ['V1__common_a.sql'],
      sqls: { 'V1__common_a.sql': "COMMENT ON TABLE catalog.product IS '商品';" },
      designs: { catalog: design('V1__common_a.sql'), tax: design('V1__common_a.sql') },
    })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /tax\.md/)
  })
})
