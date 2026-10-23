// ac-check.mjs のテスト（node --test scripts/ci/ac-check.test.mjs）
import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import {
  check,
  checkAcList,
  extractTestNames,
  isStrict,
  parseAcList,
  parseAcRefs,
  findAmbiguousAcLists,
  parseSpecAcDefinitions,
  parseSpecAcs,
  renderAcList,
} from './ac-check.mjs'

describe('spec の AC の取り出し', () => {
  test('AC の定義の行（- **AC-n**）から番号を取り出す', () => {
    const spec = [
      '- **AC-1** 税区分を名称で登録できる。',
      '  - AC-1 の補足（定義ではない）',
      '- **AC-2** 税率は、率を持つ。AC-9 を参照する。',
      '- **DC-3** 整理の spec の番号は数えない。',
    ].join('\n')
    assert.deepEqual([...parseSpecAcs(spec)], [1, 2])
  })
})

describe('テスト名の取り出し', () => {
  test('Java の @DisplayName', () => {
    const source = [
      '@Test',
      '@DisplayName("AC-43 調整後に負になる調整は拒否される")',
      'void rejects() {}',
      '@DisplayName("名前に \\"引用符\\" を含む")',
    ].join('\n')
    assert.deepEqual(extractTestNames(source, 'java'), ['AC-43 調整後に負になる調整は拒否される', '名前に "引用符" を含む'])
  })

  test('Java のクラス（@Nested を含む）に付いた @DisplayName は読まない', () => {
    const source = [
      '@DisplayName("AC-40 在庫の調整")',
      'class AdjustmentTest {',
      '  @Nested',
      '  @DisplayName("AC-41 負の調整")',
      '  @Tag("slow")',
      '  final class Negative {',
      '    @Test',
      '    @DisplayName("AC-42 拒否される")',
      '    void rejects() {}',
      '  }',
      '}',
    ].join('\n')
    assert.deepEqual(extractTestNames(source, 'java'), ['AC-42 拒否される'])
  })

  test('クラスの @DisplayName の後の注釈の引数に括弧があっても、record・sealed のクラスでも読まない', () => {
    const source = [
      '@DisplayName("AC-50 まとまり")',
      '@Tag("a)b")',
      'sealed class Group permits A {}',
      '@DisplayName("AC-51 record のまとまり")',
      'record Case(int value) {}',
    ].join('\n')
    assert.deepEqual(extractTestNames(source, 'java'), [])
  })

  test('Vitest・Playwright の test・it の第 1 引数（単一引用符・二重引用符・バッククォート）。describe は読まない', () => {
    const source = [
      "describe('AC-8 カテゴリの一覧', () => {",
      '  it("AC-9 登録できる", async () => {})',
      "  test.describe('AC-10 名前の変更', () => {})",
      '  test(`AC-11 並び順を変える`, async ({ page }) => {})',
      "  it.each([1, 2])('AC-12 %s', () => {})",
      '})',
      "const notATest = format('AC-99 テストではない')",
    ].join('\n')
    assert.deepEqual(extractTestNames(source, 'ts'), [
      'AC-9 登録できる',
      'AC-11 並び順を変える',
      'AC-12 %s',
    ])
  })

  test('skip・fixme のテストは数えない', () => {
    const source = [
      "test.skip('AC-1 飛ばす', () => {})",
      "it.skip('AC-2 飛ばす', () => {})",
      "test.fixme('AC-3 直す', () => {})",
      "it.fixme('AC-4 直す', () => {})",
      "test.only('AC-5 数える', () => {})",
    ].join('\n')
    assert.deepEqual(extractTestNames(source, 'ts'), ['AC-5 数える'])
  })

  test('メソッドの .test( の呼び出し（正規表現など）は数えない', () => {
    const source = [
      "expect(/re/.test('AC-1 正規表現')).toBe(true)",
      "const ok = pattern.test('AC-2 呼び出し')",
      "test('AC-3 本物', () => {})",
    ].join('\n')
    assert.deepEqual(extractTestNames(source, 'ts'), ['AC-3 本物'])
  })
})

