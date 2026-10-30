import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, describe, test } from 'node:test'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { withoutGitEnv } from './lib/git-env.mjs'
import { commentText, trailingCommentLines } from './lib/comment-text.mjs'
import { SHALLOW_CLONE_MESSAGE } from './lib/shallow-clone.mjs'
import * as jaText from './ja-text.mjs'
import { findAllowProblems, findCodeTerms, findStyleWarnings, findTerms, isTargetCode, isTargetDoc, stripMessageComments, validateTerms } from './ja-text.mjs'

const SCRIPT = fileURLToPath(new URL('./ja-text.mjs', import.meta.url))
const list = JSON.parse(readFileSync(new URL('./ja-text.json', import.meta.url), 'utf8'))

/** 改善のまとまりを数える中国語の字（U+6279）。ファイルには字を書かず、エスケープで書く */
const BATCH = '\u6279'

/** 「〜し直す」の動詞の連用形に「修正」が続く、崩れと確定している複合動詞の 15 語（spec 249 の「背景と目的」） */
const BROKEN_VERBS = ['書き', '作り', '置き', '読み', 'やり', '考え', '組み', '問い', '洗い', '貼り', '撮り', '練り', '言い', '数え', '測り']

/**
 * コミットメッセージのファイルを調べる入口を実行する。
 *
 * @param {string} message コミットメッセージの内容
 * @returns {import('node:child_process').SpawnSyncReturns<string>} 実行の結果
 */
