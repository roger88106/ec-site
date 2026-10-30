// pre-commit.mjs のテスト（node --test scripts/dev/lib/pre-commit.test.mjs）
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { chmodSync, copyFileSync, mkdirSync, mkdtempSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { withoutGitEnv } from '../../ci/lib/git-env.mjs'
import { BRANCH_REJECTED, CHECKOUT_FAILED, WARNING_FAILED, branchOfHead, branchRejection, checkShell, parseAddedLines, resolveIndexFile, runPreCommit, selectAddedWarnings } from './pre-commit.mjs'
import { COMMIT_MSG_FAILED, runCommitMsg } from './commit-msg.mjs'
import { gitBash } from './git-bash.mjs'

describe('一時の index のパス', () => {
  test('相対パスは、リポジトリのルートからの絶対パスにする', () => {
    assert.equal(resolveIndexFile('.git/index.lock', '/repo'), path.join('/repo', '.git/index.lock'))
  })

  test('絶対パスは、そのまま返す（POSIX と Windows）', () => {
    assert.equal(resolveIndexFile('/tmp/index', '/repo'), '/tmp/index')
    assert.equal(resolveIndexFile('C:\\tmp\\index', '/repo'), 'C:\\tmp\\index')
    assert.equal(resolveIndexFile('C:/tmp/index', '/repo'), 'C:/tmp/index')
  })

  test('値がなければ undefined を返す', () => {
    assert.equal(resolveIndexFile(undefined, '/repo'), undefined)
    assert.equal(resolveIndexFile('', '/repo'), undefined)
  })
})

describe('検査を実行する bash', () => {
  test('hook が渡した bash のパス（PRE_COMMIT_BASH）があれば、それを使う', () => {
    assert.equal(checkShell({ PRE_COMMIT_BASH: 'C:/Program Files/Git/usr/bin/bash.exe' }), 'C:/Program Files/Git/usr/bin/bash.exe')
  })

  test('なければ、PATH の bash を使う', () => {
    assert.equal(checkShell({}), 'bash')
    assert.equal(checkShell({ PRE_COMMIT_BASH: '' }), 'bash')
  })
})

describe('commit するブランチ', () => {
  test('develop では、今の develop から作業ブランチを作る手順を表示する（ステージした変更は持ち越される）', () => {
    const message = branchRejection('develop')
    assert.match(message, /develop には直接 commit しない/)
    assert.match(message, /^ {2}git switch -c <issue の番号>-<短い名前>$/m)
    assert.match(message, /持ち越される/)
  })

  test('main では、緊急の修正は main から hotfix のブランチを、それ以外は退避して develop から作業ブランチを作る手順を表示する', () => {
    const message = branchRejection('main')
    assert.match(message, /main には直接 commit しない/)
    assert.match(message, /^ {2}git switch -c hotfix-<issue の番号>-<短い名前>$/m)
    assert.match(message, /^ {2}git stash push -m <退避の名前>$/m)
    assert.match(message, /^ {2}git switch -c <issue の番号>-<短い名前> develop$/m)
    assert.doesNotMatch(message, /持ち越される/)
  })

  test('main で退避した変更は、作業ツリーの間で共有される退避の一覧から、退避の名前の行の commit を選んで戻す（pop は使わない）', () => {
    const message = branchRejection('main')
    assert.match(message, /^ {2}git stash list --format='%H %gs'$/m)
    assert.match(message, /^ {2}git stash apply <退避の名前の行の commit>$/m)
    assert.match(message, /共有/)
    assert.doesNotMatch(message, /^ {2}git stash pop/m)
  })

  test('作業ブランチと detached HEAD（ブランチを読めないときを含む）では、通す', () => {
    for (const branch of ['246-enforce-hooks', 'develop-x', 'feature/main', null]) {
      assert.equal(branchRejection(branch), null)
    }
  })

  test('HEAD がブランチを指していれば、そのブランチの名前を返す', () => {
    assert.equal(branchOfHead({ status: 0, stdout: 'refs/heads/develop\n' }), 'develop')
    assert.equal(branchOfHead({ status: 0, stdout: 'refs/heads/246-enforce-hooks\r\n' }), '246-enforce-hooks')
  })

  test('detached HEAD か、HEAD を読めなければ null を返す', () => {
    assert.equal(branchOfHead({ status: 1, stdout: '' }), null)
    assert.equal(branchOfHead({ status: 128, stdout: '' }), null)
    assert.equal(branchOfHead({ status: null, stdout: '' }), null)
    assert.equal(branchOfHead({ status: 0, stdout: '' }), null)
  })
})

/** 呼び出しを記録する外部の処理を作る */
function fakeDeps({ branch = '1-work', checkout = 0, check = 0, env = {}, diff = { status: 0, stdout: '' }, warnings = { status: 0, stdout: '' } } = {}) {
  const calls = { checkout: [], runCheck: [], removed: [], diff: 0, warnings: 0, printed: [], errors: [], temps: 0 }
  return {
    calls,
    deps: {
      root: '/repo',
      gitDir: '/repo/.git',
      branch,
      env,
      checkout: (prefix, e) => (calls.checkout.push({ prefix, env: e }), checkout),
      runCheck: (script, e) => (calls.runCheck.push({ script, env: e }), check),
      diffAdded: () => (calls.diff++, diff),
      listWarnings: () => (calls.warnings++, warnings),
      print: (text) => calls.printed.push(text),
      printError: (text) => calls.errors.push(text),
      makeTemp: () => (calls.temps++, '/tmp/staged'),
      removeTemp: (dir) => calls.removed.push(dir),
    },
  }
}

describe('ブランチの判定と検査の順', () => {
  test('develop と main では、取り出しも検査もせずに失敗にし、作業ブランチを作る手順を表示する', () => {
    for (const branch of ['develop', 'main']) {
      const { deps, calls } = fakeDeps({ branch })
      assert.equal(runPreCommit(deps), BRANCH_REJECTED)
      assert.equal(calls.temps, 0)
      assert.equal(calls.checkout.length, 0)
      assert.equal(calls.runCheck.length, 0)
      assert.equal(calls.errors.length, 1)
      assert.match(calls.errors[0], /git switch -c /)
    }
  })

  test('作業ブランチと detached HEAD では、検査を実行する', () => {
    for (const branch of ['1-work', null]) {
      const { deps, calls } = fakeDeps({ branch })
      assert.equal(runPreCommit(deps), 0)
      assert.equal(calls.runCheck.length, 1)
      assert.deepEqual(calls.errors, [])
    }
  })
})

describe('検査の実行と終了コード', () => {
  test('取り出した先の検査の script を、元のリポジトリと取り出した先の作業ツリーで実行する', () => {
    const { deps, calls } = fakeDeps()
    assert.equal(runPreCommit(deps), 0)
    assert.equal(calls.checkout[0].prefix, '/tmp/staged/')
    assert.equal(calls.runCheck[0].script, '/tmp/staged/scripts/ci/check-all.sh')
    assert.equal(calls.runCheck[0].env.GIT_DIR, '/repo/.git')
    assert.equal(calls.runCheck[0].env.GIT_WORK_TREE, '/tmp/staged')
  })

  test('検査が失敗したら、その終了コードを返して commit を止める', () => {
    assert.equal(runPreCommit(fakeDeps({ check: 1 }).deps), 1)
    assert.equal(runPreCommit(fakeDeps({ check: 7 }).deps), 7)
  })

  test('検査が終了コードを返さなければ（シグナルでの終了など）、失敗の 1 にする', () => {
    assert.equal(runPreCommit(fakeDeps({ check: null }).deps), 1)
  })

  test('取り出しに失敗したら、検査を実行せずに 2 を返す', () => {
    const { deps, calls } = fakeDeps({ checkout: 128 })
    assert.equal(runPreCommit(deps), CHECKOUT_FAILED)
    assert.equal(CHECKOUT_FAILED, 2)
    assert.equal(calls.runCheck.length, 0)
  })

  test('成功でも失敗でも、一時ディレクトリを消す', () => {
    for (const options of [{}, { check: 1 }, { checkout: 1 }]) {
      const { deps, calls } = fakeDeps(options)
      runPreCommit(deps)
      assert.deepEqual(calls.removed, ['/tmp/staged'])
    }
  })

  test('GIT_INDEX_FILE が相対パスなら、取り出しと検査に絶対パスで渡す', () => {
    const { deps, calls } = fakeDeps({ env: { GIT_INDEX_FILE: '.git/next-index' } })
    runPreCommit(deps)
    const expected = path.join('/repo', '.git/next-index')
    assert.equal(calls.checkout[0].env.GIT_INDEX_FILE, expected)
    assert.equal(calls.runCheck[0].env.GIT_INDEX_FILE, expected)
  })

  test('GIT_INDEX_FILE がなければ、環境変数に加えない', () => {
    const { deps, calls } = fakeDeps()
    runPreCommit(deps)
    assert.equal('GIT_INDEX_FILE' in calls.checkout[0].env, false)
  })
})

describe('追加した行', () => {
  const diff = ['diff --git a/docs/a.md b/docs/a.md', '--- a/docs/a.md', '+++ b/docs/a.md', '@@ -3 +3,2 @@', '+x', '+y', '@@ -9,2 +10,0 @@', '-z', '-w', '@@ -20,0 +21 @@', '+v'].join('\n')

  test('差分の追加した行の番号を、ファイルごとに取り出す（削除だけの塊は数えない）', () => {
    assert.deepEqual([...parseAddedLines(diff).get('docs/a.md')].sort((a, b) => a - b), [3, 4, 21])
  })

  test('本文が ++ で始まる追加行（差分では +++ の形）を、ファイルの見出しと取り違えない', () => {
    const body = ['diff --git a/a.md b/a.md', '--- a/a.md', '+++ b/a.md', '@@ -1,0 +2,2 @@', '+x', '+++ y', '@@ -9,0 +11 @@', '+z'].join('\n')
    const added = parseAddedLines(body)
    assert.deepEqual([...added.keys()], ['a.md'])
    assert.deepEqual([...added.get('a.md')].sort((a, b) => a - b), [2, 3, 11])
  })

  test('新しいファイルは全ての行、削除したファイルは行なしになる', () => {
    const added = parseAddedLines(['diff --git a/docs/new.md b/docs/new.md', '--- /dev/null', '+++ b/docs/new.md', '@@ -0,0 +1,3 @@', '+a', '+b', '+c', 'diff --git a/old.md b/old.md', '--- a/old.md', '+++ /dev/null', '@@ -1,2 +0,0 @@'].join('\n'))
    assert.deepEqual([...added.get('docs/new.md')], [1, 2, 3])
    assert.equal(added.has('old.md'), false)
  })

  test('warning の一覧から、追加した行の warning だけを選ぶ。件数の行は除く', () => {
    const added = parseAddedLines(diff)
    const output = ['docs/a.md:3 要求 → リクエスト', 'docs/a.md:5 要求 → リクエスト', 'docs/b.md:3 要求 → リクエスト', '注意する語と文：3'].join('\n')
    assert.deepEqual(selectAddedWarnings(output, added), ['docs/a.md:3 要求 → リクエスト'])
  })
})

describe('検査が通った後の warning', () => {
  const diff = { status: 0, stdout: ['+++ b/docs/a.md', '@@ -1,0 +2 @@', '+x'].join('\n') }
  const list = 'docs/a.md:1 要求 → リクエスト\ndocs/a.md:2 要求 → リクエスト\n注意する語と文：2\n'

  test('追加した行の warning だけを表示し、成功にする', () => {
    const { deps, calls } = fakeDeps({ diff, warnings: { status: 0, stdout: list } })
    assert.equal(runPreCommit(deps), 0)
    assert.equal(calls.printed.length, 1)
    assert.match(calls.printed[0], /docs\/a\.md:2 /)
    assert.doesNotMatch(calls.printed[0], /docs\/a\.md:1 /)
  })

  test('追加した行に warning がなければ、何も表示しない', () => {
    const { deps, calls } = fakeDeps({ diff: { status: 0, stdout: '' }, warnings: { status: 0, stdout: list } })
    assert.equal(runPreCommit(deps), 0)
    assert.deepEqual(calls.printed, [])
  })

  test('検査が失敗したら、warning を調べずにその終了コードを返す', () => {
    const { deps, calls } = fakeDeps({ check: 1, diff, warnings: { status: 0, stdout: list } })
    assert.equal(runPreCommit(deps), 1)
    assert.equal(calls.warnings, 0)
    assert.deepEqual(calls.printed, [])
  })

  test('warning の一覧か差分を取得できなければ、成功にせず失敗にする', () => {
    assert.equal(runPreCommit(fakeDeps({ diff, warnings: { status: 1, stdout: '' } }).deps), WARNING_FAILED)
    assert.equal(runPreCommit(fakeDeps({ diff, warnings: { status: null, stdout: '' } }).deps), WARNING_FAILED)
    assert.equal(runPreCommit(fakeDeps({ diff: { status: 128, stdout: '' }, warnings: { status: 0, stdout: list } }).deps), WARNING_FAILED)
  })
})

describe('commit のメッセージの検査', () => {
  /** 実行した script を記録し、script ごとの終了コードを返す */
  function fakeRun(codes) {
    const calls = []
    return { calls, run: (name, args) => (calls.push({ name, args }), codes[name]) }
  }

  test('関連 spec の照合と日本語の文の検査の両方を、メッセージのファイルを渡して実行する', () => {
    const { calls, run } = fakeRun({ 'related-specs': 0, 'ja-text': 0 })
    assert.equal(runCommitMsg({ file: '.git/MSG', run }), 0)
    assert.deepEqual(calls, [
      { name: 'related-specs', args: ['--staged', '.git/MSG'] },
      { name: 'ja-text', args: ['--message', '.git/MSG'] },
    ])
  })

  test('片方が失敗しても、もう片方を実行し、全体は失敗にする', () => {
    const first = fakeRun({ 'related-specs': 1, 'ja-text': 0 })
    assert.equal(runCommitMsg({ file: 'm', run: first.run }), 1)
    assert.equal(first.calls.length, 2)
    const second = fakeRun({ 'related-specs': 0, 'ja-text': 1 })
    assert.equal(runCommitMsg({ file: 'm', run: second.run }), 1)
    assert.equal(second.calls.length, 2)
  })

  test('終了コードを返さない（シグナルでの終了など）検査は、失敗にする', () => {
    assert.equal(runCommitMsg({ file: 'm', run: fakeRun({ 'related-specs': null, 'ja-text': 0 }).run }), 1)
    assert.equal(runCommitMsg({ file: 'm', run: fakeRun({ 'related-specs': 0, 'ja-text': null }).run }), 1)
  })

  test('メッセージのファイルがなければ、検査を実行せずに失敗にする', () => {
    const { calls, run } = fakeRun({ 'related-specs': 0, 'ja-text': 0 })
    assert.equal(runCommitMsg({ file: undefined, run }), COMMIT_MSG_FAILED)
    assert.equal(calls.length, 0)
  })
})

/**
 * 実際の hook のテストが使う、日本語の文の検査の一覧。
 * 本物の一覧は、許容用語が使われていないと失敗にする（小さな一時のリポジトリでは使われない）ので、試す語だけの一覧にする。
 */
const MINI_LIST = JSON.stringify({ error: [{ word: '道具', use: 'ツール' }], warning: [{ word: '要求', use: 'リクエスト' }], style: { no: 4, length: 100, comma: 5, cell: 80 } })

describe('実際の hook', () => {
  const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')
  /** 実際のリポジトリと同じ置き場所にコピーする、hook とその hook が読む script（日本語の文の検査と、その検査が読む補助を含む） */
  const hookFiles = [
    'scripts/dev/hooks/pre-commit',
    'scripts/dev/hooks/commit-msg',
    'scripts/dev/lib/pre-commit.mjs',
    'scripts/dev/lib/pre-commit-main.mjs',
    'scripts/dev/lib/commit-msg.mjs',
    'scripts/dev/lib/commit-msg-main.mjs',
    'scripts/ci/ja-text.mjs',
    ...readdirSync(path.join(repoRoot, 'scripts/ci/lib'))
      .filter((name) => name.endsWith('.mjs') && !name.endsWith('.test.mjs'))
      .map((name) => `scripts/ci/lib/${name}`),
  ]
  /** 一時のリポジトリで commit する作業ブランチ */
  const WORK_BRANCH = '1-work'
  /** 日本語の文の検査を流す検査の script（実際の check-all.sh のうち、この hook が頼る部分） */
  const jaTextCheck = '#!/usr/bin/env bash\ncd "$(dirname "$0")/../.." && node scripts/ci/ja-text.mjs\n'

  /**
   * 一時のリポジトリを作り、hook とその script をコピーして、元の内容を作業ブランチに commit する（本物のリポジトリには触れない）。
   * GIT_ で始まる環境変数は外す（commit の hook の中で試すと、本物のリポジトリの設定を書き換えるため）。
   * 最初のブランチは、既定のブランチ名（init.defaultBranch）によらず作業ブランチにする（hook は develop と main の commit を失敗にするため）。
   *
   * @param {string} dir 一時のディレクトリ
   * @param {Record<string, string | undefined>} env GIT_ を外した環境
   * @param {Record<string, string>} baseline 最初に commit するファイル（パス: 内容）
   * @returns {(...args: string[]) => import('node:child_process').SpawnSyncReturns<string>} 一時のリポジトリで git を実行する関数
   */
  function initRepo(dir, env, baseline) {
    const git = (...args) => spawnSync('git', args, { cwd: dir, env, encoding: 'utf8' })
    git('init', '-q')
    git('symbolic-ref', 'HEAD', `refs/heads/${WORK_BRANCH}`)
    git('config', 'user.name', 'test')
    git('config', 'user.email', 'test@example.com')
    git('config', 'commit.gpgsign', 'false')
    for (const file of hookFiles) {
      mkdirSync(path.dirname(path.join(dir, file)), { recursive: true })
      copyFileSync(path.join(repoRoot, file), path.join(dir, file))
    }
    writeFileSync(path.join(dir, 'scripts/ci/ja-text.json'), MINI_LIST)
    for (const [file, content] of Object.entries({ 'docs/base.md': 'ふつうの文書。\n', ...baseline })) {
      mkdirSync(path.dirname(path.join(dir, file)), { recursive: true })
      writeFileSync(path.join(dir, file), content)
    }
    git('add', '-A')
    git('commit', '-q', '-m', 'base', '--no-verify')
    return git
  }

  /**
   * 検査の script を差し替えた一時のリポジトリで、ファイルをステージして pre-commit の hook を実行する。
   *
   * @param {string} checkAll 検査の script の中身
   * @param {{ fakeBash?: boolean, baseline?: Record<string, string>, files?: Record<string, string>, gitConfig?: string[] | null, head?: string | null }} [options]
   *   gitConfig は一時のリポジトリだけに設定する git の設定（キーと値）。fakeBash が true なら、PATH の先頭に、終了コード 42 で終わる偽の bash を置く。baseline は最初に commit するファイル、files はステージするファイル。
   *   head は hook を実行するときのブランチ。null なら detached HEAD にする
   * @returns {{ status: number | null, output: string, error: string }} hook の終了コードと標準出力、標準エラー
   */
  function runHook(checkAll, { fakeBash = false, baseline = {}, files = { 'staged.txt': 'staged' }, gitConfig = null, head = WORK_BRANCH } = {}) {
    const dir = mkdtempSync(path.join(tmpdir(), 'pre-commit-test-'))
    try {
      const env = withoutGitEnv()
      if (fakeBash) {
        const bin = path.join(dir, '.fake-bin')
        mkdirSync(bin)
        writeFileSync(path.join(bin, 'bash'), '#!/bin/sh\nexit 42\n')
        chmodSync(path.join(bin, 'bash'), 0o755)
        const key = Object.keys(env).find((name) => name.toUpperCase() === 'PATH') ?? 'PATH'
        env[key] = `${bin}${path.delimiter}${env[key] ?? ''}`
      }
      mkdirSync(path.join(dir, 'scripts/ci'), { recursive: true })
      const git = initRepo(dir, env, { ...baseline, 'scripts/ci/check-all.sh': checkAll })
      // 設定は一時のリポジトリの中だけに書く（git は cwd が一時のディレクトリで、GIT_ を外した環境で実行する）
      if (gitConfig) git('config', ...gitConfig)
      if (head === null) git('checkout', '-q', '--detach')
      else if (head !== WORK_BRANCH) git('switch', '-q', '-c', head)
      for (const [file, content] of Object.entries(files)) {
        mkdirSync(path.dirname(path.join(dir, file)), { recursive: true })
        writeFileSync(path.join(dir, file), content)
      }
      git('add', '-A')
      writeFileSync(path.join(dir, 'unstaged.txt'), 'unstaged')
      const result = spawnSync(gitBash(), ['scripts/dev/hooks/pre-commit'], { cwd: dir, env, encoding: 'utf8' })
      return { status: result.status, output: result.stdout, error: result.stderr }
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  }

  /** 実行されたら印を出力して成功する検査の script */
  const markedCheck = '#!/usr/bin/env bash\necho CHECKED\n'

  test('develop と main では、検査の前に失敗にし、ブランチごとの作業ブランチを作る手順を表示する', () => {
    const expected = { develop: /git switch -c <issue の番号>-<短い名前>$/m, main: /git switch -c hotfix-<issue の番号>-<短い名前>$/m }
    for (const [head, command] of Object.entries(expected)) {
      const { status, output, error } = runHook(markedCheck, { head })
      assert.equal(status, BRANCH_REJECTED)
      assert.doesNotMatch(output, /CHECKED/)
      assert.match(error, new RegExp(`${head} には直接 commit しない`))
      assert.match(error, command)
    }
  })

  test('作業ブランチと detached HEAD では、検査を実行して通す', () => {
    for (const head of [WORK_BRANCH, null]) {
      const { status, output } = runHook(markedCheck, { head })
      assert.equal(status, 0)
      assert.match(output, /CHECKED/)
    }
  })

  test('検査が成功すれば 0 を返し、ステージした内容だけを検査する', () => {
    const { status, output } = runHook('#!/usr/bin/env bash\ncd "$(dirname "$0")/../.." && ls\n')
    assert.equal(status, 0)
    assert.match(output, /staged\.txt/)
    assert.doesNotMatch(output, /unstaged\.txt/)
  })

  test('検査が失敗すれば、その終了コードを返して commit を止める', () => {
    assert.equal(runHook('#!/usr/bin/env bash\nexit 1\n').status, 1)
    assert.equal(runHook('#!/usr/bin/env bash\nexit 3\n').status, 3)
  })

  test('検査は、PATH の先頭の bash でなく、hook を実行している bash で実行する', () => {
    assert.equal(runHook('#!/usr/bin/env bash\nexit 0\n', { fakeBash: true }).status, 0)
  })

  test('ステージした追加行に使わない語（error）があれば、commit を止める', () => {
    const { status, output } = runHook(jaTextCheck, { files: { 'docs/a.md': '道具を使う。\n' } })
    assert.equal(status, 1)
    assert.match(output, /docs\/a\.md:1 道具/)
  })

  test('diff.mnemonicPrefix=true の設定でも、追加行の warning を表示する', () => {
    const { status, output } = runHook(jaTextCheck, {
      baseline: { 'docs/a.md': 'ふつうの行。\n' },
      files: { 'docs/a.md': 'ふつうの行。\nサーバーが要求を受ける。\n' },
      gitConfig: ['diff.mnemonicPrefix', 'true'],
    })
    assert.equal(status, 0)
    assert.match(output, /docs\/a\.md:2 要求/)
  })

  test('使わない語がなければ、日本語の文の検査を通って成功する', () => {
    assert.equal(runHook(jaTextCheck, { files: { 'docs/a.md': 'ツールを使う。\n' } }).status, 0)
  })

  test('追加行が warning だけなら、commit は成功し、warning の行（ファイル、行、内容）を表示する', () => {
    const { status, output } = runHook(jaTextCheck, { baseline: { 'docs/a.md': 'ふつうの行。\n' }, files: { 'docs/a.md': 'ふつうの行。\nサーバーが要求を受ける。\n' } })
    assert.equal(status, 0)
    assert.match(output, /docs\/a\.md:2 要求/)
  })

  test('追加行の表のセルの warning（長いセル）を表示し、commit は成功する', () => {
    const cell = 'あ'.repeat(85)
    const { status, output } = runHook(jaTextCheck, { baseline: { 'docs/a.md': 'ふつうの行。\n' }, files: { 'docs/a.md': `ふつうの行。\n\n| 列 |\n| --- |\n| ${cell} |\n` } })
    assert.equal(status, 0)
    assert.match(output, /docs\/a\.md:5 長いセル/)
  })

  test('既存の行から始まる文に行を足して閾値以上になったら、その文の warning を（最初の追加行で）表示する', () => {
    const head = 'あ'.repeat(60)
    const tail = 'い'.repeat(60)
    const { status, output } = runHook(jaTextCheck, { baseline: { 'docs/a.md': `${head}、\n` }, files: { 'docs/a.md': `${head}、\n${tail}。\n` } })
    assert.equal(status, 0)
    assert.match(output, /docs\/a\.md:2 長い文/)
  })

  test('追加していない既存の行の warning は表示しない', () => {
    const baseline = { 'docs/a.md': '古い要求の行。\nふつうの行。\n' }
    const { status, output } = runHook(jaTextCheck, { baseline, files: { 'docs/a.md': '古い要求の行。\nふつうの行。\n新しい行。\n' } })
    assert.equal(status, 0)
    assert.doesNotMatch(output, /docs\/a\.md:1 /)
    assert.doesNotMatch(output, /警告：追加した行/)
  })

  test('warning の一覧を出力する検査が失敗したら、成功にせず失敗にする', () => {
    const dir = mkdtempSync(path.join(tmpdir(), 'pre-commit-test-'))
    try {
      const env = withoutGitEnv()
      const git = initRepo(dir, env, { 'scripts/ci/check-all.sh': '#!/usr/bin/env bash\nexit 0\n' })
      // 日本語の文の検査を壊す（異常終了させる）
      writeFileSync(path.join(dir, 'scripts/ci/ja-text.mjs'), 'process.exit(5)\n')
      writeFileSync(path.join(dir, 'staged.txt'), 'staged')
      git('add', '-A')
      const result = spawnSync(gitBash(), ['scripts/dev/hooks/pre-commit'], { cwd: dir, env, encoding: 'utf8' })
      assert.equal(result.status, WARNING_FAILED)
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  })
})

describe('実際の commit-msg の hook', () => {
  const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')
  const hookFiles = [
    'scripts/dev/hooks/commit-msg',
    'scripts/dev/lib/commit-msg.mjs',
    'scripts/dev/lib/commit-msg-main.mjs',
    'scripts/ci/ja-text.mjs',
    ...readdirSync(path.join(repoRoot, 'scripts/ci/lib'))
      .filter((name) => name.endsWith('.mjs') && !name.endsWith('.test.mjs'))
      .map((name) => `scripts/ci/lib/${name}`),
  ]

  /**
   * 一時のディレクトリ（Git のリポジトリでない場所は hook が使えないので、git init する）に hook をコピーし、
   * 関連 spec の照合を、終了コードを環境変数で決める代わりの script に差し替えて、hook を実行する。
   *
   * @param {string} message コミットメッセージのファイルの中身
   * @param {number} relatedStatus 関連 spec の照合の代わりの script が返す終了コード
   * @returns {{ status: number | null, output: string }} hook の終了コードと標準出力
   */
  function runCommitMsgHookFull(message, relatedStatus) {
    const dir = mkdtempSync(path.join(tmpdir(), 'commit-msg-test-'))
    try {
      const env = withoutGitEnv()
      spawnSync('git', ['init', '-q'], { cwd: dir, env })
      for (const file of hookFiles) {
        mkdirSync(path.dirname(path.join(dir, file)), { recursive: true })
        copyFileSync(path.join(repoRoot, file), path.join(dir, file))
      }
      writeFileSync(path.join(dir, 'scripts/ci/ja-text.json'), MINI_LIST)
      writeFileSync(path.join(dir, 'scripts/ci/related-specs.mjs'), `process.exit(${relatedStatus})\n`)
      writeFileSync(path.join(dir, 'MSG'), message)
      const result = spawnSync(gitBash(env), ['scripts/dev/hooks/commit-msg', 'MSG'], { cwd: dir, env, encoding: 'utf8' })
      return { status: result.status, output: result.stdout }
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  }

  /** hook の終了コードだけを返す */
  function runCommitMsgHook(message, relatedStatus) {
    return runCommitMsgHookFull(message, relatedStatus).status
  }

  test('メッセージに使わない語（error）がなければ成功する', () => {
    assert.equal(runCommitMsgHook('ツールを追加する\n', 0), 0)
  })

  test('メッセージに使わない語（error）があれば失敗する', () => {
    const { status, output } = runCommitMsgHookFull('道具を追加する\n', 0)
    assert.equal(status, 1)
    assert.match(output, /道具/)
  })

  test('git commit -v の差分に使わない語があっても、数えない', () => {
    const message = 'ツールを追加する\n# ------------------------ >8 ------------------------\ndiff --git a/a b/a\n-道具を使う\n'
    assert.equal(runCommitMsgHook(message, 0), 0)
  })

  test('# で始まる行の使わない語は、数えない', () => {
    assert.equal(runCommitMsgHook('ツールを追加する\n# 道具\n', 0), 0)
  })

  test('関連 spec の照合が失敗すれば、メッセージが正しくても失敗する', () => {
    assert.equal(runCommitMsgHook('ツールを追加する\n', 1), 1)
  })

  test('関連 spec の照合とメッセージの検査の両方が失敗しても、失敗する', () => {
    assert.notEqual(runCommitMsgHook('道具を追加する\n', 1), 0)
  })
})