describe('テスト名の AC 番号', () => {
  test('1 つの AC', () => {
    assert.deepEqual(parseAcRefs('AC-43 調整後に負になる調整は拒否される'), { acs: [43], ranges: [] })
  })

  test('読点・カンマで並べた AC（AC- は先頭だけでもよい）', () => {
    assert.deepEqual(parseAcRefs('AC-72、77、AC-82, 93 カートに入れて注文する'), { acs: [72, 77, 82, 93], ranges: [] })
  })

  test('範囲の表記は、範囲として返す', () => {
    assert.deepEqual(parseAcRefs('AC-72、85〜87 注文する'), { acs: [72, 85, 87], ranges: ['85〜87'] })
    assert.deepEqual(parseAcRefs('AC-8～10 カテゴリ').ranges, ['8～10'])
    assert.deepEqual(parseAcRefs('AC-8~10 カテゴリ').ranges, ['8~10'])
  })

  test('AC 番号のない名前', () => {
    assert.deepEqual(parseAcRefs('ログインしていなければ 401 になる'), { acs: [], ranges: [] })
  })

  test('AC 番号の後の数字（本文の数量など）は AC にしない', () => {
    assert.deepEqual(parseAcRefs('AC-75 数量を 10 にする').acs, [75])
  })
})

describe('照合', () => {
  const tests = (...names) => names.map((name) => ({ file: 'X.test.ts', name }))

  test('すべての AC にテストがあれば通る', () => {
    const result = check({ specAcs: new Set([1, 2]), tests: tests('AC-1 a', 'AC-2 b'), exclusions: {} })
    assert.equal(result.ok, true)
  })

  test('テスト名に出ない AC があれば失敗する', () => {
    const result = check({ specAcs: new Set([1, 2, 3]), tests: tests('AC-1 a'), exclusions: {} })
    assert.equal(result.ok, false)
    assert.deepEqual(result.missing, [2, 3])
  })

  test('範囲の表記があれば失敗する（範囲の中の AC は数えない）', () => {
    const result = check({ specAcs: new Set([1, 2, 3]), tests: tests('AC-1〜3 a'), exclusions: {} })
    assert.equal(result.ok, false)
    assert.deepEqual(result.ranges, [{ file: 'X.test.ts', name: 'AC-1〜3 a' }])
    assert.deepEqual(result.missing, [2])
  })

  test('spec にない AC 番号がテスト名にあれば失敗する', () => {
    const result = check({ specAcs: new Set([1]), tests: tests('AC-1 a', 'AC-200 b'), exclusions: {} })
    assert.equal(result.ok, false)
    assert.deepEqual(result.unknown, [{ file: 'X.test.ts', name: 'AC-200 b', ac: 200 }])
  })

  test('除外の一覧にある AC は、テストがなくてよい', () => {
    const result = check({ specAcs: new Set([1, 2]), tests: tests('AC-1 a'), exclusions: { 'AC-2': '手で確認する' } })
    assert.equal(result.ok, true)
  })

  test('除外の一覧の AC にテストがあれば失敗する（除外が古い）', () => {
    const result = check({ specAcs: new Set([1]), tests: tests('AC-1 a'), exclusions: { 'AC-1': '手で確認する' } })
    assert.equal(result.ok, false)
    assert.deepEqual(result.staleExclusions, [1])
  })

  test('除外の一覧に、spec にない AC か、理由のない項目があれば失敗する', () => {
    const result = check({ specAcs: new Set([1]), tests: tests('AC-1 a'), exclusions: { 'AC-9': '理由', 'AC-1': '' } })
    assert.equal(result.ok, false)
    assert.deepEqual(result.invalidExclusions, ['AC-9', 'AC-1'])
  })

  test('手元では、テスト名に出ない AC があっても通り、その AC を警告に出す', () => {
    const result = check({ specAcs: new Set([1, 2]), tests: tests('AC-1 a'), exclusions: {}, strict: false })
    assert.equal(result.ok, true)
    assert.deepEqual(result.missing, [2])
  })

  test('手元でも、範囲の表記と spec にない AC 番号は失敗する', () => {
    const result = check({ specAcs: new Set([1, 2]), tests: tests('AC-1〜2 a', 'AC-200 b'), exclusions: {}, strict: false })
    assert.equal(result.ok, false)
  })
})