function runMessage(message) {
  const dir = mkdtempSync(path.join(tmpdir(), 'ja-text-'))
  try {
    const file = path.join(dir, 'COMMIT_EDITMSG')
    writeFileSync(file, message)
    return spawnSync(process.execPath, [SCRIPT, '--message', file], { encoding: 'utf8', env: withoutGitEnv() })
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
}

describe('日本語の文の検査（一覧と error の項目）', () => {
  test('一覧の各項目が error か warning のどちらかである', () => {
    assert.deepEqual(Object.keys(list).sort(), ['error', 'style', 'warning'])
    for (const item of [...list.error, ...list.warning]) {
      assert.equal(typeof (item.word ?? item.pattern), 'string')
      assert.equal(typeof item.use, 'string')
    }
  })

  test('改善のまとまりを数える字が error になる', () => {
    assert.equal(findTerms(`3${BATCH}に分ける`, list.error).length, 1)
  })

  test('改善のまとまりを数える字で始まる日本語の語は通る', () => {
    const items = list.error.filter((item) => item.pattern !== undefined && item.examples.hit.some((h) => h.includes(BATCH)))
    assert.equal(items.length, 1)
    assert.deepEqual(findTerms('\u6279\u5224的な意見と\u6279\u8a55', items), [])
  })

  test('崩れと確定している複合動詞の 15 語が error になる', () => {
    assert.equal(BROKEN_VERBS.length, 15)
    for (const verb of BROKEN_VERBS) {
      assert.equal(findTerms(`設定を${verb}修正する`, list.error).length, 1, `${verb}修正`)
    }
  })

  test('「少し修正する」は error にならない', () => {
    assert.deepEqual(findTerms('少し修正する。もし修正するなら', list.error), [])
  })

  test('「有料版」は通る', () => {
    assert.deepEqual(findTerms('有料版の機能', list.error), [])
  })

  test('一覧の許す語が全て理由つきである（allow は配列でなく、各値が空でない文字列）', () => {
    for (const item of [...list.error, ...list.warning]) {
      if (item.allow === undefined) continue
      assert.equal(typeof item.allow, 'object', item.word)
      assert.equal(Array.isArray(item.allow), false, item.word)
      for (const [allowed, reason] of Object.entries(item.allow)) {
        assert.equal(typeof reason, 'string', `${item.word}: ${allowed}`)
        assert.notEqual(reason.trim(), '', `${item.word}: ${allowed}`)
      }
    }
  })

  test('許す語が使われている場合は問題にならない', () => {
    assert.deepEqual(findAllowProblems(['\u6279\u5224と\u6279\u8a55'], list.error.filter((item) => item.word === BATCH)), [])
  })
})

/** 和語の「足す」。ファイルに使わない語を書かないよう、エスケープで組み立てる */
const ADD = '足す'

describe('日本語の文の検査（pattern と examples の項目）', () => {
  test('一覧の pattern の項目が、examples の hit に当たり miss に当たらない', () => {
    const patterns = [...list.error, ...list.warning].filter((item) => item.pattern !== undefined)
    assert.ok(patterns.length >= 1)
    assert.deepEqual(jaText.findPatternProblems(patterns), [])
    for (const item of patterns) {
      for (const hit of item.examples.hit) assert.equal(findTerms(hit, [item]).length, 1, hit)
      for (const miss of item.examples.miss) assert.deepEqual(findTerms(miss, [item]), [], miss)
    }
  })

  test('examples がない・空・hit か miss が空・正規表現が読めない項目は設定の誤りになる', () => {
    const bad = [
      { pattern: 'a', use: 'x' },
      { pattern: 'a', use: 'x', examples: {} },
      { pattern: 'a', use: 'x', examples: { hit: [], miss: ['b'] } },
      { pattern: 'a', use: 'x', examples: { hit: ['a'], miss: [] } },
      { pattern: '(', use: 'x', examples: { hit: ['a'], miss: ['b'] } },
    ]
    for (const item of bad) {
      assert.throws(() => validateTerms([item]), Error, item.pattern)
      assert.notEqual(jaText.findPatternProblems([item]).length, 0, JSON.stringify(item))
    }
  })

  test('hit に当たらない例と、miss に当たる例を誤りとして示す', () => {
    const wrong = { pattern: 'a', use: 'x', examples: { hit: ['b'], miss: ['a'] } }
    assert.equal(jaText.findPatternProblems([wrong]).length, 2)
  })

  test('漢字の後の形（不足する・補足する・満足する・充足する）は通り、和語の動詞は error になる', () => {
    for (const kanji of ['不', '補', '満', '充']) {
      assert.deepEqual(findTerms(`${kanji}${ADD}`, list.error), [], kanji)
    }
    assert.equal(findTerms(`数を${ADD}`, list.error).length, 1)
    assert.equal(findTerms(`1に2を${ADD}`, list.error).length, 1)
  })

  test('当たった文字列を word に入れる', () => {
    const [hit] = findTerms('x12y', [{ pattern: '\\d+', use: 'u', examples: { hit: ['1'], miss: ['x'] } }])
    assert.equal(hit.word, '12')
  })

  test('word の項目は今まで通りで、pattern と同じ一覧に混ぜられる', () => {
    const terms = [{ word: '道具', use: 'ツール' }, { pattern: '\\d', use: 'u', examples: { hit: ['1'], miss: ['a'] } }]
    assert.deepEqual(findTerms('道具と1', terms).map((hit) => hit.word), ['道具', '1'])
  })

  test('--message でも pattern の項目が働き、漢字の後の形は成功する', () => {
    assert.equal(runMessage(`docs: 不${ADD}る\n`).status, 0)
    assert.equal(runMessage(`docs: 数を${ADD}\n`).status, 1)
  })
})

describe('日本語の文の検査（コミットメッセージ）', () => {
  test('error があれば失敗にする', () => {
    const result = runMessage(`docs: 書き修正した\n\n本文\n`)
    assert.equal(result.status, 1)
    assert.match(result.stdout, /書き修正/)
  })

  test('error がなければ成功にする', () => {
    const result = runMessage('docs: 文書を書き直す\n\n本文\n')
    assert.equal(result.status, 0)
  })

  test('# で始まる行は調べない', () => {
    const result = runMessage(`docs: 文書を書き直す\n# 書き修正した\n`)
    assert.equal(result.status, 0)
  })
})

describe('日本語の文の検査（git commit -v の差分）', () => {
  const SCISSORS = '# ------------------------ >8 ------------------------'
  const verbose = `docs: 文書を直す\n\n本文\n${SCISSORS}\n# Do not modify or remove the line above.\ndiff --git a/docs/a.md b/docs/a.md\n@@ -1 +1 @@\n-道具を使う\n+ツールを使う\n`

  test('切り取り線の行以降を除く（行の数は変えない）', () => {
    const stripped = stripMessageComments(verbose)
    assert.doesNotMatch(stripped, /道具/)
    assert.equal(stripped.split('\n').length, verbose.split('\n').length)
  })

  test('切り取り線の行の下の差分に使わない語があっても成功する', () => {
    assert.deepEqual(findTerms(stripMessageComments(verbose), [{ word: '道具', use: 'ツール' }]), [])
    assert.equal(runMessage(verbose.replace('道具', '書き修正')).status, 0)
  })

  test('切り取り線の行の上の使わない語は、これまで通り見つける', () => {
    assert.equal(runMessage(verbose.replace('本文', '書き修正した')).status, 1)
  })
})

describe('日本語の文の検査（拡張子のない hook の script）', () => {
  test('scripts/dev/hooks の下のファイルを検査の対象にする', () => {
    assert.equal(isTargetCode('scripts/dev/hooks/pre-commit'), true)
    assert.equal(isTargetCode('scripts/dev/hooks/commit-msg'), true)
    assert.equal(isTargetCode('scripts/dev/other/pre-commit'), false)
  })
})

describe('文書の用語の検査', () => {
  test('一覧の語を書いた行を見つける', () => {
    const terms = [{ word: '道具', use: 'ツール' }]
    const text = ['ビルドのツール', 'ビルドの道具', '開発の道具と道具箱'].join('\n')
    assert.deepEqual(
      findTerms(text, terms).map((hit) => hit.line),
      [2, 3],
    )
  })

  test('許す語の中の一致は数えない', () => {
    const terms = [{ word: 'ブラウザ', use: 'ブラウザー', allow: { ブラウザー: '長音を付けた正しい語' } }]
    assert.deepEqual(findTerms('ブラウザーを開く', terms), [])
  })

  test('許す語の外の一致は数える（同じ行に両方あっても）', () => {
    const terms = [{ word: 'ブラウザ', use: 'ブラウザー', allow: { ブラウザー: '長音を付けた正しい語' } }]
    const text = ['ブラウザーを開く', 'ブラウザーとブラウザを比べる', 'ブラウザ'].join('\n')
    assert.deepEqual(
      findTerms(text, terms).map((hit) => hit.line),
      [2, 3],
    )
  })

  test('許す語の一覧がない語は、これまで通り部分一致で数える', () => {
    assert.equal(findTerms('道具箱', [{ word: '道具', use: 'ツール' }]).length, 1)
  })

  test('許す語が使わない語を含まないのは設定の誤りとして失敗にする', () => {
    const terms = [{ word: 'ブラウザ', use: 'ブラウザー', allow: { ブラウザー: '理由', ウェブ: '理由' } }]
    assert.throws(() => validateTerms(terms), /ウェブ/)
  })

  test('許す語を配列で書くのは設定の誤りとして失敗にする', () => {
    const terms = [{ word: 'ブラウザ', use: 'ブラウザー', allow: ['ブラウザー'] }]
    assert.throws(() => validateTerms(terms), /理由/)
  })

  test('許す語に理由がなければ失敗にする', () => {
    const terms = [{ word: 'ブラウザ', use: 'ブラウザー', allow: { ブラウザー: ' ' } }]
    const problems = findAllowProblems(['ブラウザーを開く'], terms)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /ブラウザー.*理由がない/)
  })

  test('どの文書にも使われなくなった許す語を失敗にする', () => {
    const terms = [{ word: 'ブラウザ', use: 'ブラウザー', allow: { ブラウザー: '長音を付けた正しい語' } }]
    const problems = findAllowProblems(['道具を使う', 'ツールを使う'], terms)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /ブラウザー.*不要/)
  })

  test('許す語が 1 つの文書にでも使われていれば不要とは見ない', () => {
    const terms = [{ word: 'ブラウザ', use: 'ブラウザー', allow: { ブラウザー: '長音を付けた正しい語' } }]
    assert.deepEqual(findAllowProblems(['道具', 'ブラウザーを開く'], terms), [])
  })

  test('許す語のない項目は誤りにしない', () => {
    assert.deepEqual(findAllowProblems(['道具'], [{ word: '道具', use: 'ツール' }]), [])
  })

  test('docs・README・AGENTS・skill と進行中の spec の Markdown を見て、アーカイブした spec は見ない', () => {
    assert.equal(isTargetDoc('docs/05-開発規則/writing.md'), true)
    assert.equal(isTargetDoc('README.md'), true)
    assert.equal(isTargetDoc('AGENTS.md'), true)
    assert.equal(isTargetDoc('.claude/skills/docs/SKILL.md'), true)
    assert.equal(isTargetDoc('docs/08-spec/019-mvp-completion/plan.md'), true)
    assert.equal(isTargetDoc('docs/08-spec/mvp.md'), true)
    assert.equal(isTargetDoc('docs/08-spec/archive/mvp/002-cart-and-order/spec.md'), false)
    assert.equal(isTargetDoc('frontend/store/README.md'), false)
  })


  test('.github の yml（issue のテンプレート、workflow、action、dependabot）と Markdown を見る', () => {
    assert.equal(isTargetDoc('.github/ISSUE_TEMPLATE/bug.yml'), true)
    assert.equal(isTargetDoc('.github/workflows/ci.yml'), true)
    assert.equal(isTargetDoc('.github/actions/setup-frontend/action.yml'), true)
    assert.equal(isTargetDoc('.github/dependabot.yml'), true)
    assert.equal(isTargetDoc('.github/dependabot.yaml'), true)
    assert.equal(isTargetDoc('.github/actions/setup-frontend/action.yaml'), true)
    assert.equal(isTargetDoc('.github/pull_request_template.md'), true)
    assert.equal(isTargetDoc('frontend/store/.github/workflows/ci.yml'), false)
  })
})

