import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findChangedMigrations, isVersionedMigration } from './migrations.mjs'

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
})