describe('廃止した AC', () => {
  const tests = (...names) => names.map((name) => ({ file: 'X.test.ts', name }))

  test('廃止した AC は、テストがなくてよい', () => {
    const result = check({ specAcs: new Set([1, 2]), tests: tests('AC-1 a'), exclusions: {}, deprecated: new Set([2]) })
    assert.equal(result.ok, true)
    assert.deepEqual(result.missing, [])
  })

  test('廃止した AC をテストが参照していれば、手元でも失敗する', () => {
    const result = check({
      specAcs: new Set([1, 2]),
      tests: tests('AC-1、2 a'),
      exclusions: {},
      deprecated: new Set([2]),
      strict: false,
    })
    assert.equal(result.ok, false)
    assert.deepEqual(result.deprecatedRefs, [{ file: 'X.test.ts', name: 'AC-1、2 a', ac: 2 }])
  })
})

describe('AC 一覧', () => {
  const spec001 = [
    '- **AC-1** 税区分を名称で登録できる。名称は重複できない。',
    '- **AC-2** 税率は、率（%）、適用開始日時、適用終了日時を持つ。開始が空白なら「過去すべて」を意味する。',
  ].join('\n')

  test('spec の AC の定義から、番号と要約（最初の文、40 字まで）を取り出す', () => {
    assert.deepEqual(parseSpecAcDefinitions(spec001, '001-catalog-and-inventory'), [
      { ac: 1, spec: '001-catalog-and-inventory', specPath: '001-catalog-and-inventory', summary: '税区分を名称で登録できる。' },
      { ac: 2, spec: '001-catalog-and-inventory', specPath: '001-catalog-and-inventory', summary: '税率は、率（%）、適用開始日時、適用終了日時を持つ。' },
    ])
    assert.equal(
      parseSpecAcDefinitions(`- **AC-3** ${'あ'.repeat(50)}。`, '001-x')[0].summary,
      `${'あ'.repeat(40)}…`,
    )
  })

  const definitions = parseSpecAcDefinitions(spec001, '001-catalog-and-inventory')

  test('一覧を作る。前の一覧の状態・変えた spec・今の内容は残し、ない AC は有効・spec の定義にする', () => {
    const previous = new Map([[2, { status: '変更', changedBy: '[016](../specs/016-master-data)', location: '[税モジュール](../backend/modules/tax.md)' }]])
    const testFiles = new Map([[1, ['backend/a/TaxApiTest.java', 'e2e/tests/tax.spec.ts']]])
    const markdown = renderAcList({ definitions, previous, testFiles, exclusions: { 'AC-2': '手で確認する' } })
    assert.match(markdown, /^\| AC \| 要約 \| 状態 \| 元の spec \| 変えた spec \| 今の内容 \| テスト \|$/m)
    assert.match(
      markdown,
      /^\| AC-1 \| 税区分を名称で登録できる。 \| 有効 \| \[001\]\(\.\.\/specs\/001-catalog-and-inventory\/spec\.md\) \| — \| spec \| `TaxApiTest`、`tax\.spec\.ts` \|$/m,
    )
    assert.match(markdown, /^\| AC-2 \| .* \| 変更 \| .* \| \[016\]\(\.\.\/specs\/016-master-data\) \| \[税モジュール\]\(\.\.\/backend\/modules\/tax\.md\) \| 除外 \|$/m)
  })

  test('アーカイブの spec の AC は、元の spec のリンクをアーカイブのパスにする', () => {
    const archived = parseSpecAcDefinitions('- **AC-1** 一つ目。', '001-catalog-and-inventory', 'archive/mvp/001-catalog-and-inventory')
    const markdown = renderAcList({ definitions: archived, previous: new Map(), testFiles: new Map(), exclusions: {} })
    assert.match(markdown, /\| \[001\]\(\.\.\/specs\/archive\/mvp\/001-catalog-and-inventory\/spec\.md\) \|/)
  })

  test('テストのファイルは 3 つまで書き、残りは数を書く。テストも除外もなければ「なし」', () => {
    const testFiles = new Map([[1, ['a/A.java', 'b/B.java', 'c/C.java', 'd/D.java', 'e/E.java']]])
    const markdown = renderAcList({ definitions, previous: new Map(), testFiles, exclusions: {} })
    assert.match(markdown, /\| `A`、`B`、`C` ほか 2 \|$/m)
    assert.match(markdown, /^\| AC-2 \| .* \| なし \|$/m)
  })

  test('一覧の行から、手で書く列（状態、変えた spec、今の内容）を読む', () => {
    const markdown = renderAcList({ definitions, previous: new Map(), testFiles: new Map(), exclusions: {} })
    assert.deepEqual(parseAcList(markdown).get(1), { status: '有効', changedBy: '—', location: 'spec' })
  })

  test('spec にあって一覧にない AC、一覧にあって spec にない AC、決まっていない状態を見つける', () => {
    const list = new Map([
      [1, { status: '有効', changedBy: '—', location: 'spec' }],
      [9, { status: '有効', changedBy: '—', location: 'spec' }],
    ])
    const extra = parseSpecAcDefinitions('- **AC-3** 三つ目。', '001-x')
    const problems = checkAcList({ definitions: [...definitions, ...extra], list: new Map([...list, [3, { status: '保留', changedBy: '—', location: 'spec' }]]) })
    assert.deepEqual(problems, ['AC 一覧にない AC：AC-2', 'spec にない AC が AC 一覧にある：AC-9', 'AC 一覧の状態が有効・変更・廃止でない：AC-3（保留）'])
  })
})

