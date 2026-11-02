// Chrome とネットワークは使わない。描画は偽の関数に替える。
import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { buildCommand, checkDocs, describeFailure, extractMermaidBlocks, findChrome, isMermaidTargetDoc, quoteArg, summaryLine, toMarkdown } from './mermaid-check.mjs'

const FENCE = '```'

/** 図の本文だけを読み取って返す */
function sources(text) {
  return extractMermaidBlocks(text).diagrams.map((diagram) => diagram.source)
}

describe('Mermaid の図の読み取り：拾う', () => {
  test('行頭の ```mermaid のコードブロック', () => {
    assert.deepEqual(extractMermaidBlocks(`文\n${FENCE}mermaid\ngraph TD\n  A-->B\n${FENCE}\n`), {
      diagrams: [{ line: 2, source: 'graph TD\n  A-->B' }],
      unclosed: [],
    })
  })

  test('~~~mermaid のコードブロック', () => {
    assert.deepEqual(sources('~~~mermaid\nA\n~~~'), ['A'])
  })

  test('info string が Mermaid（大文字）', () => {
    assert.deepEqual(sources(`${FENCE}Mermaid\nA\n${FENCE}`), ['A'])
  })

  test('info string の後ろに語がある', () => {
    assert.deepEqual(sources(`${FENCE}mermaid 説明\nA\n${FENCE}`), ['A'])
  })

  test('1〜3 個の空白で字下げした開きと閉じ', () => {
    assert.deepEqual(sources(`   ${FENCE}mermaid\nA\n  ${FENCE}\n`), ['A'])
  })

  test('開きと info string の間に空白がある', () => {
    assert.deepEqual(sources(`${FENCE} mermaid\nA\n${FENCE}`), ['A'])
  })

  test('記号が 4 個以上の開きと、開き以上の長さの閉じ', () => {
    assert.deepEqual(sources('````mermaid\nA\n`````'), ['A'])
    assert.deepEqual(sources('~~~~mermaid\nA\n~~~~~~'), ['A'])
  })

  test('閉じの後ろに空白だけがある行', () => {
    assert.deepEqual(sources(`${FENCE}mermaid\nA\n${FENCE}  \t\n`), ['A'])
  })

  test('CRLF のファイルと、末尾の閉じの行に改行がないファイル', () => {
    assert.deepEqual(sources(`${FENCE}mermaid\r\nA\r\nB\r\n${FENCE}`), ['A\nB'])
  })

  test('1 つのファイルの複数の図を順に拾い、開きの行を返す', () => {
    const text = `${FENCE}mermaid\nA\n${FENCE}\n\n${FENCE}mermaid\nB\n${FENCE}\n`
    assert.deepEqual(extractMermaidBlocks(text).diagrams, [
      { line: 1, source: 'A' },
      { line: 5, source: 'B' },
    ])
  })

  test('段落の直後に空行なしで始まる図', () => {
    assert.deepEqual(extractMermaidBlocks(`段落\n${FENCE}mermaid\nA\n${FENCE}`).diagrams, [{ line: 2, source: 'A' }])
  })

  test('空の図は、空の本文で拾う', () => {
    assert.deepEqual(sources(`${FENCE}mermaid\n${FENCE}`), [''])
  })
})

describe('Mermaid の図の読み取り：拾わない', () => {
  test('4 つのバッククォートの囲みに入った ```mermaid（雛形の骨組み）', () => {
    assert.deepEqual(extractMermaidBlocks('````markdown\n```mermaid\nA\n```\n````\n'), { diagrams: [], unclosed: [] })
  })

  test('表のセルに書いたコード ```mermaid', () => {
    assert.deepEqual(extractMermaidBlocks('| 書き方 | ```mermaid |\n|---|---|\n| a | `' + FENCE + 'mermaid` |\n'), { diagrams: [], unclosed: [] })
  })

  test('他の言語のコードブロック', () => {
    assert.deepEqual(extractMermaidBlocks(`${FENCE}java\nint a;\n${FENCE}\n${FENCE}\nplain\n${FENCE}\n`), { diagrams: [], unclosed: [] })
  })

  test('4 個以上の空白かタブで字下げした ```mermaid', () => {
    assert.deepEqual(extractMermaidBlocks(`    ${FENCE}mermaid\n    A\n    ${FENCE}\n`), { diagrams: [], unclosed: [] })
    assert.deepEqual(extractMermaidBlocks(`\t${FENCE}mermaid\n\tA\n\t${FENCE}\n`), { diagrams: [], unclosed: [] })
  })

  test('先頭の語が mermaid でない（```mermaids）', () => {
    assert.deepEqual(extractMermaidBlocks(`${FENCE}mermaids\nA\n${FENCE}\n`), { diagrams: [], unclosed: [] })
  })

  test('バッククォートの開きの info string にバッククォートがある（1 行のコードの範囲）', () => {
    assert.deepEqual(extractMermaidBlocks(`${FENCE}mermaid\`x\`\n本文\n`), { diagrams: [], unclosed: [] })
  })

  test('3 個のバッククォートの markdown の囲みに入った ```mermaid（外側の閉じが新しい開きになり、mermaid でないので捨てる）', () => {
    const text = '```markdown\n```mermaid\nA\n```\n```\n'
    assert.deepEqual(extractMermaidBlocks(text), { diagrams: [], unclosed: [] })
  })

  test('~~~ に入った ```mermaid と、``` に入った ~~~mermaid', () => {
    assert.deepEqual(extractMermaidBlocks('~~~\n```mermaid\nA\n```\n~~~\n'), { diagrams: [], unclosed: [] })
    assert.deepEqual(extractMermaidBlocks('```\n~~~mermaid\nA\n~~~\n```\n'), { diagrams: [], unclosed: [] })
  })

  test('HTML のコメントの中', () => {
    assert.deepEqual(extractMermaidBlocks('<!--\n```mermaid\nA\n```\n-->\n'), { diagrams: [], unclosed: [] })
    assert.deepEqual(extractMermaidBlocks('<!-- 説明 -->\n```mermaid\nA\n```\n').diagrams.length, 1)
  })
})

