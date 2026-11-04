// ac-check.mjs のテスト（node --test scripts/ci/ac-check.test.mjs）
import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import {
  addAcsToList,
  check,
  checkAcRows,
  checkInProgress,
  checkModuleDoc,
  checkModuleDocNames,
  describeUnreadableTests,
  isStrict,
  listModuleNames,
  MODULE_AC_SECTION,
  parseAcRefs,
  findAmbiguousAcLists,
  findSpacedAcLists,
  parseSpecAcDefinitions,
  readAcList,
  readInProgressSpecs,
  renderModuleTable,
  renderTestMap,
} from './ac-check.mjs'
import { withoutGitEnv } from './lib/git-env.mjs'
import { extractTestNames, findTestFiles, scanTsTests } from './lib/test-files.mjs'
import { readSkeleton } from './spec-structure.mjs'

/** リポジトリのルート */
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')

describe('spec の AC の取り出し', () => {
  test('AC の定義の行（- **AC-n**）から番号を取り出す', () => {
    const spec = [
      '- **AC-1** 税区分を名称で登録できる。',
      '  - AC-1 の補足（定義ではない）',
      '- **AC-2** 税率は、率を持つ。AC-9 を参照する。',
      '- **DC-3** 整理の spec の番号は数えない。',
    ].join('\n')
    assert.deepEqual(parseSpecAcDefinitions(spec, '5-x').definitions.map((definition) => definition.ac), [1, 2])
  })

  test('定義の文を今の内容にし、下の箇条は「：」の後に読点でつなぐ。縦線は全角にする', () => {
    const spec = [
      '- **AC-5-1** 一覧を表示する。',
      '- **AC-5-2** 次を満たす。',
      '  - 名前は 10 文字まで。',
      '  - 値は a|b のどちらか',
      '- **AC-5-3** 三つ目。',
    ].join('\n')
    assert.deepEqual(parseSpecAcDefinitions(spec, '5-x'), {
      definitions: [
        { ac: '5-1', specRel: '5-x', content: '一覧を表示する。' },
        { ac: '5-2', specRel: '5-x', content: '次を満たす：名前は 10 文字まで、値は a｜b のどちらか。' },
        { ac: '5-3', specRel: '5-x', content: '三つ目。' },
      ],
      problems: [],
    })
  })

  test('コードブロック・HTML のコメント・1 行のコードにある定義は読まない', () => {
    const spec = [
      '```markdown',
      '- **AC-5-9** コードブロックの中',
      '```',
      '<!--',
      '- **AC-5-8** コメントの中',
      '-->',
      '`- **AC-5-7** 1 行のコード`',
      '- **AC-5-1** 本物。',
    ].join('\n')
    assert.deepEqual(parseSpecAcDefinitions(spec, '5-x').definitions.map((definition) => definition.ac), ['5-1'])
  })

  test('定義の行が崩れている（後ろが空、太字が閉じていない）と、問題にする', () => {
    const { problems } = parseSpecAcDefinitions(['- **AC-5-1**', '- **AC-5-2 閉じていない。', '- **AC-5-3**   '].join('\n'), '5-x')
    assert.equal(problems.length, 3)
    assert.match(problems[0], /^1 行：/)
  })

  test('「- **AC-」以外の形の定義（* + 字下げ 番号付き）を、崩れた定義の行として報告する', () => {
    const lines = ['* **AC-5-1** 星の箇条。', '+ **AC-5-2** 足す記号の箇条。', '  - **AC-5-3** 字下げした箇条。', '1. **AC-5-4** 番号付きの箇条。', '2) **AC-5-5** 括弧の番号。', '-  **AC-5-6** 空白が 2 つ。']
    const { definitions, problems } = parseSpecAcDefinitions(lines.join('\n'), '5-x')
    assert.deepEqual(definitions, [])
    assert.equal(problems.length, lines.length)
    assert.match(problems[0], /^1 行：AC の定義の行が崩れている/)
    assert.match(problems[5], /^6 行：/)
  })

  test('「- **AC-」以外の形でも、コードブロックの中と、AC でない太字の箇条は問題にしない', () => {
    const spec = ['```markdown', '* **AC-5-9** コードブロックの中', '```', '* **注意** AC でない太字', '  - AC-5-1 の補足', '- **AC-5-1** 本物。'].join('\n')
    const { definitions, problems } = parseSpecAcDefinitions(spec, '5-x')
    assert.deepEqual(problems, [])
    assert.deepEqual(definitions.map((definition) => definition.ac), ['5-1'])
  })

  test('同じ AC を 2 回定義していても、ac-check では問題にしない（構造の検査が見る）', () => {
    const { definitions, problems } = parseSpecAcDefinitions('- **AC-5-1** 一つ目。\n- **AC-5-1** もう一度。', '5-x')
    assert.equal(definitions.length, 2)
    assert.deepEqual(problems, [])
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
    assert.deepEqual(result.invalidExclusions, ['AC-9'])
    assert.deepEqual(result.emptyReasons, ['AC-1'])
  })

  test('除外の一覧のキーが廃止の AC なら失敗する', () => {
    const result = check({ specAcs: new Set([1, 2]), tests: tests('AC-1 a'), exclusions: { 'AC-2': '手で確認する' }, deprecated: new Set([2]) })
    assert.equal(result.ok, false)
    assert.deepEqual(result.invalidExclusions, ['AC-2'])
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

/** AC 一覧の、元の spec の列の値（MVP の spec の番号） */
const ORIGIN = 'MVP-001'

/** AC 一覧の表の見出しと区切りの行 */
const LIST_HEADER = ['| AC | モジュール | 状態 | 元の spec | 変えた spec | 今の内容 |', '|----|------------|------|-----------|-------------|----------|']

/** AC 一覧の行を作る */
function listRow({ ac = 'AC-1', module = 'order', status = '有効', origin = ORIGIN, changedBy = '—', content = '注文できる。' } = {}) {
  return `| ${ac} | ${module} | ${status} | ${origin} | ${changedBy} | ${content} |`
}

/** AC 一覧の文書を作る */
function listDoc(...rows) {
  return ['# AC 一覧', '', '説明。', '', '<!-- generated:ac-list -->', ...LIST_HEADER, ...rows, '<!-- /generated:ac-list -->', ''].join('\n')
}

/** ③ のモジュールの名前（テスト用） */
const MODULES = new Set(['order', 'cart'])

/**
 * spec のディレクトリ（テスト用。listSpecDirs の結果の name と rel）。
 * 999 のディレクトリはない。8 は、進行中とアーカイブに同じ番号のディレクトリが 2 つある。227 は 3 桁だが、MVP の外にある
 */
const SPEC_DIRS = [
  { name: '001-catalog', rel: 'archive/mvp/001-catalog' },
  { name: '003-cart', rel: 'archive/mvp/003-cart' },
  { name: '016-order', rel: 'archive/mvp/016-order' },
  { name: '019-x', rel: 'archive/mvp/019-x' },
  { name: '7-dev-process', rel: 'archive/1to10/7-dev-process' },
  { name: '9-x', rel: 'archive/1to10/9-x' },
  { name: '227-store-forms', rel: 'archive/1to10/227-store-forms' },
  { name: '8-old', rel: 'archive/1to10/8-old' },
  { name: '5-x', rel: '5-x' },
  { name: '8-new', rel: '8-new' },
]

/** AC 一覧を読み、行の検査の問題まで返す */
function listProblems(...rows) {
  const read = readAcList(listDoc(...rows))
  return [...read.problems, ...checkAcRows({ rows: read.rows, modules: MODULES, specDirs: SPEC_DIRS })]
}

/** AC 一覧の行を読む（問題がないことも確認する） */
function rowsOf(...rows) {
  const read = readAcList(listDoc(...rows))
  assert.deepEqual(read.problems, [])
  return read.rows
}

/** ③ に生成する表の、見出しと区切りの行 */
const MODULE_HEADER = ['| AC | 今の内容 | 元の spec |', '|----|----------|-----------|']

/** ③ の生成の表の行を作る */
function moduleRow(ac, content, origin = ORIGIN) {
  return `| ${ac} | ${content} | ${origin} |`
}

/** ③ の文書を作る。inner はマーカーの範囲の中の行 */
function moduleDoc(inner, { before = ['選んだ基準は、注文を扱う AC であること。'], after = [] } = {}) {
  return [
    '# 注文モジュール',
    '',
    '## 業務規則',
    '',
    'なし',
    '',
    '## 今有効な AC',
    '',
    ...before,
    '',
    '<!-- generated:module-ac -->',
    ...inner,
    '<!-- /generated:module-ac -->',
    ...after,
    '',
  ].join('\n')
}

describe('AC 一覧の読み取り', () => {
  test('6 列の行を読む。改行コードが CRLF でも、今の内容に CR が残らない', () => {
    const rows = readAcList(listDoc(listRow({ content: '注文できる。' })).replaceAll('\n', '\r\n')).rows
    assert.deepEqual(rows.map(({ ac, module, status, origin, changedBy, content }) => ({ ac, module, status, origin, changedBy, content })), [
      { ac: 1, module: 'order', status: '有効', origin: ORIGIN, changedBy: '—', content: '注文できる。' },
    ])
  })

  test('列「テスト」のない 6 列の一覧は、問題にしない', () => {
    assert.deepEqual(listProblems(listRow()), [])
  })

  test('先頭のセルが AC- の行のセルが足りない・多いと、読み飛ばさず問題にする', () => {
    const short = '| AC-2 | order | 有効 | — | 注文できる。 |'
    const long = `${listRow({ ac: 'AC-3' })} 余り |`
    const { rows, problems } = readAcList(listDoc(listRow(), short, long))
    assert.deepEqual(rows.map((row) => row.ac), [1])
    assert.equal(problems.length, 2)
    assert.match(problems[0], /AC-2/)
    assert.match(problems[1], /AC-3/)
  })

  test('マーカーの範囲の中の、表の行でない行（セルの中の改行の続き）を問題にする', () => {
    const { problems } = readAcList(listDoc('| AC-1 | order | 有効 | ' + ORIGIN + ' | — | 一行目', '二行目 |'))
    assert.ok(problems.length > 0)
  })

  test('AC の番号を読めない行（0 埋め、小文字、空白の区切り）を問題にする', () => {
    for (const ac of ['AC-07-1', 'ac-7-1', 'AC 7-1']) {
      assert.equal(readAcList(listDoc(listRow({ ac }))).problems.length, 1, ac)
    }
  })

  test('表の見出しが AC・モジュール・状態・元の spec・変えた spec・今の内容でなければ問題にする', () => {
    const document = listDoc(listRow()).replace('| AC | モジュール | 状態 |', '| AC | 状態 | モジュール |')
    assert.equal(readAcList(document).problems.length, 1)
  })

  test('AC 一覧のマーカーの範囲がない、重複している、閉じが前にあるときは失敗にする', () => {
    const body = [...LIST_HEADER, listRow()]
    const cases = [
      ['# AC 一覧', ...body].join('\n'),
      ['<!-- generated:ac-list -->', ...body, '<!-- /generated:ac-list -->', '<!-- generated:ac-list -->', '<!-- /generated:ac-list -->'].join('\n'),
      ['<!-- /generated:ac-list -->', ...body, '<!-- generated:ac-list -->'].join('\n'),
      ['<!-- generated:ac-list -->', ...body].join('\n'),
    ]
    for (const document of cases) {
      assert.ok(readAcList(document).problems.length > 0, document)
    }
  })

  test('既知のマーカーのどれとも一致しない generated: のコメント（綴りの誤り）を失敗にする', () => {
    const document = listDoc(listRow()).replace('# AC 一覧', '# AC 一覧\n\n<!-- generated:modul-ac -->')
    assert.equal(readAcList(document).problems.length, 1)
  })

  test('表の途中（AC の行の間）に空行があれば、表がそこで切れるので失敗にする', () => {
    const { problems } = readAcList(listDoc(listRow({ ac: 'AC-1' }), '', listRow({ ac: 'AC-2' })))
    assert.equal(problems.length, 1)
    assert.match(problems[0], /^9 行：AC 一覧の表の途中に空行がある/)
  })

  test('表の見出しと区切りの行の間に空行があれば、失敗にする', () => {
    const document = listDoc(listRow()).replace(`${LIST_HEADER[0]}\n`, `${LIST_HEADER[0]}\n\n`)
    const { problems } = readAcList(document)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /AC 一覧の表の途中に空行がある/)
  })

  test('マーカーの範囲で、表より前と表より後にある空行は問題にしない', () => {
    const document = listDoc(listRow()).replace('<!-- generated:ac-list -->\n', '<!-- generated:ac-list -->\n\n').replace('\n<!-- /generated:ac-list -->', '\n\n\n<!-- /generated:ac-list -->')
    assert.deepEqual(readAcList(document).problems, [])
  })
})

describe('AC 一覧の表の見出しの行', () => {
  const document = (...inner) => ['# AC 一覧', '', '<!-- generated:ac-list -->', ...inner, '<!-- /generated:ac-list -->', ''].join('\n')

  test('マーカーの範囲が空、または空行だけで、表の見出しの行がなければ問題にする', () => {
    for (const inner of [[], [''], ['', '']]) {
      const { problems } = readAcList(document(...inner))
      assert.equal(problems.length, 1, JSON.stringify(inner))
      assert.match(problems[0], /表の見出しの行がない/)
    }
  })

  test('見出しと区切りの行だけで AC の行が 0 件の範囲は、問題にしない', () => {
    assert.deepEqual(readAcList(document(...LIST_HEADER)).problems, [])
  })
})

describe('AC 一覧の行の検査', () => {
  test('正しい行は通る（有効、変更、廃止、共通）', () => {
    const changed = listRow({ ac: 'AC-2', status: '変更', changedBy: 'MVP-019', content: '1 円以上。根拠は[注文モジュール](../../docs/03-機能仕様/order.md)の「注文（集約）」。' })
    const deprecated = listRow({ ac: 'AC-3', module: '', status: '廃止', changedBy: 'MVP-019', content: '' })
    const common = listRow({ ac: 'AC-4', module: 'common', content: '死活監視は運用ポートで出す。根拠は[共通コード・ログ・可観測性](../../docs/02-設計/common.md)の「メトリクスと死活監視」。' })
    assert.deepEqual(listProblems(listRow(), changed, deprecated, common), [])
  })

  test('同じ AC の行が 2 つあれば問題にする', () => {
    assert.equal(listProblems(listRow(), listRow()).length, 1)
  })

  test('状態が有効・変更・廃止のどれでもなければ問題にする', () => {
    assert.equal(listProblems(listRow({ status: '有功' })).length, 1)
    assert.equal(listProblems(listRow({ status: '' })).length, 1)
  })

  test('有効・変更の AC のモジュールが、空白だけ・「—」・「-」なら問題にする', () => {
    for (const module of ['', '—', '-']) {
      assert.equal(listProblems(listRow({ module })).length, 1, module)
    }
  })

  test('モジュールのセルに 2 つ書くと問題にする', () => {
    for (const module of ['order、cart', 'order, cart', 'order/cart']) {
      assert.equal(listProblems(listRow({ module })).length, 1, module)
    }
  })

  test('モジュールの名前の大文字小文字・全角半角の違い、.md やパスを付けた名前を問題にする', () => {
    for (const module of ['Order', 'ｏｒｄｅｒ', 'order.md', 'docs/03-機能仕様/order.md']) {
      assert.equal(listProblems(listRow({ module })).length, 1, module)
    }
  })

  test('廃止の AC のモジュールは空でよく、空でなければ ③ の名前か common でなければならない', () => {
    const deprecated = (module) => listRow({ module, status: '廃止', changedBy: 'MVP-019', content: '' })
    assert.deepEqual(listProblems(deprecated('')), [])
    assert.deepEqual(listProblems(deprecated('order')), [])
    assert.equal(listProblems(deprecated('Order')).length, 1)
  })

  test('変更の AC の変えた spec が、空か「—」なら問題にする', () => {
    for (const changedBy of ['', '—']) {
      const row = listRow({ status: '変更', changedBy, content: '根拠は[注文モジュール](../../docs/03-機能仕様/order.md)の「注文（集約）」。' })
      assert.equal(listProblems(row).length, 1, changedBy)
    }
  })

  test('有効の AC の変えた spec が「—」以外なら問題にする', () => {
    for (const changedBy of ['', 'MVP-019', '-']) {
      assert.equal(listProblems(listRow({ changedBy })).length, 1, changedBy)
    }
  })

  test('元の spec が、番号の形でない（空、リンクの形、数字以外を含む、2 つ書いた）と問題にする', () => {
    const cases = ['', '—', '[001](../08-spec/archive/mvp/001-catalog/spec.md)', '[001](001-catalog)', '001-catalog', 'abc', '00a', '#001', '001、003', 'MVP-001、MVP-003', '１']
    for (const origin of cases) {
      const problems = listProblems(listRow({ origin }))
      assert.equal(problems.length, 1, origin)
      assert.match(problems[0], /元の spec/, origin)
    }
  })

  test('元の spec の番号の spec のディレクトリがないと問題にする', () => {
    const problems = listProblems(listRow({ origin: '999' }))
    assert.equal(problems.length, 1)
    assert.match(problems[0], /元の spec/)
    assert.match(problems[0], /ディレクトリがない/)
  })

  test('元の spec の番号の spec のディレクトリが 2 つあると問題にする', () => {
    const problems = listProblems(listRow({ ac: 'AC-8-1', origin: '8' }))
    assert.equal(problems.length, 1)
    assert.match(problems[0], /元の spec/)
    assert.match(problems[0], /2 つある/)
  })

  test('元の spec の番号は、ディレクトリの名前の先頭の番号と文字列として比べる（7 は 7-dev-process に当たり 007 は当たらない）', () => {
    assert.deepEqual(listProblems(listRow({ ac: 'AC-7-1', origin: '7' })), [])
    for (const origin of ['1', '01', '0001', '007', '07']) {
      assert.equal(listProblems(listRow({ origin })).length, 1, origin)
    }
  })

  test('AC-s-n の行は、元の spec が s と完全一致でないと問題にする（別の番号、MVP-007、0 埋めの違い）', () => {
    for (const origin of ['9', '007', 'MVP-007']) {
      const problems = listProblems(listRow({ ac: 'AC-7-1', origin }))
      assert.ok(problems.some((problem) => problem.includes(`元の spec（${origin}）が AC の番号に含まれる spec（7）と違う`)), origin)
    }
    assert.deepEqual(listProblems(listRow({ ac: 'AC-7-1', origin: '7' })), [])
  })

  test('AC-s-n の行は、状態（変更・廃止）によらず、元の spec と s を比べる', () => {
    for (const status of ['変更', '廃止']) {
      const problems = listProblems(listRow({ ac: 'AC-7-1', origin: '9', status, changedBy: '9' }))
      assert.ok(problems.some((problem) => problem.includes('AC の番号に含まれる spec（7）と違う')), status)
    }
  })

  test('MVP の形（AC-n）の行は、元の spec と AC の番号を比べない', () => {
    assert.deepEqual(listProblems(listRow({ ac: 'AC-7', origin: 'MVP-001' })), [])
  })

  test('MVP-<番号> は、archive/mvp/ の下のディレクトリに対応する', () => {
    for (const origin of ['MVP-001', 'MVP-003', 'MVP-019']) {
      assert.deepEqual(listProblems(listRow({ origin })), [], origin)
    }
  })

  test('番号だけの値は、archive/mvp/ の外のディレクトリに対応する（1→10 のアーカイブと、進行中）', () => {
    for (const origin of ['227', '7', '5']) {
      assert.deepEqual(listProblems(listRow({ origin })), [], origin)
    }
  })

  test('MVP の spec を番号だけで書く（001）と問題にし、MVP-<番号> の形を示す', () => {
    const problems = listProblems(listRow({ origin: '001' }))
    assert.equal(problems.length, 1)
    assert.match(problems[0], /元の spec/)
    assert.match(problems[0], /ディレクトリがない/)
    assert.match(problems[0], /MVP-001/)
  })

  test('MVP にない番号を MVP-<番号> で書く（MVP-227、MVP-999）と問題にする。MVP-227 に 1→10 の 227 は当たらない', () => {
    for (const origin of ['MVP-227', 'MVP-999']) {
      const problems = listProblems(listRow({ origin }))
      assert.equal(problems.length, 1, origin)
      assert.match(problems[0], /元の spec/, origin)
      assert.match(problems[0], /ディレクトリがない/, origin)
    }
  })

  test('MVP-<3 桁の番号> と番号だけのどちらでもない形（mvp-001、MVP-1 など）を問題にする', () => {
    for (const origin of ['mvp-001', 'Mvp-001', 'MVP-1', 'MVP-01', 'MVP-0001', 'MVP001', 'MVP 001', 'ＭＶＰ-001', 'MVP-', '-001', 'MVP-001-catalog']) {
      const problems = listProblems(listRow({ origin }))
      assert.equal(problems.length, 1, origin)
      assert.match(problems[0], /元の spec/, origin)
      assert.match(problems[0], /形でない/, origin)
    }
  })

  test('MVP のまとまりは、rel の archive/mvp/ で見分ける（名前の前半が同じ archive/mvp-old/ は MVP でない）', () => {
    const specDirs = [{ name: '001-x', rel: 'archive/mvp-old/001-x' }]
    const rows = (origin) => rowsOf(listRow({ origin }))
    assert.equal(checkAcRows({ rows: rows('MVP-001'), modules: MODULES, specDirs }).length, 1)
    assert.deepEqual(checkAcRows({ rows: rows('001'), modules: MODULES, specDirs }), [])
  })

  test('元の spec が進行中の spec（docs/08-spec/ の直下）の番号でも受け付ける', () => {
    assert.deepEqual(listProblems(listRow({ ac: 'AC-5-1', origin: '5' })), [])
  })

  test('変更・廃止の AC の変えた spec は、読点で区切った番号を受け付ける', () => {
    const content = '根拠は[注文モジュール](../../docs/03-機能仕様/order.md)の「注文（集約）」。'
    assert.deepEqual(listProblems(listRow({ status: '変更', changedBy: 'MVP-003、MVP-016', content })), [])
    assert.deepEqual(listProblems(listRow({ status: '廃止', module: '', changedBy: 'MVP-003、MVP-016、7', content: '' })), [])
  })

  test('変更・廃止の AC の変えた spec は、MVP-<番号> と番号だけを読点の区切りで混ぜられる', () => {
    const content = '根拠は[注文モジュール](../../docs/03-機能仕様/order.md)の「注文（集約）」。'
    for (const changedBy of ['MVP-003、MVP-016', 'MVP-016、227', '227、MVP-016']) {
      assert.deepEqual(listProblems(listRow({ status: '変更', changedBy, content })), [], changedBy)
      assert.deepEqual(listProblems(listRow({ status: '廃止', module: '', changedBy, content: '' })), [], changedBy)
    }
  })

  test('変えた spec を読点で区切っても、番号ごとに MVP の形を調べる（変更・廃止）', () => {
    const content = '根拠は[注文モジュール](../../docs/03-機能仕様/order.md)の「注文（集約）」。'
    const cases = [
      ['003、MVP-016', 1],
      ['MVP-003、016', 1],
      ['003、016', 2],
      ['MVP-016、MVP-227', 1],
      ['mvp-003、MVP-016', 1],
      ['MVP-1、MVP-016', 1],
    ]
    for (const [changedBy, count] of cases) {
      for (const row of [listRow({ status: '変更', changedBy, content }), listRow({ status: '廃止', module: '', changedBy, content: '' })]) {
        const problems = listProblems(row)
        assert.equal(problems.length, count, `${changedBy}：${row}`)
        assert.ok(problems.every((problem) => problem.includes('変えた spec')), changedBy)
      }
    }
  })

  test('変更・廃止の AC の変えた spec の番号が、番号の形でないと問題にする', () => {
    const content = '根拠は[注文モジュール](../../docs/03-機能仕様/order.md)の「注文（集約）」。'
    const cases = ['[019](../08-spec/archive/mvp/019-x/spec.md)', '019-x', 'abc', 'MVP-003,MVP-016', 'MVP-003 MVP-016', 'MVP-003、', '、MVP-003', 'MVP-003、[016](../08-spec/archive/mvp/016-order/spec.md)']
    for (const changedBy of cases) {
      for (const row of [listRow({ status: '変更', changedBy, content }), listRow({ status: '廃止', module: '', changedBy, content: '' })]) {
        const problems = listProblems(row)
        assert.equal(problems.length, 1, `${changedBy}：${row}`)
        assert.match(problems[0], /変えた spec/, changedBy)
      }
    }
  })

  test('変更・廃止の AC の変えた spec の番号の spec のディレクトリがない、2 つあると、番号ごとに問題にする', () => {
    const content = '根拠は[注文モジュール](../../docs/03-機能仕様/order.md)の「注文（集約）」。'
    const cases = [
      ['999', /ディレクトリがない/],
      ['MVP-003、999', /ディレクトリがない/],
      ['19', /ディレクトリがない/],
      ['MVP-016、007', /ディレクトリがない/],
      ['8', /2 つある/],
      ['MVP-003、8', /2 つある/],
    ]
    for (const [changedBy, reason] of cases) {
      for (const row of [listRow({ status: '変更', changedBy, content }), listRow({ status: '廃止', module: '', changedBy, content: '' })]) {
        const problems = listProblems(row)
        assert.equal(problems.length, 1, `${changedBy}：${row}`)
        assert.match(problems[0], /変えた spec/, changedBy)
        assert.match(problems[0], reason, changedBy)
      }
    }
    assert.equal(listProblems(listRow({ status: '変更', changedBy: '999、8', content })).length, 2)
  })

  test('今の内容に、spec の中だけで通じる参照（※1、事実 2、US の番号、spec の節）があれば問題にする', () => {
    for (const content of ['注文できる※1。', '事実 2 のとおり。', 'US-4-1 の画面で注文できる。', 'spec の「スコープ」のとおり。']) {
      assert.equal(listProblems(listRow({ content })).length, 1, content)
    }
  })

  test('有効・変更の AC の今の内容が、空白だけ・「—」・「なし」・<…> の仮の値なら問題にする', () => {
    for (const content of ['', '—', 'なし', '<今の内容>']) {
      assert.equal(listProblems(listRow({ content })).length, 1, content)
    }
  })

  test('今の内容が複数行（<br />）なら問題にする', () => {
    assert.equal(listProblems(listRow({ content: '一行目。<br />二行目。' })).length, 1)
  })

  test('変更の AC の今の内容に、「<文書の名前>の「<見出し>」」の形がなければ問題にする', () => {
    const row = listRow({ status: '変更', changedBy: 'MVP-019', content: '1 円以上。' })
    assert.equal(listProblems(row).length, 1)
  })

  test('common の AC の今の内容に、根拠の「<文書の名前>の「<見出し>」」の形がなければ問題にする', () => {
    assert.equal(listProblems(listRow({ module: 'common', content: 'ポートは 8080。' })).length, 1)
  })

  test('変更と common の AC の根拠が、リンクのない「…の「…」」なら問題にする（doc-refs が見出しを確認できない）', () => {
    const changed = listRow({ status: '変更', changedBy: 'MVP-019', content: '1 円以上。業務規則の「注文の成立」' })
    assert.equal(listProblems(changed).length, 1)
    assert.equal(listProblems(listRow({ module: 'common', content: 'ポートは 8080。根拠は構成の文書の「ポート」。' })).length, 1)
  })

  test('廃止の AC は、今の内容が空でよい', () => {
    const row = listRow({ module: '', status: '廃止', changedBy: 'MVP-019', content: '' })
    assert.deepEqual(listProblems(row), [])
  })
})

describe('③ のモジュールの名前', () => {
  test('バックエンドのモジュールのディレクトリと、③ の文書の両方がある名前だけ', () => {
    const names = listModuleNames({
      backendDirs: ['order', 'cart', 'newmodule'],
      docFiles: ['order.md', 'cart.md', 'template.md', 'acceptance-criteria.md', 'glossary.md', 'draft.md'],
    })
    assert.deepEqual([...names].sort(), ['cart', 'order'])
  })
})

describe('③ の文書の名前と構造の検査の対象の一覧', () => {
  const doc = (module, required) => ({ file: `docs/03-機能仕様/${module}.md`, module, required })
  const modules = new Set(['order', 'wishlist'])

  test('モジュールの ③ が構造の検査の対象の一覧（FUNCTIONAL_SPECS）になければ、追加し忘れとして失敗にする', () => {
    const problems = checkModuleDocNames({ docs: [doc('order', true), doc('wishlist', false)], modules })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /^docs\/03-機能仕様\/wishlist\.md：モジュールの ③ が構造の検査の対象の一覧（spec-structure\.mjs の FUNCTIONAL_SPECS）にない/)
  })

  test('一覧にある ③ にバックエンドのモジュールのディレクトリがなければ失敗にする', () => {
    const problems = checkModuleDocNames({ docs: [doc('legacy', true)], modules })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /バックエンドのモジュールのディレクトリがない/)
  })

  test('モジュールでない ③ の文書（template、draft）が一覧になくても、問題にしない', () => {
    assert.deepEqual(checkModuleDocNames({ docs: [doc('template', false), doc('draft', false), doc('order', true)], modules }), [])
  })
})

describe('③ の「今有効な AC」の表の生成', () => {
  test('有効・変更の、そのモジュールの AC を、AC の順（MVP、次に spec の番号と連番）に置く', () => {
    const rows = rowsOf(
      listRow({ ac: 'AC-7-10', content: 'c。' }),
      listRow({ ac: 'AC-10', content: 'b。' }),
      listRow({ ac: 'AC-7-2', content: 'd。' }),
      listRow({ ac: 'AC-9', content: 'a。' }),
      listRow({ ac: 'AC-11', module: 'cart' }),
      listRow({ ac: 'AC-12', module: '', status: '廃止', changedBy: 'MVP-019', content: '' }),
      listRow({ ac: 'AC-13', module: 'common', content: '根拠は[共通コード・ログ・可観測性](../../docs/02-設計/common.md)の「メトリクスと死活監視」。' }),
    )
    assert.equal(
      renderModuleTable(rows, 'order'),
      [...MODULE_HEADER, moduleRow('AC-9', 'a。'), moduleRow('AC-10', 'b。'), moduleRow('AC-7-2', 'd。'), moduleRow('AC-7-10', 'c。')].join('\n'),
    )
  })

  test('列「元の spec」は、AC 一覧の元の spec の番号だけで書く（リンクにしない）', () => {
    const rows = rowsOf(listRow({ ac: 'AC-1', content: '一つ目。' }), listRow({ ac: 'AC-7-1', origin: '7', content: '二つ目。' }))
    assert.equal(renderModuleTable(rows, 'order'), [...MODULE_HEADER, '| AC-1 | 一つ目。 | MVP-001 |', '| AC-7-1 | 二つ目。 | 7 |'].join('\n'))
  })

  test('対応する AC が 0 件なら「なし。」の 1 行', () => {
    assert.equal(renderModuleTable(rowsOf(listRow({ module: 'cart' })), 'order'), 'なし。')
  })

  test('common の AC は、どの ③ の表にも入れない', () => {
    const rows = rowsOf(listRow({ module: 'common', content: '根拠は[共通コード・ログ・可観測性](../../docs/02-設計/common.md)の「メトリクスと死活監視」。' }))
    assert.equal(renderModuleTable(rows, 'common'), 'なし。')
  })
})

describe('③ の「今有効な AC」の節の名前', () => {
  test('生成の表を置く節の名前が、リポジトリの雛形の骨組みの ## にある（節の名前の正本は雛形）', () => {
    const { sections, problems } = readSkeleton(readFileSync(path.join(ROOT, 'docs/03-機能仕様/template.md'), 'utf8'))
    assert.deepEqual(problems, [])
    assert.ok(sections.includes(MODULE_AC_SECTION), sections.join('、'))
  })
})

describe('③ の「今有効な AC」の表の照合', () => {
  const rows = rowsOf(listRow({ ac: 'AC-1', content: '一つ目。' }), listRow({ ac: 'AC-2', content: '二つ目。' }))
  const table = [...MODULE_HEADER, moduleRow('AC-1', '一つ目。'), moduleRow('AC-2', '二つ目。')]
  const result = (text, required = true) => checkModuleDoc({ text, module: 'order', rows, required })

  test('生成した表と同じなら通る', () => {
    assert.deepEqual(result(moduleDoc(table)).problems, [])
  })

  test('範囲の外の説明文（選んだ基準）と、範囲の中の先頭と末尾の空行、末尾の改行の有無は問題にしない', () => {
    assert.deepEqual(result(moduleDoc(['', ...table, '', ''], { before: ['選んだ基準は、注文を扱う AC。', '', '- 在庫は入れない'] })).problems, [])
    assert.deepEqual(result(moduleDoc(table).replace(/\n$/, '')).problems, [])
  })

  test('③ の改行コードが CRLF でも、LF にそろえて比べる', () => {
    assert.deepEqual(result(moduleDoc(table).replaceAll('\n', '\r\n')).problems, [])
  })

  test('今の内容の全角と半角が AC 一覧と同じなら、正規化せずに通す', () => {
    const wide = rowsOf(listRow({ content: '数量は１０まで（ＡＢＣ）。' }))
    const doc = moduleDoc([...MODULE_HEADER, moduleRow('AC-1', '数量は１０まで（ＡＢＣ）。')])
    assert.deepEqual(checkModuleDoc({ text: doc, module: 'order', rows: wide, required: true }).problems, [])
  })

  test('対応する AC が 0 件で、範囲が「なし。」の 1 行なら通る', () => {
    assert.deepEqual(checkModuleDoc({ text: moduleDoc(['なし。']), module: 'cart', rows, required: true }).problems, [])
  })

  const stale = [
    ['行の順が AC の順と違う', [MODULE_HEADER[0], MODULE_HEADER[1], moduleRow('AC-2', '二つ目。'), moduleRow('AC-1', '一つ目。')]],
    ['生成しない行（廃止にした AC など）が残っている', [...table, moduleRow('AC-3', '三つ目。')]],
    ['行が足りない', [...MODULE_HEADER, moduleRow('AC-1', '一つ目。')]],
    ['今の内容が前の文のまま', [...MODULE_HEADER, moduleRow('AC-1', '前の文。'), moduleRow('AC-2', '二つ目。')]],
    ['列の見出しの名前が違う', ['| AC | 内容 | 元の spec |', MODULE_HEADER[1], ...table.slice(2)]],
    ['列の見出しの順が違う', ['| AC | 元の spec | 今の内容 |', MODULE_HEADER[1], ...table.slice(2)]],
    ['元の spec の番号が違う', [...MODULE_HEADER, moduleRow('AC-1', '一つ目。', 'MVP-002'), moduleRow('AC-2', '二つ目。')]],
    ['元の spec が MVP-<番号> でなく番号だけ', [...MODULE_HEADER, moduleRow('AC-1', '一つ目。', '001'), moduleRow('AC-2', '二つ目。')]],
    ['元の spec がリンクの形のまま', [...MODULE_HEADER, moduleRow('AC-1', '一つ目。', '[001](../08-spec/archive/mvp/001-catalog/spec.md)'), moduleRow('AC-2', '二つ目。')]],
    ['AC の番号の表記が違う（AC-01）', [...MODULE_HEADER, moduleRow('AC-01', '一つ目。'), moduleRow('AC-2', '二つ目。')]],
    ['AC の番号の表記が違う（ac-1）', [...MODULE_HEADER, moduleRow('ac-1', '一つ目。'), moduleRow('AC-2', '二つ目。')]],
    ['AC の番号の表記が違う（AC 1）', [...MODULE_HEADER, moduleRow('AC 1', '一つ目。'), moduleRow('AC-2', '二つ目。')]],
    ['今の内容に縦線を入れて列を増やした', [...MODULE_HEADER, moduleRow('AC-1', '一つ目。 | 追加'), moduleRow('AC-2', '二つ目。')]],
    ['範囲の中に表の他の文（注）を書いた', [...table, '', '※1：注。']],
    ['区切り行の - の数が違う', [MODULE_HEADER[0], '|---|---|---|', ...table.slice(2)]],
    ['セルの前後の空白が違う', [...MODULE_HEADER, '|  AC-1 | 一つ目。 | ' + ORIGIN + ' |', moduleRow('AC-2', '二つ目。')]],
    ['AC があるモジュールの表に「なし。」を書いた', ['なし。']],
  ]
  for (const [name, inner] of stale) {
    test(`表が古い：${name}`, () => {
      const checked = result(moduleDoc(inner))
      assert.equal(checked.problems.length, 1)
      assert.equal(checked.stale, true)
    })
  }

  test('0 件のモジュールの範囲が「なし。」の 1 行以外（空の表、空欄）なら失敗にする', () => {
    for (const inner of [[...MODULE_HEADER], [], ['なし']]) {
      assert.equal(checkModuleDoc({ text: moduleDoc(inner), module: 'cart', rows, required: true }).problems.length, 1, inner.join('/'))
    }
  })

  const broken = [
    ['開きのマーカーだけがある', moduleDoc(table).replace('<!-- /generated:module-ac -->', '')],
    ['閉じが開きより前にある', moduleDoc(table).replace('<!-- generated:module-ac -->', '@@').replace('<!-- /generated:module-ac -->', '<!-- generated:module-ac -->').replace('@@', '<!-- /generated:module-ac -->')],
    ['同じマーカーの組が 2 組ある', moduleDoc(table, { after: ['', '<!-- generated:module-ac -->', 'なし。', '<!-- /generated:module-ac -->'] })],
    ['マーカーが「今有効な AC」の節の外にある', moduleDoc(table).replace('<!-- generated:module-ac -->', '').replace('<!-- /generated:module-ac -->', '').replace('なし\n', 'なし\n\n<!-- generated:module-ac -->\nなし。\n<!-- /generated:module-ac -->\n')],
    ['マーカーの範囲が「今有効な AC」の節をまたぐ', moduleDoc(table).replace('<!-- /generated:module-ac -->', '\n## 次の節\n\n<!-- /generated:module-ac -->')],
    ['マーカーがあるのに「今有効な AC」の節がない', moduleDoc(table).replace('## 今有効な AC', '## 今の AC')],
    ['既知のマーカーと一致しない generated: のコメント', moduleDoc(table, { after: ['', '<!-- generated:module-acs -->'] })],
    ['AC 一覧のマーカーが ③ にある', moduleDoc(table, { after: ['', '<!-- generated:ac-list -->'] })],
    ['構造の検査の対象の一覧にある ③ に、マーカーがない', moduleDoc(table).replace('<!-- generated:module-ac -->', '').replace('<!-- /generated:module-ac -->', '')],
    ['マーカーをコードブロックや 1 行のコードの中にだけ書いた', moduleDoc(table).replace('<!-- generated:module-ac -->', '`<!-- generated:module-ac -->`').replace('<!-- /generated:module-ac -->', '```\n<!-- /generated:module-ac -->\n```')],
  ]
  for (const [name, text] of broken) {
    test(`失敗にし、--write もしない：${name}`, () => {
      const checked = result(text)
      assert.ok(checked.problems.length > 0)
      assert.equal(checked.stale, false)
      assert.equal(checked.updated, null)
    })
  }

  test('「今有効な AC」の節の中で、マーカーの範囲の外に AC の表の行があれば失敗にし、--write もしない', () => {
    const outside = [
      moduleDoc(table, { after: ['', '| AC | 今の内容 | 元の spec |', '|----|----------|-----------|', moduleRow('AC-999', '範囲の外。')] }),
      moduleDoc(table, { before: ['説明。', '', moduleRow('AC-999', '範囲の前。')] }),
    ]
    for (const text of outside) {
      const checked = result(text)
      assert.equal(checked.problems.length, 1)
      assert.match(checked.problems[0], /^[0-9]+ 行：「今有効な AC」の節の、生成の印の範囲の外に、AC の表の行がある/)
      assert.equal(checked.stale, false)
      assert.equal(checked.updated, null)
    }
  })

  test('範囲の外の AC の表の行でも、コードブロックの中と、次の節にあるものは問題にしない', () => {
    const text = moduleDoc(table, { after: ['', '```markdown', moduleRow('AC-999', '例。'), '```', '', '## 次の節', '', moduleRow('AC-998', '別の節。')] })
    assert.deepEqual(result(text).problems, [])
  })

  test('構造の検査の対象の一覧にない ③ にマーカーがあれば失敗にする', () => {
    const checked = result(moduleDoc(table), false)
    assert.equal(checked.problems.length, 1)
    assert.equal(checked.updated, null)
  })

  test('構造の検査の対象の一覧にない ③ にマーカーがなければ通り、書き換えない', () => {
    const text = moduleDoc(table).replace('<!-- generated:module-ac -->', '').replace('<!-- /generated:module-ac -->', '')
    const checked = result(text, false)
    assert.deepEqual(checked.problems, [])
    assert.equal(checked.updated, null)
  })

  test('コードブロックや 1 行のコードの中に引用したマーカーは、マーカーとして読まない', () => {
    const text = moduleDoc(table, { before: ['印は `<!-- generated:module-ac -->` の形。', '', '```markdown', '<!-- generated:module-ac -->', '<!-- generated:modul-ac -->', '```'] })
    assert.deepEqual(result(text).problems, [])
    const unlisted = text.replace(/^<!-- \/?generated:module-ac -->$/gm, '')
    assert.deepEqual(result(unlisted, false).problems, [])
  })
})

describe('③ の表の書き換え（--write）', () => {
  const rows = rowsOf(listRow({ ac: 'AC-1', content: '一つ目。' }))
  const table = [...MODULE_HEADER, moduleRow('AC-1', '一つ目。')]

  test('マーカーの範囲の中だけを生成した表にし、範囲の外は変えない', () => {
    const before = moduleDoc([moduleRow('AC-1', '古い。')], { after: ['', '末尾の文。'] })
    const { updated } = checkModuleDoc({ text: before, module: 'order', rows, required: true })
    assert.equal(updated, moduleDoc(table, { after: ['', '末尾の文。'] }))
  })

  test('CRLF の文書は、範囲の外の改行コードを変えず、書いた行も CRLF にする', () => {
    const before = moduleDoc([moduleRow('AC-1', '古い。')]).replaceAll('\n', '\r\n')
    const { updated } = checkModuleDoc({ text: before, module: 'order', rows, required: true })
    assert.equal(updated, moduleDoc(table).replaceAll('\n', '\r\n'))
  })

  test('書き換えた直後の検査は通り、2 回目の書き換えで差分が出ない', () => {
    const first = checkModuleDoc({ text: moduleDoc(['']), module: 'order', rows, required: true }).updated
    const second = checkModuleDoc({ text: first, module: 'order', rows, required: true })
    assert.deepEqual(second.problems, [])
    assert.equal(second.updated, first)
  })

  test('「今有効な AC」の節がない ③ は書き換えない', () => {
    const text = moduleDoc(table).replace('## 今有効な AC', '## 今の AC')
    assert.equal(checkModuleDoc({ text, module: 'order', rows, required: true }).updated, null)
  })
})

describe('AC 一覧への転記の位置', () => {
  const definition = (ac) => ({ ac, specRel: '5-x', content: '転記する。' })
  const added = (ac) => `| AC-${ac} |  | 有効 | 5 | — | 転記する。 |`

  test('既存の行の全てより後ろの AC は、範囲の末尾に空行があっても、最後の AC の行の直後に入れる', () => {
    const base = listDoc(listRow({ ac: 'AC-1' })).replace('\n<!-- /generated:ac-list -->', '\n\n\n<!-- /generated:ac-list -->')
    const expected = ['# AC 一覧', '', '説明。', '', '<!-- generated:ac-list -->', ...LIST_HEADER, listRow({ ac: 'AC-1' }), added(5), '', '', '<!-- /generated:ac-list -->', ''].join('\n')
    assert.equal(addAcsToList(base, [definition(5)]), expected)
  })

  test('既存の行が 0 件なら、区切りの行の直後に入れる', () => {
    const base = listDoc().replace('\n<!-- /generated:ac-list -->', '\n\n<!-- /generated:ac-list -->')
    const expected = ['# AC 一覧', '', '説明。', '', '<!-- generated:ac-list -->', ...LIST_HEADER, added(5), added(6), '', '<!-- /generated:ac-list -->', ''].join('\n')
    assert.equal(addAcsToList(base, [definition(6), definition(5)]), expected)
  })

  test('既存の行の間の AC は、その順の位置に入れる', () => {
    const base = listDoc(listRow({ ac: 'AC-1' }), listRow({ ac: 'AC-9' }))
    const expected = listDoc(listRow({ ac: 'AC-1' }), added(5), listRow({ ac: 'AC-9' }), added(12))
    assert.equal(addAcsToList(base, [definition(12), definition(5)]), expected)
  })
})

describe('進行中の spec の AC と AC 一覧', () => {
  const IN_PROGRESS = '5'
  const spec = (markdown) => [{ rel: '5-x', ...parseSpecAcDefinitions(markdown, '5-x') }]

  test('進行中の spec の AC が AC 一覧にあり、元の spec がその spec なら通る', () => {
    const rows = rowsOf(listRow({ ac: 'AC-5-1', origin: IN_PROGRESS }))
    assert.deepEqual(checkInProgress({ rows, specs: spec('- **AC-5-1** 一つ目。') }), [])
  })

  test('進行中の spec の AC が AC 一覧になければ、草案でも確定でも失敗にする', () => {
    assert.equal(checkInProgress({ rows: rowsOf(listRow()), specs: spec('- **AC-5-1** 一つ目。') }).length, 1)
  })

  test('転記の後に spec から AC を消したのに、AC 一覧に有効のまま残れば失敗にする', () => {
    const rows = rowsOf(listRow({ ac: 'AC-5-1', origin: IN_PROGRESS }), listRow({ ac: 'AC-5-2', origin: IN_PROGRESS }))
    const problems = checkInProgress({ rows, specs: spec('- **AC-5-1** 一つ目。') })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /AC-5-2 は、元の spec（5-x）にない/)
  })

  test('進行中の spec の AC の番号が、元の spec が別の既存の AC と重なれば失敗にする', () => {
    const rows = rowsOf(listRow({ ac: 'AC-12', origin: 'MVP-003' }))
    assert.equal(checkInProgress({ rows, specs: spec('- **AC-12** 一つ目。') }).length, 1)
  })

  test('AC 一覧の元の spec がその進行中の spec かを、番号で比べる（別の番号、リンクの形、0 埋めの違いは失敗）', () => {
    for (const origin of ['6', '[5](../08-spec/5-x/spec.md)', '05', '5-x', 'MVP-5', 'MVP-005']) {
      const rows = rowsOf(listRow({ ac: 'AC-5-1', origin }))
      assert.equal(checkInProgress({ rows, specs: spec('- **AC-5-1** 一つ目。') }).length, 1, origin)
    }
    const dev = [{ rel: '7-dev-process', ...parseSpecAcDefinitions('- **AC-7-1** 一つ目。', '7-dev-process') }]
    assert.deepEqual(checkInProgress({ rows: rowsOf(listRow({ ac: 'AC-7-1', origin: '7' })), specs: dev }), [])
    assert.equal(checkInProgress({ rows: rowsOf(listRow({ ac: 'AC-7-1', origin: '007' })), specs: dev }).length, 1)
  })

  test('spec から消した AC は、元の spec の番号がその spec の番号の行だけを問題にする', () => {
    const rows = rowsOf(listRow({ ac: 'AC-5-1', origin: IN_PROGRESS }), listRow({ ac: 'AC-5-2', origin: IN_PROGRESS }), listRow({ ac: 'AC-6-1', origin: '6' }), listRow({ ac: 'AC-50-1', origin: '50' }))
    const problems = checkInProgress({ rows, specs: spec('- **AC-5-1** 一つ目。') })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /AC-5-2/)
  })

  test('元の spec が MVP の行（MVP-007）は、同じ数の進行中の spec（7）の行として扱わない', () => {
    const dev = [{ rel: '7-dev-process', ...parseSpecAcDefinitions('- **AC-7-1** 一つ目。', '7-dev-process') }]
    const rows = rowsOf(listRow({ ac: 'AC-7', origin: 'MVP-007' }), listRow({ ac: 'AC-7-1', origin: '7' }))
    assert.deepEqual(checkInProgress({ rows, specs: dev }), [])
  })

  test('転記の後に、進行中の spec で AC の本文を修正しても、AC 一覧と比べない', () => {
    const rows = rowsOf(listRow({ ac: 'AC-5-1', origin: IN_PROGRESS, content: '転記した文。' }))
    assert.deepEqual(checkInProgress({ rows, specs: spec('- **AC-5-1** 直した文。') }), [])
  })

  test('アーカイブに移した spec の AC が、AC 一覧にだけあっても通る', () => {
    const rows = rowsOf(listRow({ ac: 'AC-5-1', origin: '5' }))
    assert.deepEqual(checkInProgress({ rows, specs: [] }), [])
  })

  test('--write は、AC 一覧にない進行中の spec の AC を、AC の順の位置に転記する（モジュールは空、状態は有効）', () => {
    const document = listDoc(listRow({ ac: 'AC-1' }), listRow({ ac: 'AC-5-3', origin: IN_PROGRESS }), listRow({ ac: 'AC-9-1', origin: '9' }))
    const { definitions } = parseSpecAcDefinitions('- **AC-5-1** 一つ目。\n- **AC-5-3** 三つ目。\n- **AC-5-10** 十。', '5-x')
    const updated = addAcsToList(document, definitions)
    assert.equal(
      updated,
      listDoc(
        listRow({ ac: 'AC-1' }),
        `| AC-5-1 |  | 有効 | ${IN_PROGRESS} | — | 一つ目。 |`,
        listRow({ ac: 'AC-5-3', origin: IN_PROGRESS }),
        `| AC-5-10 |  | 有効 | ${IN_PROGRESS} | — | 十。 |`,
        listRow({ ac: 'AC-9-1', origin: '9' }),
      ),
    )
    // 転記した行はモジュールが空なので、モジュールを書くまで検査が失敗する
    const read = readAcList(updated)
    assert.equal(checkAcRows({ rows: read.rows, modules: MODULES, specDirs: SPEC_DIRS }).length, 2)
    assert.deepEqual(checkInProgress({ rows: read.rows, specs: [{ rel: '5-x', definitions }] }), [])
  })

  test('--write の転記は、読めない AC 一覧（マーカーの閉じがない）では例外を投げる', () => {
    const broken = listDoc(listRow()).replace('<!-- /generated:ac-list -->', '')
    const { definitions } = parseSpecAcDefinitions('- **AC-5-1** 一つ目。', '5-x')
    assert.throws(() => addAcsToList(broken, definitions), /AC 一覧を読めないので転記しない/)
  })

  test('--write は、範囲の外と既存の行の改行コード（CRLF）を変えず、転記する AC がなければ文書を変えない', () => {
    const document = listDoc(listRow({ ac: 'AC-1' })).replaceAll('\n', '\r\n')
    assert.equal(addAcsToList(document, []), document)
    const { definitions } = parseSpecAcDefinitions('- **AC-5-1** 一つ目。', '5-x')
    assert.equal(addAcsToList(document, definitions), listDoc(listRow({ ac: 'AC-1' }), `| AC-5-1 |  | 有効 | ${IN_PROGRESS} | — | 一つ目。 |`).replaceAll('\n', '\r\n'))
  })
})

