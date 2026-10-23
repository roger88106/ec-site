import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { closingParen } from './closing-paren.mjs'

describe('閉じ括弧の位置', () => {
  test('入れ子の括弧に対応する閉じ括弧を返す', () => {
    const text = 'f(a(b), c) + g()'
    assert.equal(closingParen(text, 1), 9)
  })

  test('文字列の中の括弧は数えない', () => {
    const text = "f('a)', \"b(\", `c)`)"
    assert.equal(closingParen(text, 1), text.length - 1)
  })

  test('行コメントの中の引用符と括弧は数えない', () => {
    const text = ['f(', "  // it's skipped (really", '  a,', ')', "g('x')"].join('\n')
    assert.equal(closingParen(text, 1), text.indexOf('\n)') + 1)
  })

  test('ブロックコメントの中の引用符と括弧は数えない', () => {
    const text = "f(/* don't ( */ a)"
    assert.equal(closingParen(text, 1), text.length - 1)
  })

  test('文字列の中の // はコメントでない', () => {
    const text = "f('http://x', a)"
    assert.equal(closingParen(text, 1), text.length - 1)
  })

  test('正規表現のリテラルの中の括弧・引用符・// は数えない', () => {
    for (const regex of ['/[)"\']/', '/\\/\\//', '/\\)/', '/(/', '/"/']) {
      const text = `f(${regex}.test(x), a)`
      assert.equal(closingParen(text, 1), text.length - 1, regex)
    }
  })

  test('引数の先頭・, の後・= の後の正規表現も読み飛ばす', () => {
    const text = 'f(a, /\\)/g, b = /"/)'
    assert.equal(closingParen(text, 1), text.length - 1)
  })

  test('割り算は正規表現の始まりと読まない', () => {
    const text = "f(a / b / c, ')')"
    assert.equal(closingParen(text, 1), text.length - 1)
    const text2 = 'f((a) / 2 / (b))'
    assert.equal(closingParen(text2, 1), text2.length - 1)
  })

  test('閉じなければソースの終わりを返す', () => {
    assert.equal(closingParen('f(a', 1), 3)
  })
})
