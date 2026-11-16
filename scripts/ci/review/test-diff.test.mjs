import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { findRenameProblems, findViolations, parseRenames } from './test-diff.mjs'

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

  test('= のない行、= が 2 つ以上の行、旧か新が空の行を、行の番号つきで返す', () => {
    const text = ['# 説明', 'A=B', 'NoEquals', 'A=B=C', '=New', 'Old=', ''].join('\n')
    assert.deepEqual(findRenameProblems(text).map(({ line }) => line), [3, 4, 5, 6])
    assert.deepEqual(findRenameProblems(''), [])
  })
})

describe('入口の使い方の誤り', () => {
  const SCRIPT = fileURLToPath(new URL('./test-diff.mjs', import.meta.url))
  const run = (args) => spawnSync(process.execPath, [SCRIPT, ...args], { encoding: 'utf8' })

  test('元の commit がない、対応のファイルがない、対応のファイルの形が崩れているときは、終了コード 2 で理由を示す', () => {
    const dir = mkdtempSync(path.join(tmpdir(), 'test-diff-'))
    try {
      const broken = path.join(dir, 'renames.txt')
      writeFileSync(broken, 'A=B\nNoEquals\n')
      assert.equal(run([]).status, 2)
      const missing = run(['HEAD', path.join(dir, 'none.txt')])
      assert.equal(missing.status, 2)
      assert.match(missing.stderr, /名前の対応のファイルがない/)
      const shape = run(['HEAD', broken])
      assert.equal(shape.status, 2)
      assert.match(shape.stderr, /renames\.txt:2 「旧=新」の形でない：NoEquals/)
      const commit = run(['no-such-commit-xyz'])
      assert.equal(commit.status, 2)
      assert.match(commit.stderr, /git diff を実行できない/)
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
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
      { file: TEST, kind: 'removed', line: '        assertThat(total).isEqualTo(100);', location: `${TEST}:1` },
      { file: TEST, kind: 'added', line: '        assertThat(total).isEqualTo(200);', location: `${TEST}:1` },
    ])
  })

  test('行を追加するだけ・消すだけの変更も失敗する', () => {
    const diff = fileDiff({ from: TEST, added: ['        verify(mock).call();'] })
    assert.deepEqual(findViolations(diff, []), [
      { file: TEST, kind: 'added', line: '        verify(mock).call();', location: `${TEST}:1` },
    ])
  })

  test('説明できない行の場所は、@@ の行と文脈の行から数え、消した行は変更の前のファイルの行にする', () => {
    const diff = [
      `diff --git a/${TEST} b/${TEST}`,
      `--- a/${TEST}`,
      `+++ b/${TEST}`,
      '@@ -10,4 +20,4 @@',
      ' class FooTest {',
      '-    int a = 1;',
      '+    int a = 2;',
      '     int b = 3;',
      '+    int c = 4;',
    ].join('\n')
    assert.deepEqual(
      findViolations(diff, []).map(({ kind, location }) => [kind, location]),
      [
        ['removed', `${TEST}:11`],
        ['added', `${TEST}:21`],
        ['added', `${TEST}:23`],
      ],
    )
  })

  test('新しく追加したテストのファイルは見ない', () => {
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

describe('ファイルをまたぐテストの移動（中身を変えない移動は通し、中身を変えた移動は違反にする）', () => {
  const FROM = 'frontend/store/src/lib/orderDisplay.test.ts'
  const TO = 'frontend/store/src/lib/labels.test.ts'
  const MOVED = ["  test('注文の状態の表示名', () => {", "    expect(orderStatusLabel('ACCEPTED')).toBe('受付')", '  })']

  test('中身を変えずに既存のファイルへ移したテストは通る', () => {
    const diff = [fileDiff({ from: FROM, removed: MOVED }), fileDiff({ from: TO, added: MOVED })].join('\n')
    assert.deepEqual(findViolations(diff, []), [])
  })

  test('中身を変えずに新しいファイルへ移したテストは通る', () => {
    const diff = [
      fileDiff({ from: FROM, removed: MOVED }),
      fileDiff({ from: TO, newFile: true, added: ["describe('表示名', () => {", ...MOVED, '})'] }),
    ].join('\n')
    assert.deepEqual(findViolations(diff, []), [])
  })

  test('移した行を変えると、元の消した行を出して失敗する', () => {
    const changed = [MOVED[0], "    expect(orderStatusLabel('ACCEPTED')).toBe('受付済み')", MOVED[2]]
    const diff = [
      fileDiff({ from: FROM, removed: MOVED }),
      fileDiff({ from: TO, newFile: true, added: changed }),
    ].join('\n')
    assert.deepEqual(findViolations(diff, []), [{ file: FROM, kind: 'removed', line: MOVED[1], location: `${FROM}:2` }])
  })

  test('字下げだけが変わった行は通る', () => {
    const diff = [
      fileDiff({ from: FROM, removed: MOVED }),
      fileDiff({ from: TO, added: MOVED.map((line) => `  ${line}`) }),
    ].join('\n')
    assert.deepEqual(findViolations(diff, []), [])
  })

  test('複数行の import の中の行の変更は通る', () => {
    const diff = fileDiff({
      from: FROM,
      removed: ['import {', '  formatYen,', "} from '@/lib/orderDisplay'"],
      added: ['import {', '  formatYen,', '  taxLabel,', "} from '@ec-site/shared/lib/format'"],
    })
    assert.deepEqual(findViolations(diff, []), [])
  })

  test('消したファイルの行が、どこにも移っていなければ失敗する', () => {
    const diff = `diff --git a/${FROM} b/${FROM}\ndeleted file mode 100644\n--- a/${FROM}\n+++ /dev/null\n@@ -1,3 +0,0 @@\n${MOVED.map((l) => `-${l}`).join('\n')}`
    assert.equal(findViolations(diff, []).length, 3)
  })

  test('新しく追加したファイルの、照合して残った行は出さない', () => {
    const diff = [
      fileDiff({ from: FROM, removed: MOVED }),
      fileDiff({ from: TO, newFile: true, added: [...MOVED, "  test('新しいテスト', () => {})"] }),
    ].join('\n')
    assert.deepEqual(findViolations(diff, []), [])
  })
})
