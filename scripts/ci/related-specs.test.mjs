import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findMissingSpecs, parseRelatedSpecs, specsOfCommit } from './related-specs.mjs'

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

  test('commit のメッセージの 1 行目から、新しい形式のタスク番号を読む。除外のタスクの前置きに合うものは数えない', () => {
    assert.deepEqual(specsOfCommit('order: 注文を直す（T017-03-01）', []), ['017'])
    assert.deepEqual(specsOfCommit('scripts: 検査を追加する（T018-03-05、T018-03-06）', []), ['018'])
    assert.deepEqual(specsOfCommit('docs: 言い回しを直す（T018-05-03）', ['T018-05']), [])
    assert.deepEqual(specsOfCommit('scripts: 漏れを直す（T018-03-12、T018-03-13）', ['T018-03-13']), [])
    assert.deepEqual(specsOfCommit('order: 古い形式（T7803）', []), [])
    assert.deepEqual(specsOfCommit('docs: spec 017 を書く', []), [])
  })

  test('文書を変えた commit の spec のうち、関連 spec にないものを返す', () => {
    const subjects = ['payment: 期限切れを直す（T017-06-01）', 'payment: 支払を追加する（T0701）', 'docs: 言い回し（T018-05-03）', 'payment: 構成を追加する（T018-04-04）']
    assert.deepEqual(findMissingSpecs(['007', '017'], subjects, ['T018-05']), ['018'])
    assert.deepEqual(findMissingSpecs(['007', '017', '018'], subjects, ['T018-05']), [])
  })
})
