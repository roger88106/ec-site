import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { blankSqlNonCode } from './sql-code.mjs'

describe('SQL のコメントと文字列を読み飛ばす補助', () => {
  test('行コメント、入れ子のブロックコメント、文字列の中身を空白にし、長さと改行は変えない', () => {
    const sql = ["SELECT 'a.b' -- x.y", '/* p.q /* r.s */ */ FROM t.u;'].join('\n')
    const { code, unreadable } = blankSqlNonCode(sql)
    assert.equal(code.length, sql.length)
    assert.equal(code.split('\n').length, 2)
    assert.equal(code, ["SELECT '   '       ",'                    FROM t.u;'].join('\n'))
    assert.deepEqual(unreadable, [])
  })

  test('引用符付きの識別子の中は _ にし、引用符の重ね書きは文字列の続きとして読む', () => {
    const { code } = blankSqlNonCode(`SELECT "ab", 'it''s' FROM t`)
    assert.equal(code, `SELECT "__", '     ' FROM t`)
  })

  test('ドル引用の中は、文字列もコメントも含めて字面のまま残す', () => {
    const sql = "DO $$ BEGIN EXECUTE 'DROP TABLE a.b'; END $$;"
    assert.equal(blankSqlNonCode(sql).code, sql)
  })

  test('閉じていない文字列・ブロックコメント・ドル引用は、読めないものとして返す', () => {
    assert.equal(blankSqlNonCode("SELECT 'abc").unreadable.length, 1)
    assert.equal(blankSqlNonCode('/* abc').unreadable.length, 1)
    assert.equal(blankSqlNonCode('DO $$ abc').unreadable.length, 1)
  })
})
