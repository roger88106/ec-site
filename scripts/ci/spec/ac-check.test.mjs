// ac-check.mjs のテスト（node --test scripts/ci/spec/ac-check.test.mjs）。
// 照合、AC 一覧、テスト名の読み方のテストは lib/ac/ のテストにある。ここは、入口の判定と、script としての実行を調べる
import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { cpSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { withoutGitEnv } from '../../shared/git/git-env.mjs'
import { extractTestNames } from '../../shared/source/test-files.mjs'
import { isStrict } from './ac-check.mjs'

/** リポジトリのルート */
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../')

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

describe('手元と CI の見分け', () => {
  test('CI（環境変数 CI が true）では、テスト名に出ない AC を失敗にする', () => {
    assert.equal(isStrict({ CI: 'true' }), true)
  })

  test('手元（CI がない）では、テスト名に出ない AC を警告にする', () => {
    assert.equal(isStrict({}), false)
  })
})

describe('ac-check.mjs を script として実行する', () => {
  const script = path.join(path.dirname(fileURLToPath(import.meta.url)), 'ac-check.mjs')
  const LIST = 'docs/03-機能仕様/acceptance-criteria.md'
  const ORDER = 'docs/03-機能仕様/order.md'
  const LEGACY = 'docs/03-機能仕様/legacy.md'
  const SPEC = 'docs/08-spec/5-x/spec.md'
  const ORIGIN_SPEC = 'docs/08-spec/archive/mvp/001-x/spec.md'
  const BACKEND_MODULE = 'backend/ecsite/src/main/java/com/ecsite/module/order/Order.java'
  const TEST_FILE = 'frontend/store/src/a.test.ts'

  /** 構造の検査の対象の一覧にない ③（マーカーがなく、表は AC 一覧と違う） */
  const legacyText = ['# 旧モジュール', '', '## 今有効な AC', '', ...MODULE_HEADER, moduleRow('AC-9', '古い。'), ''].join('\n')

  /** 書き換える前のファイル。AC 一覧にない進行中の spec の AC と、古い注文の ③ の表がある */
  function fixture(overrides = {}) {
    return {
      [LIST]: listDoc(listRow({ ac: 'AC-1', content: '一つ目。' })),
      [ORDER]: moduleDoc([moduleRow('AC-1', '古い。')]),
      [LEGACY]: legacyText,
      [SPEC]: '- **AC-5-1** 転記する。\n',
      ...overrides,
    }
  }

  /** 照合が通るファイル。AC 一覧の 2 つの AC が注文のモジュールにあり、2 つともテスト名に出る */
  function passing(overrides = {}) {
    return {
      [LIST]: listDoc(listRow({ ac: 'AC-1', content: '一つ目。' }), listRow({ ac: 'AC-2', content: '二つ目。' })),
      [ORDER]: moduleDoc([...MODULE_HEADER, moduleRow('AC-1', '一つ目。'), moduleRow('AC-2', '二つ目。')]),
      [ORIGIN_SPEC]: '- **AC-1** 一つ目。\n',
      [BACKEND_MODULE]: 'class Order {}\n',
      [TEST_FILE]: "test('AC-1 一つ目', () => {})\ntest('AC-2 二つ目', () => {})\n",
      ...overrides,
    }
  }

  /**
   * git の作業ディレクトリにファイルと script の写しを置いて実行し、終了コードと出力と実行の後のファイルを返す。
   *
   * 除外の一覧は script と同じフォルダから読むので、script の写しの隣に、このテストの一覧を置く。
   */
  function run(files, { args = [], env = {}, exclusions = {} } = {}) {
    const root = mkdtempSync(path.join(tmpdir(), 'ac-check-'))
    try {
      execFileSync('git', ['init', '-q'], { cwd: root, env: withoutGitEnv() })
      const ciDir = path.join(ROOT, 'scripts/ci')
      cpSync(path.join(ciDir, 'lib'), path.join(root, 'scripts/ci/lib'), { recursive: true, filter: (source) => !source.endsWith('.test.mjs') })
      cpSync(path.join(ROOT, 'scripts/shared'), path.join(root, 'scripts/shared'), { recursive: true, filter: (source) => !source.endsWith('.test.mjs') })
      mkdirSync(path.join(root, 'scripts/ci/spec'), { recursive: true })
      cpSync(script, path.join(root, 'scripts/ci/spec/ac-check.mjs'))
      writeFileSync(path.join(root, 'scripts/ci/spec/ac-exclusions.json'), JSON.stringify(exclusions))
      for (const [file, text] of Object.entries(files)) {
        mkdirSync(path.dirname(path.join(root, file)), { recursive: true })
        writeFileSync(path.join(root, file), text)
      }
      const result = spawnSync(process.execPath, [path.join(root, 'scripts/ci/spec/ac-check.mjs'), ...args], { cwd: root, encoding: 'utf8', env: { ...withoutGitEnv(), ...env } })
      const after = Object.fromEntries(Object.keys(files).map((file) => [file, readFileSync(path.join(root, file), 'utf8')]))
      return { status: result.status, stdout: result.stdout, after }
    } finally {
      rmSync(root, { recursive: true, force: true })
    }
  }

  test('AC 一覧のマーカーの範囲が壊れているとき、終了コード 1 で、どのファイルも変えない', () => {
    const files = fixture({ [LIST]: listDoc(listRow({ ac: 'AC-1', content: '一つ目。' })).replace('<!-- /generated:ac-list -->', '') })
    const { status, stdout, after } = run(files, { args: ['--write'] })
    assert.equal(status, 1, stdout)
    assert.deepEqual(after, files)
  })

  test('対象の ③ に「今有効な AC」の節がないとき、AC 一覧への転記も含めて何も書かずに失敗する', () => {
    const files = fixture({ [ORDER]: moduleDoc([moduleRow('AC-1', '古い。')]).replace('## 今有効な AC', '## 今の AC') })
    const { status, stdout, after } = run(files, { args: ['--write'] })
    assert.equal(status, 1, stdout)
    assert.deepEqual(after, files)
  })

  test('転記と対象の ③ の書き換えをし、構造の検査の対象の一覧にない ③ は変えない', () => {
    const files = fixture()
    const { status, stdout, after } = run(files, { args: ['--write'] })
    assert.equal(status, 0, stdout)
    assert.match(after[LIST], /^\| AC-5-1 \| {2}\| 有効 \| 5 \| — \| 転記する。 \|$/m)
    assert.equal(after[ORDER], moduleDoc([...MODULE_HEADER, moduleRow('AC-1', '一つ目。')]))
    assert.equal(after[LEGACY], legacyText)
  })

  test('照合が全て通れば、終了コード 0 で、件数の要約の行を表示する', () => {
    const { status, stdout } = run(passing(), { env: { CI: 'true' } })
    assert.equal(status, 0, stdout)
    assert.match(stdout, /^OK：AC 2 件（廃止 0 件）、③ の生成の表 1 件、テスト名 2 件、除外 0 件$/m)
  })

  test('AC 一覧の行に違反があれば、終了コード 1 で、AC 一覧のファイルと行を示す', () => {
    const files = passing({ [LIST]: listDoc(listRow({ ac: 'AC-1', changedBy: 'MVP-001', content: '一つ目。' }), listRow({ ac: 'AC-2', content: '二つ目。' })) })
    const { status, stdout } = run(files, { env: { CI: 'true' } })
    assert.equal(status, 1, stdout)
    assert.match(stdout, /^docs\/03-機能仕様\/acceptance-criteria\.md：8 行：AC-1：有効の AC の変えた spec は「—」にする/m)
    assert.match(stdout, /^NG：AC 2 件/m)
  })

  test('CI（環境変数 CI が true）では、テスト名に出ない有効な AC があると終了コード 1 で、AC の番号を示す', () => {
    const files = passing({ [TEST_FILE]: "test('AC-1 一つ目', () => {})\n" })
    const { status, stdout } = run(files, { env: { CI: 'true' } })
    assert.equal(status, 1, stdout)
    assert.match(stdout, /^テスト名に出ない AC：AC-2$/m)
    assert.match(stdout, /^NG：/m)
  })

  test('手元（CI が true でない）では、テスト名に出ない有効な AC は終了コード 0 の警告にする', () => {
    const files = passing({ [TEST_FILE]: "test('AC-1 一つ目', () => {})\n" })
    const { status, stdout } = run(files, { env: { CI: 'false' } })
    assert.equal(status, 0, stdout)
    assert.match(stdout, /^警告：テスト名に出ない AC：AC-2$/m)
    assert.match(stdout, /^OK：/m)
  })

  test('--tests は、AC ごとにテストのファイルか除外を表示し、最後に件数の行を出して終了コード 0 にする', () => {
    const files = passing({ [TEST_FILE]: "test('AC-1 一つ目', () => {})\n" })
    const { status, stdout } = run(files, { args: ['--tests'], exclusions: { 'AC-2': '手で確認する' } })
    assert.equal(status, 0, stdout)
    assert.deepEqual(stdout.trimEnd().split('\n'), [`AC-1：${TEST_FILE}`, 'AC-2：除外（手で確認する）', 'AC とテストの対応：AC 2 件'])
  })

  test('--tests は、AC 一覧を読めなければ、問題と「NG：AC 一覧を読めない」を表示して終了コード 1 にする', () => {
    const files = passing({ [LIST]: passing()[LIST].replace('<!-- /generated:ac-list -->', '') })
    const { status, stdout } = run(files, { args: ['--tests'] })
    assert.equal(status, 1, stdout)
    assert.match(stdout, /開きが 1 個、閉じが 0 個/)
    assert.match(stdout, /^NG：AC 一覧を読めない$/m)
  })
})
