import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { checkDocuments, findSectionProblems, listSections, loadDocuments, readTargetDocs } from './rule-sections.mjs'

// 正しい形の 2 行。節の末尾に置く
const rows = ['理由：理由を書く。', '確認方法：レビュー']

// 見出しの後に空行を置き、本文を続ける。本文の最初の行は 3 行目になる
const section = (...body) => ['## 1. 節', '', ...body].join('\n')

// 3 行目に例の行を置き、正しい形の 2 行を続ける
const withRows = (...extra) => section(...extra, '', ...rows)

const LINE_OF_EXTRA = 3

function expectOne(text, pattern, line) {
  const problems = findSectionProblems(text)
  assert.equal(problems.length, 1, JSON.stringify(problems))
  assert.match(problems[0].message, pattern)
  if (line !== undefined) {
    assert.equal(problems[0].line, line)
  }
}

function expectNone(text, isExcluded) {
  assert.deepEqual(findSectionProblems(text, isExcluded), [])
}

const excludeAll = () => true

describe('検出すべき例', () => {
  test('例 1：本文があり、確認方法の行がない節は失敗にする', () => {
    expectOne(section('理由：理由を書く。'), /確認方法/)
  })

  test('例 2：本文があり、理由の行がない節は失敗にする', () => {
    expectOne(section('確認方法：レビュー'), /理由/)
  })

  test('例 3：行の途中の確認方法は失敗にする', () => {
    expectOne(withRows('規則の文（確認方法：レビュー）'), /確認方法/, LINE_OF_EXTRA)
  })

  test('例 4：箇条の確認方法は失敗にする', () => {
    expectOne(withRows('- 確認方法：レビュー'), /確認方法/, LINE_OF_EXTRA)
  })

  test('例 5：表のセルの確認方法は失敗にする', () => {
    expectOne(withRows('| 確認方法：自動 |'), /確認方法/, LINE_OF_EXTRA)
  })

  test('例 6：字下げした確認方法は失敗にする', () => {
    expectOne(withRows('  確認方法：レビュー'), /確認方法/, LINE_OF_EXTRA)
  })

  test('例 7：半角のコロンの確認方法は失敗にする', () => {
    expectOne(withRows('確認方法:レビュー'), /確認方法/, LINE_OF_EXTRA)
  })

  test('例 8：コロンの前に空白のある確認方法は失敗にする', () => {
    expectOne(withRows('確認方法 ：レビュー'), /確認方法/, LINE_OF_EXTRA)
  })

  test('例 9：同じ節に確認方法の行が 2 つあれば失敗にする', () => {
    expectOne(section('確認方法：レビュー', '確認方法：自動', '', '理由：理由を書く。'), /確認方法/)
  })

  test('例 10：内容のない理由の行だけでは失敗にする', () => {
    expectOne(section('理由：', '確認方法：レビュー'), /理由/)
  })

  test('例 11：箇条の理由だけでは失敗にする', () => {
    expectOne(section('- 理由：〜ため。', '確認方法：レビュー'), /理由/)
  })

  test('例 12：行の途中の理由だけでは失敗にする', () => {
    expectOne(section('〜する。理由：〜ため。', '確認方法：レビュー'), /理由/)
  })

  test('例 13：内容のない確認方法の後に箇条がなければ失敗にする', () => {
    expectOne(section('理由：理由を書く。', '確認方法：', '', 'ただの文。'), /確認方法/)
  })

  test('例 14：親の見出しに本文があって行がなく、子の見出しにだけ行があれば、親を失敗にする', () => {
    const text = ['## 1. 親', '', '理由：理由を書く。', '', '### 1.1 子', '', ...rows].join('\n')
    expectOne(text, /確認方法/)
  })

  test('例 15：確認方法がコードブロックの中にだけあれば失敗にする', () => {
    expectOne(section('理由：理由を書く。', '', '```', '確認方法：レビュー', '```'), /確認方法/)
  })

  test('例 16：除外の一覧にあるが、今の文書にない節は失敗にする', () => {
    const result = checkDocuments([{ file: 'a.md', text: withRows('本文。') }], { 'a.md「消えた節」': '理由' })
    assert.equal(result.exclusionProblems.length, 1)
    assert.match(result.exclusionProblems[0], /消えた節/)
  })

  test('例 17：理由が空の除外は失敗にする', () => {
    const result = checkDocuments([{ file: 'a.md', text: withRows('本文。') }], { 'a.md「節」': '' })
    assert.equal(result.exclusionProblems.length, 1)
    assert.match(result.exclusionProblems[0], /節/)
  })

  test('例 18：正しい行のある節でも、別の行の形の誤りは失敗にする', () => {
    for (const bad of ['規則の文（確認方法：自動）', '- 確認方法：自動']) {
      expectOne(withRows(bad), /確認方法/, LINE_OF_EXTRA)
    }
  })

  test('例 19：1 行に確認方法が 2 つあれば、2 つ目を行の途中として失敗にする', () => {
    const text = section('理由：理由を書く。', '確認方法：レビュー。確認方法：自動')
    expectOne(text, /確認方法/, 4)
  })

  test('例 20：半角のコロンと位置の組み合わせは失敗にする', () => {
    for (const bad of ['- 確認方法:レビュー', '| 確認方法 : 自動 |', '（確認方法:レビュー）']) {
      expectOne(withRows(bad), /確認方法/, LINE_OF_EXTRA)
    }
  })

  test('例 21：コロンの前が全角の空白かタブなら失敗にする', () => {
    for (const bad of ['確認方法　：レビュー', '確認方法\t：レビュー']) {
      expectOne(withRows(bad), /確認方法/, LINE_OF_EXTRA)
    }
  })

  test('例 22：番号付きの箇条、* の箇条、引用の確認方法は失敗にする', () => {
    for (const bad of ['1. 確認方法：レビュー', '* 確認方法：レビュー', '> 確認方法：レビュー']) {
      expectOne(withRows(bad), /確認方法/, LINE_OF_EXTRA)
    }
  })

  test('例 23：全角の空白かタブで字下げした確認方法は失敗にする', () => {
    for (const bad of ['　確認方法：レビュー', '\t確認方法：レビュー']) {
      expectOne(withRows(bad), /確認方法/, LINE_OF_EXTRA)
    }
  })

  test('例 24：太字の確認方法は失敗にする', () => {
    for (const bad of ['**確認方法：**レビュー', '- **確認方法**：レビュー']) {
      expectOne(withRows(bad), /確認方法/, LINE_OF_EXTRA)
    }
  })

  test('例 25：表の 2 列目以降のセルの確認方法は失敗にする', () => {
    expectOne(withRows('| 規則 | 確認方法：自動 |'), /確認方法/, LINE_OF_EXTRA)
  })

  test('例 26：見出しの確認方法は失敗にする', () => {
    const text = [section(...rows), '', '### 確認方法：レビュー'].join('\n')
    expectOne(text, /確認方法/, 6)
  })

  test('例 27：行頭が確認方法で始まっても、コロンのない語だけなら行がないとして失敗にする', () => {
    for (const bad of ['確認方法は自動', '確認方法 レビュー']) {
      expectOne(section('理由：理由を書く。', bad), /確認方法/)
    }
  })

  test('例 28：内容のない確認方法の直後が次の見出しか文書の終わりなら失敗にする', () => {
    const followedByHeading = ['## 1. 節', '', '理由：理由を書く。', '確認方法：', '', '## 2. 次', '', ...rows].join('\n')
    expectOne(followedByHeading, /確認方法/)
    const adjacentHeading = ['## 1. 節', '', '理由：理由を書く。', '確認方法：', '## 2. 次', '', ...rows].join('\n')
    expectOne(adjacentHeading, /確認方法/)
    expectOne(section('理由：理由を書く。', '確認方法：'), /確認方法/)
  })

  test('例 29：内容のない確認方法の後がコードブロックなら、その後に箇条があっても失敗にする', () => {
    const text = section('理由：理由を書く。', '確認方法：', '', '```', '例', '```', '', '- 名前：方法')
    expectOne(text, /確認方法/)
  })

  test('例 30：後ろに空白だけがある確認方法の後に箇条がなければ失敗にする', () => {
    expectOne(section('理由：理由を書く。', '確認方法：   ', '', 'ただの文。'), /確認方法/)
  })

  test('例 31：内容のない確認方法の後が箇条の形でなければ失敗にする', () => {
    for (const bad of ['-名前：方法', '  - 名前：方法']) {
      expectOne(section('理由：理由を書く。', '確認方法：', '', bad), /確認方法/)
    }
  })

  test('例 32：後ろに空白だけがある理由の行だけでは失敗にする', () => {
    expectOne(section('理由：  ', '確認方法：レビュー'), /理由/)
  })

  test('例 33：表のセルの理由だけでは失敗にする', () => {
    expectOne(section('| 理由： |', '確認方法：レビュー'), /理由/)
  })

  test('例 34：行頭から始まらない形の理由だけでは失敗にする', () => {
    const variants = ['  理由：〜ため。', '理由:〜ため。', '理由 ：〜ため。', '理由は〜ため。', '例外の理由：〜ため。', '**理由**：〜ため。']
    for (const bad of variants) {
      expectOne(section(bad, '確認方法：レビュー'), /理由/)
    }
  })

  test('例 35：理由がコードブロックかインラインコードの中にだけあれば失敗にする', () => {
    expectOne(section('```', '理由：〜ため。', '```', '確認方法：レビュー'), /理由/)
    expectOne(section('これは `理由：〜ため。` の例。', '確認方法：レビュー'), /理由/)
    expectOne(section('`理由：〜ため。`', '確認方法：レビュー'), /理由/)
  })

  test('例 36：確認方法がインラインコードの中にだけあれば失敗にする', () => {
    expectOne(section('理由：理由を書く。', 'これは `確認方法：レビュー` の例。'), /確認方法/)
  })

  test('例 37：確認方法か理由の行が HTML のコメントの中にだけあれば失敗にする', () => {
    expectOne(section('理由：理由を書く。', '<!-- 確認方法：レビュー -->'), /確認方法/)
    expectOne(section('理由：理由を書く。', '<!--', '確認方法：レビュー', '-->'), /確認方法/)
    expectOne(section('<!-- 理由：理由を書く。 -->', '確認方法：レビュー'), /理由/)
    expectOne(section('<!--', '理由：理由を書く。', '-->', '確認方法：レビュー'), /理由/)
  })

  test('例 38：本文がコードブロックだけの節は、確認方法と理由の両方がないとして失敗にする', () => {
    const problems = findSectionProblems(section('```', '例', '```'))
    assert.equal(problems.length, 2)
    assert.ok(problems.some((problem) => /確認方法/.test(problem.message)))
    assert.ok(problems.some((problem) => /理由/.test(problem.message)))
  })

  test('例 39：親の節に行があっても、本文のある子の節に行がなければ、子を失敗にする', () => {
    const text = ['## 1. 親', '', ...rows, '', '### 1.1 子', '', '本文。'].join('\n')
    const problems = findSectionProblems(text)
    assert.equal(problems.length, 2)
    assert.ok(problems.some((problem) => /確認方法/.test(problem.message)))
    assert.ok(problems.some((problem) => /理由/.test(problem.message)))
  })

  test('例 40：文書の最後の節、深さ 6 の見出しの節、Setext の見出しの節に行がなければ失敗にする', () => {
    const last = ['## 1. 節', '', ...rows, '', '## 2. 最後', '', '本文。'].join('\n')
    assert.equal(findSectionProblems(last).length, 2)
    const deep = ['## 1. 節', '', ...rows, '', '###### 深い節', '', '本文。'].join('\n')
    assert.equal(findSectionProblems(deep).length, 2)
    const setext = ['## 1. 節', '', ...rows, '', 'Setext の節', '-----------', '', '本文。'].join('\n')
    assert.equal(findSectionProblems(setext).length, 2)
  })

  test('例 41：確認方法の直後の行が --- なら、その行が見出しになって行がなくなり失敗にする', () => {
    const text = ['## 1. 節', '', '理由：理由を書く。', '', '確認方法：レビュー', '---'].join('\n')
    expectOne(text, /確認方法/)
  })

  test('例 42：閉じていないコードブロックは失敗にする', () => {
    const text = [withRows('本文。'), '', '```', '閉じない'].join('\n')
    expectOne(text, /コードブロック/)
    const result = checkDocuments([{ file: 'a.md', text }], {})
    assert.equal(result.problems.length, 1)
    assert.equal(result.problems[0].file, 'a.md')
  })

  test('例 43：最初の ## より前の確認方法は、行頭でも箇条でも失敗にする', () => {
    for (const bad of ['確認方法：レビュー', '- 確認方法：レビュー']) {
      const text = ['# 題', '', bad, '', withRows('本文。')].join('\n')
      expectOne(text, /確認方法/, 3)
    }
  })

  test('例 44：除外の節でも、形の誤りは失敗にする', () => {
    const isExcluded = (title) => title === '節'
    for (const bad of ['- 確認方法：レビュー', '確認方法:レビュー']) {
      const problems = findSectionProblems(section(bad), isExcluded)
      assert.equal(problems.length, 1)
      assert.equal(problems[0].line, LINE_OF_EXTRA)
    }
    const emptyWithoutBullet = findSectionProblems(section('確認方法：', '', 'ただの文。'), isExcluded)
    assert.equal(emptyWithoutBullet.length, 1)
  })

  test('例 45：除外のキーが本文のない親の見出しを指していれば失敗にする', () => {
    const text = ['## 1. 親', '', '### 1.1 子', '', ...rows].join('\n')
    const result = checkDocuments([{ file: 'a.md', text }], { 'a.md「親」': '理由' })
    assert.equal(result.exclusionProblems.length, 1)
    assert.match(result.exclusionProblems[0], /親/)
  })

  test('例 46：除外のキーの文書がリンクになければ失敗にする。同じ名前の節が他の文書にあっても除外にならない', () => {
    const result = checkDocuments([{ file: 'a.md', text: section('本文。') }], { 'b.md「節」': '理由' })
    assert.equal(result.exclusionProblems.length, 1)
    assert.match(result.exclusionProblems[0], /b\.md/)
    assert.equal(result.problems.length, 2)
    assert.ok(result.problems.every((problem) => problem.file === 'a.md'))
  })

  test('例 47：除外の理由が空白だけか null なら失敗にする', () => {
    for (const reason of ['   ', null]) {
      const result = checkDocuments([{ file: 'a.md', text: withRows('本文。') }], { 'a.md「節」': reason })
      assert.equal(result.exclusionProblems.length, 1)
      assert.match(result.exclusionProblems[0], /節/)
    }
  })

  test('例 48：冒頭の文のリンクが 0 件か、リンク先のファイルがなければ、対象を読めないとして失敗にする', () => {
    const noLink = ['# 書き方の規則とレビュー', '', 'リンクのない冒頭の文。', '', '## 1. 規則の書き方', '', '[別](other.md)'].join('\n')
    assert.deepEqual(readTargetDocs(noLink), [])
    const noLinkResult = loadDocuments(noLink, () => withRows('本文。'))
    assert.deepEqual(noLinkResult.docs, [])
    assert.equal(noLinkResult.problems.length, 1)

    const review = ['# 書き方の規則とレビュー', '', '規則（[A](a.md)、[B](b.md)）。'].join('\n')
    const read = (file) => {
      if (file === 'b.md') throw new Error('ENOENT')
      return withRows('本文。')
    }
    const missing = loadDocuments(review, read)
    assert.deepEqual(missing.docs.map((doc) => doc.file), ['a.md'])
    assert.equal(missing.problems.length, 1)
    assert.match(missing.problems[0], /b\.md/)
  })
})

