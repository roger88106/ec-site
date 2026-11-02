import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { GLOSSARIES, checkGlossary, findMissing, isExclusionNeeded, isSearchedFile, parseGlossaryIds } from './glossary-ids.mjs'

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
    assert.equal(isSearchedFile('scripts/ci/ja-text.json'), false)
    assert.equal(isSearchedFile('scripts/ci/glossary-ids-exclusions.json'), false)
    assert.equal(isSearchedFile('scripts/ci/glossary-ids.mjs'), true)
    assert.equal(isSearchedFile('scripts/dev/lib/slot.test.mjs'), true)
    assert.equal(isSearchedFile('backend/ecsite/src/Foo.java'), true)
    assert.equal(isSearchedFile('frontend/store/logo.png'), false)
  })
})

describe('用語集の表の見出し', () => {
  test('見出しの行が「用語、識別子、…」の表は、複数あっても、他の見出しの表があっても問題にしない', () => {
    const markdown = [GLOSSARY, '', '| 配送業者 | 配送中 | 返送 |', '|---|---|---|', '| a | b | c |', '', '| 用語 | 識別子 | 発行元 |', '|------|--------|--------|', '| 追加 | `Added` | x |'].join('\n')
    assert.deepEqual(checkGlossary(markdown), [])
  })

  test('見出しの行の 2 列目が「識別子」でない表（列の入れ替え、識別子の列がない表）を問題にする', () => {
    const swapped = ['| 用語 | 意味 | 識別子 |', '|------|------|--------|', '| 注文 | 注文 | `Order` |'].join('\n')
    const identifierFirst = ['| 識別子 | 用語 | 意味 |', '|--------|------|------|', '| `Order` | 注文 | 注文 |'].join('\n')
    const twoColumns = ['| 用語 | 意味 |', '|------|------|', '| 注文 | 注文 |'].join('\n')
    for (const markdown of [swapped, identifierFirst, twoColumns]) {
      const problems = checkGlossary([GLOSSARY, '', markdown].join('\n'))
      assert.equal(problems.length, 1, markdown)
      assert.match(problems[0], /^[0-9]+ 行：表の見出しの行の 2 列目が「識別子」でない/)
    }
  })

  test('識別子を 1 つも取り出せない用語集（列を入れ替えた表だけ、表がない）を問題にする', () => {
    const swapped = ['| 用語 | 意味 | 識別子 |', '|------|------|--------|', '| 注文 | 注文 | `Order` |'].join('\n')
    for (const markdown of [swapped, '# 用語集\n\n説明だけ。\n']) {
      assert.ok(checkGlossary(markdown).some((problem) => /識別子を 1 つも取り出せない/.test(problem)), markdown)
    }
  })
})

describe('調べる用語集', () => {
  test('業務の用語集と開発の用語集の両方を読む', () => {
    assert.deepEqual(GLOSSARIES, ['docs/03-機能仕様/glossary.md', 'docs/05-開発規則/glossary.md'])
  })
})

describe('除外の一覧は根拠にしない', () => {
  test('scripts/ci/ の全ての *-exclusions.json を調べない', () => {
    assert.equal(isSearchedFile('scripts/ci/migrations-exclusions.json'), false)
    assert.equal(isSearchedFile('scripts/ci/ac-exclusions.json'), false)
    assert.equal(isSearchedFile('scripts/ci/frontend-rules-exclusions.json'), false)
    assert.equal(isSearchedFile('scripts/ci/hardcoded-text-exclusions.json'), false)
    assert.equal(isSearchedFile('scripts/ci/message-numbers-exclusions.json'), false)
    assert.equal(isSearchedFile('scripts/dev/lib/x-exclusions.json'), true)
  })
})
