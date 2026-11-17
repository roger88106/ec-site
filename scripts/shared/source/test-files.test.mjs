import assert from 'node:assert/strict'
import path from 'node:path'
import { describe, test } from 'node:test'
import { extractTestNames, findTestFiles, readTsTests, scanTsTests, testFileKind } from './test-files.mjs'

describe('テストのファイルの種類', () => {
  test('Java の src/test・src/integrationTest と .test.ts(x)・.spec.ts を選び、他は null にする', () => {
    const cases = [
      ['backend/ecsite/src/test/java/a/ATest.java', 'java'],
      ['backend/ecsite/src/integrationTest/java/a/BIT.java', 'java'],
      ['backend/ecsite/src/main/java/a/Main.java', null],
      ['backend/ecsite/src/test/resources/x.sql', null],
      ['frontend/store/src/a.test.ts', 'ts'],
      ['frontend/store/src/b.test.tsx', 'ts'],
      ['frontend/store/e2e/c.spec.ts', 'ts'],
      ['frontend/store/src/d.spec.tsx', null],
      ['frontend/store/src/e.ts', null],
    ]
    for (const [file, kind] of cases) assert.equal(testFileKind(file), kind, file)
  })

  test('選んだテストのファイルを、絶対パスと種類にして、順を保って返す', () => {
    const files = ['backend/ecsite/src/test/java/a/ATest.java', 'frontend/store/src/e.ts', 'frontend/store/src/a.test.ts']
    assert.deepEqual(
      findTestFiles('/r', files).map((entry) => [path.relative('/r', entry.file).split(path.sep).join('/'), entry.kind]),
      [
        ['backend/ecsite/src/test/java/a/ATest.java', 'java'],
        ['frontend/store/src/a.test.ts', 'ts'],
      ],
    )
  })
})

