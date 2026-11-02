import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { readCodeBlocks } from './code-block.mjs'

describe('囲みのコードブロックの読み方', () => {
  test('開き・閉じ・info string・行・中身を返す', () => {
    const blocks = readCodeBlocks('前\n```java 説明\nint a;\nint b;\n```\n後')
    assert.deepEqual(blocks, [{ char: '`', length: 3, info: 'java 説明', line: 2, closeLine: 5, closed: true, body: ['int a;', 'int b;'] }])
  })

  test('~~~ の囲みも読む', () => {
    const [block] = readCodeBlocks('~~~mermaid\nx\n~~~')
    assert.equal(block.char, '~')
    assert.equal(block.info, 'mermaid')
    assert.equal(block.closed, true)
  })

  test('閉じがなければ、文書の終わりまでを中身にして closed を false にする', () => {
    const [block] = readCodeBlocks('```mermaid\na\nb')
    assert.equal(block.closed, false)
    assert.equal(block.closeLine, null)
    assert.deepEqual(block.body, ['a', 'b'])
  })

  test('開きの行が最後の行でも、閉じていないコードブロックとして返す', () => {
    const [block] = readCodeBlocks('文\n```mermaid')
    assert.equal(block.line, 2)
    assert.equal(block.closed, false)
    assert.deepEqual(block.body, [])
  })

  test('閉じは、開き以上の長さの同じ字と空白だけの行。短い・字が違う・後ろに文字がある行では閉じない', () => {
    assert.equal(readCodeBlocks('````x\n```\n````')[0].closeLine, 3)
    assert.equal(readCodeBlocks('````x\n`````  \n')[0].closeLine, 2)
    assert.equal(readCodeBlocks('```x\n~~~\n```')[0].closeLine, 3)
    assert.equal(readCodeBlocks('```x\n``` y\n')[0].closed, false)
    assert.equal(readCodeBlocks('```x\n```mermaid\n')[0].closed, false)
  })

  test('0〜3 個の空白で字下げした開きと閉じを読み、4 個の空白かタブの字下げは開きにしない', () => {
    assert.equal(readCodeBlocks('   ```x\n   ```')[0].closed, true)
    assert.deepEqual(readCodeBlocks('    ```x\n    ```'), [])
    assert.deepEqual(readCodeBlocks('\t```x\n\t```'), [])
    assert.equal(readCodeBlocks('```x\n    ```\n')[0].closed, false)
  })

  test('バッククォートの開きの info string にバッククォートがあれば、開きにしない（1 行のコードの範囲）', () => {
    assert.deepEqual(readCodeBlocks('```mermaid`x`\n本文\n```'), [{ char: '`', length: 3, info: '', line: 3, closeLine: null, closed: false, body: [] }])
    assert.equal(readCodeBlocks('~~~a`b\nx\n~~~')[0].info, 'a`b')
  })

  test('開きと info string の間の空白を除いて info string を返す', () => {
    assert.equal(readCodeBlocks('``` mermaid\n```')[0].info, 'mermaid')
  })

  test('別の囲みの中の開きは、開きにしない', () => {
    const blocks = readCodeBlocks('````markdown\n```mermaid\na\n```\n````\n')
    assert.equal(blocks.length, 1)
    assert.equal(blocks[0].info, 'markdown')
    assert.deepEqual(blocks[0].body, ['```mermaid', 'a', '```'])
    const tilde = readCodeBlocks('~~~\n```mermaid\n~~~')
    assert.equal(tilde.length, 1)
    assert.equal(tilde[0].char, '~')
    assert.equal(readCodeBlocks('```\n~~~mermaid\n```').length, 1)
  })

  test('CRLF の文書と、末尾に改行のない文書を読む', () => {
    const [block] = readCodeBlocks('```x\r\na\r\n```')
    assert.deepEqual(block.body, ['a'])
    assert.equal(block.closed, true)
  })

  test('HTML のコメントの中は読まない', () => {
    assert.deepEqual(readCodeBlocks('<!--\n```x\na\n```\n-->\n'), [])
    assert.deepEqual(readCodeBlocks('<!-- ```x -->\n'), [])
    assert.equal(readCodeBlocks('<!-- a -->\n```x\n```').length, 1)
    assert.equal(readCodeBlocks('<!-- a\n-->\n```x\n```').length, 1)
  })

  test('1 行のコードの中の <!-- は、コメントの開きにしない', () => {
    assert.equal(readCodeBlocks('コメントは `<!--` で始める\n```x\n```').length, 1)
    assert.equal(readCodeBlocks('``<!--`` と `a`\n```x\n```').length, 1)
  })

  test('コードブロックの中の <!-- は、コメントの開きにしない', () => {
    assert.equal(readCodeBlocks('```x\n<!--\n```\n```y\n```').length, 2)
  })

  test('複数のコードブロックを出る順に返す', () => {
    const blocks = readCodeBlocks('```a\n```\n\n```b\nx\n```')
    assert.deepEqual(blocks.map((block) => [block.info, block.line]), [['a', 1], ['b', 4]])
  })
})
