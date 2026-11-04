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

/**
 * 文書の本文から、異常の行の番号と理由を読む。
 * @param {string[]} lines 文書の行
 * @returns {{line: number, message: string}[]} 異常の一覧
 */
function errorsOf(lines) {
  const r = readCheckMethods(lines.join('\n'), 'docs/05-開発規則/e.md')
  return r ? r.errors : []
}

test('異常：確認方法の行が半角のコロン、行頭の空白、表の行のとき', () => {
  assert.equal(errorsOf(['# 題', '', '## 1 a', '', '確認方法: レビュー']).length, 1)
  assert.equal(errorsOf(['# 題', '', '## 1 a', '', '  確認方法：レビュー']).length, 1)
  assert.equal(errorsOf(['# 題', '', '## 1 a', '', '| 規則 | 確認方法：レビュー |']).length, 1)
  assert.equal(errorsOf(['# 題', '', '## 1 a', '', '確認方法：', '', '- 整形: なし']).length, 1)
})

test('異常：形 1 の行の後に箇条が続くとき', () => {
  const errors = errorsOf(['# 題', '', '## 1 a', '', '確認方法：自動（ArchUnit）。ほかは次のとおり', '', '- 置き場所：レビュー'])
  assert.equal(errors.length, 1)
  assert.equal(errors[0].line, 7)
})

test('異常：形 2 の箇条の途中の形の違う行は、その後の箇条を落とさず報告する', () => {
  const lines = [
    '# 題', '', '## 1 a', '', '確認方法：', '',
    '- 名前：自動', '* 置き場所：レビュー', '  （補足）', '箇条でない文', '- 最後：レビュー',
  ]
  assert.deepEqual(errorsOf(lines).map((e) => e.line), [8, 9, 10])
  assert.equal(readCheckMethods(lines.join('\n'), 'a.md').entries.length, 2)
})

test('異常：形 2 の確認方法：の後に箇条が 1 つもないとき', () => {
  assert.equal(errorsOf(['# 題', '', '## 1 a', '', '確認方法：', '', '文だけ']).length, 1)
  assert.equal(errorsOf(['# 題', '', '## 1 a', '', '確認方法：']).length, 1)
})

test('#### の節の確認方法は、その #### の見出しの名前で出る', () => {
  const r = readCheckMethods(['# 題', '', '### 2.1 上', '', '#### 2.1.1 下', '', '確認方法：レビュー'].join('\n'), 'a.md')
  assert.deepEqual(reviewItems(r.entries), ['- [ ] 「2.1.1 下」：レビュー'])
  assert.deepEqual(r.errors, [])
})

test('4 つのバッククォートの囲みの中の ``` では閉じず、閉じた後の確認方法は読む', () => {
  const lines = ['# 題', '', '## 1 a', '', '````md', '```', '確認方法：レビュー', '```', '````', '', '確認方法：レビュー']
  const r = readCheckMethods(lines.join('\n'), 'a.md')
  assert.equal(r.entries.length, 1)
  assert.deepEqual(r.errors, [])
})

test('~~~ の囲みの中は読まない', () => {
  const lines = ['# 題', '', '## 1 a', '', '~~~', '確認方法：レビュー', '~~~', '', '確認方法：自動']
  const r = readCheckMethods(lines.join('\n'), 'a.md')
  assert.equal(r.entries.length, 1)
  assert.equal(r.entries[0].check, '自動')
})

test('異常：閉じないコードブロックがあるとき', () => {
  const errors = errorsOf(['# 題', '', '## 1 a', '', '確認方法：レビュー', '', '```', '本文'])
  assert.equal(errors.length, 1)
  assert.equal(errors[0].line, 7)
})

test('行の途中の「確認方法：」は異常にしない', () => {
  const r = readCheckMethods(['# 題', '', '## 1 a', '', '説明の文の `確認方法：` の語。', '', '確認方法：レビュー'].join('\n'), 'a.md')
  assert.deepEqual(r.errors, [])
})

test('今の docs/05-開発規則/ の文書は、異常が 0 で、項目が 54', () => {
  const fs = require('fs'), path = require('path')
  const dir = path.join(__dirname, '..', '..', '..', 'docs', '05-開発規則')
  let errors = 0
  let items = 0
  for (const name of fs.readdirSync(dir).filter((n) => n.endsWith('.md'))) {
    const r = readCheckMethods(fs.readFileSync(path.join(dir, name), 'utf8'), name)
    if (!r) continue
    errors += r.errors.length
    items += reviewItems(r.entries).length
  }
  assert.equal(errors, 0)
  assert.equal(items, 54)
})
