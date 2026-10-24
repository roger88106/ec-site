import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { createLineLocator } from './line-number.mjs'

describe('位置から行番号', () => {
  const text = 'abc\ndef\n\nxyz'

  test('先頭の位置は 1 行目', () => {
    assert.equal(createLineLocator(text)(0), 1)
  })

  test('行の末尾（改行の位置）は、その行のまま', () => {
    const line = createLineLocator(text)
    assert.equal(line(2), 1)
    assert.equal(line(3), 1)
  })

  test('改行の次の位置から次の行', () => {
    const line = createLineLocator(text)
    assert.equal(line(4), 2)
    assert.equal(line(8), 3)
    assert.equal(line(9), 4)
  })

  test('最後の行と、文字列の末尾の位置', () => {
    const line = createLineLocator(text)
    assert.equal(line(11), 4)
    assert.equal(line(text.length), 4)
  })

  test('CRLF の改行も、行の数え方は LF と同じ', () => {
    const line = createLineLocator('a\r\nb\r\nc')
    assert.equal(line(0), 1)
    assert.equal(line(1), 1)
    assert.equal(line(2), 1)
    assert.equal(line(3), 2)
    assert.equal(line(6), 3)
  })

  test('改行がなければ、どの位置も 1 行目', () => {
    assert.equal(createLineLocator('abc')(3), 1)
  })

  test('slice と split で数える方法と、全ての位置で一致する', () => {
    const mixed = 'a\n\nbc\r\nd\n'
    const line = createLineLocator(mixed)
    for (let index = 0; index <= mixed.length; index += 1) {
      assert.equal(line(index), mixed.slice(0, index).split('\n').length, `位置 ${index}`)
    }
  })
})
