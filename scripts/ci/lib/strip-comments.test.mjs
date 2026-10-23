import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { stripComments, stripJavaComments } from './strip-comments.mjs'

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

describe('正規表現のリテラルの扱い', () => {
  test('正規表現の中の ) " \' // を、括弧・文字列・コメントと読まない', () => {
    for (const regex of ['/[)"\']/', '/\\/\\//', '/\\)/', '/"/', "/'/"]) {
      const text = `const a = ${regex}.test(x) // 後ろ\nconst b = 1`
      const result = stripComments(text, { keepRegex: true })
      assert.equal(result.includes('後ろ'), false, regex)
      assert.equal(result.includes('.test(x)'), true, regex)
      assert.equal(result.includes('const b = 1'), true, regex)
    }
  })

  test('正規表現の中の引用符の後ろにあるコメントも消える', () => {
    const result = stripComments('const a = /"/ // コメント "', { keepRegex: true })
    assert.equal(result.includes('コメント'), false)
  })

  test('割り算の / は正規表現と読まず、後ろのコメントを消す', () => {
    const text = 'const a = b / c / d // 後ろ'
    assert.equal(stripComments(text), 'const a = b / c / d      ')
    assert.equal(stripComments('const a = (b) / 2 / (c) // 後ろ').includes('後ろ'), false)
  })
})

describe('Java のコメントの除去', () => {
  test('Java のコメントを同じ長さの空白に置き換え、行番号を保つ', () => {
    const text = ['a /* x', 'y */ b // z', '"//w" \'"\' c'].join('\n')
    const blanked = stripJavaComments(text)
    assert.equal(blanked.length, text.length)
    assert.equal(blanked.split('\n').length, 3)
    assert.equal(blanked, ['a     ', '     b     ', '"//w" \'"\' c'].join('\n'))
  })
})