describe('進行中の spec を読む範囲', () => {
  test('docs/08-spec/ の直下の spec.md だけを読み、アーカイブと spec.md のないディレクトリは読まない', () => {
    const dir = mkdtempSync(path.join(tmpdir(), 'ac-check-'))
    try {
      mkdirSync(path.join(dir, '5-x'))
      writeFileSync(path.join(dir, '5-x', 'spec.md'), '- **AC-5-1** 一つ目。\n')
      mkdirSync(path.join(dir, '6-plan-only'))
      writeFileSync(path.join(dir, '6-plan-only', 'plan.md'), '- **AC-6-1** plan の中。\n')
      mkdirSync(path.join(dir, 'archive', 'mvp', '001-old'), { recursive: true })
      writeFileSync(path.join(dir, 'archive', 'mvp', '001-old', 'spec.md'), '- **AC-1\n壊れた spec。\n')
      const { specs, problems } = readInProgressSpecs(dir)
      assert.deepEqual(problems, [])
      assert.deepEqual(specs.map((spec) => [spec.rel, spec.definitions.map((definition) => definition.ac)]), [['5-x', ['5-1']]])
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  })

  test('進行中の spec の崩れた定義の行を、spec のパスを付けて問題にする', () => {
    const dir = mkdtempSync(path.join(tmpdir(), 'ac-check-'))
    try {
      mkdirSync(path.join(dir, '5-x'))
      writeFileSync(path.join(dir, '5-x', 'spec.md'), '- **AC-5-1**\n')
      const { problems } = readInProgressSpecs(dir)
      assert.equal(problems.length, 1)
      assert.match(problems[0], /5-x\/spec\.md/)
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  })
})

describe('テストとの対応（AC 一覧の AC に対して）', () => {
  const tests = (...names) => names.map((name) => ({ file: 'X.test.ts', name }))

  test('テスト名が参照する AC が、AC 一覧になければ失敗にする（アーカイブの spec にあっても）', () => {
    const specAcs = new Set(rowsOf(listRow({ ac: 'AC-1' })).map((row) => row.ac))
    const result = check({ specAcs, tests: tests('AC-1 a', 'AC-2 アーカイブにだけある'), exclusions: {} })
    assert.deepEqual(result.unknown.map((item) => item.ac), [2])
  })

  test('--tests の表示：AC ごとにテストのファイル、除外、なしを示す', () => {
    const rows = rowsOf(listRow({ ac: 'AC-1' }), listRow({ ac: 'AC-2' }), listRow({ ac: 'AC-3' }))
    const testFiles = new Map([[1, ['backend/a/TaxApiTest.java', 'e2e/tests/tax.spec.ts', 'backend/a/TaxApiTest.java']]])
    const { lines, ok } = renderTestMap({ list: { rows, problems: [] }, testFiles, exclusions: { 'AC-2': '手で確認する' } })
    assert.equal(ok, true)
    assert.deepEqual(lines.slice(0, 3), ['AC-1：backend/a/TaxApiTest.java、e2e/tests/tax.spec.ts', 'AC-2：除外（手で確認する）', 'AC-3：なし'])
  })

  test('--tests で、AC 一覧の読み取りに失敗したら失敗にする', () => {
    const { ok } = renderTestMap({ list: readAcList('# 印のない一覧'), testFiles: new Map(), exclusions: {} })
    assert.equal(ok, false)
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
    assert.deepEqual(
      parseSpecAcDefinitions(spec, '7-dev-process').definitions.map(({ ac, content }) => ({ ac, content })),
      [
        { ac: 12, content: 'MVP の AC。' },
        { ac: '7-1', content: '新しい形の AC。' },
        { ac: '7-2', content: '二つ目。' },
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

  test('空白（半角・全角）で区切った AC の並びを見つける', () => {
    assert.deepEqual(findSpacedAcLists('AC-1 AC-2 注文'), ['AC-1 AC-2'])
    assert.deepEqual(findSpacedAcLists('AC-98 AC-187 AC-189 注文'), ['AC-98 AC-187 AC-189'])
    assert.deepEqual(findSpacedAcLists('AC-7-1　AC-7-2 注文'), ['AC-7-1　AC-7-2'])
  })

  test('読点で並べた AC と、1 つだけの AC は、空白の並びにしない', () => {
    assert.deepEqual(findSpacedAcLists('AC-1、AC-2 注文'), [])
    assert.deepEqual(findSpacedAcLists('AC-1、2 注文'), [])
    assert.deepEqual(findSpacedAcLists('AC-7-1 数量を 10 にする'), [])
    assert.deepEqual(findSpacedAcLists('AC-1 注文し、AC-2 の確認も'), [])
  })

  test('空白で区切った AC の並びのテスト名があれば、check は失敗する（手元でも）', () => {
    const result = check({ specAcs: new Set([1, 2, 3]), tests: tests('AC-1 AC-2 a', 'AC-3 b'), exclusions: {}, strict: false })
    assert.equal(result.ok, false)
    assert.deepEqual(result.spaced, [{ file: 'X.test.ts', name: 'AC-1 AC-2 a' }])
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

  test('AC 一覧への転記：MVP の形の行の後に、新しい形（AC-<spec の番号>-<連番>）の行を、spec の番号と連番の順に置き、元の spec は 0 埋めのない番号だけで書く。読み戻せる', () => {
    const { definitions } = parseSpecAcDefinitions('- **AC-12** 一つ目。\n- **AC-7-2** 三つ目。\n- **AC-7-1** 二つ目。', '7-dev-process')
    const empty = ['<!-- generated:ac-list -->', '| AC | モジュール | 状態 | 元の spec | 変えた spec | 今の内容 |', '|----|------------|------|-----------|-------------|----------|', '<!-- /generated:ac-list -->'].join('\n')
    const markdown = addAcsToList(empty, definitions)
    assert.match(markdown, /^\| AC-7-1 \| {2}\| 有効 \| 7 \| — \| 二つ目。 \|$/m)
    assert.ok(markdown.indexOf('| AC-12 |') < markdown.indexOf('| AC-7-1 |'))
    assert.ok(markdown.indexOf('| AC-7-1 |') < markdown.indexOf('| AC-7-2 |'))
    const { rows, problems } = readAcList(markdown)
    assert.deepEqual(problems, [])
    assert.deepEqual(rows.map((row) => row.ac), [12, '7-1', '7-2'])
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

describe('スキップしたまとまりの中のテスト', () => {
  test('describe.skip・describe.fixme・test.describe.skip・test.describe.fixme・xdescribe の中のテストは数えない', () => {
    const source = [
      "describe.skip('まとまり', () => {",
      "  it('AC-1 数えない', () => { expect(f('x)')).toBe(1) })",
      '})',
      "test.describe.fixme('まとまり', () => {",
      "  test('AC-2 数えない', async () => {})",
      '})',
      "test.describe.skip('まとまり', () => { test('AC-3 数えない', () => {}) })",
      "describe.fixme('まとまり', () => { it('AC-4 数えない', () => {}) })",
      "xdescribe('まとまり', () => { it('AC-5 数えない', () => {}) })",
      "describe('生きている', () => { it('AC-6 数える', () => {}) })",
      "it('AC-7 数える', () => {})",
    ].join('\n')
    assert.deepEqual(extractTestNames(source, 'ts'), ['AC-6 数える', 'AC-7 数える'])
  })
})

describe('テストのファイルの選び方', () => {
  test('Java の src/test・src/integrationTest と .test.ts(x)・.spec.ts を選び、他のファイルを選ばない', () => {
    const files = [
      'backend/ecsite/src/test/java/a/ATest.java',
      'backend/ecsite/src/integrationTest/java/a/BIT.java',
      'backend/ecsite/src/main/java/a/Main.java',
      'backend/ecsite/src/test/resources/x.sql',
      'frontend/store/src/a.test.ts',
      'frontend/store/src/b.test.tsx',
      'frontend/store/e2e/c.spec.ts',
      'frontend/store/src/d.spec.tsx',
      'frontend/store/src/e.ts',
    ]
    assert.deepEqual(
      findTestFiles('/r', files).map((entry) => [path.relative('/r', entry.file).split(path.sep).join('/'), entry.kind]),
      [
        ['backend/ecsite/src/test/java/a/ATest.java', 'java'],
        ['backend/ecsite/src/integrationTest/java/a/BIT.java', 'java'],
        ['frontend/store/src/a.test.ts', 'ts'],
        ['frontend/store/src/b.test.tsx', 'ts'],
        ['frontend/store/e2e/c.spec.ts', 'ts'],
      ],
    )
  })
})

describe('スキップしたまとまりの範囲とコメント', () => {
  test('まとまりの中のコメントに引用符や括弧があっても、後ろのテストを数える', () => {
    const source = [
      "describe.skip('まとまり', () => {",
      "  // it's skipped (for now",
      "  it('AC-1 数えない', () => {})",
      '})',
      "it('AC-2 数える', () => {})",
    ].join('\n')
    assert.deepEqual(extractTestNames(source, 'ts'), ['AC-2 数える'])
  })
})

describe('--write を script として実行する', () => {
  const script = path.join(path.dirname(fileURLToPath(import.meta.url)), 'ac-check.mjs')
  const LIST = 'docs/03-機能仕様/acceptance-criteria.md'
  const ORDER = 'docs/03-機能仕様/order.md'
  const PAYMENT = 'docs/03-機能仕様/legacy.md'
  const SPEC = 'docs/08-spec/5-x/spec.md'

  /** 構造の検査の対象の一覧にない ③（マーカーがなく、表は AC 一覧と違う） */
  const paymentText = ['# 旧モジュール', '', '## 今有効な AC', '', ...MODULE_HEADER, moduleRow('AC-9', '古い。'), ''].join('\n')

  /** 書き換える前のファイル。AC 一覧にない進行中の spec の AC と、古い注文の ③ の表がある */
  function fixture(overrides = {}) {
    return {
      [LIST]: listDoc(listRow({ ac: 'AC-1', content: '一つ目。' })),
      [ORDER]: moduleDoc([moduleRow('AC-1', '古い。')]),
      [PAYMENT]: paymentText,
      [SPEC]: '- **AC-5-1** 転記する。\n',
      ...overrides,
    }
  }

  /** git の作業ディレクトリにファイルを置いて --write を実行し、終了コードと実行の後のファイルを返す */
  function runWrite(files) {
    const root = mkdtempSync(path.join(tmpdir(), 'ac-check-'))
    try {
      execFileSync('git', ['init', '-q'], { cwd: root, env: withoutGitEnv() })
      for (const [file, text] of Object.entries(files)) {
        mkdirSync(path.dirname(path.join(root, file)), { recursive: true })
        writeFileSync(path.join(root, file), text)
      }
      const result = spawnSync(process.execPath, [script, '--write'], { cwd: root, encoding: 'utf8', env: withoutGitEnv() })
      const after = Object.fromEntries(Object.keys(files).map((file) => [file, readFileSync(path.join(root, file), 'utf8')]))
      return { status: result.status, stdout: result.stdout, after }
    } finally {
      rmSync(root, { recursive: true, force: true })
    }
  }

  test('AC 一覧のマーカーの範囲が壊れているとき、終了コード 1 で、どのファイルも変えない', () => {
    const files = fixture({ [LIST]: listDoc(listRow({ ac: 'AC-1', content: '一つ目。' })).replace('<!-- /generated:ac-list -->', '') })
    const { status, stdout, after } = runWrite(files)
    assert.equal(status, 1, stdout)
    assert.deepEqual(after, files)
  })

  test('対象の ③ に「今有効な AC」の節がないとき、AC 一覧への転記も含めて何も書かずに失敗する', () => {
    const files = fixture({ [ORDER]: moduleDoc([moduleRow('AC-1', '古い。')]).replace('## 今有効な AC', '## 今の AC') })
    const { status, stdout, after } = runWrite(files)
    assert.equal(status, 1, stdout)
    assert.deepEqual(after, files)
  })

  test('転記と対象の ③ の書き換えをし、構造の検査の対象の一覧にない ③ は変えない', () => {
    const files = fixture()
    const { status, stdout, after } = runWrite(files)
    assert.equal(status, 0, stdout)
    assert.match(after[LIST], /^\| AC-5-1 \| {2}\| 有効 \| 5 \| — \| 転記する。 \|$/m)
    assert.equal(after[ORDER], moduleDoc([...MODULE_HEADER, moduleRow('AC-1', '一つ目。')]))
    assert.equal(after[PAYMENT], paymentText)
  })
})

describe('読めないテストの呼び出し', () => {
  test('ファイル:行と理由で返し、each の表の中の関数の呼び出しは読めるので返さない', () => {
    const source = ["test.each([[f(1)]])('AC-1 読める', () => {})", '', 'test(name, () => {})'].join('\n')
    assert.deepEqual(describeUnreadableTests(source, 'frontend/a.test.ts', scanTsTests(source).unreadable), [
      'frontend/a.test.ts:3 test(name, () => {})（テスト名が文字列でもテンプレートでもない（または閉じていない））',
    ])
  })
})
