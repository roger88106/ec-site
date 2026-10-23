import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findChangedMigrations, findRemovedMigrations, isVersionedMigration, parseAddedLog } from './migrations.mjs'

describe('適用済みのマイグレーションの検査', () => {
  test('バージョン付きのマイグレーション（V*.sql）だけを見る。繰り返しのもの（R__*.sql）は見ない', () => {
    assert.equal(isVersionedMigration('backend/ecsite/src/main/resources/db/migration/V202610140910__payment.sql'), true)
    assert.equal(isVersionedMigration('backend/ecsite/src/main/resources/db/seed/local/R__seed.sql'), false)
    assert.equal(isVersionedMigration('backend/ecsite/src/main/resources/db/migration/README.md'), false)
  })

  test('追加した時と今で中身が違うマイグレーションを見つける（コメントだけの変更も含む）', () => {
    const added = { 'V1__a.sql': 'create table a;\n', 'V2__b.sql': '-- 説明\ncreate table b;\n' }
    const now = { 'V1__a.sql': 'create table a;\n', 'V2__b.sql': '-- 説明を変えた\ncreate table b;\n' }
    assert.deepEqual(
      findChangedMigrations(['V1__a.sql', 'V2__b.sql'], (file) => added[file], (file) => now[file]),
      ['V2__b.sql'],
    )
  })

  test('まだ commit していないマイグレーション（追加した commit がない）は見ない', () => {
    assert.deepEqual(
      findChangedMigrations(['V1__a.sql', 'V2__new.sql'], (file) => (file === 'V1__a.sql' ? 'create table a;\n' : null), () => 'create table a;\n'),
      [],
    )
  })

  test('改行の違い（CRLF と LF）は変更とみなさない', () => {
    assert.deepEqual(
      findChangedMigrations(['V1__a.sql'], () => 'create table a;\n', () => 'create table a;\r\n'),
      [],
    )
  })

  test('追加した後に今はないマイグレーション（削除）を見つける', () => {
    const added = ['V1__a.sql', 'V2__b.sql', 'V3__c.sql']
    assert.deepEqual(findRemovedMigrations(added, ['V1__a.sql', 'V3__c.sql', 'V4__new.sql']), ['V2__b.sql'])
  })

  test('改名は、古い名前が今ないので見つかる', () => {
    assert.deepEqual(findRemovedMigrations(['V1__a.sql'], ['V1__renamed.sql']), ['V1__a.sql'])
  })

  test('削除も改名もなければ、何も見つけない', () => {
    assert.deepEqual(findRemovedMigrations(['V1__a.sql'], ['V1__a.sql']), [])
  })

  test('git log の出力から、ファイルごとの最初に追加した commit を読む（新しい順の出力で、最後に出たものが最初の追加）', () => {
    const c1 = 'a'.repeat(40)
    const c2 = 'b'.repeat(40)
    const log = [c2, 'V2__b.sql', 'V1__a.sql', '', c1, 'V1__a.sql', ''].join('\n')
    const added = parseAddedLog(log)
    assert.equal(added.get('V1__a.sql'), c1)
    assert.equal(added.get('V2__b.sql'), c2)
    assert.equal(added.size, 2)
  })
})
