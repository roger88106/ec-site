// checklist.cjs の確認方法の行の読み方のテスト
const test = require('node:test')
const assert = require('node:assert/strict')
const { isTargetFile, readCheckMethods, reviewItems } = require('./checklist.cjs')

const doc = [
  '# 題名',
  '',
  '## 1 前置き',
  '',
  '規則。',
  '',
  '確認方法：自動（型チェック）',
  '',
  '### 2.1 生成した型で呼ぶ',
  '',
  '規則の本文。',
  '',
  '理由：ため。',
  '',
  '確認方法：レビュー',
  '',
  '## 3 層の依存',
  '',
  '確認方法：',
  '',
  '- 層の間の依存：自動（ArchUnit）',
  '- presentation が依存する domain の範囲：レビュー',
  '- 命名：自動（ArchUnit）・レビュー',
  '',
  '## 4 コードブロック',
  '',
  '```',
  '確認方法：レビュー',
  '```',
].join('\n')

test('確認方法に「レビュー」を含む節だけをチェック項目にする（形 1）', () => {
  const items = reviewItems(readCheckMethods(doc, 'docs/05-開発規則/a.md').entries)
  assert.ok(items.includes('- [ ] 「2.1 生成した型で呼ぶ」：レビュー'))
  assert.ok(!items.some((i) => i.includes('1 前置き')))
})

test('箇条の中の「レビュー」を含むものだけを項目にする（形 2）', () => {
  const items = reviewItems(readCheckMethods(doc, 'docs/05-開発規則/a.md').entries)
  assert.ok(items.includes('- [ ] 「3 層の依存」presentation が依存する domain の範囲：レビュー'))
  assert.ok(items.includes('- [ ] 「3 層の依存」命名：自動（ArchUnit）・レビュー'))
  assert.ok(!items.some((i) => i.includes('層の間の依存')))
})

test('コードブロックの中の確認方法は読まない', () => {
  const items = reviewItems(readCheckMethods(doc, 'docs/05-開発規則/a.md').entries)
  assert.ok(!items.some((i) => i.includes('4 コードブロック')))
  assert.equal(items.length, 3)
})

test('確認方法が自動だけの文書は項目が空', () => {
  const text = '# 題\n\n## 1 a\n\n確認方法：自動（CI）\n'
  assert.deepEqual(reviewItems(readCheckMethods(text, 'x.md').entries), [])
})

test('題名は最初の # の行で、なければファイル名', () => {
  assert.equal(readCheckMethods(doc, 'docs/05-開発規則/a.md').title, '題名')
  assert.equal(readCheckMethods('## 1 a\n\n確認方法：レビュー', 'docs/05-開発規則/b.md').title, 'docs/05-開発規則/b.md')
})

test('CRLF の文書も読める', () => {
  const items = reviewItems(readCheckMethods(doc.replace(/\n/g, '\r\n'), 'a.md').entries)
  assert.equal(items.length, 3)
})

test('確認方法の行がない文書は null', () => {
  assert.equal(readCheckMethods('# 題名\n\n## 別の節\n\n本文', 'docs/05-開発規則/c.md'), null)
})

test('対象は docs/05-開発規則/ の文書だけ', () => {
  assert.equal(isTargetFile('docs/05-開発規則/backend.md'), true)
  assert.equal(isTargetFile('docs/06-手順書/setup.md'), false)
  assert.equal(isTargetFile('README.md'), false)
})

test('#### の節の確認方法は、その #### の見出しの名前で出る', () => {
  const r = readCheckMethods(['# 題', '', '### 2.1 上', '', '#### 2.1.1 下', '', '確認方法：レビュー'].join('\n'), 'a.md')
  assert.deepEqual(reviewItems(r.entries), ['- [ ] 「2.1.1 下」：レビュー'])
})

test('4 つのバッククォートの囲みの中の ``` では閉じず、閉じた後の確認方法は読む', () => {
  const lines = ['# 題', '', '## 1 a', '', '````md', '```', '確認方法：レビュー', '```', '````', '', '確認方法：レビュー']
  const r = readCheckMethods(lines.join('\n'), 'a.md')
  assert.equal(r.entries.length, 1)
})

test('~~~ の囲みの中は読まない', () => {
  const lines = ['# 題', '', '## 1 a', '', '~~~', '確認方法：レビュー', '~~~', '', '確認方法：自動']
  const r = readCheckMethods(lines.join('\n'), 'a.md')
  assert.equal(r.entries.length, 1)
  assert.equal(r.entries[0].check, '自動')
})

test('形 2 の箇条は、空行か箇条でない行で終わり、行の途中の「確認方法：」は読まない', () => {
  const lines = ['# 題', '', '## 1 a', '', '説明の文の `確認方法：` の語。', '', '確認方法：', '', '- 名前：レビュー', '- 置き場所：レビュー', '', '- 外：レビュー']
  const r = readCheckMethods(lines.join('\n'), 'a.md')
  assert.deepEqual(r.entries.map((e) => e.name), ['名前', '置き場所'])
})

test('今の docs/05-開発規則/ の文書は、項目が 56', () => {
  const fs = require('fs'), path = require('path')
  const dir = path.join(__dirname, '..', '..', '..', 'docs', '05-開発規則')
  let items = 0
  for (const name of fs.readdirSync(dir).filter((n) => n.endsWith('.md'))) {
    const r = readCheckMethods(fs.readFileSync(path.join(dir, name), 'utf8'), name)
    if (r) items += reviewItems(r.entries).length
  }
  assert.equal(items, 56)
})
