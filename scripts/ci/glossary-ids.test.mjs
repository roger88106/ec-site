import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findMissing, isExclusionNeeded, isSearchedFile, parseGlossaryIds } from './glossary-ids.mjs'

const GLOSSARY = [
  '| 用語 | 識別子 | 意味 |',
  '|------|--------|------|',
  '| 注文 | `Order`、`ordering.sales_order` | 注文 |',
  '| 値オブジェクトの置き場所 | `common/model/value/` | 共通の型 |',
  '| 更新の service | `XxxCommandService` | 名前の形 |',
  '| 発生日時 | `occurredAt`（`occurred_at`） | 発生した時刻 |',
  '| キャンセルした運営者 | `cancelled_by_id`・`_name` | ID と氏名 |',
  '| mock フラグ | `ecsite.<モジュール>.mock.enabled`、`*ContractTest` | 形 |',
  '| 説明だけの行 | — | 識別子がない |',
].join('\n')

describe('用語集の識別子の検査', () => {
  test('識別子の列から、バッククォートの中を読む。省略形を補い、名前の形の例（Xxx、<…>、*）は読まない', () => {
    assert.deepEqual(parseGlossaryIds(GLOSSARY), [
      'Order',
      'ordering.sales_order',
      'common/model/value/',
      'occurredAt',
      'occurred_at',
      'cancelled_by_id',
      'cancelled_by_name',
    ])
  })

  test('kebab-case の設定の名前は、camelCase の語があればコードにあるとみなす', () => {
    assert.deepEqual(findMissing(['fiscal-year-start-month', 'no-such-name'], new Set(['fiscalYearStartMonth']), ''), ['no-such-name'])
  })

  test('コードの語にも、コードの中身にもない識別子を返す', () => {
    const tokens = new Set(['Order', 'occurredAt', 'sales_order'])
    const content = 'select * from ordering.sales_order; // com/ecsite/common/model/value/Money.java'
    assert.deepEqual(findMissing(['Order', 'ordering.sales_order', 'common/model/value/', 'occurredAt', 'occurred_at'], tokens, content), [
      'occurred_at',
    ])
  })

  test('除外が不要なのは、用語集にない名前だけ。コードに偶然見つかっても不要としない', () => {
    const ids = ['develop', 'Order']
    assert.equal(isExclusionNeeded('develop', ids), true)
    assert.equal(isExclusionNeeded('removed-name', ids), false)
  })

  test('検査の script のテストと例の文字、使わない語の一覧、除外の一覧は、コードの根拠にしない', () => {
    assert.equal(isSearchedFile('scripts/ci/glossary-ids.test.mjs'), false)
    assert.equal(isSearchedFile('scripts/ci/report/testdata/pit-mutations.xml'), false)
    assert.equal(isSearchedFile('scripts/ci/doc-terms.json'), false)
    assert.equal(isSearchedFile('scripts/ci/glossary-ids-exclusions.json'), false)
    assert.equal(isSearchedFile('scripts/ci/glossary-ids.mjs'), true)
    assert.equal(isSearchedFile('scripts/dev/lib/slot.test.mjs'), true)
    assert.equal(isSearchedFile('backend/ecsite/src/Foo.java'), true)
    assert.equal(isSearchedFile('frontend/store/logo.png'), false)
  })
})
