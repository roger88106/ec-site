import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findEnumNameConversions, findLombok, ruleFor } from './java-rules.mjs'

describe('バックエンドの書き方の検査', () => {
  test('テストのコードの Lombok の import と、完全修飾名の注釈を見つける', () => {
    const text = [
      'package com.ecsite;',
      'import lombok.RequiredArgsConstructor;',
      'import org.junit.jupiter.api.Test;',
      '@lombok.Getter',
      'class FooTest {}',
    ].join('\n')
    assert.deepEqual(findLombok(text).map((hit) => hit.line), [2, 4])
  })

  test('Lombok という語を含むだけの行（コメントや文字列）は見つけない', () => {
    assert.deepEqual(findLombok('// テストでは lombok を使わない\nString s = "lombok";'), [])
  })

  test('列挙型の変換に name() を渡す呼び出しを見つける', () => {
    const text = [
      '        return Status.valueOf(order.status().name());',
      '        return PaymentStatus.fromValue(status.name());',
      '        return switch (status) { case PAID -> Status.PAID; };',
      '        return Status.valueOf(text);',
    ].join('\n')
    assert.deepEqual(findEnumNameConversions(text).map((hit) => hit.line), [1, 2])
  })

  test('name() を渡さない valueOf は見つけない', () => {
    assert.deepEqual(findEnumNameConversions('BigDecimal.valueOf(amount.longValue()); String.valueOf(name)'), [])
  })

  test('Lombok はテストのソースだけを、name() の変換は本番のソースだけを見る', () => {
    assert.equal(ruleFor('backend/ecsite/src/test/java/com/ecsite/FooTest.java'), 'lombok')
    assert.equal(ruleFor('backend/ecsite/src/integrationTest/java/com/ecsite/FooTest.java'), 'lombok')
    assert.equal(ruleFor('backend/ecsite/src/testFixtures/java/com/ecsite/Foo.java'), 'lombok')
    assert.equal(ruleFor('backend/ecsite/src/main/java/com/ecsite/Foo.java'), 'enum-name')
    assert.equal(ruleFor('backend/ecsite/src/main/resources/application.yml'), null)
    assert.equal(ruleFor('frontend/admin/src/lib/foo.ts'), null)
  })
})
