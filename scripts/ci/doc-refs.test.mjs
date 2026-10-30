import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { checkReferences, countHeadingMatches, isExcluded, isTargetFile, readHeadings, referenceName, slug, splitSectionNumbers } from './doc-refs.mjs'

/** 調べる文書の組。キーがパス、値が中身 */
const DOCS = {
  'docs/frontend/apps/store.md': '# 店舗画面（`frontend/store`）の構成\n## 4. 取得\n### 4.5 カート\n## 8. 文言\n',
  'docs/testing/writing.md': '# テストの書き方\n## 1. 名前\n## 2. 対応\n### 2.1 AC\n',
  'docs/process/workflow.md': '# 開発の進め方\n## 1. SDD\n### 1.4 spec と plan の書き方\n```\n## 9. コードの中\n```\n',
  'docs/process/tasks.md': '# 段階の一覧\n## 段階 1\n### タスク\n## 段階 2\n### タスク\n#### 確認\n',
  'docs/process/marks.md':
    '# 記号の文書\n## 1. `pnpm` の置き場所\n## 2. [OpenAPI](workflow.md) の規則\n## 3. 全角（Ａ１）の  見出し\n',
}

/**
 * 文書の組に 1 つのファイルを追加して、切れた参照を探す。
 *
 * @param {string} file 追加するファイルのパス
 * @param {string} text 追加するファイルの中身
 * @returns {string[]} 切れた参照の説明
 */
function check(file, text) {
  const files = { ...DOCS, [file]: text }
  return checkReferences(Object.keys(files), (path) => files[path], (path) => path in files)
}

describe('文書の参照の検査', () => {
  test('進行中の spec は見て、アーカイブした spec は見ない', () => {
    assert.equal(isExcluded('docs/08-spec/019-mvp-completion/plan.md'), false)
    assert.equal(isExcluded('docs/08-spec/mvp.md'), false)
    assert.equal(isExcluded('docs/08-spec/archive/mvp/002-cart-and-order/spec.md'), true)
    assert.equal(isExcluded('docs/08-spec/archive/mvp/mvp.md'), false)
    assert.equal(isExcluded('docs/08-spec/archive/1to10/mvp.md'), true)
    assert.equal(isExcluded('backend/ecsite/src/main/resources/db/migration/V1__a.sql'), true)
  })

  test('見出しの slug は GitHub と同じ作り方（記号を除き、空白をハイフンに）', () => {
    assert.equal(slug('1.4 spec と plan の書き方'), '14-spec-と-plan-の書き方')
    assert.equal(slug('`frontend/store` の構成'), 'frontendstore-の構成')
  })

  test('節の番号は見出しの先頭から取り、コードブロックの中は見ない', () => {
    const { title, sections } = readHeadings(DOCS['docs/process/workflow.md'])
    assert.equal(title, '開発の進め方')
    assert.deepEqual([...sections.keys()], ['1', '1.4'])
  })

  test('文書の名前は括弧の中を除く', () => {
    assert.equal(referenceName('店舗画面（`frontend/store`）の構成'), '店舗画面の構成')
  })

  test('範囲と並びは、両端と各番号を確認する', () => {
    assert.deepEqual(splitSectionNumbers('4〜6・8'), ['4', '6', '8'])
    assert.deepEqual(splitSectionNumbers('4、13'), ['4', '13'])
  })

  test('リンクの先のファイルと見出しを確認する', () => {
    const text = [
      '[a](../process/workflow.md#14-spec-と-plan-の書き方)',
      '[b](../process/workflow.md#2-tdd)',
      '[c](../process/none.md)',
      '[d](https://example.com/none.md)',
    ].join('\n')
    const problems = check('docs/dev/setup.md', text)
    assert.equal(problems.length, 2)
    assert.match(problems[0], /^見出しがない/)
    assert.match(problems[1], /^ファイルがない/)
  })
})

/**
 * 切れた参照の説明から、行の番号を取り出す。
 *
 * @param {string[]} problems 切れた参照の説明
 * @returns {string[]} 行の番号
 */
function lines(problems) {
  return problems.map((problem) => problem.match(/:(\d+) /)[1])
}