describe('日本語の文の検査（コードのコメントとテスト名）', () => {
  test('アーカイブの外の .java・.kts・.ts・.tsx・.mjs・.cjs・.sh を調べる', () => {
    for (const file of [
      'backend/ecsite/src/main/java/A.java',
      'backend/ecsite/build.gradle.kts',
      'frontend/store/src/a.ts',
      'frontend/store/src/a.tsx',
      'scripts/ci/a.mjs',
      'scripts/a.cjs',
      'scripts/dev/a.sh',
    ]) {
      assert.equal(isTargetCode(file), true, file)
    }
  })

  test('生成したコード、node_modules、ビルドの出力、対象でない拡張子は調べない', () => {
    for (const file of [
      'frontend/admin/src/generated/api.ts',
      'frontend/store/node_modules/x/index.mjs',
      'backend/ecsite/build/generated/A.java',
      'frontend/store/.next/server/a.mjs',
      'frontend/store/dist/a.mjs',
      'frontend/store/package.json',
      'docs/guide.md',
    ]) {
      assert.equal(isTargetCode(file), false, file)
    }
  })

  test('語を挙げる一覧とそのテストは調べない', () => {
    assert.equal(isTargetCode('scripts/ci/ja-text.test.mjs'), false)
    assert.equal(isTargetCode('scripts/ci/lib/comment-text.test.mjs'), false)
  })

  test('Java のコメントと @DisplayName の error を、元の行番号で見つける', () => {
    const source = ['class A {', '  // 道具を使う', '  @DisplayName("作り修正する")', '  void f() { String s = "道具"; }', '}'].join('\n')
    assert.deepEqual(
      findCodeTerms('A.java', source, list.error).map((hit) => `${hit.line}:${hit.word}`),
      ['2:道具', '3:作り修正'],
    )
  })

  test('TypeScript のコメントと describe・it の名前の error を見つける', () => {
    const source = ["describe('道具の一覧', () => {", "  it('確かめる', () => {})", '})', '// 足す'].join('\n')
    assert.deepEqual(
      findCodeTerms('a.test.ts', source, list.error).map((hit) => `${hit.line}:${hit.word}`),
      ['1:道具', '2:確かめ', '4:足す'],
    )
  })

  test('画面の文言の文字列リテラルにある語は error にしない', () => {
    const source = ['const label = "道具の一覧"', '<p>{t("道具")}</p>', "expect(page.getByText('道具')).toBeVisible()"].join('\n')
    assert.deepEqual(findCodeTerms('a.tsx', source, list.error), [])
  })

  test('.kts と .sh のコメントの error を見つける。.sh の 1 行目の #! は調べない', () => {
    assert.deepEqual(
      findCodeTerms('build.gradle.kts', 'val x = 1 // 道具', list.error).map((hit) => hit.line),
      [1],
    )
    assert.deepEqual(
      findCodeTerms('a.sh', '#!/bin/sh\n# 道具\necho 道具', list.error).map((hit) => hit.line),
      [2],
    )
  })

  test('許す語はコメントの中でも通る', () => {
    assert.deepEqual(findCodeTerms('A.java', '// 有料版の機能', list.error), [])
  })

  test('対象でないファイルは何も見つけない', () => {
    assert.deepEqual(findCodeTerms('a.json', '// 道具', list.error), [])
  })
})

describe('日本語の文の検査（文体の warning）', () => {
  /** テストの中の仮の閾値（決めた値は ja-text.json の style） */
  const LIMITS = { no: 4, length: 30, comma: 3 }

  /** @param {string} text 文書 @param {string} kind 見る種類 @returns {number[]} その種類の warning の行 */
  function lines(text, kind) {
    return findStyleWarnings(text, { ...LIMITS, compound: list.style.compound })
      .filter((hit) => hit.kind === kind)
      .map((hit) => hit.line)
  }

  test('1 区間の「の」が閾値以上の文を示す。読点で区切れば同じ数でも示さない', () => {
    assert.deepEqual(lines('本日の会議の資料の変更の履歴を見る。', 'no'), [1])
    assert.deepEqual(lines('本日の会議の資料の、変更の履歴を見る。', 'no'), [])
  })

  test('字数が閾値以上の文を示す。文の区切りは「。」で、行番号は元の行である', () => {
    const long = 'あ'.repeat(30)
    assert.deepEqual(lines(`短い文である。\n${long}である。`, 'length'), [2])
    assert.deepEqual(lines('あ'.repeat(10) + 'である。', 'length'), [])
  })

  test('読点が閾値以上の文を示す', () => {
    assert.deepEqual(lines('一、二、三、四とする。', 'comma'), [1])
    assert.deepEqual(lines('一、二、三とする。', 'comma'), [])
  })

  test('「〜なく〜ない」の形の文を示す', () => {
    assert.deepEqual(lines('原因は分からなく修正もできない。', 'negation'), [1])
    assert.deepEqual(lines('原因は分からないが修正できる。', 'negation'), [])
  })

  test('コードブロック、表の区切り行、URL、インラインコードは数えない', () => {
    const text = ['```', '本日の会議の資料の変更の履歴を見る。', '```', '|---|---|', 'https://example.com/の/の/の/の/の/の/の/の/の/の/の/の/の/の/の/の/の/の/の/の/の/の/の/の', 'ここは `のののの` と [リンク](https://example.com/の/の) だ。'].join('\n')
    assert.deepEqual(findStyleWarnings(text, LIMITS), [])
  })

  test('かなを含まない文は数えない', () => {
    assert.deepEqual(findStyleWarnings('A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P。', LIMITS), [])
  })

  test('かなの直後に助詞を挟まず漢語が続く形を、複合動詞の崩れの疑いとして示す', () => {
    assert.deepEqual(lines('少し修正する。', 'compound'), [1])
    assert.deepEqual(lines('設定を作り確認する。', 'compound'), [1])
  })

  test('「を修正する」「の確認」「して確認」は崩れの疑いにしない', () => {
    assert.deepEqual(lines('設定を修正する。結果の確認をして確認する。', 'compound'), [])
  })

  test('「要求」は warning の語になる。「要求する」も示す', () => {
    assert.equal(findTerms('要求を送る', list.warning).length, 1)
    assert.equal(findTerms('権限を要求する', list.warning).length, 1)
  })

  test('閾値は一覧に数値で書いてある', () => {
    for (const key of ['no', 'length', 'comma', 'cell']) {
      assert.equal(typeof list.style[key], 'number', key)
    }
    assert.ok(list.style.compound.includes('修正'))
  })

  test('表のセルで、文の字数の合計が閾値以上のものを示す。表でない行は示さない', () => {
    const cells = (text) => findStyleWarnings(text, { cell: 10 }).filter((hit) => hit.kind === 'cell').map((hit) => hit.line)
    assert.deepEqual(cells(`| 項目 | ${'あ'.repeat(5)}。${'い'.repeat(5)}。 |`), [1])
    assert.deepEqual(cells(`| 項目 | ${'あ'.repeat(9)} |`), [])
    assert.deepEqual(cells(`${'あ'.repeat(20)}である。`), [])
  })

  test('セルの閾値は 80 字である', () => {
    assert.equal(list.style.cell, 80)
  })
})

