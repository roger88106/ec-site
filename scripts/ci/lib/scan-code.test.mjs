import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { scanCode, templateEnd } from './scan-code.mjs'

/** 走査の結果を、種類ごとに連結した文字列にする（code は 1 字ずつ続くので 1 つにまとめる） */
function segments(text, options) {
  const result = []
  scanCode(text, options, (type, from, to) => {
    const last = result.at(-1)
    if (type === 'code' && last?.type === 'code') last.text += text.slice(from, to)
    else result.push({ type, text: text.slice(from, to) })
  })
  return result
}

describe('コード・コメント・文字列・正規表現の走査', () => {
  test('行コメントとブロックコメントを、コメントの区間にする', () => {
    assert.deepEqual(segments('a // x\nb /* y */ c'), [
      { type: 'code', text: 'a ' },
      { type: 'comment', text: '// x' },
      { type: 'code', text: '\nb ' },
      { type: 'comment', text: '/* y */' },
      { type: 'code', text: ' c' },
    ])
  })

  test('文字列の中の // と括弧は、文字列の区間にする', () => {
    assert.deepEqual(segments("f('http://x', \"(\")"), [
      { type: 'code', text: 'f(' },
      { type: 'string', text: "'http://x'" },
      { type: 'code', text: ', ' },
      { type: 'string', text: '"("' },
      { type: 'code', text: ')' },
    ])
  })

  test("単独の ' は、改行で文字列を終える", () => {
    const result = segments("<p>it's\n)")
    assert.equal(result.at(-1).type, 'code')
    assert.equal(result.at(-1).text, ')')
  })

  test('正規表現のリテラルを、正規表現の区間にする。regex: false なら割り算と読む', () => {
    assert.deepEqual(segments('x = /[/)]/g.test(a)'), [
      { type: 'code', text: 'x = ' },
      { type: 'regex', text: '/[/)]/g' },
      { type: 'code', text: '.test(a)' },
    ])
    assert.deepEqual(segments('x = a / b / c', { regex: false }), [{ type: 'code', text: 'x = a / b / c' }])
    assert.equal(segments('x = (/a/)', { regex: false }).some((s) => s.type === 'regex'), false)
  })

  test('割り算の / と JSX の />は、正規表現の始まりと読まない', () => {
    assert.equal(segments('a / b / c').some((s) => s.type === 'regex'), false)
    assert.equal(segments('x = <br />').some((s) => s.type === 'regex'), false)
  })

  test('正規表現かの判定は、コメントを除いた直前のコードで決める（コメントの最後の字で決めない）', () => {
    // コメントの後の行頭の / は、前のコードで決める。コメントの最後の字が ( や , でも割り算
    const division = segments('const a = b // 割る(\n/ 2 + c')
    assert.equal(division.some((s) => s.type === 'regex'), false)
    // コメントの最後の字が割り算の字でも、前のコードが = なら正規表現
    const regexAfterLineComment = segments('x = // 説明\n/https?:\\/\\//,')
    assert.deepEqual(regexAfterLineComment.filter((s) => s.type === 'regex').map((s) => s.text), ['/https?:\\/\\//'])
    const regexAfterBlockComment = segments('x = /* c */ /a\\/\\/b/')
    assert.deepEqual(regexAfterBlockComment.filter((s) => s.type === 'regex').map((s) => s.text), ['/a\\/\\/b/'])
    const divisionAfterBlockComment = segments('x = a /* ( */ / 2')
    assert.equal(divisionAfterBlockComment.some((s) => s.type === 'regex'), false)
  })

  test('複数行のテンプレートの文字の部分の // と /* は、コメントと読まない', () => {
    const text = 'const a = `\n// 行\n/* 塊 */\n${b}\n// 続き\n`'
    assert.equal(segments(text).some((s) => s.type === 'comment'), false)
  })

  test('テンプレートの ${...} の中はコードとして読み、入れ子のテンプレートも読む', () => {
    const text = 'f(`a${g(`b${1}`) // x\n}c`)'
    assert.deepEqual(segments(text), [
      { type: 'code', text: 'f(' },
      { type: 'string', text: '`a${' },
      { type: 'code', text: 'g(' },
      { type: 'string', text: '`b${' },
      { type: 'code', text: '1' },
      { type: 'string', text: '}`' },
      { type: 'code', text: ') ' },
      { type: 'comment', text: '// x' },
      { type: 'code', text: '\n' },
      { type: 'string', text: '}c`' },
      { type: 'code', text: ')' },
    ])
  })

  test('templates: false なら、バッククォートを普通の字と読む（Java）', () => {
    assert.deepEqual(segments('a ` b', { templates: false }), [{ type: 'code', text: 'a ` b' }])
  })

  test('textBlocks: true なら、テキストブロックを複数行の文字列にする（Java）', () => {
    const text = 'a = """\n  // x "q"\n  """; // y'
    assert.deepEqual(segments(text, { regex: false, templates: false, textBlocks: true }), [
      { type: 'code', text: 'a = ' },
      { type: 'string', text: '"""\n  // x "q"\n  """' },
      { type: 'code', text: '; ' },
      { type: 'comment', text: '// y' },
    ])
  })

  test('start から読み始め、正規表現の判定は start より前を見ない', () => {
    assert.deepEqual(segments('ab(/x/)', { start: 2 }), [
      { type: 'code', text: '(' },
      { type: 'regex', text: '/x/' },
      { type: 'code', text: ')' },
    ])
  })

  test('訪問の関数が true を返したら止まり、その区間の始まりの位置を返す。止まらなければソースの終わりを返す', () => {
    const text = 'a /* ) */ )'
    assert.equal(
      scanCode(text, {}, (type, from) => type === 'code' && text[from] === ')'),
      text.length - 1,
    )
    assert.equal(scanCode(text, {}, () => false), text.length)
  })

  test('閉じないコメントと文字列は、ソースの終わりまで', () => {
    assert.deepEqual(segments('a /* b'), [
      { type: 'code', text: 'a ' },
      { type: 'comment', text: '/* b' },
    ])
    assert.deepEqual(segments('a `b${'), [
      { type: 'code', text: 'a ' },
      { type: 'string', text: '`b${' },
    ])
  })
})

describe('テンプレートの終わり', () => {
  test('入れ子のテンプレートと ${...} の中の式を読み、終わりの次の位置を返す', () => {
    const text = 'x(`a${`b${`c`}`}${"`"}d`) + `e`'
    assert.equal(templateEnd(text, 2), text.indexOf(') + '))
  })

  test('空のテンプレートと、エスケープしたバッククォートを含むテンプレート', () => {
    assert.equal(templateEnd('``x', 0), 2)
    assert.equal(templateEnd('`a\\`b`x', 0), 6)
  })

  test('閉じなければソースの終わりを返す', () => {
    assert.equal(templateEnd('`abc', 0), 4)
  })
})
