import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { keepJavaComments, keepTsComments, stripComments, stripJavaComments } from './strip-comments.mjs'

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

  test('テンプレートの ${...} の中はコードとして読み、入れ子のテンプレートの中の // をコメントにしない', () => {
    assert.equal(stripComments('const a = `x${`//y`}` // c\nz'), 'const a = `x${`//y`}`     \nz')
    const text = 'const a = `${b /* 注 */}` + `${{ c: `}` }.c}` // 後ろ'
    assert.equal(stripComments(text), text.replace('/* 注 */', ' '.repeat(7)).replace('// 後ろ', ' '.repeat(5)))
  })

  test('コメントの後の行頭の / は、コメントの最後の字でなく、前のコードで正規表現か割り算かを決める', () => {
    // 割り算：コメントが ( で終わっても、後ろの行を正規表現と読んで消さない
    const division = "const a = b // 割る(\n/ 2 + c('日本語')"
    assert.equal(stripComments(division), "const a = b       \n/ 2 + c('日本語')")
    // 正規表現：中の // をコメントと読まず、後ろの文字列を残す
    assert.equal(stripComments('x = // 説明\n/https?:\\/\\//.test(a) // 後ろ', { keepRegex: true }).includes('https?:\\/\\//.test(a)'), true)
    assert.equal(stripComments('x = /* c */ /a\\/\\/b/.test(a) // 後ろ', { keepRegex: true }).includes('.test(a)'), true)
    assert.equal(stripComments('x = /* c */ /a\\/\\/b/.test(a) // 後ろ', { keepRegex: true }).includes('後ろ'), false)
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

  test('改行を挟んだ割り算（行頭の /）は、直前の意味のある字で割り算と読む', () => {
    assert.equal(stripComments('const a = b\n  / c // 後ろ'), 'const a = b\n  / c      ')
    assert.equal(stripComments('const a = b\n// 間\n  / c // 後ろ'), 'const a = b\n    \n  / c      ')
  })

  test('ファイルの始まりと、改行の後の } の後の / は正規表現と読む', () => {
    assert.equal(stripComments("/'/.test(a) // 後ろ").includes('後ろ'), false)
    assert.equal(stripComments("if (a) {\n}\n/'/.test(b) // 後ろ").includes('後ろ'), false)
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

describe('Java のコメントだけを残す', () => {
  test('Javadoc、ブロックのコメント、行のコメントを残し、コードを空白にする', () => {
    const text = ['/** 説明 */', 'int a; /* 注 */', 'int b; // 後ろ'].join('\n')
    assert.equal(keepJavaComments(text), ['/** 説明 */', '       /* 注 */', '       // 後ろ'].join('\n'))
  })

  test('文字列とテキストブロックの中の // と /* を残さない', () => {
    const text = ['String a = "//x /* y */"; // 注', 'String b = """', '  // z /* w', '  """;'].join('\n')
    const kept = keepJavaComments(text)
    assert.equal(kept.includes('x'), false)
    assert.equal(kept.includes('z'), false)
    assert.equal(kept.includes('// 注'), true)
  })

  test('長さと行ごとの長さが元と同じ（行番号が変わらない）', () => {
    const text = 'a /* x\ny */ b // z\n"//w" c'
    const kept = keepJavaComments(text)
    assert.equal(kept.length, text.length)
    assert.deepEqual(
      kept.split('\n').map((line) => line.length),
      text.split('\n').map((line) => line.length),
    )
  })
})

describe('TypeScript のコメントだけを残す', () => {
  test('TSDoc、ブロックのコメント、行のコメントを残し、コードを空白にする', () => {
    const text = ['/** 説明 */', 'const a = 1 /* 注 */', 'const b = 2 // 後ろ'].join('\n')
    assert.equal(keepTsComments(text), ['/** 説明 */', '            /* 注 */', '            // 後ろ'].join('\n'))
  })

  test('文字列、テンプレート、正規表現の中の // と /* を残さない', () => {
    const text = ["const a = 'http://x'", 'const b = `//y ${1} /* z */`', 'const c = /\\/\\/w/ // 注'].join('\n')
    const kept = keepTsComments(text)
    assert.equal(kept.includes('http'), false)
    assert.equal(kept.includes('y'), false)
    assert.equal(kept.includes('w'), false)
    assert.equal(kept.includes('// 注'), true)
  })

  test('長さと行ごとの長さが元と同じ（行番号が変わらない）', () => {
    const text = 'a /* x\ny */ b // z\n"//w" c'
    const kept = keepTsComments(text)
    assert.equal(kept.length, text.length)
    assert.deepEqual(
      kept.split('\n').map((line) => line.length),
      text.split('\n').map((line) => line.length),
    )
  })
})
