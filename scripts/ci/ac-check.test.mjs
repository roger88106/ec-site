// ac-check.mjs のテスト（node --test "scripts/ci/*.test.mjs"）
import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { check, extractTestNames, parseAcRefs, parseSpecAcs } from './ac-check.mjs'

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

  test('Vitest・Playwright の test・it・describe の第 1 引数（単一引用符・二重引用符・バッククォート）', () => {
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
      'AC-8 カテゴリの一覧',
      'AC-9 登録できる',
      'AC-10 名前の変更',
      'AC-11 並び順を変える',
      'AC-12 %s',
    ])
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
    const result = check({ specAcs: new Set([1, 2]), tests: tests('AC-1 a'), exclusions: { 'AC-2': '手で確かめる' } })
    assert.equal(result.ok, true)
  })

  test('除外の一覧の AC にテストがあれば失敗する（除外が古い）', () => {
    const result = check({ specAcs: new Set([1]), tests: tests('AC-1 a'), exclusions: { 'AC-1': '手で確かめる' } })
    assert.equal(result.ok, false)
    assert.deepEqual(result.staleExclusions, [1])
  })

  test('除外の一覧に、spec にない AC か、理由のない項目があれば失敗する', () => {
    const result = check({ specAcs: new Set([1]), tests: tests('AC-1 a'), exclusions: { 'AC-9': '理由', 'AC-1': '' } })
    assert.equal(result.ok, false)
    assert.deepEqual(result.invalidExclusions, ['AC-9', 'AC-1'])
  })
})
