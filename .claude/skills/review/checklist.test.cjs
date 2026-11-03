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