describe('TypeScript のテストの読み方', () => {
  /** 読めたテストの名前と、実行されないか */
  const namesAndSkipped = (source) => readTsTests(source).map(({ name, skipped }) => [name, skipped])

  test('skip・fixme のテストと、まとまりの中のテストを、実行されないものとして全て返す', () => {
    const source = [
      "it('通常', () => {})",
      "test.skip('飛ばす', () => {})",
      "it.fixme('保留', () => {})",
      "describe.skip('まとまり', () => {",
      "  it('中', () => {})",
      '})',
      "/re/.test('x')",
      "test.describe('グループ', () => { test.only('中の通常', () => {}) })",
    ].join('\n')
    assert.deepEqual(namesAndSkipped(source), [
      ['通常', false],
      ['飛ばす', true],
      ['保留', true],
      ['中', true],
      ['中の通常', false],
    ])
  })

  test('xdescribe と test.describe.fixme のまとまりの中のテストも、実行されないものにする', () => {
    const source = ["xdescribe('a', () => {", "  it('中 1', () => {})", '})', "test.describe.fixme('b', () => {", "  test('中 2', () => {})", '})', "test('外', () => {})"].join('\n')
    assert.deepEqual(namesAndSkipped(source), [
      ['中 1', true],
      ['中 2', true],
      ['外', false],
    ])
  })

  test('実行されるテストの名前だけを取り出す', () => {
    const source = "it('a', () => {})\ntest.skip('b', () => {})\ntest.each([1])('c %s', () => {})"
    assert.deepEqual(extractTestNames(source, 'ts'), ['a', 'c %s'])
  })

  test('each の表の中に関数の呼び出し・入れ子の括弧・文字列と正規表現の中の ) があっても、テスト名を読む', () => {
    const source = [
      "test.each([[f(1), 'a']])('AC-1 関数 %s', () => {})",
      "it.each([[[1, 2], (x) => g(h(x))]])('AC-2 入れ子 %s', () => {})",
      "test.each([['a)b', 1]])('AC-3 文字列 %s', () => {})",
      "test.each([[/\\)/.source]])('AC-4 正規表現 %s', () => {})",
      'it.each`',
      '  a | b',
      '  ${f(1)} | ${2}',
      "`('AC-5 テンプレートの表 $a', () => {})",
      "test.skip.each([[f(1)]])('AC-6 飛ばす', () => {})",
    ].join('\n')
    assert.deepEqual(namesAndSkipped(source), [
      ['AC-1 関数 %s', false],
      ['AC-2 入れ子 %s', false],
      ['AC-3 文字列 %s', false],
      ['AC-4 正規表現 %s', false],
      ['AC-5 テンプレートの表 $a', false],
      ['AC-6 飛ばす', true],
    ])
    assert.deepEqual(scanTsTests(source).unreadable, [])
  })

  test('test.for と skipIf・runIf・sequential・fails・todo の付いた呼び出しを読む', () => {
    const source = [
      "test.for([[1, 2]])('AC-1 表', () => {})",
      "test.skipIf(isCi)('AC-2 条件で飛ばす', () => {})",
      "test.runIf(isCi)('AC-3 条件で実行', () => {})",
      "test.sequential('AC-4 順に', () => {})",
      "test.fails('AC-5 失敗する', () => {})",
      "it.todo('AC-6 後で')",
    ].join('\n')
    assert.deepEqual(namesAndSkipped(source), [
      ['AC-1 表', false],
      ['AC-2 条件で飛ばす', false],
      ['AC-3 条件で実行', false],
      ['AC-4 順に', false],
      ['AC-5 失敗する', false],
      ['AC-6 後で', true],
    ])
    assert.deepEqual(scanTsTests(source).unreadable, [])
  })

  test('コメント・ブロックコメント・文字列・テンプレートの中の test・it の呼び出しは、テストとして数えない', () => {
    const source = [
      "// test('AC-1 行コメント', () => {})",
      "/* it('AC-2 ブロックコメント', () => {}) */",
      "const s = \"test('AC-3 文字列', 1)\"",
      "const t = `it('AC-4 テンプレート', 1)`",
      "test('AC-5 コード', () => {})",
    ].join('\n')
    assert.deepEqual(extractTestNames(source, 'ts'), ['AC-5 コード'])
  })

  test('each の表と名前のテンプレートの ${...} の中に、入れ子のテンプレートや括弧があっても、テスト名を読む', () => {
    const source = [
      'test.each([[`a${`b)`}`]])(`AC-1 入れ子 %s`, () => {})',
      "it.each([[`${f(')')}`, `${{ a: `}` }.a}`]])('AC-2 括弧と波括弧', () => {})",
      'test(`AC-3 名前 ${`x`}`, () => {})',
    ].join('\n')
    assert.deepEqual(
      readTsTests(source).map(({ name }) => name),
      ['AC-1 入れ子 %s', 'AC-2 括弧と波括弧', 'AC-3 名前 ${`x`}'],
    )
    assert.deepEqual(scanTsTests(source).unreadable, [])
  })

  test('skip のまとまりの呼び出しがコメントや文字列の中にあっても、後ろのテストを実行されないものにしない', () => {
    const source = ['// describe.skip(', "const s = 'describe.skip('", "test('AC-1 a', () => {})", '/* xdescribe( */', "test('AC-2 b', () => {})"].join('\n')
    assert.deepEqual(
      readTsTests(source).map(({ skipped }) => skipped),
      [false, false],
    )
  })

  test('JSX の単独の引用符（it\'s）で、後ろを全て文字列と読まない', () => {
    const source = ["const a = <p>it's</p>", "describe.skip('x', () => {})", "test('AC-1 a', () => {})"].join('\n')
    assert.deepEqual(
      readTsTests(source).map(({ skipped }) => skipped),
      [false],
    )
  })

  test('1 回の走査で、読めたテストと読めない呼び出しを両方返す', () => {
    const source = ["test('AC-1 a', () => {})", 'test(name, () => {})'].join('\n')
    const { tests, unreadable } = scanTsTests(source)
    assert.deepEqual(
      tests.map(({ name }) => name),
      ['AC-1 a'],
    )
    assert.equal(unreadable.length, 1)
  })
})

