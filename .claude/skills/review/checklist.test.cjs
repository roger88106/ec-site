// checklist.cjs の表の読み方のテスト
const test = require('node:test')
const assert = require('node:assert/strict')
const { readRuleTable, reviewItems } = require('./checklist.cjs')

const doc = [
  '# 題名',
  '',
  '## 規則の一覧',
  '',
  '| 節 | 規則 | 確認方法 |',
  '|----|------|----------|',
  '| 1 | 規則 A | レビュー |',
  '| 2 | 規則 B | CI |',
  '| 3 | 規則 C | CI・レビュー |',
  '',
  '## 次の節',
  '| 9 | 対象外 | レビュー |',
].join('\n')

test('表の見出しの行（節）と - の区切りの行は読み飛ばす', () => {
  const table = readRuleTable(doc, 'docs/a.md')
  assert.deepEqual(table.rows.map((r) => r.section), ['1', '2', '3'])
})

test('次の節の行は読まない', () => {
  const table = readRuleTable(doc, 'docs/a.md')
  assert.ok(!table.rows.some((r) => r.section === '9'))
})

test('題名は最初の # の行で、なければファイル名', () => {
  assert.equal(readRuleTable(doc, 'docs/a.md').title, '題名')
  assert.equal(readRuleTable('## 規則の一覧\n| 1 | a | b |', 'docs/b.md').title, 'docs/b.md')
})

test('確認方法に「レビュー」がある行だけをチェック項目にする', () => {
  const items = reviewItems(readRuleTable(doc, 'docs/a.md').rows)
  assert.deepEqual(items, [
    '- [ ] 第 1 節：規則 A（レビュー）',
    '- [ ] 第 3 節：規則 C（CI・レビュー）',
  ])
})

test('確認方法に「レビュー」がない表は項目が空', () => {
  const text = '## 規則の一覧\n| 節 | 規則 | 確認方法 |\n|---|---|---|\n| 1 | a | CI |'
  assert.deepEqual(reviewItems(readRuleTable(text, 'x.md').rows), [])
})

test('CRLF の文書も読める', () => {
  const table = readRuleTable(doc.replace(/\n/g, '\r\n'), 'docs/a.md')
  assert.equal(table.rows.length, 3)
})

test('「規則の一覧」の節がない文書は null', () => {
  assert.equal(readRuleTable('# 題名\n\n## 別の節\n| 1 | a | レビュー |', 'docs/c.md'), null)
})
