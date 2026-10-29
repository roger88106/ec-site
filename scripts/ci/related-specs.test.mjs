import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { checkRelatedExclusions, findBadRelatedLinks, findMissingInStaged, findMissingSpecs, historyLogArgs, isPrefixUsed, parseRelatedSpecs, specsOfCommit } from './related-specs.mjs'

describe('設計の文書の関連 spec の検査', () => {
  test('概要の表の「関連 spec」の行から、spec の番号を読む', () => {
    const markdown = [
      '| 項目 | 内容 |',
      '|------|------|',
      '| レベル | A |',
      '| 関連 spec | [007](../08-spec/007-payment)、[009](../08-spec/009-shipping)、[017](../08-spec/017-stability) |',
    ].join('\n')
    assert.deepEqual(parseRelatedSpecs(markdown), ['007', '009', '017'])
    assert.equal(parseRelatedSpecs('| 項目 | 内容 |'), null)
  })

  test('関連 spec の行の脚注（[1] など）は、spec の番号として読まない', () => {
    const markdown = '| 関連 spec | [007](../08-spec/007-payment)[1]、[9](../08-spec/9-shipping) ※[2] |'
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
    const markdown = '| 関連 spec | [011](../08-spec/archive/mvp/011-dev-foundation)、[7](../08-spec/7-dev-process) |'
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
    const payment = { file: 'docs/03-機能仕様/payment.md', markdown: '| 関連 spec | [007](../08-spec/007-payment)、[017](../08-spec/017-stability) |' }
    const guide = { file: 'docs/06-手順書/setup.md', markdown: '# 開発環境の起動' }
    assert.deepEqual(findMissingInStaged([payment, guide], 'payment: 構成を追加する（T019-01-07）', []), [
      { file: 'docs/03-機能仕様/payment.md', spec: '019' },
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

  test('merge の途中なら、除外の要否を見る履歴に merge の相手の履歴も含める', () => {
    assert.deepEqual(historyLogArgs(false), ['log', '--format=%s'])
    assert.deepEqual(historyLogArgs(true), ['log', '--format=%s', 'HEAD', 'MERGE_HEAD'])
  })
})

describe('関連 spec のリンクの形', () => {
  const dirs = new Set(['docs/08-spec/archive/mvp/007-payment', 'docs/08-spec/archive/1to10/155-backend-structure', 'docs/08-spec/185-new-checks'])
  const isDirectory = (path) => dirs.has(path)
  const check = (row) => findBadRelatedLinks('docs/03-機能仕様/payment.md', `| 関連 spec | ${row} |`, isDirectory)

  test('spec のディレクトリを指すリンクは、アーカイブの下も進行中の spec も通る', () => {
    assert.deepEqual(check('[007](../08-spec/archive/mvp/007-payment)、[155](../08-spec/archive/1to10/155-backend-structure)、[185](../08-spec/185-new-checks)'), [])
  })

  test('spec.md などのファイルを指すリンクは失敗にする', () => {
    assert.equal(check('[007](../08-spec/archive/mvp/007-payment/spec.md)').length, 1)
  })

  test('存在しないディレクトリ（アーカイブへの移動前の古いパスなど）を指すリンクは失敗にする', () => {
    assert.equal(check('[007](../08-spec/007-payment)').length, 1)
  })

  test('docs/08-spec/ の下でないディレクトリを指すリンクは失敗にする', () => {
    const other = (row) => findBadRelatedLinks('docs/03-機能仕様/payment.md', `| 関連 spec | ${row} |`, () => true)
    assert.equal(other('[007](../06-手順書/007-payment)').length, 1)
    assert.equal(other('[007](../08-spec/archive/007-payment)').length, 1)
    assert.deepEqual(other('[007](../08-spec/archive/mvp/007-payment)'), [])
  })

  test('リンクの番号と、ディレクトリ名の番号が違うものは失敗にする', () => {
    assert.equal(check('[155](../08-spec/archive/mvp/007-payment)').length, 1)
  })

  test('関連 spec の行がない文書は見ない', () => {
    assert.deepEqual(findBadRelatedLinks('docs/06-手順書/setup.md', '# 開発環境の起動', isDirectory), [])
  })
})

describe('除外の一覧の誤りの扱い', () => {
  const exclusions = { 'T018-05': '言い回し', 'T099-01': '当たる commit がない', 'T018-06': ' ' }
  const isNeeded = (prefix) => prefix !== 'T099-01'

  test('CI では、当たる commit がない除外を失敗にする', () => {
    const { failures, warnings } = checkRelatedExclusions(exclusions, isNeeded, true)
    assert.equal(failures.some((line) => line.includes('T099-01')), true)
    assert.deepEqual(warnings, [])
  })

  test('手元では、当たる commit がない除外を「警告：」で始まる行にし、失敗にしない', () => {
    const { failures, warnings } = checkRelatedExclusions(exclusions, isNeeded, false)
    assert.equal(failures.some((line) => line.includes('T099-01')), false)
    assert.equal(warnings.length, 1)
    assert.equal(warnings[0].startsWith('警告：'), true)
    assert.equal(warnings[0].includes('T099-01'), true)
  })

  test('理由のない除外は、手元でも失敗にする', () => {
    const { failures } = checkRelatedExclusions(exclusions, isNeeded, false)
    assert.equal(failures.some((line) => line.includes('T018-06') && line.includes('理由がない')), true)
  })
})
