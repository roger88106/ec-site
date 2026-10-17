import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findViolations, parseRenames } from './test-diff.mjs'

/** 1 つのファイルの差分（git diff -M の形）を作る */
function fileDiff({ from, to = from, removed = [], added = [], newFile = false }) {
  const header = [`diff --git a/${from} b/${to}`]
  if (newFile) {
    header.push('new file mode 100644', '--- /dev/null')
  } else {
    if (from !== to) header.push('similarity index 90%', `rename from ${from}`, `rename to ${to}`)
    header.push(`--- a/${from}`)
  }
  header.push(`+++ b/${to}`, '@@ -1,2 +1,2 @@')
  return [...header, ...removed.map((l) => `-${l}`), ...added.map((l) => `+${l}`)].join('\n')
}

const TEST = 'backend/ecsite/src/integrationTest/java/com/ecsite/FooTest.java'

describe('名前の対応の読み込み', () => {
  test('旧=新 の行を読み、空行と # の行を飛ばす', () => {
    const text = ['# 説明', '', 'ProductDirectory=CatalogDirectory', ' com.a.Foo = com.b.Foo '].join('\n')
    assert.deepEqual(parseRenames(text), [
      ['ProductDirectory', 'CatalogDirectory'],
      ['com.a.Foo', 'com.b.Foo'],
    ])
  })
})

describe('テストの差分の検査', () => {
  test('import・package の行だけの変更は通る', () => {
    const diff = fileDiff({
      from: TEST,
      removed: ['package com.a;', 'import com.a.Foo;'],
      added: ['package com.b;', 'import com.b.Foo;'],
    })
    assert.deepEqual(findViolations(diff, []), [])
  })

  test('名前の対応を当てると一致する行の変更は通る', () => {
    const diff = fileDiff({
      from: TEST,
      removed: ['    ProductDirectory directory;'],
      added: ['    CatalogDirectory directory;'],
    })
    assert.deepEqual(findViolations(diff, [['ProductDirectory', 'CatalogDirectory']]), [])
  })

  test('名前は語の境目で当て、長い名前の一部を置き換えない', () => {
    const diff = fileDiff({
      from: TEST,
      removed: ['    ProductDirectoryTest test;'],
      added: ['    CatalogDirectoryTest test;'],
    })
    assert.equal(findViolations(diff, [['ProductDirectory', 'CatalogDirectory']]).length, 2)
  })

  test('ファイルの移動で、中身の変更が名前の対応だけなら通る', () => {
    const diff = fileDiff({
      from: 'backend/ecsite/src/test/java/com/a/OldTest.java',
      to: 'backend/ecsite/src/test/java/com/b/NewTest.java',
      removed: ['class OldTest {'],
      added: ['class NewTest {'],
    })
    assert.deepEqual(findViolations(diff, [['OldTest', 'NewTest']]), [])
  })

  test('期待値の変更は失敗し、変えた行を出す', () => {
    const diff = fileDiff({
      from: TEST,
      removed: ['        assertThat(total).isEqualTo(100);'],
      added: ['        assertThat(total).isEqualTo(200);'],
    })
    assert.deepEqual(findViolations(diff, []), [
      { file: TEST, kind: 'removed', line: '        assertThat(total).isEqualTo(100);' },
      { file: TEST, kind: 'added', line: '        assertThat(total).isEqualTo(200);' },
    ])
  })

  test('行を足すだけ・消すだけの変更も失敗する', () => {
    const diff = fileDiff({ from: TEST, added: ['        verify(mock).call();'] })
    assert.deepEqual(findViolations(diff, []), [
      { file: TEST, kind: 'added', line: '        verify(mock).call();' },
    ])
  })

  test('新しく足したテストのファイルは見ない', () => {
    const diff = fileDiff({ from: TEST, newFile: true, added: ['class FooTest {', '}'] })
    assert.deepEqual(findViolations(diff, []), [])
  })

  test('テストでないファイルの変更は見ない', () => {
    const diff = fileDiff({
      from: 'backend/ecsite/src/main/java/com/ecsite/Foo.java',
      removed: ['    return 1;'],
      added: ['    return 2;'],
    })
    assert.deepEqual(findViolations(diff, []), [])
  })

  test('E2E と画面のテストのファイルも見る', () => {
    const e2e = fileDiff({ from: 'e2e/tests/cart.spec.ts', added: ['  await page.reload()'] })
    const store = fileDiff({ from: 'frontend/store/src/lib/price.test.ts', added: ['  expect(1).toBe(1)'] })
    assert.equal(findViolations(`${e2e}\n${store}`, []).length, 2)
  })
})
