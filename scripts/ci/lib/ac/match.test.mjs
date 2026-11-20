// lib/ac/match.mjs のテスト（node --test scripts/ci/lib/ac/match.test.mjs）
import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { parseSpecAcDefinitions } from '../../../shared/docs/ac-definitions.mjs'
import { addAcsToList, parseAcList } from './ac-list.mjs'
import { check, checkAcRows, checkInProgress, checkModuleDoc, checkModuleDocNames, renderTestMap } from './match.mjs'

/** 照合に渡すテスト（ファイルは共通で、名前だけを変える） */
const tests = (...names) => names.map((name) => ({ file: 'X.test.ts', name }))

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
 *
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
  const read = parseAcList(listDoc(...rows))
  return [...read.problems, ...checkAcRows({ rows: read.rows, modules: MODULES, specDirs: SPEC_DIRS })]
}

/** AC 一覧の行を読む（問題がないことも確認する） */
function rowsOf(...rows) {
  const read = parseAcList(listDoc(...rows))
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

describe('照合', () => {
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

describe('1→10 の AC（AC-n-m）の照合', () => {
  test('曖昧な書き方のテスト名があれば、check は失敗する（手元でも）', () => {
    const result = check({ specAcs: new Set(['7-1', '7-2', 2]), tests: tests('AC-7-1、2 a', 'AC-7-2 b'), exclusions: {}, strict: false })
    assert.equal(result.ok, false)
    assert.deepEqual(result.ambiguous, [{ file: 'X.test.ts', name: 'AC-7-1、2 a' }])
  })

  test('空白で区切った AC の並びのテスト名があれば、check は失敗する（手元でも）', () => {
    const result = check({ specAcs: new Set([1, 2, 3]), tests: tests('AC-1 AC-2 a', 'AC-3 b'), exclusions: {}, strict: false })
    assert.equal(result.ok, false)
    assert.deepEqual(result.spaced, [{ file: 'X.test.ts', name: 'AC-1 AC-2 a' }])
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

  test('AC 番号の後の空白が抜けたテスト名（AC-7-41 回目、AC-7-4１回目）は、AC-7-4 のテストとして数えず、検査が失敗する', () => {
    for (const name of ['AC-7-41 回目 a', 'AC-7-4１回目 a']) {
      const result = check({ specAcs: new Set(['7-4']), tests: tests(name), exclusions: {} })
      assert.equal(result.ok, false)
      assert.deepEqual(result.unknown, [{ file: 'X.test.ts', name, ac: '7-41' }])
      assert.deepEqual(result.missing, ['7-4'])
    }
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
})

describe('AC 一覧の行の検査', () => {
  test('列が AC 一覧の定義どおりの一覧は、問題にしない', () => {
    assert.deepEqual(listProblems(listRow()), [])
  })

  test('正しい行は通る（有効、変更、廃止、共通）', () => {
    const changed = listRow({ ac: 'AC-2', status: '変更', changedBy: 'MVP-019', content: '1 円以上。根拠は[注文モジュール](../../../../docs/03-機能仕様/order.md)の「注文（集約）」。' })
    const deprecated = listRow({ ac: 'AC-3', module: '', status: '廃止', changedBy: 'MVP-019', content: '' })
    const common = listRow({ ac: 'AC-4', module: 'common', content: '死活監視は運用ポートで出す。根拠は[共通コード・ログ・可観測性](../../../../docs/02-設計/common.md)の「メトリクスと死活監視」。' })
    assert.deepEqual(listProblems(listRow(), changed, deprecated, common), [])
  })

  test('同じ AC の行が 2 つあれば問題にする', () => {
    const problems = listProblems(listRow(), listRow())
    assert.equal(problems.length, 1)
    assert.match(problems[0], /同じ AC の行が/)
  })

  test('状態が有効・変更・廃止のどれでもなければ問題にする', () => {
    for (const status of ['有功', '']) {
      const problems = listProblems(listRow({ status }))
      assert.equal(problems.length, 1, status)
      assert.match(problems[0], /状態が有効・変更・廃止のどれでもない/, status)
    }
  })

  test('有効・変更の AC のモジュールが、空白だけ・「—」・「-」なら問題にする', () => {
    for (const module of ['', '—', '-']) {
      const problems = listProblems(listRow({ module }))
      assert.equal(problems.length, 1, module)
      assert.match(problems[0], /モジュールが、③ の名前か common の 1 つでない/, module)
    }
  })

  test('モジュールのセルに 2 つ書くと問題にする', () => {
    for (const module of ['order、cart', 'order, cart', 'order/cart']) {
      const problems = listProblems(listRow({ module }))
      assert.equal(problems.length, 1, module)
      assert.match(problems[0], /モジュールが、③ の名前か common の 1 つでない/, module)
    }
  })

  test('モジュールの名前の大文字小文字・全角半角の違い、.md やパスを付けた名前を問題にする', () => {
    for (const module of ['Order', 'ｏｒｄｅｒ', 'order.md', 'docs/03-機能仕様/order.md']) {
      const problems = listProblems(listRow({ module }))
      assert.equal(problems.length, 1, module)
      assert.match(problems[0], /モジュールが、③ の名前か common の 1 つでない/, module)
    }
  })

  test('廃止の AC のモジュールは空でよく、空でなければ ③ の名前か common でなければならない', () => {
    const deprecated = (module) => listRow({ module, status: '廃止', changedBy: 'MVP-019', content: '' })
    assert.deepEqual(listProblems(deprecated('')), [])
    assert.deepEqual(listProblems(deprecated('order')), [])
    const problems = listProblems(deprecated('Order'))
    assert.equal(problems.length, 1)
    assert.match(problems[0], /廃止の AC のモジュールが、空でも ③ の名前でも common でもない/)
  })

  test('廃止の AC の変えた spec が、空か「—」なら問題にし、番号があれば通す', () => {
    for (const changedBy of ['', '—']) {
      const problems = listProblems(listRow({ module: '', status: '廃止', changedBy, content: '' }))
      assert.equal(problems.length, 1, changedBy)
      assert.match(problems[0], /廃止の AC の変えた spec がない/, changedBy)
    }
    assert.deepEqual(listProblems(listRow({ module: '', status: '廃止', changedBy: '227', content: '' })), [])
  })

  test('変更の AC の変えた spec が、空か「—」なら問題にする', () => {
    for (const changedBy of ['', '—']) {
      const row = listRow({ status: '変更', changedBy, content: '根拠は[注文モジュール](../../../../docs/03-機能仕様/order.md)の「注文（集約）」。' })
      const problems = listProblems(row)
      assert.equal(problems.length, 1, changedBy)
      assert.match(problems[0], /変更の AC の変えた spec がない/, changedBy)
    }
  })

  test('有効の AC の変えた spec が「—」以外なら問題にする', () => {
    for (const changedBy of ['', 'MVP-019', '-']) {
      const problems = listProblems(listRow({ changedBy }))
      assert.equal(problems.length, 1, changedBy)
      assert.match(problems[0], /有効の AC の変えた spec は「—」にする/, changedBy)
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

  test('AC-n の行は、元の spec が番号だけだと、ディレクトリがあっても問題にする（有効、変更、廃止）', () => {
    const content = '根拠は[注文モジュール](../../../../docs/03-機能仕様/order.md)の「注文（集約）」。'
    const rows = [listRow({ ac: 'AC-312', origin: '227' }), listRow({ ac: 'AC-312', origin: '227', status: '変更', changedBy: '227', content }), listRow({ ac: 'AC-312', origin: '227', module: '', status: '廃止', changedBy: '227', content: '' })]
    for (const row of rows) {
      const problems = listProblems(row)
      assert.equal(problems.length, 1, row)
      assert.match(problems[0], /AC-312：元の spec（227）が番号だけ/, row)
    }
  })

  test('AC-n の番号が上限の 312 を超える行は、元の spec と状態によらず問題にする', () => {
    const content = '1 円以上。根拠は[注文モジュール](../../../../docs/03-機能仕様/order.md)の「注文（集約）」。'
    const rows = [listRow({ ac: 'AC-313', origin: 'MVP-019' }), listRow({ ac: 'AC-313', origin: 'MVP-019', status: '変更', changedBy: 'MVP-019', content }), listRow({ ac: 'AC-313', origin: 'MVP-019', module: '', status: '廃止', changedBy: 'MVP-019', content: '' })]
    for (const row of rows) {
      const problems = listProblems(row)
      assert.equal(problems.length, 1, row)
      assert.match(problems[0], /AC-313：AC-n の番号が上限（312）を超えている/, row)
    }
    assert.ok(listProblems(listRow({ ac: 'AC-400', origin: '227' })).some((problem) => problem.includes('上限（312）')))
  })

  test('AC-n の番号が上限の 312 の行と、AC-s-n の形の行は、上限の問題にしない', () => {
    assert.deepEqual(listProblems(listRow({ ac: 'AC-312', origin: 'MVP-019' })), [])
    assert.deepEqual(listProblems(listRow({ ac: 'AC-227-313', origin: '227' })), [])
  })

  test('進行中の spec に AC-n の形で定義した AC は、転記した行が問題になる', () => {
    const { definitions } = parseSpecAcDefinitions('- **AC-400** 文。', '5-x')
    const read = parseAcList(addAcsToList(listDoc(listRow()), definitions))
    assert.deepEqual(read.problems, [])
    const problems = checkAcRows({ rows: read.rows, modules: MODULES, specDirs: SPEC_DIRS })
    assert.ok(problems.some((problem) => problem.includes('AC-400：元の spec（5）が番号だけ')), problems.join('\n'))
  })

  test('MVP-<番号> は、archive/mvp/ の下のディレクトリに対応する', () => {
    for (const origin of ['MVP-001', 'MVP-003', 'MVP-019']) {
      assert.deepEqual(listProblems(listRow({ origin })), [], origin)
    }
  })

  test('番号だけの値は、archive/mvp/ の外のディレクトリに対応する（1→10 のアーカイブと、進行中）', () => {
    for (const origin of ['227', '7', '5']) {
      assert.deepEqual(listProblems(listRow({ ac: `AC-${origin}-1`, origin })), [], origin)
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
    const specDirs = [{ name: '100-x', rel: 'archive/mvp-old/100-x' }]
    assert.equal(checkAcRows({ rows: rowsOf(listRow({ origin: 'MVP-100' })), modules: MODULES, specDirs }).length, 1)
    assert.deepEqual(checkAcRows({ rows: rowsOf(listRow({ ac: 'AC-100-1', origin: '100' })), modules: MODULES, specDirs }), [])
  })

  test('元の spec が進行中の spec（docs/08-spec/ の直下）の番号でも受け付ける', () => {
    assert.deepEqual(listProblems(listRow({ ac: 'AC-5-1', origin: '5' })), [])
  })

  test('変更・廃止の AC の変えた spec は、読点で区切った番号を受け付ける', () => {
    const content = '根拠は[注文モジュール](../../../../docs/03-機能仕様/order.md)の「注文（集約）」。'
    assert.deepEqual(listProblems(listRow({ status: '変更', changedBy: 'MVP-003、MVP-016', content })), [])
    assert.deepEqual(listProblems(listRow({ status: '廃止', module: '', changedBy: 'MVP-003、MVP-016、7', content: '' })), [])
  })

  test('変更・廃止の AC の変えた spec は、MVP-<番号> と番号だけを読点の区切りで混ぜられる', () => {
    const content = '根拠は[注文モジュール](../../../../docs/03-機能仕様/order.md)の「注文（集約）」。'
    for (const changedBy of ['MVP-003、MVP-016', 'MVP-016、227', '227、MVP-016']) {
      assert.deepEqual(listProblems(listRow({ status: '変更', changedBy, content })), [], changedBy)
      assert.deepEqual(listProblems(listRow({ status: '廃止', module: '', changedBy, content: '' })), [], changedBy)
    }
  })

  test('変えた spec を読点で区切っても、番号ごとに MVP の形を調べる（変更・廃止）', () => {
    const content = '根拠は[注文モジュール](../../../../docs/03-機能仕様/order.md)の「注文（集約）」。'
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
    const content = '根拠は[注文モジュール](../../../../docs/03-機能仕様/order.md)の「注文（集約）」。'
    const cases = ['[019](../../08-spec/archive/mvp/019-x/spec.md)', '019-x', 'abc', 'MVP-003,MVP-016', 'MVP-003 MVP-016', 'MVP-003、', '、MVP-003', 'MVP-003、[016](../../08-spec/archive/mvp/016-order/spec.md)']
    for (const changedBy of cases) {
      for (const row of [listRow({ status: '変更', changedBy, content }), listRow({ status: '廃止', module: '', changedBy, content: '' })]) {
        const problems = listProblems(row)
        assert.equal(problems.length, 1, `${changedBy}：${row}`)
        assert.match(problems[0], /変えた spec/, changedBy)
      }
    }
  })

  test('変更・廃止の AC の変えた spec の番号の spec のディレクトリがない、2 つあると、番号ごとに問題にする', () => {
    const content = '根拠は[注文モジュール](../../../../docs/03-機能仕様/order.md)の「注文（集約）」。'
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
      const problems = listProblems(listRow({ content }))
      assert.equal(problems.length, 1, content)
      assert.match(problems[0], /spec の中だけで通じる参照/, content)
    }
  })

  test('有効・変更の AC の今の内容が、空白だけ・「—」・「なし」・<…> の仮の値なら問題にする', () => {
    for (const content of ['', '—', 'なし', '<今の内容>']) {
      const problems = listProblems(listRow({ content }))
      assert.equal(problems.length, 1, content)
      assert.match(problems[0], /今の内容が空か仮の値/, content)
    }
  })

  test('有効の AC の今の内容が「-」だけなら問題にし、「-」や「なし」で始まる文は通す', () => {
    const problems = listProblems(listRow({ content: '-' }))
    assert.equal(problems.length, 1)
    assert.match(problems[0], /今の内容が空か仮の値/)
    assert.deepEqual(listProblems(listRow({ content: '-1 以下は拒否する。' })), [])
    assert.deepEqual(listProblems(listRow({ content: 'なしにはしない。' })), [])
  })

  test('今の内容が複数行（<br />）なら問題にする', () => {
    const problems = listProblems(listRow({ content: '一行目。<br />二行目。' }))
    assert.equal(problems.length, 1)
    assert.match(problems[0], /今の内容を複数行にしない/)
  })

  test('変更の AC の今の内容に、「<文書の名前>の「<見出し>」」の形がなければ問題にする', () => {
    const row = listRow({ status: '変更', changedBy: 'MVP-019', content: '1 円以上。' })
    const problems = listProblems(row)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /今の内容に、根拠を/)
  })

  test('common の AC の今の内容に、根拠の「<文書の名前>の「<見出し>」」の形がなければ問題にする', () => {
    const problems = listProblems(listRow({ module: 'common', content: 'ポートは 8080。' }))
    assert.equal(problems.length, 1)
    assert.match(problems[0], /今の内容に、根拠を/)
  })

  test('変更と common の AC の根拠が、リンクのない「…の「…」」なら問題にする（doc-refs が見出しを確認できない）', () => {
    const changed = listRow({ status: '変更', changedBy: 'MVP-019', content: '1 円以上。業務規則の「注文の成立」' })
    const changedProblems = listProblems(changed)
    assert.equal(changedProblems.length, 1)
    assert.match(changedProblems[0], /今の内容に、根拠を/)
    const commonProblems = listProblems(listRow({ module: 'common', content: 'ポートは 8080。根拠は構成の文書の「ポート」。' }))
    assert.equal(commonProblems.length, 1)
    assert.match(commonProblems[0], /今の内容に、根拠を/)
  })

  test('廃止の AC は、今の内容が空でよい', () => {
    const row = listRow({ module: '', status: '廃止', changedBy: 'MVP-019', content: '' })
    assert.deepEqual(listProblems(row), [])
  })
})

describe('③ の文書の名前と構造の検査の対象の一覧', () => {
  const doc = (module, required) => ({ file: `docs/03-機能仕様/${module}.md`, module, required })
  const modules = new Set(['order', 'wishlist'])

  test('モジュールの ③ が構造の検査の対象の一覧（FUNCTIONAL_SPECS）になければ、追加し忘れとして失敗にする', () => {
    const problems = checkModuleDocNames({ docs: [doc('order', true), doc('wishlist', false)], modules })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /^docs\/03-機能仕様\/wishlist\.md：モジュールの ③ が構造の検査の対象の一覧（scripts\/ci\/lib\/docs\/functional-specs\.mjs の FUNCTIONAL_SPECS）にない/)
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

describe('③ の「今有効な AC」の表の照合', () => {
  const rows = rowsOf(listRow({ ac: 'AC-1', content: '一つ目。' }), listRow({ ac: 'AC-2', content: '二つ目。' }))
  const table = [...MODULE_HEADER, moduleRow('AC-1', '一つ目。'), moduleRow('AC-2', '二つ目。')]
  const result = (text, required = true) => checkModuleDoc({ text, module: 'order', rows, required })

  test('生成した表と同じなら通る', () => {
    assert.deepEqual(result(moduleDoc(table)).problems, [])
  })

  test('「今有効な AC」の節が 2 つあると失敗にし、同じ名前の ### の見出しは節として数えない', () => {
    const twice = moduleDoc(table).replace('## 今有効な AC', '## 今有効な AC\n\n説明。\n\n## 今有効な AC')
    const { problems } = result(twice)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /「今有効な AC」の節が 2 個ある/)
    assert.deepEqual(result(moduleDoc(table, { before: ['### 今有効な AC', '', '選んだ基準は、注文を扱う AC。'] })).problems, [])
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
      assert.match(checked.problems[0], /「今有効な AC」の表が AC 一覧と違う/)
      assert.equal(checked.stale, true)
    })
  }

  test('0 件のモジュールの範囲が「なし。」の 1 行以外（空の表、空欄）なら失敗にする', () => {
    for (const inner of [[...MODULE_HEADER], [], ['なし']]) {
      const { problems } = checkModuleDoc({ text: moduleDoc(inner), module: 'cart', rows, required: true })
      assert.equal(problems.length, 1, inner.join('/'))
      assert.match(problems[0], /「今有効な AC」の表が AC 一覧と違う/, inner.join('/'))
    }
  })

  const broken = [
    ['開きのマーカーだけがある', moduleDoc(table).replace('<!-- /generated:module-ac -->', ''), /開きが 1 個、閉じが 0 個/],
    ['閉じが開きより前にある', moduleDoc(table).replace('<!-- generated:module-ac -->', '@@').replace('<!-- /generated:module-ac -->', '<!-- generated:module-ac -->').replace('@@', '<!-- /generated:module-ac -->'), /より前にある/],
    ['同じマーカーの組が 2 組ある', moduleDoc(table, { after: ['', '<!-- generated:module-ac -->', 'なし。', '<!-- /generated:module-ac -->'] }), /開きが 2 個、閉じが 2 個/],
    ['マーカーが「今有効な AC」の節の外にある', moduleDoc(table).replace('<!-- generated:module-ac -->', '').replace('<!-- /generated:module-ac -->', '').replace('なし\n', 'なし\n\n<!-- generated:module-ac -->\nなし。\n<!-- /generated:module-ac -->\n'), /節の外にあるか、節をまたぐ/],
    ['マーカーの範囲が「今有効な AC」の節をまたぐ', moduleDoc(table).replace('<!-- /generated:module-ac -->', '\n## 次の節\n\n<!-- /generated:module-ac -->'), /節の外にあるか、節をまたぐ/],
    ['マーカーがあるのに「今有効な AC」の節がない', moduleDoc(table).replace('## 今有効な AC', '## 今の AC'), /「今有効な AC」の節が 0 個ある/],
    ['既知のマーカーと一致しない generated: のコメント', moduleDoc(table, { after: ['', '<!-- generated:module-acs -->'] }), /知らない生成の印/],
    ['AC 一覧のマーカーが ③ にある', moduleDoc(table, { after: ['', '<!-- generated:ac-list -->'] }), /③ に AC 一覧の生成の印/],
    ['構造の検査の対象の一覧にある ③ に、マーカーがない', moduleDoc(table).replace('<!-- generated:module-ac -->', '').replace('<!-- /generated:module-ac -->', ''), /の組がない/],
    ['マーカーをコードブロックや 1 行のコードの中にだけ書いた', moduleDoc(table).replace('<!-- generated:module-ac -->', '`<!-- generated:module-ac -->`').replace('<!-- /generated:module-ac -->', '```\n<!-- /generated:module-ac -->\n```'), /の組がない/],
  ]
  for (const [name, text, reason] of broken) {
    test(`失敗にし、--write もしない：${name}`, () => {
      const checked = result(text)
      assert.ok(checked.problems.length > 0)
      assert.match(checked.problems[0], reason)
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
    assert.match(checked.problems[0], /にない ③ に、生成の印がある/)
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

describe('進行中の spec の AC と AC 一覧', () => {
  const IN_PROGRESS = '5'
  const spec = (markdown) => [{ rel: '5-x', ...parseSpecAcDefinitions(markdown, '5-x') }]

  test('進行中の spec の AC が AC 一覧にあり、元の spec がその spec なら通る', () => {
    const rows = rowsOf(listRow({ ac: 'AC-5-1', origin: IN_PROGRESS }))
    assert.deepEqual(checkInProgress({ rows, specs: spec('- **AC-5-1** 一つ目。') }), [])
  })

  test('進行中の spec の AC が AC 一覧になければ、草案でも確定でも失敗にする', () => {
    const problems = checkInProgress({ rows: rowsOf(listRow()), specs: spec('- **AC-5-1** 一つ目。') })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /AC-5-1 が AC 一覧にない/)
  })

  test('転記の後に spec から AC を消したのに、AC 一覧に有効のまま残れば失敗にする', () => {
    const rows = rowsOf(listRow({ ac: 'AC-5-1', origin: IN_PROGRESS }), listRow({ ac: 'AC-5-2', origin: IN_PROGRESS }))
    const problems = checkInProgress({ rows, specs: spec('- **AC-5-1** 一つ目。') })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /AC-5-2 は、元の spec（5-x）にない/)
  })

  test('進行中の spec の AC の番号が、元の spec が別の既存の AC と重なれば失敗にする', () => {
    const rows = rowsOf(listRow({ ac: 'AC-12', origin: 'MVP-003' }))
    const problems = checkInProgress({ rows, specs: spec('- **AC-12** 一つ目。') })
    assert.equal(problems.length, 1)
    assert.match(problems[0], /AC-12 の元の spec が、その AC を定義する進行中の spec/)
  })

  test('AC 一覧の元の spec がその進行中の spec かを、番号で比べる（別の番号、リンクの形、0 埋めの違いは失敗）', () => {
    for (const origin of ['6', '[5](../../08-spec/5-x/spec.md)', '05', '5-x', 'MVP-5', 'MVP-005']) {
      const rows = rowsOf(listRow({ ac: 'AC-5-1', origin }))
      const problems = checkInProgress({ rows, specs: spec('- **AC-5-1** 一つ目。') })
      assert.equal(problems.length, 1, origin)
      assert.match(problems[0], /AC-5-1 の元の spec が、その AC を定義する進行中の spec/, origin)
    }
    const dev = [{ rel: '7-dev-process', ...parseSpecAcDefinitions('- **AC-7-1** 一つ目。', '7-dev-process') }]
    assert.deepEqual(checkInProgress({ rows: rowsOf(listRow({ ac: 'AC-7-1', origin: '7' })), specs: dev }), [])
    const zeroPadded = checkInProgress({ rows: rowsOf(listRow({ ac: 'AC-7-1', origin: '007' })), specs: dev })
    assert.equal(zeroPadded.length, 1)
    assert.match(zeroPadded[0], /AC-7-1 の元の spec が、その AC を定義する進行中の spec/)
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
    const read = parseAcList(updated)
    assert.equal(checkAcRows({ rows: read.rows, modules: MODULES, specDirs: SPEC_DIRS }).length, 2)
    assert.deepEqual(checkInProgress({ rows: read.rows, specs: [{ rel: '5-x', definitions }] }), [])
  })
})

describe('テストとの対応（AC 一覧の AC に対して）', () => {
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

  test('--tests の表示：テストも除外もない廃止の AC は「なし（廃止）」とし、テストか除外があればそれを示す', () => {
    const deprecated = (ac) => listRow({ ac, module: '', status: '廃止', changedBy: 'MVP-019', content: '' })
    const rows = rowsOf(deprecated('AC-1'), deprecated('AC-2'), deprecated('AC-3'), listRow({ ac: 'AC-4' }))
    const testFiles = new Map([[2, ['backend/a/TaxApiTest.java']]])
    const { lines } = renderTestMap({ list: { rows, problems: [] }, testFiles, exclusions: { 'AC-3': '手で確認する' } })
    assert.deepEqual(lines.slice(0, 4), ['AC-1：なし（廃止）', 'AC-2：backend/a/TaxApiTest.java', 'AC-3：除外（手で確認する）', 'AC-4：なし'])
  })

  test('--tests で、AC 一覧の読み取りに失敗したら失敗にする', () => {
    const { ok } = renderTestMap({ list: parseAcList('# 印のない一覧'), testFiles: new Map(), exclusions: {} })
    assert.equal(ok, false)
  })
})