describe('日本語の文の検査（複数の行にまたがる文）', () => {
  /** 決めた値と同じ閾値 */
  const LIMITS = { no: 4, length: 100, comma: 5 }
  /** 60 字と 57 字の断片（つなぐと「である」を足して 120 字。どちらも 1 行では閾値に届かない） */
  const HEAD = 'あ'.repeat(60)
  const TAIL = `${'い'.repeat(57)}である。`

  /**
   * @param {string} text 文書
   * @param {{ kind?: 'markdown' | 'code' | 'lines', added?: Set<number> | null }} [options] 測り方と追加行
   * @returns {string[]} warning の「種類:行」（行の順）
   */
  function hits(text, options) {
    return findStyleWarnings(text, LIMITS, options).map((hit) => `${hit.kind}:${hit.line}`)
  }

  test('2 行に分けた 120 字の文を 1 文として測り、文の最初の行に示す', () => {
    assert.deepEqual(hits(`${HEAD}\n${TAIL}`), ['length:1'])
    assert.match(findStyleWarnings(`${HEAD}\n${TAIL}`, LIMITS)[0].use, /字数が 120 字/)
  })

  test('読点が 3 個と 3 個の行に分かれた文を、読点 6 個の 1 文として測る', () => {
    const result = findStyleWarnings('一、二、三、\n四、五、六、七とする。', LIMITS)
    assert.deepEqual(result.map((hit) => `${hit.kind}:${hit.line}`), ['comma:1'])
    assert.match(result[0].use, /読点が 6 個/)
  })

  test('行の途中の「。」で文を切り、残りを次の行とつなぐ', () => {
    assert.deepEqual(hits(`短い文である。${HEAD}\n${TAIL}次の文である。`), ['length:1'])
  })

  test('空行、表の行、見出し、項目の行、コードブロックの前で切る', () => {
    for (const between of ['', '| 表 | セル |', '## 見出し', '- 項目である。', '1. 項目である。', '```\nコード\n```']) {
      assert.deepEqual(hits(`${HEAD}\n${between}\n${TAIL}`), [], between)
    }
  })

  test('見出しと表の行は、次の行とつながない', () => {
    assert.deepEqual(hits(`## ${HEAD}\n${TAIL}`), [])
    assert.deepEqual(hits(`| ${HEAD} |\n${TAIL}`), [])
  })

  test('項目の行は、続く行（字下げした続きの行）とつなぐ', () => {
    assert.deepEqual(hits(`- ${HEAD}\n  ${TAIL}`), ['length:1'])
    assert.deepEqual(hits(`- ${HEAD}\n- ${TAIL}`), [])
  })

  test('項目の記号より深く字下げしていない行は、項目の続きにしない', () => {
    assert.deepEqual(hits(`- ${HEAD}\n${TAIL}`), [])
    assert.deepEqual(hits(`  - ${HEAD}\n  ${TAIL}`), [])
    assert.deepEqual(hits(commentText(`// - ${HEAD}\n// ${TAIL}\n`, 'ts'), { kind: 'code' }), [])
    assert.deepEqual(hits(commentText(`// - ${HEAD}\n//   ${TAIL}\n`, 'ts'), { kind: 'code' }), ['length:1'])
  })

  test('罫線の行（===、--- など）の前後で切る', () => {
    assert.deepEqual(hits(commentText(`# ${'='.repeat(20)}\n# ${HEAD}\n# ${TAIL}\n`, 'sh'), { kind: 'code' }), ['length:2'])
    assert.deepEqual(hits(commentText(`// ${HEAD}\n// ${'='.repeat(20)}\n// ${TAIL}\n`, 'ts'), { kind: 'code' }), [])
  })

  test('コードでは、語の見出しで始まる行（「対象：」など）の前で切る', () => {
    assert.deepEqual(hits(commentText(`// ${HEAD}\n// 対象：${TAIL}\n`, 'ts'), { kind: 'code' }), [])
  })

  test('コードでは、行末のコメント（コードの後ろのコメント）を前後の行とつながない', () => {
    const code = (source) => hits(commentText(source, 'ts'), { kind: 'code', trailing: trailingCommentLines(source, 'ts') })
    assert.deepEqual(code(`const a = 1 // 短い注\n// ${HEAD}\n// ${TAIL}\n`), ['length:2'])
    assert.deepEqual(code(`a() // ${HEAD}\nb() // ${TAIL}\n`), [])
    assert.deepEqual(code(`// ${HEAD}\nb() // ${TAIL}\n`), [])
  })

  test('コードでは、罫線の記号で始まるか終わる行（節の見出し）を前後の行とつながない', () => {
    const body = `${'い'.repeat(80)}である。`
    for (const heading of [`--- ${'見'.repeat(30)}（説明） ---`, `===== ${'見'.repeat(30)}`, `${'見'.repeat(30)} -----`]) {
      assert.deepEqual(hits(commentText(`// ${heading}\n// ${body}\n`, 'ts'), { kind: 'code' }), [], heading)
    }
    assert.deepEqual(hits(commentText(`# ---- ${'見'.repeat(30)} ----\n# ${body}\n`, 'sh'), { kind: 'code' }), [])
  })

  test('コードでは、語の見出しで始まる行の続きは、深く字下げした行だけとする', () => {
    assert.deepEqual(hits(commentText(`// 使い方：${HEAD}\n// ${TAIL}\n`, 'ts'), { kind: 'code' }), [])
    assert.deepEqual(hits(commentText(`// 使い方：${HEAD}\n//   ${TAIL}\n`, 'ts'), { kind: 'code' }), ['length:1'])
  })

  test('コードでは、行末の <br /> の後で切り、<br /> は字数に数えない', () => {
    assert.deepEqual(hits(commentText(`/**\n * ${HEAD}<br />\n * ${TAIL}\n */\n`, 'java'), { kind: 'code' }), [])
    assert.deepEqual(hits(commentText(`/**\n * ${'あ'.repeat(97)}<br />\n */\n`, 'java'), { kind: 'code' }), [])
  })

  test('コードでは、日本語の字のない行（コマンドの例など）を前後の行とつながない', () => {
    assert.deepEqual(hits(commentText(`// ${HEAD}\n// ./gradlew check ${'-Pa=b '.repeat(10)}\n// ${TAIL}\n`, 'ts'), { kind: 'code' }), [])
  })

  test('文書の最初の front matter の行は、行ごとに測る', () => {
    assert.deepEqual(hits(`---\nname: ${HEAD}\ndescription: ${TAIL}\n---\n本文である。`), [])
  })

  test('「の」の連なり、否定、複合動詞の崩れも、つないだ文で見る', () => {
    const style = (text, kind) => findStyleWarnings(text, { no: 4, compound: list.style.compound }).filter((hit) => hit.kind === kind).map((hit) => hit.line)
    assert.deepEqual(style('本日の会議の\n資料の変更の履歴を見る。', 'no'), [1])
    assert.deepEqual(style('原因は分からなく\n修正もできない。', 'negation'), [1])
    assert.deepEqual(style('作業は、少し\n修正する。', 'compound'), [1])
  })

  test('kind が lines のとき（.github の yml）は、行ごとに測る', () => {
    assert.deepEqual(hits(`${HEAD}\n${TAIL}`, { kind: 'lines' }), [])
  })

  test('コードでは、続くコメントの行をつなぎ、コメントの記号は数えない', () => {
    assert.deepEqual(hits(commentText(`// ${HEAD}\n// ${TAIL}\nconst a = 1\n`, 'ts'), { kind: 'code' }), ['length:1'])
    assert.deepEqual(hits(commentText(`/**\n * ${HEAD}\n * ${TAIL}\n */\n`, 'ts'), { kind: 'code' }), ['length:2'])
    assert.deepEqual(hits(commentText(`# ${HEAD}\n# ${TAIL}\n`, 'sh'), { kind: 'code' }), ['length:1'])
    assert.match(findStyleWarnings(commentText(`// ${HEAD}\n// ${TAIL}\n`, 'ts'), LIMITS, { kind: 'code' })[0].use, /字数が 120 字/)
  })

  test('コードでは、コメントでない行、空のコメントの行、項目の行、JSDoc のタグの行、テスト名の前で切る', () => {
    const cases = [
      `// ${HEAD}\nconst a = 1\n// ${TAIL}\n`,
      `// ${HEAD}\n\n// ${TAIL}\n`,
      `/**\n * ${HEAD}\n *\n * ${TAIL}\n */\n`,
      `// ${HEAD}\n// - ${TAIL}\n`,
      `/**\n * @param {string} a ${HEAD}\n * @returns {string} ${TAIL}\n */\n`,
      `// ${HEAD}\ntest('${TAIL}', () => {})\n`,
    ]
    for (const source of cases) assert.deepEqual(hits(commentText(source, 'ts'), { kind: 'code' }), [], source)
  })

  test('「：」で終わる行（一覧を導く行）は、次の行とつながない', () => {
    assert.deepEqual(hits(`${HEAD}：\n${TAIL}`), [])
    assert.deepEqual(hits(commentText(`// ${HEAD}：\n// ${TAIL}\n`, 'ts'), { kind: 'code' }), [])
  })

  test('コードでは、Javadoc の段落・項目の要素で始まる行の前と、整列の空白を含む行の前後で切る', () => {
    assert.deepEqual(hits(commentText(`/**\n * ${HEAD}\n * <p>${TAIL}\n */\n`, 'java'), { kind: 'code' }), [])
    assert.deepEqual(hits(commentText(`/**\n * ${HEAD}\n * <li>${TAIL}\n */\n`, 'java'), { kind: 'code' }), [])
    assert.deepEqual(hits(commentText(`# ${HEAD}\n#   a.sh store    # ${TAIL}\n`, 'sh'), { kind: 'code' }), [])
  })

  test('タグの文の型（波括弧の組）は字数に数えない', () => {
    const type = `{{ ${'a'.repeat(40)}: string,\n *   ${'b'.repeat(40)}: number }}`
    assert.deepEqual(hits(commentText(`/**\n * @returns ${type} ${HEAD}\n */\n`, 'ts'), { kind: 'code' }), [])
    assert.deepEqual(hits(commentText(`/**\n * @returns {string} ${HEAD}\n *   ${TAIL}\n */\n`, 'ts'), { kind: 'code' }), ['length:2'])
  })

  test('JSDoc のタグの行は、字下げした続きの行とつなぐ', () => {
    assert.deepEqual(hits(commentText(`/**\n * @param {string} a ${HEAD}\n *   ${TAIL}\n */\n`, 'ts'), { kind: 'code' }), ['length:2'])
  })

  test('追加行を渡すと、文のどれかの行が追加行のときだけ示し、行は文の行のうち最初の追加行にする', () => {
    const text = `${HEAD}\n${'う'.repeat(10)}\n${TAIL}`
    assert.deepEqual(hits(text, { added: new Set([2]) }), ['length:2'])
    assert.deepEqual(hits(text, { added: new Set([3, 2]) }), ['length:2'])
    assert.deepEqual(hits(text, { added: new Set([4]) }), [])
    assert.deepEqual(hits(text, { added: null }), ['length:1'])
  })

  test('styleKind は、Markdown、yml、コードの測り方を返す', () => {
    assert.equal(jaText.styleKind('docs/a.md'), 'markdown')
    assert.equal(jaText.styleKind('.github/workflows/ci.yml'), 'lines')
    assert.equal(jaText.styleKind('scripts/ci/a.mjs'), 'code')
  })
})

