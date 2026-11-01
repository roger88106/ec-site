import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { readCodeSpans, replaceCodeSpans } from './code-span.mjs'

describe('インラインのコードスパンの読み方', () => {
  test('1 つのバッククォートで囲んだ中身と位置を返す', () => {
    assert.deepEqual(readCodeSpans('a `b` c'), { spans: [{ start: 2, end: 5, content: 'b' }], unmatched: false })
  })

  test('開きと同じ数のバッククォートの並びで閉じ、中の短い並びでは閉じない', () => {
    const { spans, unmatched } = readCodeSpans('``x`y`` と `z`')
    assert.deepEqual(spans.map((span) => span.content), ['x`y', 'z'])
    assert.equal(unmatched, false)
  })

  test('開きより長い並びでは閉じない', () => {
    const { spans } = readCodeSpans('`a``b` c')
    assert.deepEqual(spans.map((span) => span.content), ['a``b'])
  })

  test('組にならないバッククォートが残れば、unmatched を true にする', () => {
    assert.equal(readCodeSpans('a ``b` c').unmatched, true)
    assert.equal(readCodeSpans('a ` b').unmatched, true)
  })

  test('組にならない並びの後のコードスパンも読む', () => {
    const { spans, unmatched } = readCodeSpans('`` a `b`')
    assert.deepEqual(spans.map((span) => span.content), ['b'])
    assert.equal(unmatched, true)
  })

  test('バッククォートのない行は、コードスパンがない', () => {
    assert.deepEqual(readCodeSpans('abc'), { spans: [], unmatched: false })
  })

  test('コードスパンを、中身を受け取る関数の戻り値に替える', () => {
    assert.equal(replaceCodeSpans('a ``x`y`` b `z` c', (content) => `[${content}]`), 'a [x`y] b [z] c')
  })
})
