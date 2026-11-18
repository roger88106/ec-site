import assert from 'node:assert/strict'
import { spawn, spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, describe, test } from 'node:test'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { withoutGitEnv } from '../../shared/git/git-env.mjs'
import { commentText, trailingCommentLines } from '../lib/source/comment-text.mjs'
import { SHALLOW_CLONE_MESSAGE } from '../lib/git/shallow-clone.mjs'
import * as jaText from './ja-text.mjs'
import { applyPermits, findAllowProblems, findCodeTerms, findStyleWarnings, findTerms, isTargetCode, stripMessageComments, validateTerms } from './ja-text.mjs'
import { isTargetDoc } from '../lib/docs/ja-docs.mjs'

const SCRIPT = fileURLToPath(new URL('./ja-text.mjs', import.meta.url))
const list = JSON.parse(readFileSync(new URL('./ja-text.json', import.meta.url), 'utf8'))

/** 改善のまとまりを数える中国語の字（U+6279）。ファイルには字を書かず、エスケープで書く */
const BATCH = '\u6279'

/** 「〜し直す」の動詞の連用形に「修正」が続く、崩れと確定している複合動詞（「〜し直す」の崩れを調べた spec の「背景と目的」） */
const BROKEN_VERBS = ['書き', '作り', '置き', '読み', 'やり', '考え', '組み', '問い', '洗い', '貼り', '撮り', '練り', '言い', '数え', '測り']

/**
 * コマンドを非同期に実行する。git の一時リポジトリを使うテストを並べて実行するため（Windows では起動が遅い）。
 *
 * @param {string} command コマンド
 * @param {string[]} args 引数
 * @param {{ cwd: string, env: Record<string, string | undefined> }} options 実行する場所と環境変数
 * @returns {Promise<{ status: number | null, stdout: string, stderr: string }>} 終了コードと出力
 */
function runAsync(command, args, { cwd, env }) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd, env, stdio: ['ignore', 'pipe', 'pipe'] })
    let stdout = ''
    let stderr = ''
    child.stdout.setEncoding('utf8').on('data', (chunk) => (stdout += chunk))
    child.stderr.setEncoding('utf8').on('data', (chunk) => (stderr += chunk))
    child.on('error', reject)
    child.on('close', (status) => resolve({ status, stdout, stderr }))
  })
}

/**
 * コミットメッセージのファイルを調べる入口を実行する。
 *
 * @param {string} message コミットメッセージの内容
 * @param {string[]} [options] 加える印（--warning など）
 * @returns {import('node:child_process').SpawnSyncReturns<string>} 実行の結果
 */
