import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { describe, test } from 'node:test'
import { checkDocuments, findSectionProblems, listSections, RULE_DOCS } from './rule-sections.mjs'

// 正しい形の 2 行。節の末尾に置く
const rows = ['理由：理由を書く。', '確認方法：レビュー']

// 見出しの後に空行を置き、本文を続ける。本文の最初の行は 3 行目になる
const section = (...body) => ['## 節', '', ...body].join('\n')

// 3 行目に例の行を置き、正しい形の 2 行を続ける
const withRows = (...extra) => section(...extra, '', ...rows)

function expectOne(text, pattern, line) {
  const problems = findSectionProblems(text)
  assert.equal(problems.length, 1, JSON.stringify(problems))
  assert.match(problems[0].message, pattern)
  if (line !== undefined) assert.equal(problems[0].line, line)
}

function expectNone(text, isExcluded) {
  assert.deepEqual(findSectionProblems(text, isExcluded), [])
}

const excludeAll = () => true

describe('検出すべき例', () => {
  test('本文があり、確認方法の行がない節と、理由の行がない節は失敗にする', () => {
    expectOne(section('理由：理由を書く。'), /確認方法：の行/, 1)
    expectOne(section('確認方法：レビュー'), /理由：の行/, 1)
  })

  test('行頭から全角のコロンで書いていない確認方法だけの節は、確認方法の行がないとして失敗にする', () => {
    const variants = ['規則の文（確認方法：レビュー）', '- 確認方法：レビュー', '| 確認方法：自動 |', '  確認方法：レビュー', '確認方法:レビュー', '確認方法 ：レビュー', '**確認方法：**レビュー', '確認方法は自動']
    for (const bad of variants) expectOne(section('理由：理由を書く。', bad), /確認方法：の行/)
  })

  test('同じ節に確認方法の行が 2 つあれば失敗にする', () => {
    expectOne(section('確認方法：レビュー', '確認方法：自動', '', '理由：理由を書く。'), /2 つ以上/)
  })

  test('内容がないか、行頭から始まらない理由の行だけでは失敗にする', () => {
    const variants = ['理由：', '理由：  ', '- 理由：〜ため。', '〜する。理由：〜ため。', '| 理由： |', '  理由：〜ため。', '理由:〜ため。', '**理由**：〜ため。']
    for (const bad of variants) expectOne(section(bad, '確認方法：レビュー'), /理由/)
  })

  test('内容のない確認方法の後の、最初の空行でない行が - の箇条でなければ失敗にする', () => {
    for (const after of [['', 'ただの文。'], ['', '-名前：方法'], ['', '  - 名前：方法'], ['', '```', '例', '```', '', '- 名前：方法'], []]) {
      expectOne(section('理由：理由を書く。', '確認方法：', ...after), /箇条/)
    }
    expectOne(['## 節', '', '理由：理由を書く。', '確認方法：', '## 次', '', ...rows].join('\n'), /箇条/)
  })

  test('確認方法か理由が、コードブロック、インラインコード、HTML のコメントの中にだけあれば失敗にする', () => {
    expectOne(section('理由：理由を書く。', '', '```', '確認方法：レビュー', '```'), /確認方法/)
    expectOne(section('理由：理由を書く。', 'これは `確認方法：レビュー` の例。'), /確認方法/)
    expectOne(section('理由：理由を書く。', '<!--', '確認方法：レビュー', '-->'), /確認方法/)
    expectOne(section('`理由：〜ため。`', '確認方法：レビュー'), /理由/)
    expectOne(section('<!-- 理由：理由を書く。 -->', '確認方法：レビュー'), /理由/)
  })

  test('親と子の節は別に見る。本文のある節の全て（最後の節、深さ 6、Setext）に行が必要だ', () => {
    expectOne(['## 親', '', '理由：理由を書く。', '', '### 子', '', ...rows].join('\n'), /「親」に確認方法：の行/, 1)
    const cases = [
      ['## 節', '', ...rows, '', '### 子', '', '本文。', '「子」'],
      ['## 節', '', ...rows, '', '###### 深い節', '', '本文。', '「深い節」'],
      ['## 節', '', ...rows, '', 'Setext の節', '-----------', '', '本文。', '「Setext の節」'],
    ]
    for (const item of cases) {
      const name = item.pop()
      const problems = findSectionProblems(item.join('\n'))
      assert.equal(problems.length, 2)
      assert.ok(problems.every((problem) => problem.line === 6 && problem.message.includes(name)), JSON.stringify(problems))
    }
  })

  test('確認方法の直後の行が --- なら、その行が Setext の見出しになり、節の確認方法の行がなくなる', () => {
    expectOne(['## 節', '', '理由：理由を書く。', '', '確認方法：レビュー', '---'].join('\n'), /確認方法：の行/, 1)
  })

  test('閉じていないコードブロックは失敗にする（改行が CRLF でも）', () => {
    const text = [withRows('本文。'), '', '```', '閉じない'].join('\n')
    expectOne(text, /コードブロック/)
    expectOne(text.replaceAll('\n', '\r\n'), /コードブロック/)
  })

  test('除外の節でも、内容のない確認方法の後の箇条と、2 つの確認方法の行は失敗にする', () => {
    const isExcluded = (title) => title === '節'
    const emptyWithoutBullet = findSectionProblems(section('確認方法：', '', 'ただの文。'), isExcluded)
    assert.equal(emptyWithoutBullet.length, 1)
    assert.match(emptyWithoutBullet[0].message, /箇条/)
    const twoRows = findSectionProblems(section('確認方法：レビュー', '確認方法：自動'), isExcluded)
    assert.equal(twoRows.length, 1)
    assert.match(twoRows[0].message, /2 つ以上/)
  })

  test('理由がないか空白だけか null の除外、今の文書にない節（本文のない親、他の文書の節）の除外は失敗にする', () => {
    const doc = { file: 'a.md', text: ['## 親', '', '### 子', '', ...rows].join('\n') }
    for (const reason of ['', '   ', null]) {
      const result = checkDocuments([doc], { 'a.md「子」': reason })
      assert.equal(result.exclusionProblems.length, 1)
      assert.match(result.exclusionProblems[0], /理由がない/)
    }
    for (const key of ['a.md「親」', 'a.md「消えた節」', 'b.md「子」']) {
      const result = checkDocuments([doc], { [key]: '理由' })
      assert.equal(result.exclusionProblems.length, 1)
      assert.match(result.exclusionProblems[0], /不要/)
    }
  })

  test('節が 1 つもない文書（空のファイル、題だけの文書）は失敗にする', () => {
    for (const text of ['', '# 題', '# 題\n\n冒頭の文。']) {
      const result = checkDocuments([{ file: 'a.md', text }], {})
      assert.equal(result.problems.length, 1, text)
      assert.match(result.problems[0].message, /節が 1 つもない/)
    }
  })
})