describe('見出しの参照の検査', () => {
  test('リンクの直後の「」を見出しとして確認する（番号を除いて比べる）', () => {
    const text = [
      '[開発の進め方](../process/workflow.md)の「spec と plan の書き方」',
      '[開発の進め方](../process/workflow.md)の「TDD」',
      '[店舗画面](../frontend/apps/store.md)の「カート」',
      '[店舗画面](../frontend/apps/store.md)の「4.5 カート」',
    ].join('\n')
    const problems = check('docs/dev/setup.md', text)
    assert.deepEqual(lines(problems), ['2', '4'])
    assert.match(problems[0], /^見出しがない（docs\/process\/workflow\.md の「TDD」）/)
  })

  test('文書の名前の直後の「」を見出しとして確認する（コードのコメントも）', () => {
    const text = ['開発の進め方の「SDD」', '開発の進め方の「ない」', '店舗画面の構成の「文言」'].join('\n')
    assert.deepEqual(lines(check('docs/dev/setup.md', text)), ['2'])
    const code = ['// 開発の進め方の「SDD」', '// テストの書き方の「ない」'].join('\n')
    assert.deepEqual(lines(check('backend/Foo.java', code)), ['2'])
  })

  test('コードブロックの中の見出しは、先の見出しにしない', () => {
    assert.equal(check('docs/dev/setup.md', '開発の進め方の「コードの中」').length, 1)
  })

  test('同じ見出しが 2 つ以上あり、親の見出しがないときは失敗にする', () => {
    const problems = check('docs/dev/setup.md', '[段階の一覧](../process/tasks.md)の「タスク」')
    assert.equal(problems.length, 1)
    assert.match(problems[0], /^見出しが 1 つに決まらない（docs\/process\/tasks\.md の「タスク」）/)
  })

  test('親の見出しを付ければ、その下（子孫）の見出しを確認する', () => {
    const text = [
      '[段階の一覧](../process/tasks.md)の「段階 1」の「タスク」',
      '[段階の一覧](../process/tasks.md)の「段階 2」の「確認」',
      '[段階の一覧](../process/tasks.md)の「段階 1」の「確認」',
      '段階の一覧の「段階 2」の「タスク」の「確認」',
      '段階の一覧の「タスク」の「段階 2」',
    ].join('\n')
    assert.deepEqual(lines(check('docs/dev/setup.md', text)), ['3', '5'])
  })

  test('文書を指さない「」は見ない', () => {
    const text = [
      '「書き言葉」と書く。',
      '語の「書き言葉」は、',
      '「段階 1」の「タスク」',
      '<文書の名前>の「<見出し>」',
      '[外](https://example.com/a.md)の「ない」',
      '```',
      '開発の進め方の「ない」',
      '```',
    ].join('\n')
    assert.deepEqual(check('docs/dev/setup.md', text), [])
  })

  test('インラインコードの中のリンクも、リンクと同じく見る', () => {
    assert.equal(check('docs/dev/setup.md', '`[開発の進め方](../process/workflow.md)の「ない」`').length, 1)
  })

  test('見出しは、前後と連続の空白、全角と半角、コードとリンクの記号の違いを除いて比べる', () => {
    const text = [
      '[記号](../process/marks.md)の「pnpm の置き場所」',
      '[記号](../process/marks.md)の「`pnpm` の置き場所」',
      '[記号](../process/marks.md)の「 OpenAPI の規則 」',
      '[記号](../process/marks.md)の「全角(A1)の 見出し」',
      '[記号](../process/marks.md)の「pnpmの置き場所」',
    ].join('\n')
    assert.deepEqual(lines(check('docs/dev/setup.md', text)), ['5'])
  })

  test('見出しの末尾の全角の括弧の補足は、除いても全て書いても通る', () => {
    const docs = {
      'docs/process/plan.md':
        '# 計画\n## 5. 文体の検査（DS-283-5：DC-283-21〜25）\n### 5.1 段落（下書き）\n## 6. 用語（DS-1）\n## 7. 用語（DS-2）\n## 8. 中の（括弧）の後\n',
      'docs/dev/setup.md': [
        '[計画](../process/plan.md)の「文体の検査」',
        '[計画](../process/plan.md)の「文体の検査（DS-283-5：DC-283-21〜25）」',
        '[計画](../process/plan.md)の「文体の検査」の「段落」',
        '[計画](../process/plan.md)の「用語」',
        '[計画](../process/plan.md)の「用語（DS-1）」',
        '[計画](../process/plan.md)の「文体の検査（DS-283-5）」',
        '[計画](../process/plan.md)の「中のの後」',
        '[計画](../process/plan.md)の「中の」',
      ].join('\n'),
    }
    const problems = checkReferences(Object.keys(docs), (path) => docs[path], (path) => path in docs)
    assert.deepEqual(lines(problems), ['4', '6', '7', '8'])
    assert.match(problems[0], /^見出しが 1 つに決まらない/)
  })

  test('アンカー付きのリンクの後の「」も、リンクの先の文書で確認する', () => {
    const text = '[開発の進め方](../process/workflow.md#1-sdd)の「ない」'
    assert.equal(check('docs/dev/setup.md', text).length, 1)
  })
})