function runMessage(message, options = []) {
  const dir = mkdtempSync(path.join(tmpdir(), 'ja-text-'))
  try {
    const file = path.join(dir, 'COMMIT_EDITMSG')
    writeFileSync(file, message)
    return spawnSync(process.execPath, [SCRIPT, ...options, '--message', file], { encoding: 'utf8', env: withoutGitEnv() })
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
}

describe('日本語の文の検査（一覧と error の項目）', () => {
  test('一覧の各項目が error か warning のどちらかである', () => {
    assert.deepEqual(Object.keys(list).sort(), ['error', 'permit', 'style', 'warning'])
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

  test('崩れと確定している複合動詞が error になる', () => {
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

  test('一覧の許容用語が全て理由つきである（allow は配列でなく、各値が空でない文字列）', () => {
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

  test('一覧の許容用語が全て使われている場合は問題にならない', () => {
    const items = list.error.filter((item) => item.word === '版')
    assert.equal(items.length, 1)
    const allows = Object.keys(items[0].allow)
    assert.ok(allows.length > 0)
    assert.deepEqual(findAllowProblems([allows.join('と')], items), [])
  })
})

describe('日本語の文の検査（校閲の評価の一覧の語）', () => {
  test('「判定器」は error になり、「判定」「検査」は通る', () => {
    assert.equal(findTerms('判定器や検査に止められる形', list.error).length, 1)
    assert.deepEqual(findTerms('判定の結果を検査の script が示す', list.error), [])
  })

  test('「護り」は error になり、「守り」「防止」は通る', () => {
    assert.equal(findTerms('護りの spec では、回避の形を高にしない', list.error).length, 1)
    assert.deepEqual(findTerms('守りの設定と誤りの防止', list.error), [])
  })
})

/** 和語の「足す」 */
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

  test('files の正規表現が読めない項目は、項目の名前と files を示す設定の誤りになる', () => {
    assert.throws(() => validateTerms([{ word: '道具', use: 'ツール', files: '(' }]), /「道具」の files「\(」：正規表現として不正/)
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

  test('word の項目と pattern の項目を、同じ一覧に混ぜられる', () => {
    const terms = [{ word: '道具', use: 'ツール' }, { pattern: '\\d', use: 'u', examples: { hit: ['1'], miss: ['a'] } }]
    assert.deepEqual(findTerms('道具と1', terms).map((hit) => hit.word), ['道具', '1'])
  })

  test('files を書いた項目は、当たるファイルにだけ当て、コミットメッセージには当てない', () => {
    const terms = [{ word: '道具', use: 'ツール' }, { word: 'X', use: 'Y', files: '^docs/05-' }]
    assert.equal(jaText.termsFor(terms, 'docs/05-開発規則/a.md').length, 2)
    assert.equal(jaText.termsFor(terms, 'docs/06-手順書/a.md').length, 1)
    assert.equal(jaText.termsFor(terms, null).length, 1)
  })

  test('規則の本文の AI のサービス固有の名前は、規則の文書（constitution と 05-開発規則）でだけ error にする', () => {
    const term = list.error.find((item) => item.files !== undefined && item.pattern.includes('claude'))
    for (const file of ['docs/constitution.md', 'docs/05-開発規則/writing.md']) assert.deepEqual(jaText.termsFor([term], file), [term], file)
    for (const file of ['docs/06-手順書/setup.md', 'AGENTS.md', '.claude/skills/sessions/SKILL.md']) assert.deepEqual(jaText.termsFor([term], file), [], file)
    assert.equal(findTerms('[規則](../../CLAUDE.md) と Gemini', [term]).map((hit) => hit.word).join(), 'Gemini')
  })

  test('--message でも pattern の項目が働き、漢字の後の形は成功する', () => {
    assert.equal(runMessage(`docs: 不${ADD}る\n`).status, 0)
    assert.equal(runMessage(`docs: 数を${ADD}\n`).status, 1)
  })
})

/**
 * 一覧の中で、例に当たる項目を 1 つだけ返す（その項目に、当たらない例を当てるため）。
 *
 * @param {object[]} terms 一覧
 * @param {string} hitExample 当たるはずの例
 * @returns {object} 項目
 */
function termHitBy(terms, hitExample) {
  const items = terms.filter((item) => findTerms(hitExample, [item]).length > 0)
  assert.equal(items.length, 1, `${hitExample} に当たる項目の数`)
  return items[0]
}

/** @param {string} text 文 @returns {number} 一覧の error の語の一致の数 */
function errorHits(text) {
  return findTerms(text, list.error).length
}

/**
 * 一覧の error の項目のうち、文に当たった項目の名前（word か pattern）を返す。別の項目に当たっても通る確認にしないため。
 *
 * @param {string} text 文
 * @returns {string[]} 当たった項目の名前
 */
function errorTermNames(text) {
  return findTerms(text, list.error).map((hit) => hit.pattern ?? hit.word)
}

/**
 * 文が、名前に断片を含む項目に当たることを確認する。
 *
 * @param {string} text 文
 * @param {string} fragment 当たるはずの項目の名前（word か pattern）に含まれる断片
 */
function assertHitBy(text, fragment) {
  assert.ok(errorTermNames(text).some((name) => name.includes(fragment)), `${text} が ${fragment} の項目に当たる（当たった項目：${errorTermNames(text).join(' | ')}）`)
}

describe('言い換えの一覧（活用と語。DC-566-11・12）', () => {
  test('足す の活用（足し・足さ・足す・足せ・足そ）を、語の一部や漢字の直後以外は error にする', () => {
    for (const text of ['数を足して返す', '足さない', '足せば', '送料を足した額', '足し忘れを型エラーにする', '付け足す', '足します', '足そう']) {
      assertHitBy(text, '足[しさすせそ]')
    }
  })

  test('漢字の直後の 足 と、足りない は、足す の活用にしない', () => {
    for (const text of ['不足して', '補足して', '足りない']) assert.equal(errorHits(text), 0, text)
  })

  test('足し算 は許容用語にして、error にしない', () => {
    assert.equal(errorHits('足し算で求める'), 0)
  })

  test('要る の活用（要ら・要り・要れ・要る）を error にし、名詞の直後に助詞を抜いた形も当てる', () => {
    for (const text of ['設定は要らない', '要ります', '要れば', '設定要らない', '確認要りますか']) assertHitBy(text, '要[らりるれ]')
  })

  test('必・重・不・主・概・需 の直後の 要 と、要約・要件 は、要る の活用にしない', () => {
    for (const text of ['必要らしい', '重要らしい', '不要らしい', '主要らしい', '概要らしい', '需要らしい', '要約する', '要件']) assert.equal(errorHits(text), 0, text)
  })

  test('見直す の活用（見直さ・見直せ）を error にする', () => {
    for (const text of ['文書を見直さない', '見直せば', '文書を見直す', '見直しを行う']) assertHitBy(text, '見直[')
  })

  test('worktree は、大文字と小文字を区別せず error にする', () => {
    for (const text of ['Worktree を作る', 'WORKTREE', 'WorkTree を作る', 'worktree を作る']) assertHitBy(text, '(?i:worktree)')
  })

  test('識別子の一部の Worktree（直前が英数字・_）は、error にしない', () => {
    assert.equal(errorHits('// listWorktrees を呼ぶ'), 0)
    assert.equal(errorHits('hit_Worktree を呼ぶ'), 0)
    const comment = '/**\n * @param {{ listWorktrees: () => string }} deps 作業ツリーの一覧\n */'
    assert.deepEqual(findCodeTerms('a.mjs', comment, list.error), [])
  })

  test('git のコマンドの worktree は許容用語で、英字だけのインラインコードは見ない', () => {
    assert.equal(errorHits('git worktree'), 0)
    assert.equal(errorHits('`.claude/worktrees/` を見る'), 0)
  })

  test('カタカナの ワークツリー を error にする', () => {
    assertHitBy('ワークツリーを作る', 'ワークツリー')
  })

  test('口頭語 を error にし、口頭で伝える は当てない', () => {
    assertHitBy('口頭語で書く', '口頭語')
    assert.equal(errorHits('口頭で伝える'), 0)
  })

  test('箱の（箱の大きさ・ftyp の箱）を error にし、漢字・カタカナの直後の 箱 は当てない', () => {
    const term = termHitBy(list.error, 'ftyp の箱')
    for (const text of ['箱の大きさ', 'ftyp の箱']) assert.equal(findTerms(text, [term]).length, 1, text)
    for (const text of ['道具箱', 'ボックス', 'ゴミ箱の中身', '道具箱の中']) assert.deepEqual(findTerms(text, [term]), [], text)
  })

  test('主の（checkout・worktree・チェックアウト）を error にし、持ち主の は当てない', () => {
    // 主の worktree は worktree の項目にも当たるので、主の の項目を取り出して、その項目だけに当てる
    const terms = list.error.filter((item) => item.pattern?.includes('主の\\s?'))
    assert.equal(terms.length, 1)
    for (const text of ['主の checkout', '主の worktree', '主のチェックアウト']) assert.equal(findTerms(text, terms).length, 1, text)
    assert.deepEqual(findTerms('持ち主の checkout', [termHitBy(list.error, '主の checkout')]), [])
  })

  test('判定機 と 資料を集める役 を error にし、判定機能 と 資料を集める役割 は当てない', () => {
    assertHitBy('判定機を作る', '判定[器機]')
    assertHitBy('資料を集める役', '資料を集める役')
    for (const text of ['重複の判定機能を持つ', '資料を集める役割を担う']) assert.equal(errorHits(text), 0, text)
  })
})

describe('言い換えの一覧（インラインコードと許可の一覧。DC-566-13・14）', () => {
  test('かな・漢字を含むインラインコードの中の error の語を見つける。カタカナだけのものも見つける', () => {
    assert.ok(errorHits('`判定器の結果` を見る') > 0)
    assert.ok(errorHits('`ブラウザ` を開く') > 0)
  })

  test('warning の語は、今のとおり、全てのインラインコードの中を見ない（allCode）', () => {
    const terms = [{ word: '直す', use: '修正する' }]
    assert.deepEqual(findTerms('`直す` と書く', terms, { allCode: true }), [])
    assert.equal(findTerms('`直す` と書く', terms).length, 1)
    // コミットメッセージの入口でも、warning の語（直す）はインラインコードの中を見ない。error の段（--warning なし）では warning の語を数えないので、--warning を付ける
    assert.doesNotMatch(runMessage('docs: `直す` と書く\n', ['--warning']).stdout, /直す →/)
    assert.match(runMessage('docs: 直すと書く\n', ['--warning']).stdout, /直す →/)
  })

  test('コミットメッセージも、かな・漢字を含むインラインコードの中を検査し、英字だけのものは見ない', () => {
    assert.equal(runMessage('docs: `判定器` を直す\n').status, 1)
    assert.equal(runMessage('docs: 文書を直す\n\n本文に `足して` と書く\n').status, 1)
    assert.equal(runMessage('docs: `git worktree` を使う\n').status, 0)
  })

  const text = ['# 題', '', '## 用語の選び方', '', '`判定器` を使わない', '', '### 子の見出し', '', '`判定器` を使わない', '', '## 次の見出し', '', '`判定器` を使わない'].join('\n')
  const permit = { file: 'docs/x.md', heading: '用語の選び方', reason: '造語の例を書く' }
  const hitsOf = () => findTerms(text, list.error).map((hit) => ({ file: 'docs/x.md', ...hit }))

  test('許可した見出しの下（子の見出しを含む）の語は数えず、次の同じレベルの見出しからは数える', () => {
    const result = applyPermits(hitsOf(), [permit], () => text)
    assert.deepEqual(result.problems, [])
    assert.deepEqual(result.hits.map((hit) => hit.line), [13])
  })

  test('見出しを書かない許可は、ファイルの全体を許可する', () => {
    const result = applyPermits(hitsOf(), [{ file: 'docs/x.md', reason: 'この文書は使わない語を例に挙げる' }], () => text)
    assert.deepEqual(result, { hits: [], problems: [] })
  })

  test('許可していないファイルの語は、許可の外として残す', () => {
    const hits = hitsOf().map((hit) => ({ ...hit, file: 'docs/y.md' }))
    assert.equal(applyPermits(hits, [permit], (file) => (file === 'docs/x.md' ? text : null)).hits.length, 3)
  })

  test('どの文書の場所にも当たらない許可（ファイルがない・見出しがない）を失敗にする', () => {
    const missingFile = applyPermits(hitsOf(), [{ file: 'docs/none.md', reason: '理由' }], (file) => (file === 'docs/x.md' ? text : null))
    assert.equal(missingFile.problems.length, 1)
    assert.match(missingFile.problems[0], /docs\/none\.md.*ファイルがない/)
    const missingHeading = applyPermits(hitsOf(), [{ file: 'docs/x.md', heading: 'ない見出し', reason: '理由' }], () => text)
    assert.equal(missingHeading.problems.length, 1)
    assert.match(missingHeading.problems[0], /ない見出し.*見出しがない/)
  })

  test('場所はあるが、その中から error の語がなくなった許可を、使われなくなった許可として失敗にする', () => {
    const cleaned = text.replaceAll('`判定器`', '検査の script')
    const result = applyPermits(findTerms(cleaned, list.error).map((hit) => ({ file: 'docs/x.md', ...hit })), [permit], () => cleaned)
    assert.equal(result.problems.length, 1)
    assert.match(result.problems[0], /用語の選び方.*不要/)
  })

  test('許可の理由が空なら失敗にする', () => {
    const result = applyPermits(hitsOf(), [{ ...permit, reason: ' ' }], () => text)
    assert.equal(result.problems.length, 1)
    assert.match(result.problems[0], /理由がない/)
  })

  // この spec のファイルの許可は、アーカイブに移すときに外す一時のものなので、ここでは確認しない（外しても、このテストは成り立つ）
  test('許可の一覧は、決めた 2 か所（writing.md の「用語の選び方」、subagents-results.md の「校閲の評価の結果」）を載せ、全ての許可に理由を付ける', () => {
    const places = list.permit.map((item) => `${item.file}|${item.heading ?? ''}`)
    assert.ok(places.includes('docs/05-開発規則/writing.md|用語の選び方'))
    assert.ok(places.includes('docs/07-記録/subagents-results.md|校閲の評価の結果'))
    for (const item of list.permit) assert.ok(typeof item.reason === 'string' && item.reason.trim() !== '', item.file)
  })
})

describe('言い換えの一覧（warning の 高・必須。DC-566-15）', () => {
  const term = termHitBy(list.warning, '高・必須の指摘')

  test('高・必須 の後に ・推奨 が続く列挙は当てない', () => {
    for (const text of ['重要度（高・必須・推奨）', '重要度：高・必須・推奨', '重要度は「高・必須・推奨」のどれか']) assert.deepEqual(findTerms(text, [term]), [], text)
  })

  test('高・必須の指摘 は当てる', () => {
    for (const text of ['高・必須の指摘', '（高・必須の指摘を直す）']) assert.equal(findTerms(text, [term]).length, 1, text)
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

describe('日本語の文の検査（使い方の誤り）', () => {
  /**
   * 引数を付けて入口を実行する。
   *
   * @param {string[]} args 引数
   * @returns {import('node:child_process').SpawnSyncReturns<string>} 実行の結果
   */
  const run = (args) => spawnSync(process.execPath, [SCRIPT, ...args], { encoding: 'utf8', env: withoutGitEnv() })

  test('知らない引数と、--warning のない --added は、終了コード 2 で理由を示す', () => {
    const typo = run(['--waning'])
    assert.equal(typo.status, 2)
    assert.match(typo.stderr, /知らない引数：--waning/)
    const added = run(['--added'])
    assert.equal(added.status, 2)
    assert.match(added.stderr, /--added は --warning と合わせて使う/)
  })

  test('--message のファイルがないとき、ファイルを指定しないときは、終了コード 2 にする', () => {
    const missing = run(['--message', path.join(tmpdir(), 'ja-text-no-such-file')])
    assert.equal(missing.status, 2)
    assert.match(missing.stderr, /--message のファイルがない/)
    assert.equal(run(['--message']).status, 2)
  })

  test('引数の誤りは、知る印と --message のファイルを誤りにしない', () => {
    assert.equal(jaText.findArgumentProblem(['--warning', '--added']), null)
    assert.equal(jaText.findArgumentProblem(['--message', 'COMMIT_EDITMSG']), null)
    assert.equal(jaText.findArgumentProblem(['--changed']), null)
    assert.equal(jaText.findArgumentProblem([]), null)
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

  test('切り取り線の行の上の使わない語は見つける', () => {
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

  test('許容用語の中の一致は数えない', () => {
    const terms = [{ word: 'ブラウザ', use: 'ブラウザー', allow: { ブラウザー: '長音を付けた正しい語' } }]
    assert.deepEqual(findTerms('ブラウザーを開く', terms), [])
  })

  test('許容用語の外の一致は数える（同じ行に両方あっても）', () => {
    const terms = [{ word: 'ブラウザ', use: 'ブラウザー', allow: { ブラウザー: '長音を付けた正しい語' } }]
    const text = ['ブラウザーを開く', 'ブラウザーとブラウザを比べる', 'ブラウザ'].join('\n')
    assert.deepEqual(
      findTerms(text, terms).map((hit) => hit.line),
      [2, 3],
    )
  })

  test('許容用語の一覧がない語は、部分一致で数える', () => {
    assert.equal(findTerms('道具箱', [{ word: '道具', use: 'ツール' }]).length, 1)
  })

  test('許容用語が使わない語を含まないのは設定の誤りとして失敗にする', () => {
    const terms = [{ word: 'ブラウザ', use: 'ブラウザー', allow: { ブラウザー: '理由', ウェブ: '理由' } }]
    assert.throws(() => validateTerms(terms), /ウェブ/)
  })

  test('許容用語を配列で書くのは設定の誤りとして失敗にする', () => {
    const terms = [{ word: 'ブラウザ', use: 'ブラウザー', allow: ['ブラウザー'] }]
    assert.throws(() => validateTerms(terms), /理由/)
  })

  test('許容用語に理由がなければ失敗にする', () => {
    const terms = [{ word: 'ブラウザ', use: 'ブラウザー', allow: { ブラウザー: ' ' } }]
    const problems = findAllowProblems(['ブラウザーを開く'], terms)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /ブラウザー.*理由がない/)
  })

  test('どの文書にも使われなくなった許容用語を失敗にする', () => {
    const terms = [{ word: 'ブラウザ', use: 'ブラウザー', allow: { ブラウザー: '長音を付けた正しい語' } }]
    const problems = findAllowProblems(['道具を使う', 'ツールを使う'], terms)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /ブラウザー.*不要/)
  })

  test('許容用語が 1 つの文書にでも使われていれば不要とは見ない', () => {
    const terms = [{ word: 'ブラウザ', use: 'ブラウザー', allow: { ブラウザー: '長音を付けた正しい語' } }]
    assert.deepEqual(findAllowProblems(['道具', 'ブラウザーを開く'], terms), [])
  })

  test('許容用語のない項目は誤りにしない', () => {
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
    assert.equal(isTargetCode('scripts/ci/ja/ja-text.test.mjs'), false)
    assert.equal(isTargetCode('scripts/ci/lib/source/comment-text.test.mjs'), false)
  })

  test('評価の一覧の置き場所（review の evals）の文書とコードは調べない', () => {
    assert.equal(isTargetDoc('.claude/skills/review/evals/cases.md'), false)
    assert.equal(isTargetDoc('.claude/skills/review/SKILL.md'), true)
    assert.equal(isTargetCode('.claude/skills/review/evals/check.mjs'), false)
    assert.equal(isTargetCode('.claude/skills/review/check.mjs'), true)
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

  test('テンプレートで書いたテスト名の error も見つける（外側のバッククォートはインラインコードでない）', () => {
    const source = ['it(`道具を使う`, () => {})', 'test(`${name}の道具`, () => {})', 'it(`複数の', '道具`, () => {})', 'it(`\\`道具\\` と書く`, () => {})'].join('\n')
    assert.deepEqual(
      findCodeTerms('a.test.mjs', source, list.error).map((hit) => `${hit.line}:${hit.word}`),
      // 5 行目は、名前の中のインラインコード（かな・漢字を含む）なので、中の語も数える
      ['1:道具', '2:道具', '4:道具', '5:道具'],
    )
  })

  test('テンプレートのテスト名は、中の字で文の長さを測る', () => {
    const name = `${'あ'.repeat(60)}、${'い'.repeat(60)}`
    const warnings = findStyleWarnings(commentText(`it(\`${name}\`, () => {})\n`, 'ts'), { no: 4, length: 100, comma: 5 }, { kind: 'code' })
    assert.ok(warnings.some((warning) => /字数が/.test(warning.use)), JSON.stringify(warnings))
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

  test('許容用語はコメントの中でも通る', () => {
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

  test('字数がちょうど閾値の文は示し、閾値より 1 字少ない文は示さない（「。」は数えない）', () => {
    assert.deepEqual(lines(`${'あ'.repeat(LIMITS.length - 3)}である。`, 'length'), [1])
    assert.deepEqual(lines(`${'あ'.repeat(LIMITS.length - 4)}である。`, 'length'), [])
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
  /** @param {string} text 文書 @param {Set<string>} [names] 文書の名前の一覧 @returns {number[]} 読点の数（1 個以上の文ごと。文の順） */
  function commaCounts(text, names = new Set()) {
    return findStyleWarnings(text, { comma: 1 }, { names })
      .filter((hit) => hit.kind === 'comma')
      .map((hit) => Number(/読点が (\d+) 個/.exec(hit.use)[1]))
  }

  test('語の検査は、英字だけのインラインコードの中の一致を数えない', () => {
    const terms = [{ word: 'tool', use: 'ツール' }]
    assert.deepEqual(findTerms('`tool` と書く', terms), [])
    assert.equal(findTerms('`tool` と tool', terms).length, 1)
  })

  test('語の検査は、かな・漢字を含むインラインコードの中の一致を数える', () => {
    const terms = [{ word: '道具', use: 'ツール' }]
    assert.equal(findTerms('`道具` と書く', terms).length, 1)
    assert.equal(findTerms('`ぶんしょう道具` と書く', terms).length, 1)
  })

  test('pattern の項目も、英字だけのインラインコードの中の一致を数えない。行番号は元の行である', () => {
    const terms = [{ pattern: 'tool[sz]', use: 'ツール', examples: { hit: ['tools'], miss: ['tool'] } }]
    assert.deepEqual(findTerms('あ\n`tools`\ntoolz', terms).map((hit) => hit.line), [3])
  })

  test('インラインコードをまたいだ一致は作らない', () => {
    assert.deepEqual(findTerms('道`x`具', [{ word: '道具', use: 'ツール' }]), [])
  })

  test('許容用語の照合も、英字だけのインラインコードを除いた文で行う', () => {
    const terms = [{ word: 'tool', use: 'ツール', allow: { toolbox: '理由' } }]
    assert.deepEqual(findTerms('`toolbox` と tool', terms).map((hit) => hit.line), [1])
    assert.deepEqual(findTerms('`toolbox` を開く', terms), [])
  })

  test('許容用語が英字だけのインラインコードの中にしか出ないときは、使われていない（不要）と見る', () => {
    const terms = [{ word: 'tool', use: 'ツール', allow: { toolbox: '理由' } }]
    const problems = findAllowProblems(['`toolbox` を開く'], terms)
    assert.equal(problems.length, 1)
    assert.match(problems[0], /toolbox.*不要/)
    assert.deepEqual(findAllowProblems(['`toolbox` を開く', 'toolbox を開く'], terms), [])
  })

  test('許容用語が、かな・漢字を含むインラインコードの中に出るときは、使われているとみる', () => {
    const terms = [{ word: 'ブラウザ', use: 'ブラウザー', allow: { ブラウザー: '長音を付けた正しい語' } }]
    assert.deepEqual(findAllowProblems(['`ブラウザー` を開く'], terms), [])
  })

  test('許容用語の照合は、文書の順によらない（2 つ目以降の文書の、日本語を含むインラインコードの中の許容用語も使われているとみる）', () => {
    const terms = [{ word: 'ブラウザ', use: 'ブラウザー', allow: { ブラウザー: '長音を付けた正しい語' } }]
    assert.deepEqual(findAllowProblems(['別の文書', '`ブラウザー` を開く'], terms), [])
    assert.deepEqual(findAllowProblems(['別の文書', '別の文書', '`ブラウザー` を開く'], terms), [])
  })

  test('コードのコメントの中の、英字だけのインラインコードは語の検査で数えず、日本語を含むものは数える', () => {
    assert.deepEqual(findCodeTerms('a.mjs', '// `tool` を使う', [{ word: 'tool', use: 'ツール' }]), [])
    assert.equal(findCodeTerms('a.mjs', '// `x` と tool', [{ word: 'tool', use: 'ツール' }]).length, 1)
    assert.equal(findCodeTerms('a.mjs', '// `道具` を使う', [{ word: '道具', use: 'ツール' }]).length, 1)
  })

  test('tasks の範囲の行：インラインコードだけの項目を区切る読点は 0 と数える', () => {
    assert.deepEqual(commaCounts('このタスクの範囲：`scripts/ci/ja-text.mjs`、`scripts/ci/ja-text.json`、`scripts/ci/lib/`、`scripts/ci/migrations.mjs`'), [])
  })

  test('参照の行：リンクの後に見出しの参照（の「…」）が付いた項目も、参照だけの項目として扱う', () => {
    assert.deepEqual(commaCounts('このタスクの参照：[plan](../plan.md)の「文体の検査」、[plan](../plan.md)の「段階と変えるファイル」、[spec](../spec.md)'), [])
  })

  test('参照の行：親の見出しを付けた形（の「A」の「B」）と、文書の名前・skill の名前の後の見出しの参照も、参照だけの項目として扱う', () => {
    assert.deepEqual(commaCounts('参照：[plan](../plan.md)の「段階 1」の「タスク」、開発の進め方の「起票」、開発の進め方の「規模」、skill `sessions` の「ツールの補足」', new Set(['開発の進め方'])), [])
  })

  test('文書の名前の後の見出しの参照だけの項目の間の読点は、数えない（名前の一覧に一致する語）', () => {
    const names = new Set(['開発の進め方'])
    assert.deepEqual(commaCounts('参照：開発の進め方の「規模」、開発の進め方の「循環」、skill `sessions`。', names), [])
  })

  test('名前の一覧にない語と見出しの参照の項目の間の読点は、数える', () => {
    assert.deepEqual(commaCounts('参照：作業の「規模」、設定の「値」、画面の「表示」。', new Set(['開発の進め方'])), [2])
  })

  test('空白を含む文書の名前も、名前の一覧に一致すれば、参照だけの項目と見る', () => {
    const names = new Set(['Spec Kit 入門'])
    assert.deepEqual(commaCounts('参照：Spec Kit 入門の「規模」、Spec Kit 入門の「循環」。', names), [])
  })

  test('.md で終わる語と見出しの参照の項目は、名前の一覧になくても、参照だけの項目と見る', () => {
    assert.deepEqual(commaCounts('参照：見本/文書.md の「規模」、見本/別.md の「重要度」、skill `x`。'), [])
  })

  test('名前の一覧との一致は語の全体で、末尾だけが一致する語の項目の間の読点は数える', () => {
    const names = new Set(['記録'])
    assert.deepEqual(commaCounts('参照：作業の記録の「規模」、作業の記録の「循環」。', names), [1])
  })

  test('名前の一覧を返す関数を渡したとき、見出しの参照が付いた項目を見るときだけ呼び、1 文書で何度見ても求めるのは必要なときの 1 回である', () => {
    let calls = 0
    const names = () => {
      calls += 1
      return new Set(['開発の進め方'])
    }
    assert.deepEqual(findStyleWarnings('範囲：`a`、`b`、`c`。', { comma: 1 }, { names }), [])
    assert.deepEqual(findStyleWarnings('参照：[a](a.md)、[b](b.md)、skill `sessions`。', { comma: 1 }, { names }), [])
    assert.equal(calls, 0)
    assert.deepEqual(findStyleWarnings('参照：開発の進め方の「規模」、開発の進め方の「循環」。', { comma: 1 }, { names }), [])
    assert.ok(calls >= 1)
  })

  test('文書の名前の一覧は、参照の検査が見ないファイル（アーカイブした spec）の見出しを含まない', () => {
    const files = ['docs/a.md', 'docs/08-spec/archive/old/spec.md', 'docs/08-spec/archive/mvp/mvp.md']
    const titles = { 'docs/a.md': '# 開発の進め方\n', 'docs/08-spec/archive/old/spec.md': '# 古い spec\n', 'docs/08-spec/archive/mvp/mvp.md': '# MVP の一覧\n' }
    const names = jaText.documentNames(files, (file) => titles[file])
    assert.ok(names.has('開発の進め方'))
    assert.ok(names.has('MVP の一覧'))
    assert.ok(!names.has('古い spec'))
  })

  test('参照の行：「」の中の読点とコロンで項目を分けない。「」の中の読点は数える', () => {
    assert.deepEqual(commaCounts('参照：[a](a.md)の「起票、規模」、[b](b.md)の「入力：出力」、[c](c.md)の「d、e」、[f](f.md)'), [2])
    assert.deepEqual(commaCounts('参照：[a](a.md)の「起票、規模」、[b](b.md)'), [1])
  })

  test('他の語を含む項目の前後の読点は数える', () => {
    assert.deepEqual(commaCounts('このタスクの参照：[plan](../plan.md)の「A」、[plan](../plan.md)の「B」、[spec](../spec.md) の DS-283-5'), [1])
    assert.deepEqual(commaCounts('参照：[plan](../plan.md)の「A」、[plan](../plan.md)の「B」、[plan](../plan.md)の「C」、[plan](../plan.md)の「D」、[spec](../spec.md) の DS-283-1〜3、6'), [2])
    assert.deepEqual(commaCounts('`a`、`b` を使い、`c` を直す。'), [2])
  })

  test('コロンまでを除くのは最初の項目だけである。語の見出しとインラインコードを組にした項目の間の読点は数える', () => {
    // かなを含まない文は文として数えないので、最後に「とする」を付ける
    assert.deepEqual(commaCounts('入力：`a`、出力：`b`、設定：`c`、前提：`d`、結果：`e`、備考：`f` とする。'), [5])
  })

  test('普通の文の読点は数える', () => {
    assert.deepEqual(commaCounts('一、二、三、四とする。'), [3])
  })

  test('インラインコードの中の読点は数えず、インラインコードは字数では 1 字に数える。コードのない文の「の」は数える', () => {
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
   * @param {string} [input] 標準入力
   * @returns {import('node:child_process').SpawnSyncReturns<string>} 実行の結果
   */
  function runIn(files, args, input = '') {
    const dir = mkdtempSync(path.join(tmpdir(), 'ja-text-repo-'))
    try {
      spawnSync('git', ['init', '-q'], { cwd: dir, env: withoutGitEnv() })
      for (const [name, content] of Object.entries(files)) {
        const full = path.join(dir, name)
        mkdirSync(path.dirname(full), { recursive: true })
        writeFileSync(full, content)
      }
      return spawnSync(process.execPath, [SCRIPT, ...args], { cwd: dir, encoding: 'utf8', env: withoutGitEnv(), input })
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  }

  const longSentence = `${'あ'.repeat(200)}である。要求を送る。少し修正する。\n`
  const okCode = { 'src/ok.mjs': '// 問題のない文である。\n' }
  const files = { 'docs/a.md': longSentence, 'src/a.mjs': `// ${'い'.repeat(200)}である。\n` }

  test('warning だけのとき、既定の実行は成功し、warning を数えない。最後の行は要約の行である', () => {
    // 許容用語が使われなくなったという error を出さないよう、一覧の全ての許容用語を 1 行ずつ書く。
    // リポジトリ本体では実行しない（commit の hook は、Git のない一時の場所で検査を実行するため）
    const allowWords = [...list.error, ...list.warning].flatMap((item) => Object.keys(item.allow ?? {}))
    // 使われなくなった許可も失敗にするので、許可の一覧の全ての場所に、使わない語を 1 つずつ書く
    const permitFiles = Object.fromEntries(list.permit.map((permit) => [permit.file, `# 題\n\n${permit.heading === undefined ? '' : `## ${permit.heading}\n\n`}道具\n`]))
    const result = runIn({ ...files, ...permitFiles, 'docs/allow.md': `${allowWords.join('\n')}\n` }, [])
    assert.equal(result.status, 0)
    assert.doesNotMatch(result.stdout, /警告|要求|字数/)
    assert.equal(result.stdout.trimEnd().split('\n').at(-1), '使わない語：0')
  })

  test('使われなくなった許可（場所の中に error の語がない）があるとき、既定の実行は終了コード 1 にして、その許可を示す', () => {
    const allowWords = [...list.error, ...list.warning].flatMap((item) => Object.keys(item.allow ?? {}))
    const [stale, ...others] = list.permit
    const placeFile = (permit, body) => [permit.file, `# 題\n\n${permit.heading === undefined ? '' : `## ${permit.heading}\n\n`}${body}\n`]
    const permitFiles = Object.fromEntries([placeFile(stale, '使わない語はない'), ...others.map((permit) => placeFile(permit, '道具'))])
    const result = runIn({ ...files, ...permitFiles, 'docs/allow.md': `${allowWords.join('\n')}\n` }, [])
    assert.equal(result.status, 1)
    assert.match(result.stdout, new RegExp(`ja-text\\.json の permit.*${stale.heading ?? stale.file}.*不要`))
    assert.equal(result.stdout.trimEnd().split('\n').at(-1), '使わない語：1')
  })

  test('--warning --added は、標準入力の差分の追加行だけを一覧にする', () => {
    const diff = ['diff --git a/docs/a.md b/docs/a.md', '--- a/docs/a.md', '+++ b/docs/a.md', '@@ -0,0 +1 @@', `+${longSentence}`].join('\n')
    const result = runIn({ ...files, 'docs/b.md': '要求を送る。\n' }, ['--warning', '--added'], diff)
    assert.equal(result.status, 0)
    assert.match(result.stdout, /docs\/a\.md:1 .*字数/)
    assert.doesNotMatch(result.stdout, /docs\/b\.md|src\/a\.mjs/)
  })

  /**
   * 追加行だけの差分を作る（git diff -U0 の形）。
   *
   * @param {string} file パス
   * @param {string} hunk @@ の行
   * @param {string[]} body hunk の本文の行
   * @returns {string} 差分
   */
  function diffOf(file, hunk, body = []) {
    return [`diff --git a/${file} b/${file}`, `--- a/${file}`, `+++ b/${file}`, hunk, ...body].join('\n')
  }

  test('--warning --added は、差分にないファイルの error の語と warning の語を示さない（hook の例 1）', () => {
    const diff = diffOf('docs/a.md', '@@ -0,0 +1 @@', ['+問題のない文である。'])
    const result = runIn({ ...okCode, 'docs/a.md': '問題のない文である。\n', 'docs/b.md': `見直す。要求を送る。\n${longSentence}` }, ['--warning', '--added'], diff)
    assert.equal(result.status, 0)
    assert.equal(result.stdout.trimEnd(), '注意する語と文：0')
  })

  test('--warning --added は、削除した行だけがある文書を示さない（hook の例 2）', () => {
    const diff = diffOf('docs/a.md', '@@ -5 +4,0 @@', ['-古い行'])
    const result = runIn({ ...okCode, 'docs/a.md': `見直す。要求を送る。\n${longSentence}` }, ['--warning', '--added'], diff)
    assert.equal(result.status, 0)
    assert.equal(result.stdout.trimEnd(), '注意する語と文：0')
  })

  test('--warning --added は、名前だけを変えたファイルと改行コードだけを変えたファイルを示さない（hook の例 3）', () => {
    const renamed = ['diff --git a/docs/old.md b/docs/new.md', 'similarity index 100%', 'rename from docs/old.md', 'rename to docs/new.md'].join('\n')
    const renamedResult = runIn({ ...okCode, 'docs/new.md': `見直す。要求を送る。\n${longSentence}` }, ['--warning', '--added'], renamed)
    assert.equal(renamedResult.stdout.trimEnd(), '注意する語と文：0')
    // 改行コードだけの違いは、--ignore-cr-at-eol により差分が空になる
    const emptyResult = runIn({ ...okCode, 'docs/a.md': `見直す。要求を送る。\r\n${longSentence}` }, ['--warning', '--added'], '')
    assert.equal(emptyResult.stdout.trimEnd(), '注意する語と文：0')
  })

  test('--warning --added は、追加行の error の語を示さず、それに重なる warning の語も重ねて示さない（hook の例 4）', () => {
    const diff = diffOf('docs/a.md', '@@ -0,0 +1 @@', ['+文書を見直す。'])
    const result = runIn({ ...okCode, 'docs/a.md': '文書を見直す。\n' }, ['--warning', '--added'], diff)
    assert.equal(result.status, 0)
    assert.doesNotMatch(result.stdout, /見直す|直す/)
    assert.equal(result.stdout.trimEnd(), '注意する語と文：0')
  })

  test('--warning --added は、error の語に重ならない warning の語は、同じ行にあっても示す', () => {
    const diff = diffOf('docs/a.md', '@@ -0,0 +1 @@', ['+文書を見直す。誤りを直す。'])
    const result = runIn({ ...okCode, 'docs/a.md': '文書を見直す。誤りを直す。\n' }, ['--warning', '--added'], diff)
    assert.match(result.stdout, /^docs\/a\.md:1 直す → /m)
    assert.doesNotMatch(result.stdout, /見直す/)
    assert.equal(result.stdout.trimEnd().split('\n').at(-1), '注意する語と文：1')
  })

  test('--warning（--added なし）は、error の語に重なる warning の語も今までどおり示す', () => {
    const result = runIn({ ...okCode, 'docs/a.md': '文書を見直す。\n' }, ['--warning'])
    assert.match(result.stdout, /^docs\/a\.md:1 直す → /m)
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

describe('日本語の文の検査（差分の追加行の文体：--changed）', { concurrency: true }, () => {
  // CI の push のイベントの変数があると基準の選び方が変わるので外す（手元と CI で同じ結果にする）
  const env = withoutGitEnv()
  delete env.GITHUB_ACTIONS
  delete env.GITHUB_EVENT_NAME
  delete env.GITHUB_REF_NAME
  // git の設定は、git config を実行せずに環境変数で渡す（テストごとの git の起動を減らす。Windows では 1 回が遅い）
  const config = { 'user.email': 'test@example.com', 'user.name': 'test', 'commit.gpgsign': 'false', 'core.autocrlf': 'false' }
  Object.entries(config).forEach(([key, value], index) => {
    env[`GIT_CONFIG_KEY_${index}`] = key
    env[`GIT_CONFIG_VALUE_${index}`] = value
  })
  env.GIT_CONFIG_COUNT = String(Object.keys(config).length)
  const repos = []
  after(() => repos.forEach((repo) => rmSync(repo, { recursive: true, force: true })))

  /** 字数が閾値（100）より多い文（103 字） */
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
  async function createRepo(files, { developRef = 'refs/heads/develop' } = {}) {
    const repo = mkdtempSync(path.join(tmpdir(), 'ja-text-changed-'))
    repos.push(repo)
    const git = async (...args) => {
      const result = await runAsync('git', args, { cwd: repo, env })
      if (result.status !== 0) throw new Error(`git ${args.join(' ')}: ${result.stderr}`)
      return result.stdout
    }
    // develop を refs/heads/develop に置くときは、初めのブランチを develop にして update-ref を省く
    const localDevelop = developRef === 'refs/heads/develop'
    await git('init', '-q', '--template=', '-b', localDevelop ? 'develop' : 'main')
    const write = (name, content) => {
      const full = path.join(repo, name)
      mkdirSync(path.dirname(full), { recursive: true })
      writeFileSync(full, content)
    }
    const commit = async (message) => {
      await git('add', '-A')
      await git('commit', '-q', '--allow-empty', '-m', message)
    }
    for (const [name, content] of Object.entries(files)) write(name, content)
    await commit('base')
    if (!localDevelop) await git('update-ref', developRef, 'HEAD')
    await git('checkout', '-q', '-b', 'feature')
    const run = (cwd = repo, extraEnv = {}) => runAsync(process.execPath, [SCRIPT, '--changed'], { cwd, env: { ...env, ...extraEnv } })
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

  test('追加行の長い文、読点の多い文、長いセルに、注釈の形の警告を 1 件 1 行で出す。終了コードは 0 で、最後の行は件数である', async () => {
    const r = await createRepo({ 'docs/a.md': '短い文である。\n' })
    r.write('docs/a.md', `短い文である。\n${LONG}\n${COMMAS}\n${LONG_CELL}\n`)
    await r.commit('追加')
    const result = await r.run()
    assert.equal(result.status, 0, result.stdout + result.stderr)
    assert.deepEqual(annotated(result.stdout), ['docs/a.md:2', 'docs/a.md:3', 'docs/a.md:4'])
    assert.match(result.stdout, /^::warning file=docs\/a\.md,line=2::.*字数が 103 字/m)
    assert.match(result.stdout, /^::warning file=docs\/a\.md,line=3::.*読点が 5 個/m)
    assert.match(result.stdout, /^::warning file=docs\/a\.md,line=4::.*80 字/m)
    assert.match(result.stdout.trimEnd().split('\n').at(-1), /：3/)
  })

  test('既存の行（追加行でない行）には警告を出さない', async () => {
    const r = await createRepo({ 'docs/a.md': `${LONG}\n短い文である。\n${COMMAS}\n${LONG_CELL}\n` })
    r.write('docs/a.md', `${LONG}\n短い文を変えた。\n${COMMAS}\n${LONG_CELL}\n${LONG}\n`)
    await r.commit('変更')
    const result = await r.run()
    assert.equal(result.status, 0)
    assert.deepEqual(annotated(result.stdout), ['docs/a.md:5'])
  })

  test('複数の行にまたがる文：既存の行だけでできた文には出さず、1 行を足して長くなった文には、足した行に出す', async () => {
    const head = 'あ'.repeat(60)
    const tail = `${'い'.repeat(57)}である。`
    // 足した行（95 字）は、1 行では閾値（100）に届かない。前後の行とつなぐと閾値を超える
    const r = await createRepo({ 'docs/a.md': `${head}\n${tail}\n\nこれは\n短い文である。\n`, 'src/a.mjs': `// 短い\n// 文である。\n` })
    r.write('docs/a.md', `${head}\n${tail}\n\nこれは\n${'う'.repeat(95)}\n短い文である。\n`)
    r.write('src/a.mjs', `// 短い\n// ${'え'.repeat(95)}\n// 文である。\n`)
    await r.commit('変更')
    const result = await r.run()
    assert.equal(result.status, 0, result.stdout + result.stderr)
    assert.deepEqual(annotated(result.stdout), ['docs/a.md:5', 'src/a.mjs:2'])
  })

  test('行末のコメントは、次の行のコメントとつながない（短い注だけの追加行に警告を出さない）', async () => {
    const r = await createRepo({ 'src/b.mjs': `const a = 1 // ${'え'.repeat(60)}\n` })
    r.write('src/b.mjs', `const a = 1 // ${'え'.repeat(60)}\n// ${'お'.repeat(57)}である。\n`)
    await r.commit('追加')
    const result = await r.run()
    assert.equal(result.status, 0, result.stdout + result.stderr)
    assert.deepEqual(annotated(result.stdout), [])
  })

  test('改行コードだけを変えた既存の行（LF から CRLF）は追加行にしない。足した行だけに警告を出す', async () => {
    const r = await createRepo({ 'docs/a.md': `${LONG}\n短い文である。\n` })
    r.write('docs/a.md', `${LONG}\r\n短い文である。\r\n${COMMAS}\r\n`)
    await r.commit('改行コードの変更と追加')
    assert.deepEqual(annotated((await r.run()).stdout), ['docs/a.md:3'])
  })

  test('develop から分けたばかりで commit がない作業ブランチでは、HEAD との差分（作業ツリーの変更）だけを見る', async () => {
    const r = await createRepo({ 'docs/a.md': '短い文である。\n' })
    r.write('docs/a.md', `短い文である。\n${LONG}\n`)
    await r.commit('develop の最後の commit')
    await r.git('update-ref', 'refs/heads/develop', 'HEAD')
    r.write('docs/a.md', `短い文である。\n${LONG}\n${COMMAS}\n`)
    assert.deepEqual(annotated((await r.run()).stdout), ['docs/a.md:3'])
  })

  test('commit していない変更と、追跡していない新しい文書の行も、追加行として見る', async () => {
    const r = await createRepo({ 'docs/a.md': '短い文である。\n' })
    r.write('docs/a.md', `短い文である。\n${LONG}\n`)
    r.write('docs/new.md', `${COMMAS}\n`)
    const result = await r.run()
    assert.deepEqual(annotated(result.stdout), ['docs/a.md:2', 'docs/new.md:1'])
  })

  test('「の」の連なり、否定、複合動詞の崩れ、語の検査は出さない', async () => {
    const r = await createRepo({ 'docs/a.md': '短い文である。\n' })
    r.write('docs/a.md', '短い文である。\n本日の会議の資料の変更の履歴を見る。\n原因は分からなく修正もできない。\n少し修正する。\n道具を使う。要求を送る。\n')
    await r.commit('追加')
    const result = await r.run()
    assert.equal(result.status, 0)
    assert.deepEqual(annotated(result.stdout), [])
  })

  test('表の区切り行、コードブロック、インラインコード、URL は数えない', async () => {
    const r = await createRepo({ 'docs/a.md': '短い文である。\n' })
    const fenced = ['```', LONG, COMMAS, '```'].join('\n')
    const inline = `ここは \`${'の'.repeat(120)}\` と https://example.com/${'あ'.repeat(120)} と [リンク](https://example.com/${'い'.repeat(120)}) だ。`
    r.write('docs/a.md', `短い文である。\n${fenced}\n|${'-'.repeat(120)}|\n${inline}\n`)
    await r.commit('追加')
    assert.deepEqual(annotated((await r.run()).stdout), [])
  })

  test('対象は既定の実行と同じファイルで、コードはコメントとテスト名だけを見る。削除したファイルは見ない', async () => {
    const r = await createRepo({ 'docs/gone.md': '短い文である。\n' })
    r.write('docs/08-spec/archive/old.md', `${LONG}\n`)
    r.write('notes.txt', `${LONG}\n`)
    r.write('src/a.mjs', `const s = '${LONG}'\n// ${LONG}\n`)
    rmSync(path.join(r.repo, 'docs/gone.md'))
    await r.commit('追加と削除')
    const result = await r.run()
    assert.equal(result.status, 0, result.stdout + result.stderr)
    assert.deepEqual(annotated(result.stdout), ['src/a.mjs:2'])
  })

  test('名前を変えたファイルは、変えていない行を追加行として数えない', async () => {
    const body = Array.from({ length: 20 }, (_, index) => `${index}${LONG}`).join('\n')
    const r = await createRepo({ 'docs/old.md': `${body}\n` })
    await r.git('mv', 'docs/old.md', 'docs/new.md')
    r.write('docs/new.md', `${body}\n${COMMAS}\n`)
    await r.commit('移動')
    assert.deepEqual(annotated((await r.run()).stdout), ['docs/new.md:21'])
  })

  test('改行コードが CRLF のファイルでも、行番号は元の行である', async () => {
    const r = await createRepo({ 'docs/a.md': '短い文である。\r\n次の文である。\r\n' })
    r.write('docs/a.md', `短い文である。\r\n次の文である。\r\n${LONG}\r\n${LONG_CELL}\r\n`)
    r.write('src/a.mjs', `// 短い文である。\r\n// ${COMMAS}\r\n`)
    await r.commit('追加')
    assert.deepEqual(annotated((await r.run()).stdout), ['docs/a.md:3', 'docs/a.md:4', 'src/a.mjs:2'])
  })

  test('CI の develop への push（merge-base が HEAD 自身）では、1 つ目の親との差分を見る。手元の develop では見ない', async () => {
    const r = await createRepo({ 'docs/a.md': `${LONG}\n` }, { developRef: 'refs/remotes/origin/develop' })
    await r.git('checkout', '-q', '-B', 'develop')
    r.write('docs/a.md', `${LONG}\n${COMMAS}\n`)
    await r.commit('push')
    await r.git('update-ref', 'refs/remotes/origin/develop', 'HEAD')
    const result = await r.run(r.repo, { GITHUB_ACTIONS: 'true', GITHUB_EVENT_NAME: 'push', GITHUB_REF_NAME: 'develop' })
    assert.equal(result.status, 0)
    assert.deepEqual(annotated(result.stdout), ['docs/a.md:2'])
    // 手元（GITHUB_ACTIONS がない）では HEAD を基準にし、前のマージ（push の commit）の行を追加行にしない
    const local = await r.run()
    assert.equal(local.status, 0)
    assert.deepEqual(annotated(local.stdout), [])
  })

  test('origin/develop があれば、その分岐点からの差分を見る（CI の checkout）', async () => {
    const r = await createRepo({ 'docs/a.md': '短い文である。\n' }, { developRef: 'refs/remotes/origin/develop' })
    r.write('docs/a.md', `短い文である。\n${LONG}\n`)
    await r.commit('追加')
    assert.deepEqual(annotated((await r.run()).stdout), ['docs/a.md:2'])
  })

  test('shallow clone では差分を求められないので失敗にする', async () => {
    const r = await createRepo({ 'docs/a.md': '短い文である。\n' })
    r.write('docs/a.md', `短い文である。\n${LONG}\n`)
    await r.commit('追加')
    const shallow = mkdtempSync(path.join(tmpdir(), 'ja-text-shallow-'))
    repos.push(shallow)
    await runAsync('git', ['clone', '-q', '--depth', '1', '--no-local', pathToFileURL(r.repo).href, path.join(shallow, 'clone')], { env })
    const result = await r.run(path.join(shallow, 'clone'))
    assert.notEqual(result.status, 0)
    assert.ok(result.stdout.includes(SHALLOW_CLONE_MESSAGE), result.stdout)
  })

  test('注釈の内容とパスの、GitHub Actions が区切りに使う字を逃がす', () => {
    assert.equal(jaText.formatAnnotation('docs/a,b:c.md', 3, '100% の文\n次'), '::warning file=docs/a%2Cb%3Ac.md,line=3::100%25 の文%0A次')
  })
})

describe('日本語の文の検査（--warning --added の測るファイル）', () => {
  test('追加行の差分がなければ、全てのファイルを測る', () => {
    assert.deepEqual(jaText.filesToMeasure(['docs/a.md', 'docs/b.md'], null), ['docs/a.md', 'docs/b.md'])
  })

  test('追加行の差分があれば、追加行のあるファイルだけを（順を変えずに）測る。追加行のないファイル（削除した行だけ）は差分にあっても測らない', () => {
    const added = new Map([['docs/b.md', new Set([1])], ['docs/c.md', new Set()], ['docs/x.md', new Set([2])], ['docs/d.md', null]])
    assert.deepEqual(jaText.filesToMeasure(['docs/a.md', 'docs/b.md', 'docs/c.md', 'docs/d.md'], added), ['docs/b.md', 'docs/d.md'])
  })
})
