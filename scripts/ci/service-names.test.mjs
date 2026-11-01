// service-names.mjs のテスト。
//
// テストが前提にする service-names.mjs の export:
//   SERVICE_NAMES: string[]
//     サービス固有の名前の一覧（検査の中の 1 か所）。
//     次の名前を含む。Claude、Claude Code、Opus、Sonnet、Haiku。
//     さらに、Codex、Gemini、Copilot、Cursor を含む。
//   isTargetFile(path: string): boolean
//     リポジトリのルートからの相対パス（/ 区切り）が対象なら true。
//     対象は docs/constitution.md と docs/05-開発規則/ の下の *.md。
//   findServiceNameProblems(files: { path: string, text: string }[]): string[]
//     files は対象のファイルのパスと本文。名前が許可する形の外にある行ごとに 'パス:行番号'（行番号は 1 始まり）を、
//     ファイルの順・行の順に返す。1 行に複数あっても、その行は 1 回だけ返す。違反がなければ []。
//     許可する形は 3 つ。1 つ目は、パスや識別子の字（/ . _ - \ @ $ =）を含むインラインのコードスパン（`...`）の中。
//     2 つ目は、Markdown のリンク先（](...) の括弧の中の、最初の空白より前か <...> の中）。3 つ目は、コードブロック（``` で囲んだ行）の中。
//     リンクの表示の文字（[...] の中）とリンクのタイトル（"..."）は本文として扱う。名前は大文字小文字を区別せず、名前の境界で判定する。
//   findUnclosedFences(files: { path: string, text: string }[]): string[]
//     囲みを開いたままファイルが終わったファイルごとに、開きの行を `ファイル:行 内容（理由）` の形で返す。

import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { SERVICE_NAMES, findServiceNameProblems, findUnclosedFences, isTargetFile } from './service-names.mjs'

const check = (text, path = 'docs/05-開発規則/x.md') => findServiceNameProblems([{ path, text }])

