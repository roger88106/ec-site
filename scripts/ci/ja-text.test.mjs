import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { withoutGitEnv } from './lib/git-env.mjs'
import { findAllowProblems, findCodeTerms, findStyleWarnings, findTerms, isTargetCode, isTargetDoc } from './ja-text.mjs'

const SCRIPT = fileURLToPath(new URL('./ja-text.mjs', import.meta.url))
const list = JSON.parse(readFileSync(new URL('./ja-text.json', import.meta.url), 'utf8'))

/** 改善のまとまりを数える中国語の字（U+6279）。ファイルには字を書かず、エスケープで書く */
const BATCH = '\u6279'

/** 「〜し直す」の動詞の連用形に「修正」が続く、崩れと確定している複合動詞の 15 語（spec 249 の第 1 節） */
const BROKEN_VERBS = ['書き', '作り', '置き', '読み', 'やり', '考え', '組み', '問い', '洗い', '貼り', '撮り', '練り', '言い', '数え', '測り']

/**
 * コミットメッセージのファイルを調べる入口を実行する。
 *
 * @param {string} message コミットメッセージの内容
 * @returns {import('node:child_process').SpawnSyncReturns<string>} 実行の結果
 */
function runMessage(message) {
  const file = path.join(mkdtempSync(path.join(tmpdir(), 'ja-text-')), 'COMMIT_EDITMSG')
  writeFileSync(file, message)
  return spawnSync(process.execPath, [SCRIPT, '--message', file], { encoding: 'utf8', env: withoutGitEnv() })
}

describe('日本語の文の検査（一覧と error の項目）', () => {
  test('一覧の各項目が error か warning のどちらかである', () => {
    assert.deepEqual(Object.keys(list).sort(), ['error', 'style', 'warning'])
    for (const item of [...list.error, ...list.warning]) {
      assert.equal(typeof item.word, 'string')
      assert.equal(typeof item.use, 'string')
    }
  })

  test('改善のまとまりを数える字が error になる', () => {
    assert.equal(findTerms(`3${BATCH}に分ける`, list.error).length, 1)
  })

  test('改善のまとまりを数える字で始まる日本語の語（\u6279\u5224・\u6279\u8a55）は通る', () => {
    assert.deepEqual(findTerms('\u6279\u5224的な意見と\u6279\u8a55', list.error), [])
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

  test('許す語が使われなくなっていない（一覧の許す語が、全て理由つきである）', () => {
    assert.deepEqual(findAllowProblems(['\u6279\u5224と\u6279\u8a55'], list.error.filter((item) => item.word === BATCH)), [])
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
    assert.throws(() => findTerms('何か', terms), /ウェブ/)
  })

  test('許す語を配列で書くのは設定の誤りとして失敗にする', () => {
    const terms = [{ word: 'ブラウザ', use: 'ブラウザー', allow: ['ブラウザー'] }]
    assert.throws(() => findTerms('何か', terms), /理由/)
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
    assert.equal(isTargetDoc('docs/frontend/coding.md'), true)
    assert.equal(isTargetDoc('README.md'), true)
    assert.equal(isTargetDoc('AGENTS.md'), true)
    assert.equal(isTargetDoc('.claude/skills/docs/SKILL.md'), true)
    assert.equal(isTargetDoc('docs/specs/019-mvp-completion/plan.md'), true)
    assert.equal(isTargetDoc('docs/specs/mvp.md'), true)
    assert.equal(isTargetDoc('docs/specs/archive/mvp/002-cart-and-order/spec.md'), false)
    assert.equal(isTargetDoc('frontend/store/README.md'), false)
  })

  test('言い換える前の語を例に挙げる、言い回しの規則の文書は見ない', () => {
    assert.equal(isTargetDoc('.claude/skills/docs/tech-writing.md'), false)
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
    for (const key of ['no', 'length', 'comma']) {
      assert.equal(typeof list.style[key], 'number', key)
    }
    assert.ok(list.style.compound.includes('修正'))
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
    spawnSync('git', ['init', '-q'], { cwd: dir, env: withoutGitEnv() })
    for (const [name, content] of Object.entries(files)) {
      const full = path.join(dir, name)
      mkdirSync(path.dirname(full), { recursive: true })
      writeFileSync(full, content)
    }
    return spawnSync(process.execPath, [SCRIPT, ...args], { cwd: dir, encoding: 'utf8', env: withoutGitEnv() })
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

  test('error があれば失敗にする', () => {
    const result = runIn({ ...files, 'docs/b.md': '道具を使う。\n' }, [])
    assert.equal(result.status, 1)
    assert.match(result.stdout, /docs\/b\.md:1 道具/)
  })
})