describe('Mermaid の図の読み取り：失敗にする', () => {
  test('閉じていない ```mermaid のコードブロック（開きの行を返す）', () => {
    assert.deepEqual(extractMermaidBlocks('文\n```mermaid\nA\n'), { diagrams: [], unclosed: [2] })
  })

  test('閉じが開きより短い', () => {
    assert.deepEqual(extractMermaidBlocks('````mermaid\nA\n```\n').unclosed, [1])
  })

  test('閉じの字が違う', () => {
    assert.deepEqual(extractMermaidBlocks('```mermaid\nA\n~~~\n').unclosed, [1])
  })

  test('閉じの後ろに文字がある', () => {
    assert.deepEqual(extractMermaidBlocks('```mermaid\nA\n``` 文字\n').unclosed, [1])
  })

  test('4 個以上字下げした閉じ（他に閉じがない）', () => {
    assert.deepEqual(extractMermaidBlocks('```mermaid\nA\n    ```\n').unclosed, [1])
  })

  test('開きの行がファイルの最後の行', () => {
    assert.deepEqual(extractMermaidBlocks('文\n```mermaid'), { diagrams: [], unclosed: [2] })
  })
})

describe('対象の文書', () => {
  test('docs/ の下の .md。アーカイブと、docs/ の外と、.md でないものは除く', () => {
    assert.equal(isMermaidTargetDoc('docs/02-設計/order.md'), true)
    assert.equal(isMermaidTargetDoc('docs/08-spec/321-design-templates/plan.md'), true)
    assert.equal(isMermaidTargetDoc('docs/08-spec/archive/mvp/002-cart-and-order/spec.md'), false)
    assert.equal(isMermaidTargetDoc('README.md'), false)
    assert.equal(isMermaidTargetDoc('docs/01-概要/img/system.drawio.svg'), false)
  })
})

describe('描画の結果の判定', () => {
  test('エラーの出力が空でも、失敗として空でない文字列を返す', () => {
    assert.notEqual(describeFailure({ stderr: '', message: '' }), '')
    assert.notEqual(describeFailure({}), '')
    assert.notEqual(describeFailure(new Error('')), '')
  })

  test('エラーの出力があれば、スタックの行を除いて先頭の数行を返す', () => {
    const message = describeFailure({ stderr: 'Parse error on line 2\n  at foo (x.js:1)\n\nExpecting X\n' })
    assert.equal(message, 'Parse error on line 2\nExpecting X')
  })
})