describe('節の番号の参照の検査', () => {
  test('Markdown では、全ての形の「第 N 節」を失敗にする（名前のないもの、spec・plan の節も）', () => {
    const text = [
      '# 開発環境',
      '## 1. 起動',
      '第 1 節を読む。',
      '[店舗画面](../frontend/apps/store.md) 第 4.5 節',
      '店舗画面の構成 第 1・3 節',
      '開発の進め方 第 4.2、5.6 節',
      'テストの書き方 第 4〜6 節',
      'plan 001 第 7 節、spec 003 の第 9 節',
    ].join('\n')
    const problems = check('docs/dev/setup.md', text)
    assert.deepEqual(lines(problems), ['3', '4', '5', '6', '7', '8', '8'])
    for (const problem of problems) assert.match(problem, /^節の番号で参照している。見出しの形（<文書の名前>の「<見出し>」）に書き換える/)
  })

  test('Markdown のインラインコードとコードブロックの中の「第 N 節」は失敗にしない', () => {
    const text = ['例：`開発の進め方 第 4 節` と ``第 5 節`` は使わない。', '```', '開発の進め方 第 4 節', '```', '`a` 第 2 節'].join('\n')
    assert.deepEqual(lines(check('docs/dev/setup.md', text)), ['5'])
  })

  test('節でない語（第 2 段、第 3 回など）は失敗にしない', () => {
    const text = ['第 2 段の実装', '第 3 回のレビュー', '第 1 章', '第 12 条'].join('\n')
    assert.deepEqual(check('docs/dev/setup.md', text), [])
    assert.deepEqual(check('backend/Foo.java', '// 店舗画面の構成 第 2 段'), [])
  })

  test('コードでは、文書の名前・リンク・パスの直後の「第 N 節」を失敗にする', () => {
    const code = [
      '// （店舗画面の構成 第 4.5 節）',
      '// [開発の進め方](../../docs/process/workflow.md) 第 1 節',
      '// docs/testing/writing.md 第 2.1 節',
      '// テストの書き方の第 2 節',
      '// 第 99 節',
      '// plan 001 第 7 節、spec 003 の第 9 節',
      "'- [ ] 第 1 節：規則 A',",
    ].join('\n')
    assert.deepEqual(lines(check('backend/Foo.java', code)), ['1', '2', '3', '4'])
  })

  test('失敗のメッセージに、番号から求めた見出しを示す', () => {
    const [found] = check('backend/Foo.java', '// 店舗画面の構成 第 4.5 節')
    assert.match(found, /（docs\/frontend\/apps\/store\.md の「カート」）：backend\/Foo\.java:1 /)
    const [several] = check('docs/dev/setup.md', '店舗画面の構成 第 4・8 節')
    assert.match(several, /（docs\/frontend\/apps\/store\.md の「取得」、「文言」）/)
    const [missing] = check('docs/dev/setup.md', '店舗画面の構成 第 4、9 節')
    assert.match(missing, /（docs\/frontend\/apps\/store\.md の「取得」、番号 9 の節はない）/)
    const [self] = check('docs/dev/setup.md', '# 開発環境\n## 1. 起動\n第 1 節')
    assert.match(self, /（docs\/dev\/setup\.md の「起動」）/)
  })

  test('番号から見出しが求まらないときは、見出しを示さずに失敗にする', () => {
    const [problem] = check('docs/dev/setup.md', 'plan 001 第 7 節')
    assert.match(problem, /に書き換える：docs\/dev\/setup\.md:1 /)
  })

  test('Gradle の設定（.kts）と、xml・json・jsonc・grit・ps1 も見る（ファイルの全体を読むので、コメントの読み方は要らない）', () => {
    assert.equal(isTargetFile('backend/ecsite/build.gradle.kts'), true)
    assert.equal(isTargetFile('docs/a.md'), true)
    assert.equal(isTargetFile('backend/ecsite/config/checkstyle/checkstyle.xml'), true)
    assert.equal(isTargetFile('package.json'), true)
    assert.equal(isTargetFile('frontend/store/biome.jsonc'), true)
    assert.equal(isTargetFile('frontend/store/biome-plugins/no-aria-live.grit'), true)
    assert.equal(isTargetFile('scripts/dev/a.ps1'), true)
    assert.equal(isTargetFile('frontend/store/public/a.png'), false)
  })

  test('空白のない「第2節」と、全角の数字の「第２節」も失敗にする', () => {
    const text = ['店舗画面の構成 第4節', '店舗画面の構成 第 ４.５ 節', '第２段の実装'].join('\n')
    const problems = check('docs/dev/setup.md', text)
    assert.deepEqual(lines(problems), ['1', '2'])
    assert.match(problems[1], /（docs\/frontend\/apps\/store\.md の「カート」）/)
    assert.deepEqual(lines(check('backend/Foo.java', '// テストの書き方の第2節')), ['1'])
  })

  test('文書の名前・リンク・パスと「第 N 節」の間に括弧があっても、その文書の節として失敗にする', () => {
    const code = [
      '// CI の文書（docs/testing/writing.md）の第 2 節の表',
      '// テストの書き方（`writing.md`）の第 1 節',
      '// 店舗画面の構成（補足）の第 4 節',
    ].join('\n')
    const problems = check('backend/Foo.java', code)
    assert.deepEqual(lines(problems), ['1', '2', '3'])
    assert.match(problems[0], /（docs\/testing\/writing\.md の「対応」）/)
    assert.match(problems[2], /（docs\/frontend\/apps\/store\.md の「取得」）/)
  })

  test('Markdown で、直前が「（」「、」などで先の文書が決まらないときは、自分の文書の見出しを示さない', () => {
    const text = ['# 開発環境', '## 4. 起動', '店舗画面の手順（第 4 節）', '[テストの書き方](../testing/writing.md) 第 1 節、第 4 節'].join('\n')
    const problems = check('docs/dev/setup.md', text)
    assert.deepEqual(lines(problems), ['3', '4', '4'])
    assert.doesNotMatch(problems[0], /docs\/dev\/setup\.md の/)
    assert.match(problems[0], /先の文書を書く/)
    assert.match(problems[2], /先の文書を書く/)
  })

  test('Markdown で、直前が名前でない語と「の」のときも、自分の文書の見出しを示さない', () => {
    const text = ['# 開発環境', '## 2. 起動', '## 4. 停止', '環境変数の文書の第 2 節', '管理画面の第 4 節', '手順は第 2 節'].join('\n')
    const problems = check('docs/dev/setup.md', text)
    assert.deepEqual(lines(problems), ['4', '5', '6'])
    for (const problem of problems) {
      assert.doesNotMatch(problem, /docs\/dev\/setup\.md の/)
      assert.match(problem, /先の文書を書く/)
    }
  })

  test('Markdown で、直前が行の始め・文の切れ目・表の区切り・リストの印・「この文書の」のときは、自分の文書の見出しを示す', () => {
    const text = ['# 開発環境', '## 2. 起動', '第 2 節を読む。', '手順。第 2 節', '| a | 第 2 節 |', '- 第 2 節', '（第 2 節）', 'この文書の第 2 節', '本書の第 2 節'].join('\n')
    const problems = check('docs/dev/setup.md', text)
    assert.equal(problems.length, 7)
    for (const problem of problems) assert.match(problem, /（docs\/dev\/setup\.md の「起動」）/)
  })

  test('コードで、名前の直後に開き括弧を挟んだ「第 N 節」も失敗にする', () => {
    const code = ['// 開発の進め方（第 1 節）', '// 開発の進め方 (第 1 節)', '// 手順（第 1 節）'].join('\n')
    const problems = check('backend/Foo.java', code)
    assert.deepEqual(lines(problems), ['1', '2'])
    assert.match(problems[0], /（docs\/process\/workflow\.md の「SDD」）/)
  })
})