describe('検出してはいけない例', () => {
  test('例 1：行頭の確認方法と理由がある節は成功にする', () => {
    expectNone(section(...rows))
  })

  test('例 2：内容のない確認方法の後に空行と箇条が続く節は成功にする', () => {
    expectNone(section('理由：理由を書く。', '確認方法：', '', '- 名前：方法'))
  })

  test('例 3：本文のない親の見出しは節に数えない', () => {
    expectNone(['## 1. 親', '', '### 1.1 子', '', ...rows].join('\n'))
  })

  test('例 4：最初の ## より前にある冒頭の文は節にしない', () => {
    expectNone(['# 題', '', '冒頭の文。', '', section(...rows)].join('\n'))
  })

  test('例 5：インラインコードの確認方法は見ない', () => {
    expectNone(section('`確認方法：` の書き方。', ...rows))
  })

  test('例 6：コードブロックの中の確認方法は見ない', () => {
    expectNone(section(...rows, '', '```', '確認方法：レビュー', '```'))
  })

  test('例 7：コロンのない「確認方法が「レビュー」のもの」は行の形ではない', () => {
    expectNone(section('確認方法が「レビュー」のもの。', ...rows))
  })

  test('例 8：行頭の理由の行に加えて、行の途中の理由があってもよい', () => {
    expectNone(section('理由：理由を書く。', '〜する。理由：別の説明。', '確認方法：レビュー'))
  })

  test('例 9：表の列名「分けない理由」は、行頭から始まる理由の行が別にあれば成功にする', () => {
    expectNone(section('| 分けない理由 |', '|------|', '| 例 |', '', ...rows))
  })

  test('例 10：除外の一覧にあって今の文書にもある節は有効な除外にする', () => {
    const result = checkDocuments([{ file: 'a.md', text: section('本文。') }], { 'a.md「節」': '理由' })
    assert.deepEqual(result, { problems: [], exclusionProblems: [] })
  })

  test('例 11：親の節と子の節の両方に、それぞれ本文と行があれば成功にする', () => {
    expectNone(['## 1. 親', '', ...rows, '', '### 1.1 子', '', ...rows].join('\n'))
  })

  test('例 12：理由と確認方法の間に段落があってもよく、確認方法が先でもよい', () => {
    expectNone(section('理由：理由を書く。', '', '間の段落。', '', '確認方法：レビュー'))
    expectNone(section('確認方法：レビュー', '理由：理由を書く。'))
  })

  test('例 13：内容のない確認方法、箇条、空行、行頭の理由の順は成功にする', () => {
    expectNone(section('確認方法：', '- 名前：方法', '', '理由：理由を書く。'))
  })

  test('例 14：理由の行が 2 つあり、内容のない行と内容のある行があれば成功にする', () => {
    expectNone(section('理由：', '理由：内容。', '確認方法：レビュー'))
  })

  test('例 15：表のセルの理由に加えて、行頭の理由の行があれば成功にする', () => {
    expectNone(section('| 理由： |', '理由：理由を書く。', '確認方法：レビュー'))
  })

  test('例 16：確認方法と箇条の間に空行が 2 つ以上か空白だけの行があっても、空行がなくても成功にする', () => {
    expectNone(section('理由：理由を書く。', '確認方法：', '', '', '- 名前：方法'))
    expectNone(section('理由：理由を書く。', '確認方法：', '   ', '- 名前：方法'))
    expectNone(section('理由：理由を書く。', '確認方法：', '- 名前：方法'))
  })

  test('例 17：見出しの後が空白だけの行で次の見出しが続く親は、本文に数えない', () => {
    expectNone(['## 1. 親', '   ', '### 1.1 子', '', ...rows].join('\n'))
  })

  test('例 18：コードを含む内容も内容として読む', () => {
    expectNone(section('理由：`x`', '確認方法：`knip`'))
  })

  test('例 19：確認方法の内容の中のインラインコードの確認方法は見ない', () => {
    expectNone(section('理由：理由を書く。', '確認方法：自動（`確認方法：` の行を読む）'))
  })

  test('例 20：箇条と表のセルの中のインラインコード、2 つのバッククォートで囲んだ確認方法は見ない', () => {
    expectNone(withRows('- `確認方法：レビュー` の例'))
    expectNone(withRows('| `確認方法：` |'))
    expectNone(withRows('``確認方法：``'))
  })

  test('例 21：~~~ と 4 つのバッククォートで囲んだコードブロックの中は見ない', () => {
    expectNone(section(...rows, '', '~~~', '確認方法：レビュー', '~~~'))
    expectNone(section(...rows, '', '````', '```', '確認方法：レビュー', '```', '````'))
  })

  test('例 22：コードブロックの中の ## 手順 は見出しにしない', () => {
    expectNone(section(...rows, '', '```md', '## 手順', '本文', '```'))
  })

  test('例 23：コードブロックの後に行頭の理由と確認方法がある節は成功にする', () => {
    expectNone(section('```', '例', '```', '', ...rows))
  })

  test('例 24：Setext の親の見出しの後に、本文なしで子の見出しが続けば、下線を本文に数えない', () => {
    expectNone(['# 題', '', '親の題', '------', '', '### 子', '', ...rows].join('\n'))
  })

  test('例 25：文書の終わりに改行がなくても、改行が CRLF でも成功にする', () => {
    const lf = section(...rows)
    assert.equal(lf.endsWith('\n'), false)
    expectNone(lf)
    expectNone(lf.replaceAll('\n', '\r\n'))
    expectNone(section('理由：理由を書く。', '確認方法：', '', '- 名前：方法').replaceAll('\n', '\r\n'))
  })

  test('例 26：行頭の「確認方法が「レビュー」のものは〜」はコロンがないので成功にする', () => {
    expectNone(section('確認方法が「レビュー」のものは〜。', ...rows))
  })

  test('例 27：除外の節は、行がなくても、コロンのない語があっても、行があっても成功にする', () => {
    expectNone(section('本文。'), excludeAll)
    expectNone(section('確認方法は自動。'), excludeAll)
    expectNone(section(...rows), excludeAll)
  })

  test('例 28：除外のキーは、インラインコードや <機能> を含む見出しを書かれたままの文字で照合する', () => {
    const text = ['## 2. `foo` と <機能>', '', '本文。', '', '### 6.1 `x` の <機能>', '', '本文。'].join('\n')
    const exclusions = { 'a.md「`foo` と <機能>」': '理由', 'a.md「`x` の <機能>」': '理由' }
    assert.deepEqual(checkDocuments([{ file: 'a.md', text }], exclusions), { problems: [], exclusionProblems: [] })
  })

  test('例 29：冒頭の文のリンクにない文書は対象に入らない', () => {
    const review = [
      '# 書き方の規則とレビュー',
      '',
      '書き方の規則（[バックエンドの書き方](backend-coding.md)、[コメントの書き方](comments.md)）を書くときの形。',
      '',
      '## 1. 規則の書き方',
      '',
      '[別の文書](other.md) は対象ではない。',
    ].join('\n')
    assert.deepEqual(readTargetDocs(review), ['backend-coding.md', 'comments.md'])
  })

  test('例 30：箇条の確認方法が「- 整形：なし」でも、方法の値は判定しない', () => {
    expectNone(section('理由：理由を書く。', '確認方法：', '', '- 整形：なし'))
  })
})

