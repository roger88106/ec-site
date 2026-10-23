import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { stripComments } from './strip-comments.mjs'

describe('コメントの除去', () => {
  test('行コメントとブロックコメントを空白にし、行の数を変えない', () => {
    const text = '/*\n * 説明\n */\nconst a = 1 // 後ろ'
    const result = stripComments(text)
    assert.equal(result.split('\n').length, 4)
    assert.equal(result.includes('説明'), false)
    assert.equal(result.includes('後ろ'), false)
    assert.equal(result.includes('const a = 1'), true)
  })

  test('文字列の中の // はコメントにしない', () => {
    assert.equal(stripComments("const url = 'https://example.com' // 外"), "const url = 'https://example.com'     ")
  })

  test('正規表現のリテラルは、既定では空白にし、keepRegex を指定すると残す', () => {
    const text = 'expect(html).toMatch(/<p class="x">/) // 注'
    assert.equal(stripComments(text).includes('class'), false)
    assert.equal(stripComments(text, { keepRegex: true }).includes('<p class="x">'), true)
    assert.equal(stripComments(text, { keepRegex: true }).includes('注'), false)
  })

  test('keepRegex でも、正規表現の中の // をコメントにしない', () => {
    const text = 'const a = /\\/\\//.test(x) // 後ろ'
    assert.equal(stripComments(text, { keepRegex: true }).includes('後ろ'), false)
    assert.equal(stripComments(text, { keepRegex: true }).includes('.test(x)'), true)
  })
})
