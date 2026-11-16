import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import {
  findEnumNameConversions,
  findEnumStringConversions,
  findUnexplainedSuppressWarnings,
  isEnumNameTarget,
  isSuppressWarningsTarget,
  isStringConversionTarget,
} from './java-rules.mjs'

describe('バックエンドの書き方の検査', () => {
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

  test('name() の変換は、本番のソースだけを見る', () => {
    assert.equal(isEnumNameTarget('backend/ecsite/src/main/java/com/ecsite/Foo.java'), true)
    assert.equal(isEnumNameTarget('backend/ecsite/src/test/java/com/ecsite/FooTest.java'), false)
    assert.equal(isEnumNameTarget('backend/ecsite/src/main/resources/application.yml'), false)
    assert.equal(isEnumNameTarget('frontend/admin/src/lib/foo.ts'), false)
  })
})

describe('@SuppressWarnings の理由の検査', () => {
  test('直前に理由の // のコメントがあれば見つけない。間にある他の注釈は飛ばす', () => {
    const text = [
      '// YAML を読んだ Map を、契約の形が決まっているので型に当てはめる',
      '@SuppressWarnings("unchecked")',
      'Map<String, Object> a() {}',
      '// 理由',
      '@Override',
      '@SuppressWarnings({"rawtypes", "unchecked"})',
      'Map b() {}',
    ].join('\n')
    assert.deepEqual(findUnexplainedSuppressWarnings(text), [])
  })

  test('理由のコメントがない @SuppressWarnings を見つけ、行番号を返す', () => {
    const text = ['class A {', '    int x;', '    @SuppressWarnings("unchecked")', '    Map a() {}', '}'].join('\n')
    const hits = findUnexplainedSuppressWarnings(text)
    assert.deepEqual(hits.map((hit) => hit.line), [3])
    assert.equal(hits[0].text, '@SuppressWarnings("unchecked")')
  })

  test('Javadoc だけで、直前に // のコメントがない @SuppressWarnings は見つける', () => {
    const text = [
      '/**',
      ' * 型を当てはめる。',
      ' */',
      '@SuppressWarnings("unchecked")',
      'Map a() {}',
      '/* 理由 */',
      '@SuppressWarnings("unchecked")',
      'Map b() {}',
      '// 理由',
      '',
      '@SuppressWarnings("unchecked")',
      'Map c() {}',
    ].join('\n')
    assert.deepEqual(findUnexplainedSuppressWarnings(text).map((hit) => hit.line), [4, 7, 11])
  })

  test('コメントや文字列の中の @SuppressWarnings は見つけない', () => {
    const text = ['// @SuppressWarnings("unchecked") は理由を書く', '/** {@code @SuppressWarnings} */', 'String s = "@SuppressWarnings";'].join('\n')
    assert.deepEqual(findUnexplainedSuppressWarnings(text), [])
  })

  test('行の途中の @SuppressWarnings（引数、修飾子の後ろ、拡張 for、完全修飾名、入れ子の括弧の注釈の後ろ）も見つける', () => {
    const text = [
      'void m(@SuppressWarnings("unused") String x) {}',
      'private @SuppressWarnings("unchecked") List<X> a;',
      'final @SuppressWarnings("unchecked") var y = z;',
      'for (@SuppressWarnings("unused") String s : list) {}',
      '@java.lang.SuppressWarnings("unchecked")',
      'Map b() {}',
      '@Foo(x = @Bar("y")) @SuppressWarnings("z")',
      'Map c() {}',
    ].join('\n')
    assert.deepEqual(findUnexplainedSuppressWarnings(text).map((hit) => hit.line), [1, 2, 3, 4, 5, 7])
  })

  test('行の途中の @SuppressWarnings も、直前に // の理由があれば見つけない', () => {
    const text = [
      '// 理由',
      'void m(@SuppressWarnings("unused") String x) {}',
      '// 理由',
      'private @SuppressWarnings("unchecked") List<X> a;',
      '// 理由',
      '@java.lang.SuppressWarnings("unchecked")',
      'Map b() {}',
      '// 理由',
      '@Foo(x = @Bar("y")) @SuppressWarnings("z")',
      'Map c() {}',
    ].join('\n')
    assert.deepEqual(findUnexplainedSuppressWarnings(text), [])
  })

  test('間にある他の注釈や直前の @SuppressWarnings が複数行でも、直前に // の理由があれば見つけない', () => {
    const text = [
      '// 理由',
      '@Foo(',
      '    a = 1)',
      '@SuppressWarnings("unchecked")',
      'Map a() {}',
      '// 理由',
      '@SuppressWarnings({',
      '    "unchecked",',
      '    "rawtypes"})',
      '@Foo(',
      '    b = 2)',
      'Map b() {}',
    ].join('\n')
    assert.deepEqual(findUnexplainedSuppressWarnings(text), [])
  })

  test('複数行の注釈の前に // の理由がなければ見つける', () => {
    const text = ['int x;', '@Foo(', '    a = 1)', '@SuppressWarnings("unchecked")', 'Map a() {}'].join('\n')
    assert.deepEqual(findUnexplainedSuppressWarnings(text).map((hit) => hit.line), [4])
  })

  test('テキストブロックの中の、行の先頭の @SuppressWarnings は見つけない', () => {
    const text = ['String s = """', '    text', '@SuppressWarnings("unchecked")', '    """;', 'int x;'].join('\n')
    assert.deepEqual(findUnexplainedSuppressWarnings(text), [])
  })

  test('全てのソースセット（main、test、integrationTest、testFixtures、tools）を見て、生成したコードは見ない', () => {
    const base = 'backend/ecsite/src'
    for (const set of ['main', 'test', 'integrationTest', 'testFixtures', 'tools']) {
      assert.equal(isSuppressWarningsTarget(`${base}/${set}/java/com/ecsite/Foo.java`), true)
    }
    assert.equal(isSuppressWarningsTarget('backend/ecsite/build/generated-sources/jooq/com/ecsite/jooq/Foo.java'), false)
    assert.equal(isSuppressWarningsTarget(`${base}/main/resources/application.yml`), false)
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
  test('コメントの中の valueOf（文字列リテラル以外）は見つけず、コードの valueOf は見つける', () => {
    const text = ['// Status.valueOf(x);', '/* Status.valueOf(x) */', '/** Status.fromValue(x) */', 'Status s = Status.valueOf(x); // ok'].join('\n')
    assert.deepEqual(findEnumStringConversions(text).map((hit) => hit.line), [4])
    assert.equal(findEnumStringConversions(text)[0].text, 'Status s = Status.valueOf(x); // ok')
  })

  test('文字列リテラルの中の // の後ろにある valueOf は、コードとして見つける', () => {
    const text = 'String u = "http://x"; Status s = Status.valueOf(x);'
    assert.deepEqual(findEnumStringConversions(text).map((hit) => hit.line), [1])
  })
})