describe('対象の文書の読み取り', () => {
  test('題の次にある最初の空行でない行のリンク先のファイル名を、出る順に返す', () => {
    const review = [
      '# 書き方の規則とレビュー',
      '',
      '',
      '書き方の規則（[A](a.md)、[B](b.md)）を書くときの形。後ろの[C](c.md)も読む。',
      '',
      '次の段落の [D](d.md) は読まない。',
    ].join('\n')
    assert.deepEqual(readTargetDocs(review), ['a.md', 'b.md', 'c.md'])
  })

  test('改行が CRLF でも読める', () => {
    const review = ['# 題', '', '規則（[A](a.md)、[B](b.md)）。', '', '## 1. 節'].join('\r\n')
    assert.deepEqual(readTargetDocs(review), ['a.md', 'b.md'])
  })
})

describe('節の一覧', () => {
  test('本文のある見出しだけを、番号を除いて返す', () => {
    const text = ['# 題', '', '冒頭。', '', '## 1. 親', '', '### 1.1 子', '', '本文。', '', '## 2. `x` と <機能>', '', '本文。'].join('\n')
    assert.deepEqual(listSections(text), ['子', '`x` と <機能>'])
  })

  test('コードブロックの中の見出しは数えない', () => {
    assert.deepEqual(listSections(section('```', '## 手順', '本文', '```')), ['節'])
  })
})

describe('改行が CRLF の文書', () => {
  test('形の誤りの行番号は、改行が LF のときと同じになる', () => {
    const text = withRows('- 確認方法：レビュー').replaceAll('\n', '\r\n')
    expectOne(text, /確認方法/, LINE_OF_EXTRA)
  })

  test('閉じていないコードブロックは失敗にする', () => {
    const text = [withRows('本文。'), '', '```', '閉じない'].join('\r\n')
    expectOne(text, /コードブロック/)
  })
})