describe('サービス固有の名前の検査', () => {
  test('一覧は主な名前を含む', () => {
    for (const name of ['Claude', 'Claude Code', 'Opus', 'Sonnet', 'Haiku', 'Codex', 'Gemini', 'Copilot', 'Cursor']) {
      assert.ok(SERVICE_NAMES.includes(name), name)
    }
  })

  test('本文の行の名前は、ファイル:行 を返す', () => {
    const text = ['# 題', '', '今は Claude Code を使う。', '普通の行。', 'Opus で実装する。'].join('\n')
    assert.deepEqual(check(text), ['docs/05-開発規則/x.md:3', 'docs/05-開発規則/x.md:5'])
  })

  test('一覧の名前は全て違反にする', () => {
    for (const name of SERVICE_NAMES) {
      assert.deepEqual(check(`使う：${name} です`), ['docs/05-開発規則/x.md:1'], name)
    }
  })

  test('大文字と小文字の違いも違反にする', () => {
    assert.deepEqual(check('claude と CLAUDE と sonnet'), ['docs/05-開発規則/x.md:1'])
    assert.deepEqual(check('GEMINI'), ['docs/05-開発規則/x.md:1'])
  })

  test('1 行に複数あっても、その行は 1 回だけ返す', () => {
    assert.deepEqual(check('Claude と Codex と Gemini'), ['docs/05-開発規則/x.md:1'])
  })

  test('複数のファイルは、ファイルの順に返す', () => {
    const files = [
      { path: 'docs/constitution.md', text: 'ok\nCursor' },
      { path: 'docs/05-開発規則/a.md', text: 'Haiku' },
    ]
    assert.deepEqual(findServiceNameProblems(files), ['docs/constitution.md:2', 'docs/05-開発規則/a.md:1'])
  })

  test('インラインのコードスパンの中は違反にしない', () => {
    assert.deepEqual(check('`.claude/skills/` を読む。`CLAUDE.md` もある。'), [])
  })

  test('コードスパンの外に名前があれば、同じ行でも違反にする', () => {
    assert.deepEqual(check('`.claude/` と Claude'), ['docs/05-開発規則/x.md:1'])
  })

  test('Markdown のリンク先のパスの中は違反にしない', () => {
    assert.deepEqual(check('[評価の一覧](../../.claude/skills/review/evals/ja-proofread.md) を見る。'), [])
  })

  test('リンクの表示文字にある名前は、本文として違反にする', () => {
    assert.deepEqual(check('[Claude Code の文書](https://example.com/docs)'), ['docs/05-開発規則/x.md:1'])
  })

  test('リンクの表示の文字が違反で、リンク先が名前を含んでも、違反は 1 行 1 回', () => {
    assert.deepEqual(check('[Codex](../../.claude/codex.md)'), ['docs/05-開発規則/x.md:1'])
  })

  test('コードブロックの中の名前は違反にしない', () => {
    const text = ['```', '├── .claude/', 'Claude', '```'].join('\n')
    assert.deepEqual(check(text), [])
  })

  test('コードブロックの外の名前は違反にする', () => {
    const text = ['Claude', '```', 'Claude', '```', 'Opus'].join('\n')
    assert.deepEqual(check(text), ['docs/05-開発規則/x.md:1', 'docs/05-開発規則/x.md:5'])
  })

  test('長い囲みの中の短い囲みの字では閉じず、囲みの外の名前は違反にする', () => {
    const text = ['````', '```', 'Claude', '````', 'Opus'].join('\n')
    assert.deepEqual(check(text), ['docs/05-開発規則/x.md:5'])
  })

  test('2 つのバッククォートで囲んだコードスパンより後ろにある、本文の名前は違反にする', () => {
    assert.deepEqual(check('``a`b`` を使う。Claude を使う。`x`'), ['docs/05-開発規則/x.md:1'])
  })

  test('2 つのバッククォートで囲んだ中にあるパスは、違反にしない', () => {
    assert.deepEqual(check('``.claude/`x`/`` を読む'), [])
  })

  test('コードスパンの中身が名前だけのときは、本文として違反にする', () => {
    assert.deepEqual(check('`Claude Code` の設定を使う'), ['docs/05-開発規則/x.md:1'])
    assert.deepEqual(check('` Opus ` で実装する'), ['docs/05-開発規則/x.md:1'])
    assert.deepEqual(check('``sonnet`` で実装する'), ['docs/05-開発規則/x.md:1'])
  })

  test('コードスパンの中身が、名前に語を足しただけで、パスや識別子の字を含まないときは、本文として違反にする', () => {
    assert.deepEqual(check('`Claude Code の設定` を使う'), ['docs/05-開発規則/x.md:1'])
    assert.deepEqual(check('`Opus 4` で実装する'), ['docs/05-開発規則/x.md:1'])
  })

  test('パスや識別子の字（/ . _ - など）を含むコードスパンの中の名前は、違反にしない', () => {
    assert.deepEqual(check('`.claude/` と `CLAUDE.md` と `claude -p` と `CLAUDE_CODE_X` を使う'), [])
  })

  test('リンクのタイトルにある名前は、本文として違反にする', () => {
    assert.deepEqual(check('[文書](../x.md "Claude の文書") を見る'), ['docs/05-開発規則/x.md:1'])
    assert.deepEqual(check("[文書](<../a b.md> 'Opus') を見る"), ['docs/05-開発規則/x.md:1'])
  })

  test('山括弧で囲んだリンク先の中の名前は、違反にしない', () => {
    assert.deepEqual(check('[文書](<../../.claude/a b.md>) を見る'), [])
  })

  test('囲みを開いたままファイルが終わったら、開きの行を、閉じていないコードブロックとして失敗にする', () => {
    const problems = findUnclosedFences([{ path: 'docs/05-開発規則/x.md', text: ['本文', '```', 'Claude'].join('\n') }])
    assert.equal(problems.length, 1)
    assert.match(problems[0], /^docs\/05-開発規則\/x\.md:2 .*コードブロックが閉じていない/)
  })

  test('開きより短い閉じと、違う字の閉じは、閉じていないコードブロックとして失敗にする', () => {
    const shorter = ['````', 'x', '```', 'Claude'].join('\n')
    const otherChar = ['```', 'x', '~~~', 'Claude'].join('\n')
    for (const text of [shorter, otherChar]) {
      const problems = findUnclosedFences([{ path: 'docs/05-開発規則/x.md', text }])
      assert.equal(problems.length, 1, text)
      assert.match(problems[0], /x\.md:1 /)
    }
  })

  test('閉じたコードブロックは、閉じていないコードブロックの検査が通る', () => {
    assert.deepEqual(findUnclosedFences([{ path: 'docs/05-開発規則/x.md', text: ['```', 'Claude', '```', '~~~', 'x', '~~~~'].join('\n') }]), [])
  })

  test('語の一部に当たるもの（claudette、cursory）は違反にしない', () => {
    assert.deepEqual(check('claudette と cursory と geminis と opuses'), [])
  })

  test('名前の前後が記号や日本語でも、境界として違反にする', () => {
    assert.deepEqual(check('（Claude）と「Opus」'), ['docs/05-開発規則/x.md:1'])
    assert.deepEqual(check('Sonnetを使う'), ['docs/05-開発規則/x.md:1'])
  })

  test('名前のない文書は通る', () => {
    assert.deepEqual(check('# 規則\n\nモデルは区分で決める。'), [])
  })
})

describe('対象のファイルの選び方', () => {
  test('docs/constitution.md と docs/05-開発規則/ の下の *.md を選ぶ', () => {
    assert.equal(isTargetFile('docs/constitution.md'), true)
    assert.equal(isTargetFile('docs/05-開発規則/workflow.md'), true)
    assert.equal(isTargetFile('docs/05-開発規則/sub/x.md'), true)
  })

  test('docs/06-手順書/、AGENTS.md、.claude/ は選ばない', () => {
    assert.equal(isTargetFile('docs/06-手順書/setup.md'), false)
    assert.equal(isTargetFile('AGENTS.md'), false)
    assert.equal(isTargetFile('.claude/skills/review/SKILL.md'), false)
  })

  test('docs/05-開発規則/ の下でも .md 以外は選ばない', () => {
    assert.equal(isTargetFile('docs/05-開発規則/x.txt'), false)
  })
})
