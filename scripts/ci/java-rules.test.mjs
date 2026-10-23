import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import {
  findEnumNameConversions,
  findEnumStringConversions,
  findLombok,
  findOtherNullable,
  isNullableTarget,
  isStringConversionTarget,
  ruleFor,
} from './java-rules.mjs'

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

  test('改行を挟んだ valueOf(x.name()) も、呼び出しの始まりの行で見つける', () => {
    const text = [
      'class A {',
      '    Status f() {',
      '        return Status.valueOf(',
      '            order.status().name());',
      '    }',
      '    Status g() {',
      '        return PaymentStatus.fromValue(status.name()',
      '        );',
      '    }',
      '}',
    ].join('\n')
    assert.deepEqual(findEnumNameConversions(text).map((hit) => hit.line), [3, 7])
  })

  test('valueOf の閉じ括弧より後ろの name() は、引数でないので見つけない', () => {
    const text = [
      'BigDecimal.valueOf(amount)',
      '    .setScale(2)',
      '    .add(of(status.name()));',
      'Status.valueOf(x).name();',
    ].join('\n')
    assert.deepEqual(findEnumNameConversions(text), [])
  })

  test('引数の中の割り算（行頭の / や in / 2）を正規表現と読まず、name() を渡す valueOf を見つける', () => {
    const text = [
      'Status a = Status.valueOf(x',
      '    / 2 == 0 ? a.name() : b.name());',
      'Status b = Status.valueOf(in / 2 == 0 ? a.name() : b.name());',
      'int y = 1;',
    ].join('\n')
    assert.deepEqual(findEnumNameConversions(text).map((hit) => hit.line), [1, 3])
  })

  test('引数の途中に括弧があっても、引数が name() で終わる valueOf は見つける', () => {
    assert.deepEqual(findEnumNameConversions('Status.valueOf(order.items().get(0).status().name());').map((hit) => hit.line), [1])
  })

  test('name() を渡さない valueOf は見つけない', () => {
    assert.deepEqual(findEnumNameConversions('BigDecimal.valueOf(amount.longValue()); String.valueOf(name)'), [])
  })

  test('列挙型の名前の文字列からの変換（文字列リテラル以外を渡す valueOf・fromValue）を見つける', () => {
    const text = [
      '        return Status.valueOf(command.reason());',
      '        return PublicationStatus.fromValue(view.status());',
      '        return com.ecsite.Status.valueOf(text);',
      '        return Status.valueOf("PAID");',
      '        return Status.fromValue("PAID");',
      '        return switch (status) { case PAID -> Status.PAID; };',
    ].join('\n')
    assert.deepEqual(findEnumStringConversions(text).map((hit) => hit.line), [1, 2, 3])
  })

  test('列挙でない型の valueOf は、文字列からの変換として見つけない', () => {
    const text = [
      'String.valueOf(count);',
      'Integer.valueOf(text);',
      'Long.valueOf(text);',
      'Boolean.valueOf(text);',
      'BigDecimal.valueOf(amount);',
      'BigInteger.valueOf(amount);',
      'Double.valueOf(text);',
      'java.lang.String.valueOf(chars);',
      'Enum.valueOf(targetType, text);',
    ].join('\n')
    assert.deepEqual(findEnumStringConversions(text), [])
  })

  test('文字列からの変換は、domain と infrastructure/persistence 以外の本番のソースだけを見る', () => {
    const base = 'backend/ecsite/src/main/java/com/ecsite/module/inventory'
    assert.equal(isStringConversionTarget(`${base}/application/StockCommandService.java`), true)
    assert.equal(isStringConversionTarget(`${base}/presentation/InventoryMapper.java`), true)
    assert.equal(isStringConversionTarget(`${base}/domain/stock/StockAdjustment.java`), false)
    assert.equal(isStringConversionTarget(`${base}/infrastructure/persistence/JooqStockQueryRepository.java`), false)
    assert.equal(isStringConversionTarget('backend/ecsite/src/test/java/com/ecsite/FooTest.java'), false)
  })

  test('Lombok はテストのソースだけを、name() の変換は本番のソースだけを見る', () => {
    assert.equal(ruleFor('backend/ecsite/src/test/java/com/ecsite/FooTest.java'), 'lombok')
    assert.equal(ruleFor('backend/ecsite/src/integrationTest/java/com/ecsite/FooTest.java'), 'lombok')
    assert.equal(ruleFor('backend/ecsite/src/testFixtures/java/com/ecsite/Foo.java'), 'lombok')
    assert.equal(ruleFor('backend/ecsite/src/main/java/com/ecsite/Foo.java'), 'enum-name')
    assert.equal(ruleFor('backend/ecsite/src/main/resources/application.yml'), null)
    assert.equal(ruleFor('frontend/admin/src/lib/foo.ts'), null)
  })

  test('JSpecify 以外の Nullable の import（static import も）と、完全修飾名の注釈を見つける', () => {
    const text = [
      'package com.ecsite;',
      'import jakarta.annotation.Nullable;',
      'import javax.annotation.Nullable;',
      'import org.springframework.lang.Nullable;',
      'import static jakarta.annotation.Nullable.*;',
      'void f(@jakarta.annotation.Nullable String a, @javax.annotation.Nullable String b, @org.springframework.lang.Nullable String c) {}',
    ].join('\n')
    assert.deepEqual(findOtherNullable(text).map((hit) => hit.line), [2, 3, 4, 5, 6])
  })

  test('JSpecify の Nullable と、Nullable という語を含むだけの行は見つけない', () => {
    const text = [
      'import org.jspecify.annotations.Nullable;',
      'import jakarta.annotation.Resource;',
      'import org.springframework.lang.NonNull;',
      '// jakarta.annotation.Nullable は使わない',
      'String s = "import javax.annotation.Nullable;";',
      'void f(@Nullable String a, @org.jspecify.annotations.Nullable String b) {}',
    ].join('\n')
    assert.deepEqual(findOtherNullable(text), [])
  })

  test('他の Nullable は本番とテストのソース（tools も）を見て、生成したコードは見ない', () => {
    const base = 'backend/ecsite/src'
    assert.equal(isNullableTarget(`${base}/main/java/com/ecsite/Foo.java`), true)
    assert.equal(isNullableTarget(`${base}/test/java/com/ecsite/FooTest.java`), true)
    assert.equal(isNullableTarget(`${base}/integrationTest/java/com/ecsite/FooTest.java`), true)
    assert.equal(isNullableTarget(`${base}/testFixtures/java/com/ecsite/Foo.java`), true)
    assert.equal(isNullableTarget(`${base}/tools/java/com/ecsite/Foo.java`), true)
    assert.equal(isNullableTarget('backend/ecsite/build/generated-sources/jooq/com/ecsite/jooq/Foo.java'), false)
    assert.equal(isNullableTarget('backend/ecsite/build/generated/openapi/src/main/java/com/ecsite/api/Foo.java'), false)
    assert.equal(isNullableTarget(`${base}/main/resources/application.yml`), false)
    assert.equal(isNullableTarget('frontend/admin/src/lib/foo.ts'), false)
  })
})

