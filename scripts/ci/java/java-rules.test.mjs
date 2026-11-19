import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { withoutGitEnv } from '../../shared/git/git-env.mjs'
import {
  findEnumNameConversions,
  findUnexplainedSuppressWarnings,
  isEnumNameTarget,
  isSuppressWarningsTarget,
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

  test('共通の変換のメソッドのファイルは、name() の変換を禁じる場所から除く。他の本番のファイルは除かない', () => {
    assert.equal(isEnumNameTarget('backend/ecsite/src/main/java/com/ecsite/common/conversion/EnumConversion.java'), false)
    assert.equal(isEnumNameTarget('backend/ecsite/src/main/java/com/ecsite/common/conversion/Other.java'), true)
    assert.equal(isEnumNameTarget('backend/ecsite/src/main/java/com/ecsite/module/order/presentation/OrderMapper.java'), true)
  })

  test('Enum.valueOf(型, x.name()) は、引数が name() で終わるので見つける', () => {
    assert.deepEqual(findEnumNameConversions('return Enum.valueOf(targetType, source.name());').map((hit) => hit.line), [1])
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

  test('@interface は注釈として飛ばさないので、その前の // は @SuppressWarnings の理由にならない', () => {
    const text = ['// 理由', '@interface', '@SuppressWarnings("unchecked")', 'Map a() {}'].join('\n')
    assert.deepEqual(findUnexplainedSuppressWarnings(text).map((hit) => hit.line), [3])
  })

  test('全てのソースセット（main、test、integrationTest、testFixtures、tools、errorprone）を見て、生成したコードは見ない', () => {
    const base = 'backend/ecsite/src'
    for (const set of ['main', 'test', 'integrationTest', 'testFixtures', 'tools', 'errorprone']) {
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

  test('受け手のない name()（列挙の中の呼び出し）も見つける。getName() は見つけない', () => {
    const text = [
      'return Status.valueOf(name());',
      'return Enum.valueOf(X.class, name());',
      'return Status.valueOf(getName());',
      'return Status.valueOf(x.getName());',
      'return Status.valueOf(rename());',
    ].join('\n')
    assert.deepEqual(findEnumNameConversions(text).map((hit) => hit.line), [1, 2])
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

describe('script として実行する', () => {
  const script = fileURLToPath(new URL('./java-rules.mjs', import.meta.url))
  const main = 'backend/ecsite/src/main/java/com/ecsite/Foo.java'

  /**
   * 一時の Git のリポジトリにファイルを置いて、script を実行する。
   *
   * @param {Record<string, string>} files リポジトリのルートからのパスと内容
   * @returns {import('node:child_process').SpawnSyncReturns<string>} 実行の結果
   */
  function run(files) {
    const dir = mkdtempSync(path.join(tmpdir(), 'java-rules-'))
    const env = withoutGitEnv()
    try {
      spawnSync('git', ['init', '-q'], { cwd: dir, env })
      for (const [file, text] of Object.entries(files)) {
        mkdirSync(path.dirname(path.join(dir, file)), { recursive: true })
        writeFileSync(path.join(dir, file), text)
      }
      return spawnSync(process.execPath, [script], { cwd: dir, env, encoding: 'utf8' })
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  }

  test('違反がなければ、終了コード 0 で、違反の数を出して終わる', () => {
    const result = run({ [main]: '// 理由\n@SuppressWarnings("unchecked")\nMap a() {}\n' })
    assert.equal(result.status, 0, result.stdout)
    assert.match(result.stdout, /バックエンドの書き方の違反：0/)
  })

  test('違反があれば、ファイルと行を出し、終了コード 1 で終わる', () => {
    const result = run({ [main]: 'int x;\n@SuppressWarnings("unchecked")\nMap a() {}\nStatus s = Status.valueOf(a.name());\n' })
    assert.equal(result.status, 1, result.stdout)
    assert.ok(result.stdout.includes(`${main}:2 @SuppressWarnings の直前の行に`), result.stdout)
    assert.ok(result.stdout.includes(`${main}:4 列挙型の変換に name() を渡す`), result.stdout)
    assert.match(result.stdout, /バックエンドの書き方の違反：2/)
  })

  test('共通の変換のメソッドのファイルでは Enum.valueOf(型, x.name()) を違反にせず、他の本番のファイルでは違反にする', () => {
    const conversion = 'backend/ecsite/src/main/java/com/ecsite/common/conversion/EnumConversion.java'
    const text = 'return Enum.valueOf(targetType, source.name());\n'
    const allowed = run({ [conversion]: text })
    assert.equal(allowed.status, 0, allowed.stdout)
    const other = run({ [main]: text })
    assert.equal(other.status, 1, other.stdout)
    assert.ok(other.stdout.includes(`${main}:1 列挙型の変換に name() を渡す`), other.stdout)
  })

  test('name() の変換は本番のソースだけを見るので、テストのソースは @SuppressWarnings だけを見る', () => {
    const result = run({ 'backend/ecsite/src/test/java/com/ecsite/FooTest.java': 'Status s = Status.valueOf(a.name());\n' })
    assert.equal(result.status, 0, result.stdout)
  })

  test('対象のファイルが 0 件なら、理由を出して終了コード 2 で終わる（lib の exitIfNoFiles）', () => {
    const result = run({ 'README.md': '@SuppressWarnings\n' })
    assert.equal(result.status, 2, result.stdout)
    assert.match(result.stdout, /1 件も見つからない/)
    assert.doesNotMatch(result.stdout, /違反：/)
  })
})