describe('検出してはいけない例', () => {
  test('行頭の確認方法と理由がある節は、順と間の段落によらず成功にする', () => {
    expectNone(section(...rows))
    expectNone(section('確認方法：レビュー', '理由：理由を書く。'))
    expectNone(section('理由：理由を書く。', '', '間の段落。', '', '確認方法：レビュー'))
    expectNone(section('理由：`x`', '確認方法：`knip`'))
  })

  test('内容のない確認方法の後に箇条が続く節は、間の空行によらず成功にする', () => {
    expectNone(section('理由：理由を書く。', '確認方法：', '', '- 名前：方法'))
    expectNone(section('理由：理由を書く。', '確認方法：', '   ', '', '- 名前：方法'))
    expectNone(section('確認方法：', '- 名前：方法', '', '理由：理由を書く。'))
  })

  test('本文のない親の見出しと、最初の ## より前の冒頭の文は節にしない', () => {
    expectNone(['## 親', '   ', '### 子', '', ...rows].join('\n'))
    expectNone(['# 題', '', '冒頭の文。', '', section(...rows)].join('\n'))
    expectNone(['# 題', '', '親の題', '------', '', '### 子', '', ...rows].join('\n'))
  })

  test('インラインコードとコードブロックの中の確認方法と見出しは見ない', () => {
    expectNone(section('`確認方法：` の書き方。', ...rows))
    expectNone(section(...rows, '', '~~~', '確認方法：レビュー', '~~~'))
    expectNone(section(...rows, '', '```md', '## 手順', '本文', '```'))
  })

  test('正しい行に加えて、行の途中の理由・確認方法や、形の崩れた行があってもよい（形はレビューで見る）', () => {
    expectNone(section('理由：理由を書く。', '〜する。理由：別の説明。', '確認方法：レビュー'))
    expectNone(withRows('- 確認方法：レビュー'))
    expectNone(section('| 分けない理由 |', '|------|', '| 例 |', '', ...rows))
  })

  test('除外の節は、行がなくても、行があっても成功にする。キーは見出しを書かれたままの文字で照合する', () => {
    expectNone(section('本文。'), excludeAll)
    expectNone(section(...rows), excludeAll)
    const text = ['## `foo` と <機能>', '', '本文。'].join('\n')
    assert.deepEqual(checkDocuments([{ file: 'a.md', text }], { 'a.md「`foo` と <機能>」': '理由' }), { problems: [], exclusionProblems: [] })
  })

  test('改行が CRLF でも、文書の終わりに改行がなくても成功にする', () => {
    expectNone(section(...rows).replaceAll('\n', '\r\n'))
    expectNone(section('理由：理由を書く。', '確認方法：', '', '- 名前：方法').replaceAll('\n', '\r\n'))
  })
})

describe('節の一覧と対象の文書', () => {
  test('本文のある見出しだけを返し、番号でない先頭の数字は除かない', () => {
    const text = ['# 題', '', '冒頭。', '', '## 親', '', '### 子', '', '本文。', '', '## 2 つの節', '', '本文。'].join('\n')
    assert.deepEqual(listSections(text), ['子', '2 つの節'])
  })

  test('対象の文書は、書き方の規則とレビューの冒頭の文のリンクと同じ', () => {
    const review = readFileSync(new URL('../../docs/05-開発規則/review.md', import.meta.url), 'utf8')
    const sentence = review.split(/\r?\n/).find((line) => line.startsWith('書き方の規則（'))
    const linked = [...sentence.matchAll(/\]\(([^)]+)\)/g)].map((match) => `docs/05-開発規則/${match[1]}`)
    assert.deepEqual(RULE_DOCS, linked)
  })
})