describe('1→10 の AC（AC-n-m）', () => {
  const tests = (...names) => names.map((name) => ({ file: 'X.test.ts', name }))

  test('spec の定義の行から、MVP の AC-n と新しい形の AC-n-m の両方を取り出す。別の AC として数える', () => {
    const spec = [
      '- **AC-12** MVP の AC。',
      '- **AC-7-1** 新しい形の AC。',
      '- **AC-7-2** 二つ目。',
      '- **DC-7-3** 整理の番号は数えない。',
    ].join('\n')
    assert.deepEqual([...parseSpecAcs(spec)], [12, '7-1', '7-2'])
    assert.deepEqual(
      parseSpecAcDefinitions(spec, '7-dev-process').map(({ ac, summary }) => ({ ac, summary })),
      [
        { ac: 12, summary: 'MVP の AC。' },
        { ac: '7-1', summary: '新しい形の AC。' },
        { ac: '7-2', summary: '二つ目。' },
      ],
    )
  })

  test('テスト名の AC-7-1 を AC-7 と読まない。MVP の AC-12 を新しい形の一部と読まない', () => {
    assert.deepEqual(parseAcRefs('AC-7-1 注文できる'), { acs: ['7-1'], ranges: [] })
    assert.deepEqual(parseAcRefs('AC-12 注文できる'), { acs: [12], ranges: [] })
    assert.deepEqual(parseAcRefs('AC-7 と AC-7-1 の両方').acs, [7, '7-1'])
  })

  test('読点・カンマで、2 つの形を並べられる', () => {
    assert.deepEqual(parseAcRefs('AC-7-1、AC-7-2, AC-12 並べる'), { acs: ['7-1', '7-2', 12], ranges: [] })
  })

  test('新しい形の後に続く読点と数字だけの番号（AC-7-1、2）は、曖昧な書き方として見つける', () => {
    assert.deepEqual(findAmbiguousAcLists('AC-7-1、2 注文'), ['AC-7-1、2'])
    assert.deepEqual(findAmbiguousAcLists('AC-7-1、2、3 注文'), ['AC-7-1、2、3'])
    assert.deepEqual(findAmbiguousAcLists('AC-7-1、AC-7-2, 12 並べる'), ['AC-7-1、AC-7-2, 12'])
  })

  test('曖昧でない書き方は見つけない。MVP の AC-1、2 は今のまま', () => {
    assert.deepEqual(findAmbiguousAcLists('AC-7-1、AC-7-2 注文'), [])
    assert.deepEqual(findAmbiguousAcLists('AC-7-1、7-2 注文'), [])
    assert.deepEqual(findAmbiguousAcLists('AC-1、2 注文'), [])
    assert.deepEqual(findAmbiguousAcLists('AC-7-1 数量を 10 にする'), [])
  })

  test('曖昧な書き方のテスト名があれば、check は失敗する（手元でも）', () => {
    const result = check({ specAcs: new Set(['7-1', '7-2', 2]), tests: tests('AC-7-1、2 a', 'AC-7-2 b'), exclusions: {}, strict: false })
    assert.equal(result.ok, false)
    assert.deepEqual(result.ambiguous, [{ file: 'X.test.ts', name: 'AC-7-1、2 a' }])
  })

  test('新しい形の範囲（AC-7-1〜3、AC-7-1〜7-3）は、範囲として返す', () => {
    assert.deepEqual(parseAcRefs('AC-7-1〜3 注文'), { acs: ['7-1', '7-3'], ranges: ['7-1〜3'] })
    assert.deepEqual(parseAcRefs('AC-7-1~7-3 注文'), { acs: ['7-1', '7-3'], ranges: ['7-1~7-3'] })
  })

  test('AC 番号の後の数字（本文の数量など）は AC にしない', () => {
    assert.deepEqual(parseAcRefs('AC-7-1 数量を 10 にする').acs, ['7-1'])
  })

  test('両方の形がすべてテスト名に出れば通る。AC-7 のテストは AC-7-1 のテストにならない', () => {
    const specAcs = new Set([7, '7-1'])
    assert.equal(check({ specAcs, tests: tests('AC-7 a', 'AC-7-1 b'), exclusions: {} }).ok, true)
    const result = check({ specAcs, tests: tests('AC-7 a'), exclusions: {} })
    assert.equal(result.ok, false)
    assert.deepEqual(result.missing, ['7-1'])
  })

  test('新しい形のテスト名に出ない AC は、手元では警告、CI では失敗。並びは MVP の後に spec の番号、連番の順', () => {
    const specAcs = new Set(['7-10', '7-2', 3, '8-1'])
    const loose = check({ specAcs, tests: [], exclusions: {}, strict: false })
    assert.equal(loose.ok, true)
    assert.deepEqual(loose.missing, [3, '7-2', '7-10', '8-1'])
    assert.equal(check({ specAcs, tests: [], exclusions: {} }).ok, false)
  })

  test('新しい形の範囲の表記があれば失敗する（手元でも）', () => {
    const specAcs = new Set(['7-1', '7-2', '7-3'])
    const result = check({ specAcs, tests: tests('AC-7-1〜3 a'), exclusions: {}, strict: false })
    assert.equal(result.ok, false)
    assert.deepEqual(result.ranges, [{ file: 'X.test.ts', name: 'AC-7-1〜3 a' }])
  })

  test('spec にない新しい形の AC 番号がテスト名にあれば失敗する。MVP の AC-7 があっても AC-7-9 は通らない', () => {
    const result = check({ specAcs: new Set([7, '7-1']), tests: tests('AC-7-1 a', 'AC-7-9 b'), exclusions: {}, strict: false })
    assert.equal(result.ok, false)
    assert.deepEqual(result.unknown, [{ file: 'X.test.ts', name: 'AC-7-9 b', ac: '7-9' }])
  })

  test('除外の一覧は新しい形の AC も扱う', () => {
    const specAcs = new Set(['7-1', '7-2'])
    assert.equal(check({ specAcs, tests: tests('AC-7-1 a'), exclusions: { 'AC-7-2': '手で確認する' } }).ok, true)
    const stale = check({ specAcs, tests: tests('AC-7-1 a', 'AC-7-2 b'), exclusions: { 'AC-7-2': '手で確認する' } })
    assert.deepEqual(stale.staleExclusions, ['7-2'])
    const invalid = check({ specAcs, tests: tests('AC-7-1 a', 'AC-7-2 b'), exclusions: { 'AC-7-9': '理由' } })
    assert.deepEqual(invalid.invalidExclusions, ['AC-7-9'])
  })

  test('廃止した新しい形の AC は、テストがなくてよく、参照すると失敗する', () => {
    const specAcs = new Set(['7-1', '7-2'])
    assert.equal(check({ specAcs, tests: tests('AC-7-1 a'), exclusions: {}, deprecated: new Set(['7-2']) }).ok, true)
    const result = check({ specAcs, tests: tests('AC-7-1、7-2 a'), exclusions: {}, deprecated: new Set(['7-2']) })
    assert.deepEqual(result.deprecatedRefs, [{ file: 'X.test.ts', name: 'AC-7-1、7-2 a', ac: '7-2' }])
  })

  test('AC 一覧：新しい形の行を作り、元の spec のリンクは 0 埋めのない番号でも書ける。手で書く列を読み戻せる', () => {
    const definitions = parseSpecAcDefinitions('- **AC-12** 一つ目。\n- **AC-7-2** 三つ目。\n- **AC-7-1** 二つ目。', '7-dev-process')
    const markdown = renderAcList({ definitions, previous: new Map(), testFiles: new Map([['7-1', ['a/A.java']]]), exclusions: {} })
    assert.match(markdown, /^\| AC-7-1 \| 二つ目。 \| 有効 \| \[7\]\(\.\.\/specs\/7-dev-process\/spec\.md\) \| — \| spec \| `A` \|$/m)
    assert.ok(markdown.indexOf('| AC-12 |') < markdown.indexOf('| AC-7-1 |'))
    assert.ok(markdown.indexOf('| AC-7-1 |') < markdown.indexOf('| AC-7-2 |'))
    assert.deepEqual(parseAcList(markdown).get('7-1'), { status: '有効', changedBy: '—', location: 'spec' })
    assert.equal(parseAcList(markdown).has(12), true)
  })

  test('AC 一覧との食い違いも、新しい形の AC で見つける', () => {
    const definitions = parseSpecAcDefinitions('- **AC-7-1** 一つ目。\n- **AC-7-2** 二つ目。', '7-dev-process')
    const list = new Map([
      ['7-1', { status: '有効', changedBy: '—', location: 'spec' }],
      ['7-9', { status: '有効', changedBy: '—', location: 'spec' }],
    ])
    assert.deepEqual(checkAcList({ definitions, list }), ['AC 一覧にない AC：AC-7-2', 'spec にない AC が AC 一覧にある：AC-7-9'])
  })
})

describe('手元と CI の見分け', () => {
  test('CI（環境変数 CI が true）では、テスト名に出ない AC を失敗にする', () => {
    assert.equal(isStrict({ CI: 'true' }), true)
  })

  test('手元（CI がない）では、テスト名に出ない AC を警告にする', () => {
    assert.equal(isStrict({}), false)
  })
})
