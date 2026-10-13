import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findSpecNumbers, isProductionFile } from './spec-numbers.mjs'

describe('本番のコードの spec の番号の検査', () => {
  test('AC・DC などの番号、タスクの番号、spec・plan の番号を見つける', () => {
    const text = [
      '// 配信する画像は WebP にする（AC-68）',
      '/** 共有の基準（DC-49、T6305） */',
      '// plan 001 第 7 節、spec 003',
      '// 配信する画像は、すべて WebP にする',
    ].join('\n')
    assert.deepEqual(
      findSpecNumbers(text).map((hit) => `${hit.line} ${hit.number}`),
      ['1 AC-68', '2 DC-49', '2 T6305', '3 plan 001', '3 spec 003'],
    )
  })

  test('番号に似た識別子は見つけない', () => {
    assert.deepEqual(findSpecNumbers('const ACTION = "X-12"; const T10 = 1; const UTF8 = true'), [])
  })

  test('テスト、生成物、マイグレーションは見ない', () => {
    assert.equal(isProductionFile('backend/ecsite/src/main/java/com/ecsite/Foo.java'), true)
    assert.equal(isProductionFile('frontend/store/src/lib/price.ts'), true)
    assert.equal(isProductionFile('frontend/store/src/lib/price.test.ts'), false)
    assert.equal(isProductionFile('frontend/admin/src/test/server.ts'), false)
    assert.equal(isProductionFile('frontend/admin/src/api/generated/schema.ts'), false)
    assert.equal(isProductionFile('backend/ecsite/src/main/resources/db/migration/V1__init.sql'), false)
    assert.equal(isProductionFile('backend/ecsite/src/test/java/com/ecsite/FooTest.java'), false)
  })
})
