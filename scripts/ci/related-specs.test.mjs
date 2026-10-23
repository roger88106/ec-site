import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findMissingInStaged, findMissingSpecs, isPrefixUsed, parseRelatedSpecs, specsOfCommit } from './related-specs.mjs'

describe('設計の文書の関連 spec の検査', () => {
  test('概要の表の「関連 spec」の行から、spec の番号を読む', () => {
    const markdown = [
      '| 項目 | 内容 |',
      '|------|------|',
      '| レベル | A |',
      '| 関連 spec | [007](../../specs/007-payment)、[009](../../specs/009-shipping)、[017](../../specs/017-stability) |',
    ].join('\n')
    assert.deepEqual(parseRelatedSpecs(markdown), ['007', '009', '017'])
    assert.equal(parseRelatedSpecs('| 項目 | 内容 |'), null)
  })

  test('関連 spec の行の脚注（[1] など）は、spec の番号として読まない', () => {
    const markdown = '| 関連 spec | [007](../../specs/007-payment)[1]、[9](../../specs/9-shipping) ※[2] |'
    assert.deepEqual(parseRelatedSpecs(markdown), ['007', '9'])
  })

  test('commit のメッセージの 1 行目から、新しい形式のタスク番号を読む。除外のタスクの前置きに合うものは数えない', () => {
    assert.deepEqual(specsOfCommit('order: 注文を直す（T017-03-01）', []), ['017'])
    assert.deepEqual(specsOfCommit('scripts: 検査を追加する（T018-03-05、T018-03-06）', []), ['018'])
    assert.deepEqual(specsOfCommit('docs: 言い回しを直す（T018-05-03）', ['T018-05']), [])
    assert.deepEqual(specsOfCommit('scripts: 漏れを直す（T018-03-12、T018-03-13）', ['T018-03-13']), [])
    assert.deepEqual(specsOfCommit('order: 古い形式（T7803）', []), [])
    assert.deepEqual(specsOfCommit('docs: spec 017 を書く', []), [])
  })

  test('関連 spec の行の [011] と [7] を、別の spec として読む', () => {
    const markdown = '| 関連 spec | [011](../../specs/archive/mvp/011-dev-foundation)、[7](../../specs/7-dev-process) |'
    assert.deepEqual(parseRelatedSpecs(markdown), ['011', '7'])
  })

  test('タスク番号を 0 埋めなしでも読み、T011- は MVP の 011、T7- は 1→10 の 7 として区別する', () => {
    assert.deepEqual(specsOfCommit('scripts: 検査を直す（T7-02-01、T7-02-02）', []), ['7'])
    assert.deepEqual(specsOfCommit('scripts: 検査を直す（T011-02-01、T7-02-02）', []), ['011', '7'])
    assert.deepEqual(specsOfCommit('scripts: 検査を直す（T12-01-01）', []), ['12'])
    assert.notDeepEqual(specsOfCommit('x（T7-01-01）', []), specsOfCommit('x（T007-01-01）', []))
  })

  test('除外の前置きは、新しい形（T7-01）も扱い、0 埋めの有無を区別する', () => {
    assert.deepEqual(specsOfCommit('docs: 言い回し（T7-01-03）', ['T7-01']), [])
    assert.deepEqual(specsOfCommit('docs: 言い回し（T7-01-03）', ['T007-01']), ['7'])
    assert.deepEqual(specsOfCommit('docs: 言い回し（T7-02-03）', ['T7-01']), ['7'])
  })

  test('関連 spec の [7] は T7- の commit に合い、[007] には合わない', () => {
    assert.deepEqual(findMissingSpecs(['7'], ['docs: 追加（T7-03-01）'], []), [])
    assert.deepEqual(findMissingSpecs(['007'], ['docs: 追加（T7-03-01）'], []), ['7'])
    assert.deepEqual(findMissingSpecs(['7'], ['payment: 追加（T007-03-01）'], []), ['007'])
  })

  test('文書を変えた commit の spec のうち、関連 spec にないものを返す', () => {
    const subjects = ['payment: 期限切れを直す（T017-06-01）', 'payment: 支払を追加する（T0701）', 'docs: 言い回し（T018-05-03）', 'payment: 構成を追加する（T018-04-04）']
    assert.deepEqual(findMissingSpecs(['007', '017'], subjects, ['T018-05']), ['018'])
    assert.deepEqual(findMissingSpecs(['007', '017', '018'], subjects, ['T018-05']), [])
  })

  test('ステージした設計の文書を、次の commit のメッセージの 1 行目と照合する', () => {
    const payment = { file: 'docs/design/modules/payment.md', markdown: '| 関連 spec | [007](../../specs/007-payment)、[017](../../specs/017-stability) |' }
    const guide = { file: 'docs/guides/setup.md', markdown: '# 開発環境の起動' }
    assert.deepEqual(findMissingInStaged([payment, guide], 'payment: 構成を追加する（T019-01-07）', []), [
      { file: 'docs/design/modules/payment.md', spec: '019' },
    ])
    assert.deepEqual(findMissingInStaged([payment], 'payment: 期限切れを直す（T017-06-01）', []), [])
    assert.deepEqual(findMissingInStaged([payment], 'docs: 言い回し（T019-05-03）', ['T019-05']), [])
    assert.deepEqual(findMissingInStaged([payment], 'docs: 用語をそろえる', []), [])
  })

  test('前置きで始まるタスク番号が、どれかの commit にあれば、その除外はまだ使われている', () => {
    const subjects = ['docs: 言い回し（T018-05-02）', 'payment: 構成を追加する（T019-01-07）']
    assert.equal(isPrefixUsed('T018-05', subjects), true)
    assert.equal(isPrefixUsed('T019-01-07', subjects), true)
    assert.equal(isPrefixUsed('T018-06', subjects), false)
    assert.equal(isPrefixUsed('T018-05', ['docs: T018-050 の話']), false)
    assert.equal(isPrefixUsed('T018-05', []), false)
  })
})