describe('name() の変換の検査の境界', () => {
  test('引数の途中の空白・改行・コメントを挟んだ呼び出しも見つけ、始まりの行を返す', () => {
    const text = [
      'Status.valueOf(order.status()',
      '    .name());',
      'Status.valueOf(order',
      '  .status()',
      '  .name());',
      'Status.valueOf(',
      '  // c',
      '  order.status().name());',
      'valueOf(/* c */ a.name());',
      'Status.valueOf(order.status());',
    ].join('\n')
    assert.deepEqual(findEnumNameConversions(text).map((hit) => hit.line), [1, 3, 6, 9])
  })

  test('コメントの中だけにある呼び出しは見つけない。文字列の中の // はコメントにしない', () => {
    const text = [
      '// Status.valueOf(a.name());',
      '/* Status.valueOf(a.name()); */',
      'String url = "http://x"; Status.valueOf(a.name());',
    ].join('\n')
    assert.deepEqual(findEnumNameConversions(text).map((hit) => hit.line), [3])
  })

})

describe('コメントの中を違反と読まない', () => {
  test('コメントの中の Lombok は見つけず、コードの Lombok は行番号のまま見つける', () => {
    const text = ['// import lombok.Data;', '/**', ' * @lombok.Getter と @Data は使わない', ' */', 'import lombok.Data;'].join('\n')
    assert.deepEqual(findLombok(text).map((hit) => hit.line), [5])
  })

  test('コメントの中の valueOf（文字列リテラル以外）は見つけず、コードの valueOf は見つける', () => {
    const text = ['// Status.valueOf(x);', '/* Status.valueOf(x) */', '/** Status.fromValue(x) */', 'Status s = Status.valueOf(x); // ok'].join('\n')
    assert.deepEqual(findEnumStringConversions(text).map((hit) => hit.line), [4])
    assert.equal(findEnumStringConversions(text)[0].text, 'Status s = Status.valueOf(x); // ok')
  })

  test('文字列リテラルの中の // の後ろにある valueOf は、コードとして見つける', () => {
    const text = 'String u = "http://x"; Status s = Status.valueOf(x);'
    assert.deepEqual(findEnumStringConversions(text).map((hit) => hit.line), [1])
  })

  test('コメントの中の Nullable は見つけず、コードの Nullable は見つける', () => {
    const text = [
      '// import org.springframework.lang.Nullable;',
      '/* @org.springframework.lang.Nullable */',
      '/**',
      ' * {@code @javax.annotation.Nullable} は使わない',
      ' */',
      'import javax.annotation.Nullable;',
    ].join('\n')
    assert.deepEqual(findOtherNullable(text).map((hit) => hit.line), [6])
  })
})
