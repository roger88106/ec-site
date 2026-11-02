// Chrome とネットワークは使わない。描画は偽の関数に替える。
import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { emptyListReason } from '../lib/git-files.mjs'
import { buildCommand, checkDocs, describeFailure, extractMermaidBlocks, findChrome, isMermaidTargetDoc, puppeteerConfig, quoteArg, runCheck, summaryLine, toMarkdown } from './mermaid-check.mjs'

const FENCE = '```'

/** 図の本文だけを読み取って返す */
function sources(text) {
  return extractMermaidBlocks(text).diagrams.map((diagram) => diagram.source)
}

describe('Mermaid の図の読み取り：取り出す', () => {
  test('行頭の ```mermaid のコードブロック', () => {
    assert.deepEqual(extractMermaidBlocks(`文\n${FENCE}mermaid\ngraph TD\n  A-->B\n${FENCE}\n`), {
      diagrams: [{ line: 2, source: 'graph TD\n  A-->B' }],
      unclosed: [],
      misplaced: [],
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

  test('1 つのファイルの複数の図を順に取り出し、開きの行を返す', () => {
    const text = `${FENCE}mermaid\nA\n${FENCE}\n\n${FENCE}mermaid\nB\n${FENCE}\n`
    assert.deepEqual(extractMermaidBlocks(text).diagrams, [
      { line: 1, source: 'A' },
      { line: 5, source: 'B' },
    ])
  })

  test('段落の直後に空行なしで始まる図', () => {
    assert.deepEqual(extractMermaidBlocks(`段落\n${FENCE}mermaid\nA\n${FENCE}`).diagrams, [{ line: 2, source: 'A' }])
  })

  test('空の図は、空の本文で取り出す', () => {
    assert.deepEqual(sources(`${FENCE}mermaid\n${FENCE}`), [''])
  })
})

describe('Mermaid の図の読み取り：取り出さない', () => {
  test('4 つのバッククォートの囲みに入った ```mermaid（雛形の骨組み）', () => {
    assert.deepEqual(extractMermaidBlocks('````markdown\n```mermaid\nA\n```\n````\n'), { diagrams: [], unclosed: [], misplaced: [] })
  })

  test('表のセルに書いたコード ```mermaid', () => {
    assert.deepEqual(extractMermaidBlocks('| 書き方 | ```mermaid |\n|---|---|\n| a | `' + FENCE + 'mermaid` |\n'), { diagrams: [], unclosed: [], misplaced: [] })
  })

  test('他の言語のコードブロック', () => {
    assert.deepEqual(extractMermaidBlocks(`${FENCE}java\nint a;\n${FENCE}\n${FENCE}\nplain\n${FENCE}\n`), { diagrams: [], unclosed: [], misplaced: [] })
  })

  test('先頭の語が mermaid でない（```mermaids）', () => {
    assert.deepEqual(extractMermaidBlocks(`${FENCE}mermaids\nA\n${FENCE}\n`), { diagrams: [], unclosed: [], misplaced: [] })
  })

  test('バッククォートの開きの info string にバッククォートがある（1 行のコードの範囲）', () => {
    assert.deepEqual(extractMermaidBlocks(`${FENCE}mermaid\`x\`\n本文\n`), { diagrams: [], unclosed: [], misplaced: [] })
  })

  test('3 個のバッククォートの markdown の囲みに入った ```mermaid（外側の閉じが新しい開きになり、mermaid でないので取り出さない）', () => {
    const text = '```markdown\n```mermaid\nA\n```\n```\n'
    assert.deepEqual(extractMermaidBlocks(text), { diagrams: [], unclosed: [], misplaced: [] })
  })

  test('markdown・md の ~~~ に入った ```mermaid と、markdown・md の ``` に入った ~~~mermaid（雛形の骨組み）', () => {
    assert.deepEqual(extractMermaidBlocks('~~~markdown\n```mermaid\nA\n```\n~~~\n'), { diagrams: [], unclosed: [], misplaced: [] })
    assert.deepEqual(extractMermaidBlocks('```md\n~~~mermaid\nA\n~~~\n```\n'), { diagrams: [], unclosed: [], misplaced: [] })
  })

  test('mermaid の図の本文の中の ```mermaid の行は、閉じていない疑いにしない（描画の検査に任せる）', () => {
    assert.deepEqual(extractMermaidBlocks('````mermaid\n```mermaid\n````\n').misplaced, [])
  })

  test('コードブロックの中の、字下げした ```mermaid と、引用の中の ```mermaid', () => {
    assert.deepEqual(extractMermaidBlocks('````markdown\n    ```mermaid\n> ```mermaid\n````\n').misplaced, [])
  })

  test('HTML のコメントの中', () => {
    assert.deepEqual(extractMermaidBlocks('<!--\n```mermaid\nA\n```\n-->\n'), { diagrams: [], unclosed: [], misplaced: [] })
    assert.deepEqual(extractMermaidBlocks('<!-- 説明 -->\n```mermaid\nA\n```\n').diagrams.length, 1)
  })
})

describe('Mermaid の図の読み取り：失敗にする', () => {
  test('閉じていない ```mermaid のコードブロック（開きの行を返す）', () => {
    assert.deepEqual(extractMermaidBlocks('文\n```mermaid\nA\n'), { diagrams: [], unclosed: [2], misplaced: [] })
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
    assert.deepEqual(extractMermaidBlocks('文\n```mermaid'), { diagrams: [], unclosed: [2], misplaced: [] })
  })

  /** 対象にしない書き方の図の行 */
  function misplacedLines(text) {
    return extractMermaidBlocks(text).misplaced.filter((item) => /対象にしない書き方の図/.test(item.message)).map((item) => item.line)
  }

  test('4 個以上の空白かタブで字下げした ```mermaid は、対象にしない書き方の図として失敗にする（中身は取り出さない）', () => {
    const indented = extractMermaidBlocks(`文\n    ${FENCE}mermaid\n    A\n    ${FENCE}\n`)
    assert.deepEqual(indented.diagrams, [])
    assert.deepEqual(indented.misplaced.map((item) => item.line), [2])
    assert.match(indented.misplaced[0].message, /対象にしない書き方の図（箇条書き・引用の中など）/)
    assert.deepEqual(misplacedLines(`\t${FENCE}mermaid\n\tA\n\t${FENCE}\n`), [1])
  })

  test('箇条書き・番号付きの項目・引用の中の ```mermaid と ~~~mermaid は、対象にしない書き方の図として失敗にする', () => {
    assert.deepEqual(misplacedLines(`- ${FENCE}mermaid\n  A\n  ${FENCE}\n`), [1])
    assert.deepEqual(misplacedLines(`10. 項目\n\n    ${FENCE}mermaid\n    A\n    ${FENCE}\n`), [3])
    assert.deepEqual(misplacedLines(`> ${FENCE}mermaid\n> A\n> ${FENCE}\n`), [1])
    assert.deepEqual(misplacedLines('> - ~~~Mermaid\n'), [1])
    assert.deepEqual(misplacedLines(`1. > ${FENCE} mermaid 説明\n`), [1])
  })

  test('取り出した図の開き、閉じていない図の開き、コメントの中、1 行のコードの中は、対象にしない書き方の図にしない', () => {
    assert.deepEqual(misplacedLines(`  ${FENCE}mermaid\nA\n${FENCE}\n`), [])
    assert.deepEqual(misplacedLines(`${FENCE}mermaid\nA\n`), [])
    assert.deepEqual(misplacedLines(`<!--\n> ${FENCE}mermaid\n-->\n`), [])
    assert.deepEqual(misplacedLines(`- \`${FENCE}mermaid\` と書く\n- ${FENCE}mermaids\n`), [])
  })

  test('mermaid でも markdown・md でもないコードブロックの本文に、図の開きの形の行があれば、前のコードブロックが閉じていない疑いとして失敗にする', () => {
    const text = `${FENCE}java\nint a;\n\n${FENCE}mermaid\nA\n${FENCE}\n`
    const result = extractMermaidBlocks(text)
    assert.deepEqual(result.diagrams, [])
    assert.deepEqual(result.misplaced.map((item) => item.line), [4])
    assert.match(result.misplaced[0].message, /前のコードブロックが閉じていない疑い/)
    assert.deepEqual(extractMermaidBlocks('~~~\n```mermaid\nA\n```\n~~~\n').misplaced.map((item) => item.line), [2])
    assert.deepEqual(extractMermaidBlocks('```\n~~~mermaid\nA\n~~~\n```\n').misplaced.map((item) => item.line), [2])
    assert.deepEqual(extractMermaidBlocks('````text\n   ```Mermaid 説明\n````\n').misplaced.map((item) => item.line), [2])
  })

  test('コードブロックの本文の、4 個以上字下げした ```mermaid と、先頭の語が mermaid でない行は、閉じていない疑いにしない', () => {
    assert.deepEqual(extractMermaidBlocks('```java\n    ```mermaid\n```mermaids\n```\n').misplaced, [])
  })
})

describe('対象の文書', () => {
  test('Git で扱う全ての .md。アーカイブと、.md でないものは除く', () => {
    assert.equal(isMermaidTargetDoc('docs/02-設計/order.md'), true)
    assert.equal(isMermaidTargetDoc('docs/08-spec/321-design-templates/plan.md'), true)
    assert.equal(isMermaidTargetDoc('README.md'), true)
    assert.equal(isMermaidTargetDoc('.claude/skills/docs/SKILL.md'), true)
    assert.equal(isMermaidTargetDoc('docs/08-spec/archive/mvp/002-cart-and-order/spec.md'), false)
    assert.equal(isMermaidTargetDoc('docs/01-概要/img/system.drawio.svg'), false)
    assert.equal(isMermaidTargetDoc('docs/a.mdx'), false)
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
  /** 入力の図の数（Markdown なら mermaid のコードブロックの数、図の本文なら 1） */
  function diagramCount(input, kind) {
    return kind === 'markdown' ? input.split('mermaid\n').length - 1 : 1
  }

  /** 描画を記録し、`BAD` を含む入力を失敗にし、できた SVG の数を図の数にする偽の描画 */
  function fakeRender() {
    const calls = []
    const render = (input, kind) => {
      calls.push({ input, kind })
      return input.includes('BAD') ? { error: 'Parse error', outputs: 0 } : { error: null, outputs: diagramCount(input, kind) }
    }
    return { calls, render }
  }

  test('全ての図が描画できれば、ファイルごとに 1 回だけ描画する', () => {
    const { calls, render } = fakeRender()
    const text = `${FENCE}mermaid\nA\n${FENCE}\n\n${FENCE}mermaid\nB\n${FENCE}\n`
    const result = checkDocs([{ file: 'docs/a.md', text }, { file: 'docs/b.md', text }], render)
    assert.deepEqual(result, { problems: [], count: 4 })
    assert.equal(calls.length, 2)
    assert.ok(calls[0].input.includes('A') && calls[0].input.includes('B'))
    assert.equal(calls[0].kind, 'markdown')
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
    // 図ごとの描画は、図の本文だけを入力にする（本文の中の ``` や ::: に影響されない）
    assert.deepEqual(calls.slice(1, 4).map((call) => [call.kind, call.input]), [['diagram', 'A'], ['diagram', 'BAD'], ['diagram', 'C']])
  })

  test('複数の図が失敗したら、それぞれの行を示す', () => {
    const { render } = fakeRender()
    const text = `${FENCE}mermaid\nBAD1\n${FENCE}\n${FENCE}mermaid\nok\n${FENCE}\n${FENCE}mermaid\nBAD2\n${FENCE}\n`
    assert.deepEqual(checkDocs([{ file: 'docs/a.md', text }], render).problems.map((problem) => problem.line), [1, 7])
  })

  test('まとめては失敗し、図ごとには全て通るときは、最初の図の行に、まとめて描画したときの誤りを示す', () => {
    const render = (input, kind) => (kind === 'markdown' ? { error: 'まとめての失敗', outputs: 0 } : { error: null, outputs: 1 })
    const text = `${FENCE}mermaid\nA\n${FENCE}\n${FENCE}mermaid\nB\n${FENCE}\n`
    assert.deepEqual(checkDocs([{ file: 'docs/a.md', text }], render).problems, [{ file: 'docs/a.md', line: 1, message: 'まとめての失敗' }])
  })

  test('出力の SVG が図の数より少なければ、プロセスが成功でも失敗とし、図ごとに描画する', () => {
    // 2 つ目の図の本文に ``` で終わる行があり、mermaid-cli がその図を取り出せない場合を想定した偽の描画
    const calls = []
    const render = (input, kind) => {
      calls.push(kind)
      if (kind === 'markdown') return { error: null, outputs: 1 }
      return input === 'B' ? { error: null, outputs: 0 } : { error: null, outputs: 1 }
    }
    const text = `${FENCE}mermaid\nA\n${FENCE}\n${FENCE}mermaid\nB\n${FENCE}\n`
    const result = checkDocs([{ file: 'docs/a.md', text }], render)
    assert.equal(result.problems.length, 1)
    assert.equal(result.problems[0].line, 4)
    assert.match(result.problems[0].message, /足りない/)
    assert.deepEqual(calls, ['markdown', 'diagram', 'diagram'])
  })

  test('本文に行末の ``` か ::: がある図は、まとめて描画せず、図ごとに描画する（まとめると途中で切れるため）', () => {
    const { calls, render } = fakeRender()
    const text = `${FENCE}mermaid\nA\n${FENCE}\n${FENCE}mermaid\n%% 注 :::\nB\n${FENCE}\n${FENCE}mermaid\nC ${FENCE}x\n${FENCE}\n`
    const result = checkDocs([{ file: 'docs/a.md', text }], render)
    assert.deepEqual(result.problems, [])
    assert.deepEqual(calls.map((call) => call.kind), ['diagram', 'diagram', 'diagram'])
  })

  test('図ごとの描画でも、出力の SVG ができなければ失敗とする', () => {
    const render = () => ({ error: null, outputs: 0 })
    const text = `${FENCE}mermaid\nA\n${FENCE}\n`
    const result = checkDocs([{ file: 'docs/a.md', text }], render)
    assert.equal(result.problems.length, 1)
    assert.match(result.problems[0].message, /0 個/)
  })

  test('出力の SVG が図の数より多くても、失敗とする', () => {
    const render = () => ({ error: null, outputs: 3 })
    const text = `${FENCE}mermaid\nA\n${FENCE}\n${FENCE}mermaid\nB\n${FENCE}\n`
    assert.ok(checkDocs([{ file: 'docs/a.md', text }], render).problems.length > 0)
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

  test('対象にしない書き方の図と、閉じていない疑いの図は、行と理由を示して失敗にし、図の数に数える', () => {
    const { calls, render } = fakeRender()
    const text = `${FENCE}mermaid\nA\n${FENCE}\n> ${FENCE}mermaid\n${FENCE}java\n${FENCE}mermaid\nB\n${FENCE}\n`
    const result = checkDocs([{ file: 'docs/a.md', text }], render)
    assert.deepEqual(result.problems.map((problem) => [problem.line, /対象にしない/.test(problem.message), /閉じていない疑い/.test(problem.message)]), [
      [4, true, false],
      [6, false, true],
    ])
    assert.equal(result.count, 3)
    assert.equal(calls.length, 1)
  })

  test('描画の入力の Markdown は、図ごとに 3 個のバッククォートのコードブロックにする（mermaid-cli が読み取る形）', () => {
    assert.equal(toMarkdown(['A', 'B']), `${FENCE}mermaid\nA\n${FENCE}\n\n${FENCE}mermaid\nB\n${FENCE}\n`)
  })
})

describe('検査の本体（runCheck）', () => {
  /** 呼び出しを記録する偽の外部処理。上書きで差し替える */
  function fakeDeps(overrides = {}) {
    const log = { events: [], out: [], err: [] }
    const text = `${FENCE}mermaid\nA\n${FENCE}\n`
    const deps = {
      findChrome: () => '/chrome',
      listDocs: () => [{ file: 'docs/a.md', text }],
      makeWork: () => {
        log.events.push('make')
        return '/work'
      },
      removeWork: (work) => log.events.push(`remove ${work}`),
      makeRender: () => () => ({ error: null, outputs: 1 }),
      out: (line) => log.out.push(line),
      err: (line) => log.err.push(line),
      ...overrides,
    }
    return { deps, log }
  }

  test('Chrome がなければ、作業のディレクトリを作らずに終了コード 2', () => {
    const { deps, log } = fakeDeps({ findChrome: () => null })
    assert.equal(runCheck(deps), 2)
    assert.deepEqual(log.events, [])
    assert.match(log.err[0], /Chrome/)
  })

  test('全て描画できれば終了コード 0 で、要約の行を出し、作業のディレクトリを削除する', () => {
    const { deps, log } = fakeDeps()
    assert.equal(runCheck(deps), 0)
    assert.deepEqual(log.out, [summaryLine(0, 1)])
    assert.deepEqual(log.events, ['make', 'remove /work'])
  })

  test('描画が失敗したら、終了コード 1 と、図の位置と要約の行を出力する', () => {
    const { deps, log } = fakeDeps({ makeRender: () => () => ({ error: 'Parse error', outputs: 0 }) })
    assert.equal(runCheck(deps), 1)
    assert.deepEqual(log.err, ['docs/a.md:1\nParse error'])
    assert.deepEqual(log.out, [summaryLine(1, 1)])
  })

  test('描画が失敗しても、作業のディレクトリを削除する', () => {
    const { deps, log } = fakeDeps({ makeRender: () => () => ({ error: 'Parse error', outputs: 0 }) })
    runCheck(deps)
    assert.deepEqual(log.events, ['make', 'remove /work'])
  })

  test('描画の関数が例外を投げても、作業のディレクトリを削除する', () => {
    const { deps, log } = fakeDeps({
      makeRender: () => () => {
        throw new Error('想定外')
      },
    })
    assert.throws(() => runCheck(deps), /想定外/)
    assert.deepEqual(log.events, ['make', 'remove /work'])
  })

  test('取り出した図が 0 件なら、終了コード 1 と理由を出力する', () => {
    const { deps, log } = fakeDeps({ listDocs: () => [{ file: 'docs/a.md', text: '本文のみ\n' }] })
    assert.equal(runCheck(deps), 1)
    assert.deepEqual(log.out, [emptyListReason([], 'Mermaid の図')])
  })

  test('対象の文書が 0 件なら、作業のディレクトリを作らずに終了コード 1', () => {
    const { deps, log } = fakeDeps({ listDocs: () => [] })
    assert.equal(runCheck(deps), 1)
    assert.deepEqual(log.events, [])
  })

  test('閉じていない図だけでも、図の数に数えて失敗にする（0 件の失敗とは別）', () => {
    const { deps, log } = fakeDeps({ listDocs: () => [{ file: 'docs/a.md', text: `${FENCE}mermaid\nA\n` }] })
    assert.equal(runCheck(deps), 1)
    assert.deepEqual(log.out, [summaryLine(1, 1)])
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
    const mac = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
    assert.equal(findChrome({}, exists(mac)), mac)
  })

  test('どこにもなければ null を返す（環境変数の場所が実在しないときも、既定の場所に替えない）', () => {
    assert.equal(findChrome({}, exists()), null)
    assert.equal(findChrome({ MERMAID_CHROME: '/nowhere' }, exists('/usr/bin/chromium')), null)
  })
})

describe('puppeteer の設定', () => {
  test('Chrome の場所を入れ、Linux のときだけ --no-sandbox を付ける', () => {
    assert.deepEqual(puppeteerConfig('/usr/bin/google-chrome', 'linux'), { executablePath: '/usr/bin/google-chrome', args: ['--no-sandbox'] })
    assert.deepEqual(puppeteerConfig('C:/chrome.exe', 'win32'), { executablePath: 'C:/chrome.exe', args: [] })
    assert.deepEqual(puppeteerConfig('/Applications/x', 'darwin'), { executablePath: '/Applications/x', args: [] })
  })
})

describe('起動の引数', () => {
  test('空白を含むパスは、引用符で囲む', () => {
    assert.equal(quoteArg('C:\\Users\\a b\\Temp\\x.md'), '"C:\\Users\\a b\\Temp\\x.md"')
  })

  test('起動のコマンドは、全ての引数を引用符で囲み、空白のあるパスが 1 つの引数になる', () => {
    const command = buildCommand('npx', ['--yes', '@mermaid-js/mermaid-cli@12.0.0', '-i', 'C:\\Temp\\a b\\in.md'])
    assert.equal(command, 'npx "--yes" "@mermaid-js/mermaid-cli@12.0.0" "-i" "C:\\Temp\\a b\\in.md"')
  })
})