describe('ファイルごとの描画', () => {
  /** 描画を記録し、`BAD` を含む入力を失敗にする偽の描画 */
  function fakeRender() {
    const calls = []
    const render = (markdown) => {
      calls.push(markdown)
      return markdown.includes('BAD') ? 'Parse error' : null
    }
    return { calls, render }
  }

  test('全ての図が描ければ、ファイルごとに 1 回だけ描画する', () => {
    const { calls, render } = fakeRender()
    const text = `${FENCE}mermaid\nA\n${FENCE}\n\n${FENCE}mermaid\nB\n${FENCE}\n`
    const result = checkDocs([{ file: 'docs/a.md', text }, { file: 'docs/b.md', text }], render)
    assert.deepEqual(result, { problems: [], count: 4 })
    assert.equal(calls.length, 2)
    assert.ok(calls[0].includes('A') && calls[0].includes('B'))
  })

  test('図のないファイルは描画しない', () => {
    const { calls, render } = fakeRender()
    assert.deepEqual(checkDocs([{ file: 'docs/a.md', text: '本文のみ\n' }], render), { problems: [], count: 0 })
    assert.equal(calls.length, 0)
  })

  test('失敗したファイルだけ、図ごとに再描画し、失敗した図の行を示す', () => {
    const { calls, render } = fakeRender()
    const bad = `前\n${FENCE}mermaid\nA\n${FENCE}\n\n${FENCE}mermaid\nBAD\n${FENCE}\n\n${FENCE}mermaid\nC\n${FENCE}\n`
    const good = `${FENCE}mermaid\nD\n${FENCE}\n`
    const result = checkDocs([{ file: 'docs/bad.md', text: bad }, { file: 'docs/good.md', text: good }], render)
    assert.deepEqual(result.problems, [{ file: 'docs/bad.md', line: 6, message: 'Parse error' }])
    assert.equal(result.count, 4)
    // bad.md：まとめて 1 回 + 図ごとに 3 回、good.md：1 回
    assert.equal(calls.length, 5)
  })

  test('複数の図が失敗したら、それぞれの行を示す', () => {
    const { render } = fakeRender()
    const text = `${FENCE}mermaid\nBAD1\n${FENCE}\n${FENCE}mermaid\nok\n${FENCE}\n${FENCE}mermaid\nBAD2\n${FENCE}\n`
    assert.deepEqual(checkDocs([{ file: 'docs/a.md', text }], render).problems.map((problem) => problem.line), [1, 7])
  })

  test('まとめては失敗し、図ごとには全て通るときは、最初の図の行に、まとめての誤りを示す', () => {
    const render = (markdown) => (markdown.split('mermaid').length > 2 ? 'まとめての失敗' : null)
    const text = `${FENCE}mermaid\nA\n${FENCE}\n${FENCE}mermaid\nB\n${FENCE}\n`
    assert.deepEqual(checkDocs([{ file: 'docs/a.md', text }], render).problems, [{ file: 'docs/a.md', line: 1, message: 'まとめての失敗' }])
  })

  test('閉じていない図は、行を示して失敗にし、他の図は描画する', () => {
    const { calls, render } = fakeRender()
    const text = `${FENCE}mermaid\nA\n${FENCE}\n${FENCE}mermaid\nB\n`
    const result = checkDocs([{ file: 'docs/a.md', text }], render)
    assert.equal(result.problems.length, 1)
    assert.equal(result.problems[0].line, 4)
    assert.match(result.problems[0].message, /閉じ/)
    assert.equal(calls.length, 1)
    assert.equal(result.count, 2)
  })

  test('図の本文のバッククォートより長い囲みで、描画の入力を作る', () => {
    const markdown = toMarkdown(['a\n````\nb'])
    assert.ok(markdown.startsWith('`````mermaid\n'))
    assert.ok(markdown.trimEnd().endsWith('`````'))
  })
})

describe('要約の行', () => {
  test('描画できない図の数と図の数を示す', () => {
    assert.equal(summaryLine(2, 15), '描画できない Mermaid の図：2（図の数 15）')
    assert.equal(summaryLine(0, 0), '描画できない Mermaid の図：0（図の数 0）')
  })
})

describe('Chrome の場所', () => {
  const exists = (...found) => (candidate) => found.includes(candidate)

  test('環境変数 MERMAID_CHROME があり、実在すれば、それを使う', () => {
    assert.equal(findChrome({ MERMAID_CHROME: '/opt/chrome' }, exists('/opt/chrome', '/usr/bin/google-chrome')), '/opt/chrome')
  })

  test('環境変数がなければ、既定の場所を順に探す', () => {
    assert.equal(findChrome({}, exists('/usr/bin/chromium')), '/usr/bin/chromium')
    assert.equal(findChrome({}, exists('/usr/bin/google-chrome', '/usr/bin/chromium')), '/usr/bin/google-chrome')
    assert.equal(findChrome({}, exists('C:/Program Files/Google/Chrome/Application/chrome.exe')), 'C:/Program Files/Google/Chrome/Application/chrome.exe')
  })

  test('どこにもなければ null を返す（環境変数の場所が実在しないときも、既定の場所に替えない）', () => {
    assert.equal(findChrome({}, exists()), null)
    assert.equal(findChrome({ MERMAID_CHROME: '/nowhere' }, exists('/usr/bin/chromium')), null)
  })
})

describe('起動の引数', () => {
  test('空白を含むパスは、引用符で囲む', () => {
    assert.equal(quoteArg('C:\\Users\\a b\\Temp\\x.md'), '"C:\\Users\\a b\\Temp\\x.md"')
  })

  test('起動の命令は、全ての引数を引用符で囲み、空白のあるパスが 1 つの引数になる', () => {
    const command = buildCommand('npx', ['--yes', '@mermaid-js/mermaid-cli@12.0.0', '-i', 'C:\\Temp\\a b\\in.md'])
    assert.equal(command, 'npx "--yes" "@mermaid-js/mermaid-cli@12.0.0" "-i" "C:\\Temp\\a b\\in.md"')
  })
})