describe('日本語の文の検査（インラインコードの扱い）', () => {
  /** @param {string} text 文書 @returns {number[]} 読点の数（1 個以上の文ごと。文の順） */
  function commaCounts(text) {
    return findStyleWarnings(text, { comma: 1 })
      .filter((hit) => hit.kind === 'comma')
      .map((hit) => Number(/読点が (\d+) 個/.exec(hit.use)[1]))
  }

  test('語の検査は、インラインコードの中の一致を数えない', () => {
    const terms = [{ word: '道具', use: 'ツール' }]
    assert.deepEqual(findTerms('`道具` と書く', terms), [])
    assert.equal(findTerms('`道具` と道具', terms).length, 1)
  })

  test('pattern の項目も、インラインコードの中の一致を数えない。行番号は元の行である', () => {
    const terms = [{ pattern: '道[具ぐ]', use: 'ツール', examples: { hit: ['道具'], miss: ['道'] } }]
    assert.deepEqual(findTerms('あ\n`道具`\n道ぐ', terms).map((hit) => hit.line), [3])
  })

  test('インラインコードをまたいだ一致は作らない', () => {
    assert.deepEqual(findTerms('道`x`具', [{ word: '道具', use: 'ツール' }]), [])
  })

  test('許す語の照合も、インラインコードを除いた文で行う', () => {
    const terms = [{ word: 'ブラウザ', use: 'ブラウザー', allow: { ブラウザー: '長音を付けた正しい語' } }]
    assert.deepEqual(findTerms('`ブラウザー` とブラウザ', terms).map((hit) => hit.line), [1])
  })

  test('許す語がインラインコードの中にしか出ないときは、使われていない（不要）と見る', () => {
    const terms = [{ word: 'ブラウザ', use: 'ブラウザー', allow: { ブラウザー: '長音を付けた正しい語' } }]
    const problems = findAllowProblems(['`ブラウザー` を開く'], terms)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /ブラウザー.*不要/)
    assert.deepEqual(findAllowProblems(['`ブラウザー` を開く', 'ブラウザーを開く'], terms), [])
  })

  test('コードのコメントの中のインラインコードも、語の検査で数えない', () => {
    assert.deepEqual(findCodeTerms('a.mjs', '// `道具` を使う', [{ word: '道具', use: 'ツール' }]), [])
    assert.equal(findCodeTerms('a.mjs', '// `x` と道具', [{ word: '道具', use: 'ツール' }]).length, 1)
  })

  test('tasks の範囲の行：インラインコードだけの項目を区切る読点は 0 と数える', () => {
    assert.deepEqual(commaCounts('このタスクの範囲：`scripts/ci/ja-text.mjs`、`scripts/ci/ja-text.json`、`scripts/ci/lib/`、`scripts/ci/migrations.mjs`'), [])
  })

  test('参照の行：リンクの後に「第 N 節」が付いた項目も、リンクだけの項目として扱う', () => {
    assert.deepEqual(commaCounts('このタスクの参照：[plan](../plan.md) 第 5 節、[plan](../plan.md) 第 7 節、[plan](../plan.md) 第 5.2 節'), [])
  })

  test('他の語を含む項目の前後の読点は数える', () => {
    assert.deepEqual(commaCounts('このタスクの参照：[plan](../plan.md) 第 5 節、[plan](../plan.md) 第 7 節、[spec](../spec.md) の DS-283-5'), [1])
    assert.deepEqual(commaCounts('`a`、`b` を使い、`c` を直す。'), [2])
  })

  test('コロンまでを除くのは最初の項目だけである。語の見出しとインラインコードを組にした項目の間の読点は数える', () => {
    // かなを含まない文は文として数えないので、最後に「とする」を付ける
    assert.deepEqual(commaCounts('入力：`a`、出力：`b`、設定：`c`、前提：`d`、結果：`e`、備考：`f` とする。'), [5])
  })

  test('普通の文の読点は数える', () => {
    assert.deepEqual(commaCounts('一、二、三、四とする。'), [3])
  })

  test('インラインコードの読点そのものは数えない。字数と「の」は今のとおり', () => {
    assert.deepEqual(commaCounts('`a、b、c` を使う。'), [])
    assert.equal(findStyleWarnings('本日の会議の資料の変更の履歴を見る。', { no: 4 }).length, 1)
    assert.equal(findStyleWarnings('範囲：`a`、`b` を直す。', { length: 10 }).length, 1)
  })
})

