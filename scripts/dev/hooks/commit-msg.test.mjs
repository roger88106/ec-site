// commit-msg.mjs のテスト（node --test scripts/dev/hooks/commit-msg.test.mjs）
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { withoutGitEnv } from '../../shared/git/git-env.mjs'
import { gitBash } from '../lib/git-bash.mjs'
import { COMMIT_MSG_FAILED, runCommitMsg } from './commit-msg.mjs'
import { COMMIT_MSG_FILES, JA_TEXT_FILES, MINI_LIST, copyFromRepo } from './support/test-support.mjs'

describe('commit のメッセージの検査', () => {
  /** 実行した script を記録し、script ごとの終了コードと、要約の行のある出力を返す */
  function fakeRun(codes) {
    const calls = []
    return { calls, run: (name, args) => (calls.push({ name, args }), { status: codes[name], output: '使わない語：0\n' }) }
  }

  test('終了コード 0 でも、出力の最後の行が要約の行でなければ、失敗にして理由を表示する', () => {
    const logs = []
    for (const output of ['', '何もしない\n']) {
      assert.equal(runCommitMsg({ file: 'm', run: () => ({ status: 0, output }), log: (text) => logs.push(text) }), 1)
    }
    assert.equal(logs.length, 2)
    assert.match(logs[0], /要約の行/)
  })

  test('日本語の文の検査を、メッセージのファイルを渡して実行する', () => {
    const { calls, run } = fakeRun({ 'ja-text': 0 })
    assert.equal(runCommitMsg({ file: '.git/MSG', run }), 0)
    assert.deepEqual(calls, [{ name: 'ja-text', args: ['--message', '.git/MSG'] }])
  })

  test('検査が失敗すれば、全体は失敗にする', () => {
    assert.equal(runCommitMsg({ file: 'm', run: fakeRun({ 'ja-text': 1 }).run }), 1)
  })

  test('終了コードを返さない（シグナルでの終了など）検査は、失敗にする', () => {
    assert.equal(runCommitMsg({ file: 'm', run: fakeRun({ 'ja-text': null }).run }), 1)
  })

  test('メッセージのファイルがなければ、検査を実行せずに失敗にし、理由を表示する', () => {
    const { calls, run } = fakeRun({ 'ja-text': 0 })
    const logs = []
    assert.equal(runCommitMsg({ file: undefined, run, log: (text) => logs.push(text) }), COMMIT_MSG_FAILED)
    assert.equal(calls.length, 0)
    assert.equal(logs.length, 1)
    assert.match(logs[0], /メッセージのファイルが渡されていない/)
  })
})

describe('実際の commit-msg の hook', () => {
  /**
   * 一時のディレクトリ（Git のリポジトリでない場所は hook が使えないので、git init する）に hook をコピーして、hook を実行する。
   *
   * @param {string} message コミットメッセージのファイルの中身
   * @returns {{ status: number | null, output: string }} hook の終了コードと標準出力
   */
  function runCommitMsgHookFull(message, jaText) {
    const dir = mkdtempSync(path.join(tmpdir(), 'commit-msg-test-'))
    try {
      const env = withoutGitEnv()
      spawnSync('git', ['init', '-q'], { cwd: dir, env })
      copyFromRepo(dir, [...COMMIT_MSG_FILES, ...JA_TEXT_FILES])
      if (jaText !== undefined) writeFileSync(path.join(dir, 'scripts/ci/ja/ja-text.mjs'), jaText)
      writeFileSync(path.join(dir, 'scripts/ci/ja/ja-text.json'), MINI_LIST)
      writeFileSync(path.join(dir, 'MSG'), message)
      const result = spawnSync(gitBash(env), ['scripts/dev/hooks/commit-msg', 'MSG'], { cwd: dir, env, encoding: 'utf8' })
      return { status: result.status, output: result.stdout }
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  }

  /** hook の終了コードだけを返す */
  function runCommitMsgHook(message) {
    return runCommitMsgHookFull(message).status
  }

  test('メッセージに使わない語（error）がなければ成功する', () => {
    assert.equal(runCommitMsgHook('ツールを追加する\n'), 0)
  })

  test('メッセージに使わない語（error）があれば失敗する', () => {
    const { status, output } = runCommitMsgHookFull('道具を追加する\n')
    assert.equal(status, 1)
    assert.match(output, /道具/)
  })

  test('検査が成功するときも、検査の出力（要約の行）を表示する', () => {
    const { status, output } = runCommitMsgHookFull('ツールを追加する\n')
    assert.equal(status, 0)
    assert.match(output, /使わない語：0/)
  })

  test('日本語の文の検査が何も調べずに終了コード 0 で終わる（要約の行がない）と、hook は失敗する', () => {
    const { status, output } = runCommitMsgHookFull('道具を追加する\n', '// 直接実行の判定が外れた検査の代わり\n')
    assert.equal(status, 1)
    assert.match(output, /要約の行/)
  })

  test('git commit -v の差分に使わない語があっても、数えない', () => {
    const message = 'ツールを追加する\n# ------------------------ >8 ------------------------\ndiff --git a/a b/a\n-道具を使う\n'
    assert.equal(runCommitMsgHook(message), 0)
  })

  test('# で始まる行の使わない語は、数えない', () => {
    assert.equal(runCommitMsgHook('ツールを追加する\n# 道具\n'), 0)
  })
})
