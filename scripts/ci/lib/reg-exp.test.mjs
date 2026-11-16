import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { escapeRegExp } from './reg-exp.mjs'

describe('正規表現の特別な文字を逃がす', () => {
  test('特別な文字を全て逃がし、その文字のままに一致する', () => {
    const text = 'a.b*c+d?e^f$g{h}i(j)k|l[m]n\\o'
    assert.ok(new RegExp(`^${escapeRegExp(text)}$`).test(text))
    assert.equal(new RegExp(`^${escapeRegExp('a.b')}$`).test('axb'), false)
  })

  test('特別な文字のない文字列は変えない', () => {
    assert.equal(escapeRegExp('cartItems'), 'cartItems')
  })
})