describe('日本語の文の検査（表のセルの字数の合計）', () => {
  /** @param {string} text 文書 @returns {number[]} セルごとの合計（行の順） */
  function totals(text) {
    return jaText.tableCellTotals(text).map((cell) => cell.total)
  }

  test('1 つのセルの複数の文の字数を合計する。「。」は数えず、かなのないセルは数えない', () => {
    assert.deepEqual(totals('| 項目 | あいう。えお。 |'), [5])
  })

  test('リンクは表示の文字列、インラインコードは 1 字、URL は 1 字で数える', () => {
    assert.deepEqual(totals('| [あいう](https://example.com/x)と`code`と https://example.com/y です |'), [11])
  })

  test('表の区切り行と、表でない行は数えない。行番号は元の行である', () => {
    assert.deepEqual(jaText.tableCellTotals('| あ | い |\n|---|---|\nただの文である。'), [
      { line: 1, total: 1 },
      { line: 1, total: 1 },
    ])
  })

  test('コードブロックの中の表は数えない', () => {
    assert.deepEqual(totals('```\n| あいう | えお |\n```\n| か |'), [1])
  })
})

describe('日本語の文の検査（warning と終了コード）', () => {
  /**
   * 一時の git リポジトリで検査を実行する。
   *
   * @param {Record<string, string>} files パスと内容
   * @param {string[]} args 引数
   * @returns {import('node:child_process').SpawnSyncReturns<string>} 実行の結果
   */
  function runIn(files, args) {
    const dir = mkdtempSync(path.join(tmpdir(), 'ja-text-repo-'))
    try {
      spawnSync('git', ['init', '-q'], { cwd: dir, env: withoutGitEnv() })
      for (const [name, content] of Object.entries(files)) {
        const full = path.join(dir, name)
        mkdirSync(path.dirname(full), { recursive: true })
        writeFileSync(full, content)
      }
      return spawnSync(process.execPath, [SCRIPT, ...args], { cwd: dir, encoding: 'utf8', env: withoutGitEnv() })
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  }

  const longSentence = `${'あ'.repeat(200)}である。要求を送る。少し修正する。\n`
  const files = { 'docs/a.md': longSentence, 'src/a.mjs': `// ${'い'.repeat(200)}である。\n` }

  test('warning だけのとき、既定の実行は成功し、最後の行は要約の行である', () => {
    // 許す語が使われなくなったという error を出さないよう、一覧の全ての許す語を 1 行ずつ書く。
    // リポジトリ本体では実行しない（commit の hook は、Git のない一時の場所で検査を実行するため）
    const allowWords = [...list.error, ...list.warning].flatMap((item) => Object.keys(item.allow ?? {}))
    const result = runIn({ ...files, 'docs/allow.md': `${allowWords.join('\n')}\n` }, [])
    assert.equal(result.status, 0)
    assert.match(result.stdout, /^警告：warning が \d+ 件/m)
    const last = result.stdout.trimEnd().split('\n').at(-1)
    assert.match(last, /^[^：]+：.*\d/)
  })

  test('--warning は文体・崩れの疑い・「要求」を一覧にし、成功する。最後の行は要約の行である', () => {
    const result = runIn(files, ['--warning'])
    assert.equal(result.status, 0)
    assert.match(result.stdout, /docs\/a\.md:1 .*字数/)
    assert.match(result.stdout, /src\/a\.mjs:1 .*字数/)
    assert.match(result.stdout, /docs\/a\.md:1 .*修正/)
    assert.match(result.stdout, /docs\/a\.md:1 要求/)
    const last = result.stdout.trimEnd().split('\n').at(-1)
    assert.match(last, /^[^：]+：.*\d/)
  })

  test('--warning は、複数の行にまたがる文を 1 文として測り、文の最初の行で示す', () => {
    const result = runIn({ 'docs/c.md': `短い文である。\n${'あ'.repeat(60)}\n${'い'.repeat(57)}である。\n`, 'src/c.mjs': `// ${'あ'.repeat(60)}\n// ${'い'.repeat(57)}である。\n` }, ['--warning'])
    assert.equal(result.status, 0)
    assert.match(result.stdout, /^docs\/c\.md:2 .*字数が 120 字/m)
    assert.match(result.stdout, /^src\/c\.mjs:1 .*字数が 120 字/m)
  })

  test('error があれば失敗にする', () => {
    const result = runIn({ ...files, 'docs/b.md': '道具を使う。\n' }, [])
    assert.equal(result.status, 1)
    assert.match(result.stdout, /docs\/b\.md:1 道具/)
  })
})

describe('日本語の文の検査（差分の追加行の文体：--changed）', () => {
  // CI の push のイベントの変数があると基準の選び方が変わるので外す（手元と CI で同じ結果にする）
  const env = withoutGitEnv()
  delete env.GITHUB_EVENT_NAME
  delete env.GITHUB_REF_NAME
  const repos = []
  after(() => repos.forEach((repo) => rmSync(repo, { recursive: true, force: true })))

  /** 字数が閾値（100）以上の文 */
  const LONG = `${'あ'.repeat(100)}である。`
  /** 読点が閾値（5）以上の文 */
  const COMMAS = '一、二、三、四、五、六とする。'
  /** 文の字数の合計が閾値（80）以上のセルの行（文の字数は 100 に届かない） */
  const LONG_CELL = `| 項目 | ${'い'.repeat(80)} |`

  /**
   * 試すリポジトリを作る。files を develop に commit し、作業ブランチ（feature）に切り替えた状態で返す。
   *
   * @param {Record<string, string>} files develop にあるファイル
   * @param {{ developRef?: string }} [options] develop を置く ref
   */
  function createRepo(files, { developRef = 'refs/heads/develop' } = {}) {
    const repo = mkdtempSync(path.join(tmpdir(), 'ja-text-changed-'))
    repos.push(repo)
    const git = (...args) => execFileSync('git', args, { cwd: repo, env, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] })
    git('init', '-q', '-b', 'main')
    git('config', 'user.email', 'test@example.com')
    git('config', 'user.name', 'test')
    git('config', 'commit.gpgsign', 'false')
    git('config', 'core.autocrlf', 'false')
    const write = (name, content) => {
      const full = path.join(repo, name)
      mkdirSync(path.dirname(full), { recursive: true })
      writeFileSync(full, content)
    }
    const commit = (message) => {
      git('add', '-A')
      git('commit', '-q', '--allow-empty', '-m', message)
    }
    for (const [name, content] of Object.entries(files)) write(name, content)
    commit('base')
    git('update-ref', developRef, 'HEAD')
    git('checkout', '-q', '-b', 'feature')
    const run = (cwd = repo) => spawnSync(process.execPath, [SCRIPT, '--changed'], { cwd, env, encoding: 'utf8' })
    return { repo, git, write, commit, run }
  }

  /**
   * 注釈の行を「パス:行」の並びにする。
   *
   * @param {string} stdout 出力
   * @returns {string[]} 注釈ごとの「パス:行」
   */
  function annotated(stdout) {
    return [...stdout.matchAll(/^::warning file=([^,]+),line=(\d+)::.+$/gm)].map((match) => `${match[1]}:${match[2]}`)
  }

  test('追加行の長い文、読点の多い文、長いセルに、注釈の形の警告を 1 件 1 行で出す。終了コードは 0 で、最後の行は件数である', () => {
    const r = createRepo({ 'docs/a.md': '短い文である。\n' })
    r.write('docs/a.md', `短い文である。\n${LONG}\n${COMMAS}\n${LONG_CELL}\n`)
    r.commit('追加')
    const result = r.run()
    assert.equal(result.status, 0, result.stdout + result.stderr)
    assert.deepEqual(annotated(result.stdout), ['docs/a.md:2', 'docs/a.md:3', 'docs/a.md:4'])
    assert.match(result.stdout, /^::warning file=docs\/a\.md,line=2::.*字数が 103 字/m)
    assert.match(result.stdout, /^::warning file=docs\/a\.md,line=3::.*読点が 5 個/m)
    assert.match(result.stdout, /^::warning file=docs\/a\.md,line=4::.*80 字/m)
    assert.match(result.stdout.trimEnd().split('\n').at(-1), /：3/)
  })

  test('既存の行（追加行でない行）には警告を出さない', () => {
    const r = createRepo({ 'docs/a.md': `${LONG}\n短い文である。\n${COMMAS}\n${LONG_CELL}\n` })
    r.write('docs/a.md', `${LONG}\n短い文を変えた。\n${COMMAS}\n${LONG_CELL}\n${LONG}\n`)
    r.commit('変更')
    const result = r.run()
    assert.equal(result.status, 0)
    assert.deepEqual(annotated(result.stdout), ['docs/a.md:5'])
  })

  test('複数の行にまたがる文：既存の行だけでできた文には出さず、1 行を足して長くなった文には、足した行に出す', () => {
    const head = 'あ'.repeat(60)
    const tail = `${'い'.repeat(57)}である。`
    // 足した行（95 字）は、1 行では閾値（100）に届かない。前後の行とつなぐと閾値を超える
    const r = createRepo({ 'docs/a.md': `${head}\n${tail}\n\nこれは\n短い文である。\n`, 'src/a.mjs': `// 短い\n// 文である。\n` })
    r.write('docs/a.md', `${head}\n${tail}\n\nこれは\n${'う'.repeat(95)}\n短い文である。\n`)
    r.write('src/a.mjs', `// 短い\n// ${'え'.repeat(95)}\n// 文である。\n`)
    r.commit('変更')
    const result = r.run()
    assert.equal(result.status, 0, result.stdout + result.stderr)
    assert.deepEqual(annotated(result.stdout), ['docs/a.md:5', 'src/a.mjs:2'])
  })

  test('行末のコメントは、次の行のコメントとつながない（短い注だけの追加行に警告を出さない）', () => {
    const r = createRepo({ 'src/b.mjs': `const a = 1 // ${'え'.repeat(60)}\n` })
    r.write('src/b.mjs', `const a = 1 // ${'え'.repeat(60)}\n// ${'お'.repeat(57)}である。\n`)
    r.commit('追加')
    const result = r.run()
    assert.equal(result.status, 0, result.stdout + result.stderr)
    assert.deepEqual(annotated(result.stdout), [])
  })

  test('改行コードだけを変えた既存の行（LF から CRLF）は追加行にしない。足した行だけに警告を出す', () => {
    const r = createRepo({ 'docs/a.md': `${LONG}\n短い文である。\n` })
    r.write('docs/a.md', `${LONG}\r\n短い文である。\r\n${COMMAS}\r\n`)
    r.commit('改行コードの変更と追加')
    assert.deepEqual(annotated(r.run().stdout), ['docs/a.md:3'])
  })

  test('develop から分けたばかりで commit がない作業ブランチでは、HEAD との差分（作業ツリーの変更）だけを見る', () => {
    const r = createRepo({ 'docs/a.md': '短い文である。\n' })
    r.write('docs/a.md', `短い文である。\n${LONG}\n`)
    r.commit('develop の最後の commit')
    r.git('update-ref', 'refs/heads/develop', 'HEAD')
    r.write('docs/a.md', `短い文である。\n${LONG}\n${COMMAS}\n`)
    assert.deepEqual(annotated(r.run().stdout), ['docs/a.md:3'])
  })

  test('commit していない変更と、追跡していない新しい文書の行も、追加行として見る', () => {
    const r = createRepo({ 'docs/a.md': '短い文である。\n' })
    r.write('docs/a.md', `短い文である。\n${LONG}\n`)
    r.write('docs/new.md', `${COMMAS}\n`)
    const result = r.run()
    assert.deepEqual(annotated(result.stdout), ['docs/a.md:2', 'docs/new.md:1'])
  })

  test('「の」の連なり、否定、複合動詞の崩れ、語の検査は出さない', () => {
    const r = createRepo({ 'docs/a.md': '短い文である。\n' })
    r.write('docs/a.md', '短い文である。\n本日の会議の資料の変更の履歴を見る。\n原因は分からなく修正もできない。\n少し修正する。\n道具を使う。要求を送る。\n')
    r.commit('追加')
    const result = r.run()
    assert.equal(result.status, 0)
    assert.deepEqual(annotated(result.stdout), [])
  })

  test('表の区切り行、コードブロック、インラインコード、URL は数えない', () => {
    const r = createRepo({ 'docs/a.md': '短い文である。\n' })
    const fenced = ['```', LONG, COMMAS, '```'].join('\n')
    const inline = `ここは \`${'の'.repeat(120)}\` と https://example.com/${'あ'.repeat(120)} と [リンク](https://example.com/${'い'.repeat(120)}) だ。`
    r.write('docs/a.md', `短い文である。\n${fenced}\n|${'-'.repeat(120)}|\n${inline}\n`)
    r.commit('追加')
    assert.deepEqual(annotated(r.run().stdout), [])
  })

  test('対象は既定の実行と同じファイルで、コードはコメントとテスト名だけを見る。削除したファイルは見ない', () => {
    const r = createRepo({ 'docs/gone.md': '短い文である。\n' })
    r.write('docs/08-spec/archive/old.md', `${LONG}\n`)
    r.write('notes.txt', `${LONG}\n`)
    r.write('src/a.mjs', `const s = '${LONG}'\n// ${LONG}\n`)
    rmSync(path.join(r.repo, 'docs/gone.md'))
    r.commit('追加と削除')
    const result = r.run()
    assert.equal(result.status, 0, result.stdout + result.stderr)
    assert.deepEqual(annotated(result.stdout), ['src/a.mjs:2'])
  })

  test('名前を変えたファイルは、変えていない行を追加行として数えない', () => {
    const body = Array.from({ length: 20 }, (_, index) => `${index}${LONG}`).join('\n')
    const r = createRepo({ 'docs/old.md': `${body}\n` })
    r.git('mv', 'docs/old.md', 'docs/new.md')
    r.write('docs/new.md', `${body}\n${COMMAS}\n`)
    r.commit('移動')
    assert.deepEqual(annotated(r.run().stdout), ['docs/new.md:21'])
  })

  test('改行コードが CRLF のファイルでも、行番号は元の行である', () => {
    const r = createRepo({ 'docs/a.md': '短い文である。\r\n次の文である。\r\n' })
    r.write('docs/a.md', `短い文である。\r\n次の文である。\r\n${LONG}\r\n${LONG_CELL}\r\n`)
    r.write('src/a.mjs', `// 短い文である。\r\n// ${COMMAS}\r\n`)
    r.commit('追加')
    assert.deepEqual(annotated(r.run().stdout), ['docs/a.md:3', 'docs/a.md:4', 'src/a.mjs:2'])
  })

  test('develop への push（merge-base が HEAD 自身）では、1 つ目の親との差分を見る', () => {
    const r = createRepo({ 'docs/a.md': `${LONG}\n` }, { developRef: 'refs/remotes/origin/develop' })
    r.git('checkout', '-q', '-B', 'develop')
    r.write('docs/a.md', `${LONG}\n${COMMAS}\n`)
    r.commit('push')
    r.git('update-ref', 'refs/remotes/origin/develop', 'HEAD')
    const result = r.run()
    assert.equal(result.status, 0)
    assert.deepEqual(annotated(result.stdout), ['docs/a.md:2'])
  })

  test('origin/develop があれば、その分岐点からの差分を見る（CI の checkout）', () => {
    const r = createRepo({ 'docs/a.md': '短い文である。\n' }, { developRef: 'refs/remotes/origin/develop' })
    r.write('docs/a.md', `短い文である。\n${LONG}\n`)
    r.commit('追加')
    assert.deepEqual(annotated(r.run().stdout), ['docs/a.md:2'])
  })

  test('shallow clone では差分を求められないので失敗にする', () => {
    const r = createRepo({ 'docs/a.md': '短い文である。\n' })
    r.write('docs/a.md', `短い文である。\n${LONG}\n`)
    r.commit('追加')
    const shallow = mkdtempSync(path.join(tmpdir(), 'ja-text-shallow-'))
    repos.push(shallow)
    execFileSync('git', ['clone', '-q', '--depth', '1', '--no-local', pathToFileURL(r.repo).href, path.join(shallow, 'clone')], { env, stdio: 'ignore' })
    const result = r.run(path.join(shallow, 'clone'))
    assert.notEqual(result.status, 0)
    assert.ok(result.stdout.includes(SHALLOW_CLONE_MESSAGE), result.stdout)
  })

  test('差分の本文の「+++」で始まる追加行を、ファイルの見出しと取り違えない。引用符で囲んだパスと、空白のあるパスを読む', () => {
    const diff = [
      'diff --git a/docs/a.md b/docs/a.md',
      '--- a/docs/a.md',
      '+++ b/docs/a.md',
      '@@ -1,0 +2,2 @@',
      '+++ b/docs/x.md',
      '+次の行',
      '@@ -5 +7 @@',
      '-古い行',
      '+新しい行',
      'diff --git "a/docs/q\\"\\346\\227\\245.md" "b/docs/q\\"\\346\\227\\245.md"',
      '+++ "b/docs/q\\"\\346\\227\\245.md"',
      '@@ -0,0 +1 @@',
      '+あ',
      '\\ No newline at end of file',
      '+++ b/docs/a b.md\t',
      '@@ -3,2 +3,0 @@',
      '-消した行',
      '-消した行',
      '',
    ].join('\n')
    assert.deepEqual(
      [...jaText.parseAddedLines(diff)].map(([file, lines]) => [file, [...lines]]),
      [['docs/a.md', [2, 3, 7]], ['docs/q"日.md', [1]], ['docs/a b.md', []]],
    )
  })

  test('注釈の内容とパスの、GitHub Actions が区切りに使う字を逃がす', () => {
    assert.equal(jaText.formatAnnotation('docs/a,b:c.md', 3, '100% の文\n次'), '::warning file=docs/a%2Cb%3Ac.md,line=3::100%25 の文%0A次')
  })
})
