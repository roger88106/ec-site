// lib/ac/test-names.mjs のテスト（node --test scripts/ci/lib/ac/test-names.test.mjs）
import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { scanTsTests } from '../source/test-files.mjs'
import { describeUnreadableTests, findAmbiguousAcLists, findSpacedAcLists, parseAcRefs } from './test-names.mjs'

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

  test('テスト名の AC-7-1 を AC-7 と読まない。MVP の AC-12 を新しい形の一部と読まない', () => {
    assert.deepEqual(parseAcRefs('AC-7-1 注文できる'), { acs: ['7-1'], ranges: [] })
    assert.deepEqual(parseAcRefs('AC-12 注文できる'), { acs: [12], ranges: [] })
    assert.deepEqual(parseAcRefs('AC-7 と AC-7-1 の両方').acs, [7, '7-1'])
  })

  test('読点・カンマで、2 つの形を並べられる', () => {
    assert.deepEqual(parseAcRefs('AC-7-1、AC-7-2, AC-12 並べる'), { acs: ['7-1', '7-2', 12], ranges: [] })
  })

  test('新しい形の範囲（AC-7-1〜3、AC-7-1〜7-3）は、範囲として返す', () => {
    assert.deepEqual(parseAcRefs('AC-7-1〜3 注文'), { acs: ['7-1', '7-3'], ranges: ['7-1〜3'] })
    assert.deepEqual(parseAcRefs('AC-7-1~7-3 注文'), { acs: ['7-1', '7-3'], ranges: ['7-1~7-3'] })
  })

  test('新しい形の AC 番号の後の数字（本文の数量など）は AC にしない', () => {
    assert.deepEqual(parseAcRefs('AC-7-1 数量を 10 にする').acs, ['7-1'])
  })
})

describe('テスト名の AC の並べ方の検出', () => {
  test('新しい形の後に続く読点と数字だけの番号（AC-7-1、2）は、曖昧な書き方として見つける', () => {
    assert.deepEqual(findAmbiguousAcLists('AC-7-1、2 注文'), ['AC-7-1、2'])
    assert.deepEqual(findAmbiguousAcLists('AC-7-1、2、3 注文'), ['AC-7-1、2、3'])
    assert.deepEqual(findAmbiguousAcLists('AC-7-1、AC-7-2, 12 並べる'), ['AC-7-1、AC-7-2, 12'])
  })

  test('曖昧でない書き方（AC-7-1、AC-7-2 や AC-1、2）は見つけない', () => {
    assert.deepEqual(findAmbiguousAcLists('AC-7-1、AC-7-2 注文'), [])
    assert.deepEqual(findAmbiguousAcLists('AC-7-1、7-2 注文'), [])
    assert.deepEqual(findAmbiguousAcLists('AC-1、2 注文'), [])
    assert.deepEqual(findAmbiguousAcLists('AC-7-1 数量を 10 にする'), [])
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
})

describe('読めないテストの呼び出し', () => {
  test('ファイル:行と理由で返し、each の表の中の関数の呼び出しは読めるので返さない', () => {
    const source = ["test.each([[f(1)]])('AC-1 読める', () => {})", '', 'test(name, () => {})'].join('\n')
    assert.deepEqual(describeUnreadableTests(source, 'frontend/a.test.ts', scanTsTests(source).unreadable), [
      'frontend/a.test.ts:3 test(name, () => {})（テスト名が文字列でもテンプレートでもない（または閉じていない））',
    ])
  })
})
