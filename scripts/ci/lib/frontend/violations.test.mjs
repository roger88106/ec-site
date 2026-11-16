import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findViolations } from './violations.mjs'

/** ファイルの一覧と、中身を読む関数を作る */
function sources(contents) {
  return { files: Object.keys(contents), read: (file) => contents[file] }
}

describe('共有と同じ名前の定義', () => {
  test('画面の本番のソースの定義を、除外のキー（<画面>:<名前>）を添えて出す', () => {
    const { files, read } = sources({ 'frontend/store/src/lib/t.ts': 'export const translate = 1' })
    const messages = findViolations(files, read, new Set(['translate']), {})
    assert.equal(messages.length, 1)
    assert.match(messages[0], /^frontend\/store\/src\/lib\/t\.ts:1 .*translate.*キー store:translate/)
  })

  test('共有のパッケージ自身と、本番のソースでないもの（テストの補助など）は見ない', () => {
    const { files, read } = sources({
      'frontend/shared/src/lib/t.ts': 'export const translate = 1',
      'frontend/store/src/test/t.ts': 'export const translate = 1',
    })
    assert.deepEqual(findViolations(files, read, new Set(['translate']), {}), [])
  })

  test('除外の一覧にキーがあれば出さず、定義がなくなった除外は不要として出す', () => {
    const { files, read } = sources({ 'frontend/admin/src/lib/t.ts': 'export const translate = 1' })
    const exclusions = { 'admin:translate': '理由', 'store:translate': '理由' }
    assert.deepEqual(findViolations(files, read, new Set(['translate']), exclusions), ['frontend-rules-exclusions.json：store:translate は不要になった除外（一覧から消す）'])
  })
})

describe('画面のテストの並びのインデックス', () => {
  test('画面のテストだけを読む', () => {
    const line = "const first = screen.getAllByRole('row')[0]"
    const { files, read } = sources({ 'frontend/store/src/a.test.tsx': line, 'frontend/store/src/a.tsx': line })
    const messages = findViolations(files, read, new Set(), {})
    assert.equal(messages.length, 1)
    assert.match(messages[0], /^frontend\/store\/src\/a\.test\.tsx:1 /)
  })
})

describe('店舗画面と管理画面の同じ内容のファイル', () => {
  test('同じ内容のファイルは、除外になければ出す', () => {
    const { files, read } = sources({
      'frontend/store/src/lib/a.ts': 'same',
      'frontend/admin/src/lib/a.ts': 'same',
      'frontend/store/src/lib/b.ts': 'same',
      'frontend/admin/src/lib/b.ts': 'same',
    })
    assert.deepEqual(findViolations(files, read, new Set(), { 'lib/a.ts': '理由' }), ['frontend/{store,admin}/src/lib/b.ts 店舗画面と管理画面に同じ内容のファイルがある（共有パッケージに置く）'])
  })

  test('内容が変わったファイルの除外は、不要として出す', () => {
    const { files, read } = sources({ 'frontend/store/src/lib/a.ts': 'store', 'frontend/admin/src/lib/a.ts': 'admin' })
    assert.deepEqual(findViolations(files, read, new Set(), { 'lib/a.ts': '理由' }), ['frontend-rules-exclusions.json：lib/a.ts は不要になった除外（一覧から消す）'])
  })

  test('理由のない除外は、必要な除外でも出す', () => {
    const { files, read } = sources({ 'frontend/store/src/lib/a.ts': 'same', 'frontend/admin/src/lib/a.ts': 'same' })
    assert.deepEqual(findViolations(files, read, new Set(), { 'lib/a.ts': '' }), ['frontend-rules-exclusions.json：lib/a.ts の理由がない（除外には理由を書く）'])
  })
})