describe('見出しの番号と、見出しの参照の先', () => {
  test('見出しの番号は「1.」か「4.1」の形だけを除く（「1 通だけ送る」の 1 は見出しの一部）', () => {
    const { sections, headings } = readHeadings('# 通知\n## 1. 送る\n### 1 通だけ送る\n### 1.2 再送\n')
    assert.deepEqual([...sections.keys()], ['1', '1.2'])
    assert.equal(countHeadingMatches(headings, ['1 通だけ送る']), 1)
    assert.equal(countHeadingMatches(headings, ['通だけ送る']), 0)
  })

  test('パスの後の「」も、その文書の見出しとして確認する', () => {
    const code = [
      '// docs/testing/writing.md の「名前」',
      '// docs/testing/writing.md の「ない」',
      '// `docs/process/workflow.md` の「ない」',
      '// （docs/testing/writing.md）の「ない」',
      '// 外のリポジトリの references/build-features.md の「ない」',
      '// `${DIR}${module}.md` の「ない」',
    ].join('\n')
    assert.deepEqual(lines(check('backend/Foo.java', code)), ['2', '3', '4'])
  })

  test('ディレクトリまで書いたパスは、ファイルがなければ、同じファイル名の docs/ の文書を先にしない', () => {
    const code = ['// 外のリポジトリの docs/writing.md の「Install」', '// old/workflow.md の「ない」', '// writing.md の「ない」'].join('\n')
    // ファイル名だけのパスは、docs/ の下の同じ名前の文書（1 つだけのとき）を先にする
    assert.deepEqual(lines(check('backend/Foo.java', code)), ['3'])
  })

  test('パスは、Markdown では参照のあるファイルからの相対を先に、コードではルートからを先に見る', () => {
    const docs = {
      'README.md': '# ルート\n## 1. 根\n',
      'docs/process/README.md': '# 隣\n## 1. 隣の節\n',
      'docs/process/a.md': ['`README.md` の「隣の節」', '`README.md` の「根」'].join('\n'),
      'scripts/a.mjs': ['// README.md の「根」', '// README.md の「隣の節」'].join('\n'),
    }
    const problems = checkReferences(Object.keys(docs), (path) => docs[path], (path) => path in docs)
    assert.deepEqual(problems.map((problem) => problem.match(/：(\S+:\d+) /)[1]), ['docs/process/a.md:2', 'scripts/a.mjs:2'])
  })

  test('skill の名前（skill `<名前>`）の後の見出しの参照は、.claude/skills/<名前>/SKILL.md の見出しとして確認する', () => {
    const docs = {
      '.claude/skills/sessions/SKILL.md': '# sessions\n## ツールの補足\n### 作業ツリーのセッションが読むもの\n',
      'docs/process/a.md': ['skill `sessions` の「ツールの補足」', 'skill `sessions`の「ツールの補足」の「作業ツリーのセッションが読むもの」', 'skill `sessions` の「ツールの注」', 'skill `nothing` の「ない」'].join('\n'),
      'scripts/a.mjs': '// skill `sessions` の「段の終わり」',
    }
    const problems = checkReferences(Object.keys(docs), (path) => docs[path], (path) => path in docs)
    assert.deepEqual(problems.map((problem) => problem.match(/：(\S+:\d+) /)[1]), ['docs/process/a.md:3', 'scripts/a.mjs:1'])
  })

  test('skill の名前の後のファイル名（skill `<名前>` の <ファイル名>）は、その skill のフォルダーのファイルとして確認する', () => {
    const docs = {
      '.claude/skills/review/SKILL.md': '# review\n## 手順\n',
      '.claude/skills/review/prompts.md': '# prompts\n## 重要度\n',
      'docs/process/a.md': ['skill `review` の prompts.md の「重要度」', 'skill `review` の `prompts.md` の「手順」', 'skill `review` の prompts.md の「ない」'].join('\n'),
    }
    const problems = checkReferences(Object.keys(docs), (path) => docs[path], (path) => path in docs)
    assert.deepEqual(problems.map((problem) => problem.match(/：(\S+:\d+) /)[1]), ['docs/process/a.md:2', 'docs/process/a.md:3'])
    assert.match(problems[0], /\.claude\/skills\/review\/prompts\.md/)
  })

  test('兄弟の「設定」と「設定（任意）」では、完全に一致する見出しを選ぶ', () => {
    const docs = {
      'docs/process/plan.md': '# 計画\n## 1. 設定\n## 2. 設定（任意）\n## 3. 用語（DS-1）\n',
      'docs/dev/setup.md': ['[計画](../process/plan.md)の「設定」', '[計画](../process/plan.md)の「設定（任意）」', '[計画](../process/plan.md)の「用語」'].join('\n'),
    }
    assert.deepEqual(checkReferences(Object.keys(docs), (path) => docs[path], (path) => path in docs), [])
  })
})