describe('TypeScript の読めない呼び出し', () => {
  /** 読めない呼び出しの位置と理由 */
  const unreadable = (source) => scanTsTests(source).unreadable.map(({ index, reason }) => [index, reason])

  test('名前が文字列でない呼び出しと、括弧が閉じない呼び出しを、位置と理由で返す', () => {
    const source = ['test(name, () => {})', 'it.each([[1]])(title, () => {})', 'test.each([[1, 2]', "test('閉じない名前"].join('\n')
    const problems = scanTsTests(source).unreadable
    assert.deepEqual(
      problems.map(({ index }) => index),
      [0, source.indexOf('it.each'), source.indexOf('test.each'), source.indexOf("test('閉")],
    )
    assert.match(problems[0].reason, /テスト名/)
    assert.match(problems[1].reason, /テスト名/)
    assert.match(problems[2].reason, /括弧/)
    assert.match(problems[3].reason, /テスト名/)
  })

  test('条件の修飾の後に括弧がないか、括弧が閉じない呼び出しは、理由を添えて返す', () => {
    assert.deepEqual(unreadable("test.skipIf.only('a', () => {})"), [[0, 'skipIf の条件の括弧がない']])
    assert.deepEqual(unreadable('test.runIf(isCi'), [[0, 'runIf の条件の括弧が閉じない']])
  })

  test('each の表のテンプレートと、テスト名のテンプレートが閉じない呼び出しは、理由を添えて返す', () => {
    assert.deepEqual(unreadable('test.each`a | b'), [[0, 'each の表のテンプレートが閉じない']])
    assert.deepEqual(unreadable('test(`名前'), [[0, 'テスト名のテンプレートが閉じない']])
  })

  test('each の後に表がない呼び出しは、テストでも読めない呼び出しでもない', () => {
    const source = "test.each.only('a', () => {})"
    assert.deepEqual(scanTsTests(source), { tests: [], unreadable: [] })
  })

  test('知らない修飾の付いた test・it の呼び出しを、読めない呼び出しにする', () => {
    const source = "test.shuffle('AC-1 知らない', () => {})\nit.unknown.only('AC-2 知らない', () => {})"
    const problems = scanTsTests(source).unreadable
    assert.deepEqual(
      problems.map(({ index }) => index),
      [0, source.indexOf('it.unknown')],
    )
    assert.match(problems[0].reason, /修飾/)
  })

  test('テストの宣言でない呼び出しは、読めない呼び出しにしない', () => {
    const source = [
      "test.describe('まとまり', () => {})",
      'test.use({ locale: "ja" })',
      'test.step(name, async () => {})',
      "test.skip(browserName === 'webkit', '理由')",
      'test.fixme()',
      'test.setTimeout(10)',
      'expect(/re/.test(name)).toBe(true)',
      'pattern.test(name)',
      '// test(name) はコメント',
      "const text = 'test(name)'",
    ].join('\n')
    assert.deepEqual(scanTsTests(source).unreadable, [])
  })

  test('each の表の後に、名前の括弧が続かない呼び出しは、読めない呼び出しにする', () => {
    const source = 'test.each([[1]])`AC-1 名前`'
    assert.deepEqual(readTsTests(source), [])
    assert.deepEqual(
      scanTsTests(source).unreadable.map(({ index }) => index),
      [0],
    )
  })
})

describe('Java のテストの読み方', () => {
  test('メソッドの @DisplayName を読み、クラスの名前を読まない', () => {
    const source = ['@DisplayName("まとまり")', 'class A {', '  @Test', '  @DisplayName("AC-1 通る")', '  void a() {}', '}'].join('\n')
    assert.deepEqual(extractTestNames(source, 'java'), ['AC-1 通る'])
  })

  test('コメントの中の @DisplayName をテスト名に数えない', () => {
    const source = [
      '  // @DisplayName("AC-1 行コメント")',
      '  /* @DisplayName("AC-2 ブロック") */',
      '  /** @DisplayName("AC-3 Javadoc") */',
      '  @DisplayName("AC-4 生きている")',
      '  void a() {}',
    ].join('\n')
    assert.deepEqual(extractTestNames(source, 'java'), ['AC-4 生きている'])
  })

  test('注釈と修飾子が重なる形でも、指数的に遅くならない', () => {
    const source = `@DisplayName("a")@${'.final @'.repeat(40)}`
    const start = Date.now()
    assert.deepEqual(extractTestNames(source, 'java'), ['a'])
    assert.ok(Date.now() - start < 1000)
  })
})
