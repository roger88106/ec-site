// agent-guard.mjs（Claude Code の PreToolUse の hook）のテスト（node --test scripts/dev/hooks/agent-guard.test.mjs）
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, readFileSync, realpathSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { withoutGitEnv } from '../../ci/lib/git-env.mjs'
import { createDeps, handleInput, judgeCommand, parseCommands, parseGitInvocation, resolveDir, splitCommands } from './agent-guard.mjs'

/** pre-commit hook を置いたときの中身（開発環境の起動の置き方のとおり） */
const INSTALLED_HOOK = '#!/bin/sh\n"$(git rev-parse --show-toplevel)/scripts/dev/hooks/pre-commit" || exit 1\n'

/** テストの起点のディレクトリ（OS の絶対パス） */
const CWD = path.resolve(tmpdir(), 'agent-guard-cwd')

/**
 * git と gh の呼び出しと、ファイルの読み込みを差し替えた依存を作る。
 * rev-parse は `.git/hooks/pre-commit` を返し、show-ref は `tags` にある tag だけを見つける。ファイルは `files`（絶対パスをキー）から読む。
 * gh は `gh`（結果か、引数と選択肢から結果を返す関数）を返す。既定は失敗。
 */
function fakeDeps({
  files = { [path.join(CWD, '.git/hooks/pre-commit')]: INSTALLED_HOOK },
  revParse = { status: 0, stdout: '.git/hooks/pre-commit\n' },
  tags = [],
  gh = { status: 1, stdout: '', stderr: 'テストの gh は、この例では呼ばれない想定' },
} = {}) {
  const calls = []
  const ghCalls = []
  return {
    calls,
    ghCalls,
    runGit: (args, cwd, env = {}) => {
      calls.push({ args, cwd, env })
      if (args[0] === 'show-ref') return { status: tags.some((tag) => args.includes(`refs/tags/${tag}`)) ? 0 : 1, stdout: '' }
      return revParse
    },
    readText: (file) => files[path.resolve(file)] ?? null,
    runGh: (args, options) => {
      ghCalls.push({ args, ...options })
      return typeof gh === 'function' ? gh(args, options) : gh
    },
  }
}

/** 判定の結果が拒否で、理由が `pattern` に一致することを確認する */
function assertDenied(command, pattern, deps = fakeDeps()) {
  const result = judgeCommand(command, CWD, deps)
  assert.equal(result?.decision, 'deny', `拒否されない：${command}`)
  assert.match(result.reason, pattern, `理由が違う：${command}`)
}

/** 判定の結果が確認を求めるもので、理由が `pattern` に一致することを確認する */
function assertAsked(command, pattern, deps = fakeDeps()) {
  const result = judgeCommand(command, CWD, deps)
  assert.equal(result?.decision, 'ask', `確認を求めない：${command}（${JSON.stringify(result)}）`)
  assert.match(result.reason, pattern, `理由が違う：${command}（${result.reason}）`)
}

/** 判定の結果が通す（null）ことを確認する */
function assertPassed(command, deps = fakeDeps()) {
  assert.equal(judgeCommand(command, CWD, deps), null, `通らない：${command}`)
}

describe('コマンドの文字列を、1 つずつのコマンドの語に分ける', () => {
  test('&&、||、;、|、改行で区切る', () => {
    assert.deepEqual(splitCommands('cd a && git status || echo x; ls | tail\ngit log'), [['cd', 'a'], ['git', 'status'], ['echo', 'x'], ['ls'], ['tail'], ['git', 'log']])
  })

  test('引用符の中の区切りと空白は、1 つの語のまま残す', () => {
    assert.deepEqual(splitCommands(`echo "git push --force; x" 'a && b'`), [['echo', 'git push --force; x', 'a && b']])
  })

  test('引用符の外の \\ は次の文字をそのまま残し、二重引用符の中の \\" は " にする', () => {
    assert.deepEqual(splitCommands('echo a\\;b "c\\"d"'), [['echo', 'a;b', 'c"d']])
  })

  test('# から行の終わりまでのコメントは読まない', () => {
    assert.deepEqual(splitCommands('git push origin x # --force\necho a#b'), [['git', 'push', 'origin', 'x'], ['echo', 'a#b']])
  })

  test('リダイレクトとその先のファイルは、語に含めない', () => {
    assert.deepEqual(splitCommands('git push origin x 2>&1 | tail > out.txt'), [['git', 'push', 'origin', 'x'], ['tail']])
    assert.deepEqual(splitCommands('git status &> log.txt; echo a >>b'), [['git', 'status'], ['echo', 'a']])
  })

  test('ヒアドキュメントの本文は、コマンドとして読まない', () => {
    const command = "git commit -F - <<'EOF'\ngit push --force\nEOF\necho done"
    assert.deepEqual(splitCommands(command), [['git', 'commit', '-F', '-'], ['echo', 'done']])
  })

  test('括弧の中のコマンドも区切って読む', () => {
    assert.deepEqual(splitCommands('(cd a && git push -f)'), [['cd', 'a'], ['git', 'push', '-f']])
  })

  test('$(…) とバッククォートの中は、引用符の中でも外でも、別のコマンドとして読む', () => {
    assert.deepEqual(splitCommands('out="$(git push --force 2>&1)"; echo "$out"'), [['git', 'push', '--force'], ['out=$(…)'], ['echo', '$out']])
    assert.deepEqual(splitCommands('echo "a `git push -f` b"'), [['git', 'push', '-f'], ['echo', 'a `…` b']])
    assert.deepEqual(splitCommands('echo $(cd x && git log)'), [['cd', 'x'], ['git', 'log'], ['echo', '$(…)']])
  })

  test('$(…) の中のヒアドキュメントの本文は、" を含んでもコマンドとして読まない', () => {
    const command = "git commit -m \"$(cat <<'EOF'\nfix: 12\" の画面\ngit commit -n は拒否する\nEOF\n)\" && git status"
    assert.deepEqual(splitCommands(command), [['cat'], ['git', 'commit', '-m', '$(…)'], ['git', 'status']])
  })

  test('引用符、$(…)、バッククォートが閉じていなければ、例外にする', () => {
    for (const command of ['echo "abc', "echo 'abc", 'echo "$(git log"', 'echo `git log', "git commit -m \"$(cat <<'EOF'\nx\nEOF\n"]) {
      assert.throws(() => splitCommands(command), /閉じていない/, command)
    }
  })
})

describe('cd と git -C から、対象のディレクトリを決める', () => {
  test('cd がなければ、入力の cwd を使う', () => {
    assert.deepEqual(parseCommands('git status', CWD).map((c) => c.dir), [CWD])
  })

  test('cd の後のコマンドは、cd の先（相対パスは前のディレクトリから）を使う', () => {
    const dirs = parseCommands('cd sub && git status; cd ../other; git log', CWD).map((c) => c.dir)
    assert.deepEqual(dirs, [CWD, path.join(CWD, 'sub'), path.join(CWD, 'sub'), path.join(CWD, 'other')])
  })

  test('env -C は、そのコマンドのディレクトリだけを替える', () => {
    const commands = parseCommands('env -C sub git status; git log', CWD)
    assert.deepEqual(commands.map((c) => [c.words[0], c.dir]), [['git', path.join(CWD, 'sub')], ['git', CWD]])
  })

  test('git -C は、そのコマンドのディレクトリを替える（複数は順に重ねる）', () => {
    const [command] = parseCommands('git -C a -C b commit -m x', CWD)
    assert.equal(parseGitInvocation(command).dir, path.join(CWD, 'a', 'b'))
  })

  test('Windows では、Git Bash の /c/… の形をドライブのパスにする', () => {
    assert.equal(resolveDir('C:\\work', '/c/Users/x', { platform: 'win32', home: 'C:\\Users\\me' }), 'C:\\Users\\x')
    assert.equal(resolveDir('C:\\work', '~/repo', { platform: 'win32', home: 'C:\\Users\\me' }), 'C:\\Users\\me\\repo')
    assert.equal(resolveDir('/work', '/c/Users/x', { platform: 'linux', home: '/home/me' }), '/c/Users/x')
  })
})

describe('git の呼び出しを読む', () => {
  test('フルパスと git.exe の形も git として読み、似た名前のコマンドは読まない', () => {
    for (const words of [['/usr/bin/git', 'push'], ['C:\\Program Files\\Git\\cmd\\git.exe', 'push'], ['git.exe', 'push'], ['GIT', 'push']]) {
      assert.equal(parseGitInvocation({ words, dir: CWD })?.subcommand, 'push', words[0])
    }
    for (const program of ['gitk', 'github', 'legit', 'echo']) {
      assert.equal(parseGitInvocation({ words: [program, 'push'], dir: CWD }), null, program)
    }
  })

  test('前に付けた環境変数の設定と command・env は飛ばす', () => {
    const [command] = parseCommands('GIT_TRACE=1 command git push -f', CWD)
    assert.deepEqual(parseGitInvocation(command)?.args, ['-f'])
  })

  test('git の前の選択肢（-c、--git-dir など）を飛ばして、サブコマンドを読む', () => {
    const invocation = parseGitInvocation({ words: ['git', '-c', 'user.name=a', '--no-pager', '--git-dir', 'x/.git', 'commit', '-m', 'y'], dir: CWD })
    assert.equal(invocation.subcommand, 'commit')
    assert.deepEqual(invocation.configs, ['user.name=a'])
    assert.deepEqual(invocation.args, ['-m', 'y'])
  })
})

describe('push 済みの履歴を書き換える push を拒否する', () => {
  test('force の選択肢と + で始まる refspec を拒否し、代わりに新しい commit を push するよう返す', () => {
    for (const command of [
      'git push --force',
      'git push -f origin 246-enforce-hooks',
      'git push -uf origin 246-enforce-hooks',
      'git push --force-with-lease',
      'git push --force-with-lease=main:abc123 origin main',
      'git push --force-if-includes --force-with-lease',
      'git push origin +246-enforce-hooks',
      'git push origin +HEAD:refs/heads/x',
      'git push --dry-run --force origin x',
      'git push --forc origin x',
      'git push --mirror origin',
      'git push --mirr origin',
    ]) {
      assertDenied(command, /force push.*新しい commit/s)
    }
  })

  test('cd を前に付けた形、つないだ形、git -C、フルパスの git でも拒否する', () => {
    for (const command of [
      'cd sub && git push --force',
      'git status; git push -f',
      'git fetch\ngit push --force',
      'git log | git push -f',
      'git -C sub push --force',
      '"C:/Program Files/Git/cmd/git.exe" push --force',
      '/usr/bin/git push -f origin x',
    ]) {
      assertDenied(command, /force push/)
    }
  })

  test('通常の push、upstream の設定、dry-run（-n）、値を取る選択肢の値は通す', () => {
    for (const command of [
      'git push',
      'git push -u origin 246-enforce-hooks',
      'git push -n origin x',
      'git push origin main:refs/heads/main',
      'git push --no-follow-tags origin x',
      'git push --no-force-with-lease origin x',
      'git push -ofoo origin x',
      'git push -o +x origin x',
      'git push --push-option +x origin x',
    ]) {
      assertPassed(command)
    }
  })

  test('$(…) とバッククォートの中の push、ヒアドキュメントの commit に続けた push を拒否する', () => {
    for (const command of [
      'out="$(git push --force 2>&1)"; echo "$out"',
      'echo "$(git push -f)"',
      'echo "`git push -f`"',
      'x=`git push --force`',
      "git commit -m \"$(cat <<'EOF'\nfix: 12\" の画面\nEOF\n)\" && git push --force",
    ]) {
      assertDenied(command, /force push/)
    }
  })

  test('前に付ける timeout・nice・stdbuf・xargs・env（選択肢を含む）を介した push を拒否する', () => {
    for (const command of [
      'timeout 60 git push --force',
      'timeout -k 5 --signal=KILL 60s git push -f',
      'env -u GIT_TRACE git push -f',
      'env -i PATH=/usr/bin git push -f',
      'nice git push -f',
      'nice -n 5 git push -f',
      'stdbuf -oL git push --force',
      'stdbuf -o L git push --force',
      'echo x | xargs git push --force origin',
      'xargs -n 1 -I{} git push -f origin {}',
    ]) {
      assertDenied(command, /force push/)
    }
    assertDenied('env -C sub git commit --no-verify -m x', /--no-verify/)
    assertPassed('timeout 60 git push origin x')
  })

  test('引用符の中や、git 以外のコマンドの引数の文字列は通す', () => {
    for (const command of ['echo "git push --force"', "grep -n 'git push -f' a.md", 'echo git push --force']) {
      assertPassed(command)
    }
  })
})

describe('commit の前の検査の省略を拒否する', () => {
  test('commit と push の --no-verify、commit の -n（短い選択肢の組み合わせを含む）を拒否し、問題を直すよう返す', () => {
    for (const command of [
      'git commit --no-verify -m x',
      'git commit --amend --no-verify',
      'git commit --no-verif -m x',
      'git commit -n -m x',
      'git commit -an -m x',
      'git commit -anm x',
      'git push --no-verify',
      'git push --no-verify origin x',
    ]) {
      assertDenied(command, /--no-verify.*直して/s)
    }
  })

  test('メッセージなどの値の中の -n と、--amend・--no-edit は通す', () => {
    for (const command of [
      'git commit -m -n',
      'git commit -mn',
      'git commit -m "git commit -n"',
      'git commit --message -n',
      'git commit --amend',
      'git commit --amend --no-edit',
      "git commit -m \"$(cat <<'EOF'\nfix: 12\" の画面\n\ngit commit -n は拒否する\nEOF\n)\"",
      'git commit -F - -a',
    ]) {
      assertPassed(command)
    }
  })
})

describe('hook の置き場所を替える git -c core.hooksPath を拒否する', () => {
  test('空白あり・なし、大文字小文字、--config-env の形を拒否する', () => {
    for (const command of [
      'git -c core.hooksPath=/dev/null commit -m x',
      'git -ccore.hooksPath=x commit -m x',
      'git -c CORE.HOOKSPATH=x push',
      'git -c core.hookspath= commit -m x',
      'git --config-env=core.hooksPath=HOOKS commit -m x',
    ]) {
      assertDenied(command, /core\.hooksPath/)
    }
  })

  test('他の設定の -c は通す', () => {
    assertPassed('git -c user.name=a commit -m x')
    assertPassed('git -c core.autocrlf=false status')
  })
})

describe('pre-commit hook を置いていないリポジトリの commit を拒否する', () => {
  test('hook のファイルがなければ拒否し、hook のパスと置き方の文書を返す', () => {
    const result = judgeCommand('git commit -m x', CWD, fakeDeps({ files: {} }))
    assert.equal(result?.decision, 'deny')
    assert.match(result.reason, /pre-commit/)
    assert.ok(result.reason.includes(path.join(CWD, '.git/hooks/pre-commit')), result.reason)
    assert.match(result.reason, /docs\/guides\/setup\.md/)
  })

  test('hook のファイルが scripts/dev/hooks/pre-commit を呼んでいなければ拒否する', () => {
    const deps = fakeDeps({ files: { [path.join(CWD, '.git/hooks/pre-commit')]: '#!/bin/sh\nexit 0\n' } })
    assertDenied('git commit -m x', /docs\/guides\/setup\.md/, deps)
  })

  test('対象のリポジトリを決められない（rev-parse が失敗する）ときは拒否する', () => {
    assertDenied('git commit -m x', /リポジトリ/, fakeDeps({ revParse: { status: 128, stdout: '' } }))
    assertDenied('git commit -m x', /リポジトリ/, fakeDeps({ revParse: { status: null, stdout: '' } }))
  })

  test('hook が置かれていれば通す', () => {
    assertPassed('git commit -m x')
    assertPassed('git commit --amend')
  })

  test('rev-parse は、cd と git -C で決めたディレクトリで、git の前の --git-dir を付けて呼ぶ', () => {
    const deps = fakeDeps({ files: { [path.join(CWD, 'a', 'b', '.git/hooks/pre-commit')]: INSTALLED_HOOK } })
    assert.equal(judgeCommand('cd a && git -C b --git-dir=.git commit -m x', CWD, deps), null)
    assert.deepEqual(deps.calls, [{ args: ['--git-dir=.git', 'rev-parse', '--git-path', 'hooks/pre-commit'], cwd: path.join(CWD, 'a', 'b'), env: {} }])
  })

  test('前に付けた GIT_DIR・GIT_WORK_TREE は、ディレクトリからの絶対パスにして rev-parse の環境に渡す', () => {
    const deps = fakeDeps()
    assert.equal(judgeCommand('GIT_DIR=other/.git GIT_WORK_TREE=other GIT_TRACE=1 git commit -m x', CWD, deps), null)
    assert.deepEqual(deps.calls[0].env, { GIT_DIR: path.join(CWD, 'other', '.git'), GIT_WORK_TREE: path.join(CWD, 'other') })
  })

  test('commit 以外の git のコマンドでは、hook を調べない', () => {
    const deps = fakeDeps({ files: {} })
    assertPassed('git status && git push origin x && git log', deps)
    assert.deepEqual(deps.calls.filter((call) => call.args.includes('rev-parse')), [])
  })
})

/** マージする pull request の今の head の commit */
const HEAD_SHA = '28361e9899ad2db8de4c6452d1a2b21aa83e370d'

/** 危険度が低で、issue を閉じる本文 */
const LOW_BODY = '## 概要\n\n変更の説明\n\nCloses #246\n\n## 危険度\n\n<!-- 判定：高 -->\n判定：低\n'

/** gh pr merge の、許可なしに通せる形 */
const MERGE = `gh pr merge 246 --squash --match-head-commit ${HEAD_SHA}`

/** Dependabot の作成者（gh api graphql の author の形） */
const DEPENDABOT = { __typename: 'Bot', login: 'dependabot' }

/**
 * CI の検査（check run）を作る。
 *
 * @param {string} name 検査の名前
 * @param {string} [conclusion] 結論
 * @param {string} [status] 状態。COMPLETED でなければ結論は null
 * @param {string | null} [startedAt] 開始の時刻
 * @param {string} [workflow] workflow の名前
 */
function check(name, conclusion = 'SUCCESS', status = 'COMPLETED', startedAt = '2026-10-28T10:00:00Z', workflow = 'CI') {
  return {
    __typename: 'CheckRun',
    name,
    status,
    conclusion: status === 'COMPLETED' ? conclusion : null,
    startedAt,
    checkSuite: { workflowRun: { workflow: { name: workflow } } },
  }
}

/** 前の実行の開始の時刻 */
const OLDER = '2026-10-28T09:00:00Z'

/** 再実行の開始の時刻 */
const NEWER = '2026-10-28T11:00:00Z'

/**
 * gh api graphql が返す pull request を作る。既定は、許可なしにマージする条件を全て満たすもの。
 * closing は閉じる issue とそのラベル、files は変更のファイル、checks は CI の検査、ほかの項目は pull request の項目を上書きする。
 */
function pullRequest({
  closing = [{ number: 246, labels: ['種類:整理'] }],
  files = ['README.md'],
  checks = [check('危険度の照合'), check('バックエンド'), check('E2E', 'SKIPPED'), check('CodeQL', 'NEUTRAL')],
  ...fields
} = {}) {
  const rollup = checks === null ? null : { contexts: { nodes: checks, pageInfo: { hasNextPage: false } } }
  return {
    data: {
      repository: {
        pullRequest: {
          state: 'OPEN',
          baseRefName: 'develop',
          headRefOid: HEAD_SHA,
          title: 'hooks: マージに確認を求める',
          body: LOW_BODY,
          author: { __typename: 'User', login: 'roger88106' },
          changedFiles: files.length,
          files: { nodes: files.map((file) => ({ path: file })), pageInfo: { hasNextPage: false } },
          closingIssuesReferences: {
            nodes: closing.map(({ number, labels }) => ({ number, labels: { nodes: labels.map((name) => ({ name })), pageInfo: { hasNextPage: false } } })),
            pageInfo: { hasNextPage: false },
          },
          commits: { nodes: [{ commit: { statusCheckRollup: rollup } }] },
          ...fields,
        },
      },
    },
  }
}

/** pull request を返す gh を持つ依存を作る */
function mergeDeps(fields = {}) {
  return fakeDeps({ gh: { status: 0, stdout: JSON.stringify(pullRequest(fields)), stderr: '' } })
}

describe('マージのコマンドを判定する', () => {
  test('gh api のマージと --admin は拒否し、gh pr merge --match-head-commit を使うよう返す', () => {
    for (const command of [
      `gh api -X PUT repos/roger88106/ec-site/pulls/246/merge`,
      `gh api --method PUT /repos/{owner}/{repo}/pulls/246/merge`,
      `gh api repos/roger88106/ec-site/pulls/246/merge -f merge_method=squash`,
      `gh pr merge 246 --admin --match-head-commit ${HEAD_SHA}`,
    ]) {
      assertDenied(command, /--match-head-commit/, mergeDeps())
    }
  })

  test('--match-head-commit がない gh pr merge は拒否する', () => {
    for (const command of ['gh pr merge 246', 'gh pr merge 246 --squash --auto', 'gh pr merge']) {
      assertDenied(command, /--match-head-commit/, mergeDeps())
    }
  })

  test('自動のマージの解除（--disable-auto だけの形）は、gh を呼ばずに通す', () => {
    for (const command of ['gh pr merge 246 --disable-auto', 'gh pr merge --disable-auto', 'gh pr merge 246 -R other/repo --disable-auto']) {
      const deps = mergeDeps()
      assertPassed(command, deps)
      assert.deepEqual(deps.ghCalls, [], command)
    }
    assertDenied('gh pr merge 246 --disable-auto --squash', /--match-head-commit/, mergeDeps())
    assertDenied('gh pr merge 246 --disable-auto --auto', /--match-head-commit/, mergeDeps())
    assertDenied('gh pr merge 246 --disable-auto --admin', /--admin/, mergeDeps())
    assertAsked(`gh pr merge 246 --disable-auto --auto --match-head-commit ${HEAD_SHA}`, /--auto/, mergeDeps())
  })

  test('--auto は、マージの時点の条件を判定できないので確認を求める', () => {
    const deps = mergeDeps()
    assertAsked(`${MERGE} --auto`, /--auto/, deps)
  })

  test('条件を全て満たすマージは通す（skipped と neutral の検査を含む）', () => {
    const deps = mergeDeps()
    assertPassed(MERGE, deps)
    assert.equal(deps.ghCalls.length, 1)
  })

  test('フルパスの gh と gh.exe でも判定する', () => {
    for (const program of ['"/c/Program Files/GitHub CLI/gh.exe"', 'gh.exe', '/usr/bin/gh']) {
      assertPassed(`${program} pr merge 246 --match-head-commit ${HEAD_SHA}`, mergeDeps())
      assertDenied(`${program} pr merge 246`, /--match-head-commit/, mergeDeps())
      assertAsked(`${program} pr merge 246 --match-head-commit ${HEAD_SHA}`, /向き先/, mergeDeps({ baseRefName: 'main' }))
    }
  })

  test('番号も URL もない gh pr merge は、今のブランチの pull request を指すので、gh を呼ばずに確認を求める', () => {
    for (const command of [`gh pr merge --match-head-commit ${HEAD_SHA}`, `gh pr merge 246-enforce-hooks --match-head-commit ${HEAD_SHA}`]) {
      const deps = mergeDeps()
      assertAsked(command, /番号か URL/, deps)
      assert.deepEqual(deps.ghCalls, [])
    }
  })

  test('条件が 1 つ欠けると、欠けた条件を書いて確認を求める', () => {
    const cases = [
      [{ state: 'MERGED' }, /開いていない/],
      [{ baseRefName: 'main' }, /向き先.*main/],
      [{ headRefOid: 'ffffffffffffffffffffffffffffffffffffffff' }, /head/],
      [{ checks: [check('危険度の照合'), check('E2E', null, 'IN_PROGRESS')] }, /終わっていない.*E2E/],
      [{ checks: [check('危険度の照合'), check('E2E', null, 'QUEUED')] }, /終わっていない.*E2E/],
      [{ checks: [check('危険度の照合'), check('E2E', 'FAILURE')] }, /成功していない.*E2E/],
      [{ checks: [check('危険度の照合'), check('E2E', 'CANCELLED')] }, /成功していない.*E2E/],
      [{ checks: [check('危険度の照合'), { __typename: 'StatusContext', context: '外部の検査', state: 'PENDING' }] }, /終わっていない.*外部の検査/],
      [{ checks: [] }, /CI の検査がない/],
      [{ checks: null }, /CI の検査がない/],
      [{ checks: [check('バックエンド')] }, /危険度の照合/],
      [{ checks: [check('危険度の照合', 'SKIPPED'), check('バックエンド')] }, /危険度の照合/],
      [{ body: LOW_BODY.replace('判定：低', '判定：高') }, /本文の危険度/],
      [{ body: 'Closes #246' }, /本文の危険度/],
      [{ closing: [] }, /Closes/],
      [{ closing: [{ number: 246, labels: ['種類:整理'] }, { number: 247, labels: ['種類:機能'] }] }, /種類:機能.*#247/],
    ]
    for (const [fields, pattern] of cases) {
      assertAsked(MERGE, pattern, mergeDeps(fields))
    }
  })

  test('同じ検査が再実行で重なれば、最も新しい実行だけで判定する', () => {
    const base = [check('危険度の照合'), check('バックエンド')]
    for (const oldConclusion of ['FAILURE', 'CANCELLED']) {
      assertPassed(MERGE, mergeDeps({ checks: [...base, check('E2E', oldConclusion, 'COMPLETED', OLDER), check('E2E', 'SUCCESS', 'COMPLETED', NEWER)] }))
    }
    assertPassed(MERGE, mergeDeps({ checks: [check('バックエンド'), check('危険度の照合', 'FAILURE', 'COMPLETED', OLDER, '危険度'), check('危険度の照合', 'SUCCESS', 'COMPLETED', NEWER, '危険度')] }))
    assertPassed(
      MERGE,
      mergeDeps({
        checks: [
          ...base,
          { __typename: 'StatusContext', context: '外部の検査', state: 'FAILURE', createdAt: OLDER },
          { __typename: 'StatusContext', context: '外部の検査', state: 'SUCCESS', createdAt: NEWER },
        ],
      }),
    )
    assertAsked(MERGE, /終わっていない.*E2E/, mergeDeps({ checks: [...base, check('E2E', 'SUCCESS', 'COMPLETED', OLDER), check('E2E', null, 'IN_PROGRESS', NEWER)] }))
    assertAsked(MERGE, /成功していない.*E2E/, mergeDeps({ checks: [...base, check('E2E', 'SUCCESS', 'COMPLETED', OLDER), check('E2E', 'FAILURE', 'COMPLETED', NEWER)] }))
  })

  test('workflow が違えば、同じ名前の検査も別の検査として判定する', () => {
    const checks = [check('危険度の照合'), check('検査', 'SUCCESS', 'COMPLETED', NEWER, 'A'), check('検査', 'FAILURE', 'COMPLETED', OLDER, 'B')]
    assertAsked(MERGE, /成功していない.*検査/, mergeDeps({ checks }))
  })

  test('重なった検査の時刻が読めなければ、確認を求める', () => {
    const checks = [check('危険度の照合'), check('E2E', 'FAILURE', 'COMPLETED', null), check('E2E', 'SUCCESS', 'COMPLETED', NEWER)]
    assertAsked(MERGE, /時刻/, mergeDeps({ checks }))
  })

  test('欠けた条件が複数あれば、全てを書く', () => {
    const result = judgeCommand(MERGE, CWD, mergeDeps({ baseRefName: 'main', closing: [] }))
    assert.equal(result?.decision, 'ask')
    assert.match(result.reason, /向き先/)
    assert.match(result.reason, /Closes/)
  })

  test('gh の失敗、時間切れ、読めない JSON では、確認を求める', () => {
    assertAsked(MERGE, /読めない.*Could not resolve/s, fakeDeps({ gh: { status: 1, stdout: '', stderr: 'GraphQL: Could not resolve to a PullRequest' } }))
    assertAsked(MERGE, /時間切れ/, fakeDeps({ gh: { status: null, stdout: '', stderr: '', error: Object.assign(new Error('spawnSync gh ETIMEDOUT'), { code: 'ETIMEDOUT' }) } }))
    assertAsked(MERGE, /読めない/, fakeDeps({ gh: { status: 0, stdout: '{', stderr: '' } }))
    assertAsked(MERGE, /読めない/, fakeDeps({ gh: { status: 0, stdout: '{"data":{"repository":{"pullRequest":null}}}', stderr: '' } }))
    assertAsked(MERGE, /読めない/, fakeDeps({ gh: () => { throw new Error('gh がない') } }))
  })

  test('検査・ファイル・閉じる issue・ラベルの一覧が途中までしか読めなければ、確認を求める', () => {
    const data = pullRequest()
    data.data.repository.pullRequest.commits.nodes[0].commit.statusCheckRollup.contexts.pageInfo.hasNextPage = true
    assertAsked(MERGE, /一覧/, fakeDeps({ gh: { status: 0, stdout: JSON.stringify(data), stderr: '' } }))
  })

  test('gh には時間切れを渡す', () => {
    const deps = mergeDeps()
    judgeCommand(MERGE, CWD, deps)
    const { timeout } = deps.ghCalls[0]
    assert.ok(Number.isInteger(timeout) && timeout > 0 && timeout <= 30000, `時間切れ：${timeout}`)
  })

  test('Dependabot は、本文と Closes の代わりに、変更のファイルと題を危険度の判定に渡して低なら通す', () => {
    const dependabot = { author: DEPENDABOT, body: 'Bumps the gradle-minor-patch group.', closing: [] }
    const low = { ...dependabot, title: 'deps: bump the gradle-minor-patch group in /backend/ecsite with 4 updates', files: ['backend/ecsite/gradle/libs.versions.toml'] }
    assertPassed(MERGE, mergeDeps(low))
    assertAsked(MERGE, /危険度が高.*major/, mergeDeps({ ...low, title: 'deps: bump org.springframework.boot from 3.5.0 to 4.0.0 in /backend/ecsite' }))
    assertAsked(MERGE, /危険度が高.*CI とリリース/, mergeDeps({ ...low, files: ['.github/workflows/ci.yml'] }))
    assertAsked(MERGE, /終わっていない/, mergeDeps({ ...low, checks: [check('バックエンド', null, 'IN_PROGRESS')] }))
  })

  test('作成者の login が dependabot でも、bot でなければ Dependabot として扱わない', () => {
    assertAsked(MERGE, /本文の危険度/, mergeDeps({ author: { __typename: 'User', login: 'dependabot' }, body: '', closing: [] }))
  })

  test('対象のリポジトリは、URL、-R・--repo、前に付けた GH_REPO から決め、なければ対象のディレクトリで gh に決めさせる', () => {
    const cases = [
      [`gh pr merge https://github.com/roger88106/ec-site/pull/246 --match-head-commit ${HEAD_SHA}`, ['owner=roger88106', 'name=ec-site', 'number=246'], CWD],
      [`gh pr merge 246 -R other/repo --match-head-commit ${HEAD_SHA}`, ['owner=other', 'name=repo', 'number=246'], CWD],
      [`gh pr merge 246 --repo=github.com/other/repo --match-head-commit ${HEAD_SHA}`, ['owner=other', 'name=repo', 'number=246'], CWD],
      [`GH_REPO=other/repo gh pr merge 246 --match-head-commit ${HEAD_SHA}`, ['owner=other', 'name=repo', 'number=246'], CWD],
      [`cd sub && gh pr merge 246 --match-head-commit ${HEAD_SHA}`, ['owner={owner}', 'name={repo}', 'number=246'], path.join(CWD, 'sub')],
    ]
    for (const [command, expected, cwd] of cases) {
      const deps = mergeDeps()
      assertPassed(command, deps)
      assert.equal(deps.ghCalls.length, 1, command)
      for (const arg of expected) assert.ok(deps.ghCalls[0].args.includes(arg), `${command}：${deps.ghCalls[0].args.join(' ')}`)
      assert.equal(deps.ghCalls[0].cwd, cwd, command)
    }
  })

  test('github.com でない URL は、確認を求める', () => {
    assertAsked(`gh pr merge https://example.com/o/r/pull/246 --match-head-commit ${HEAD_SHA}`, /番号か URL/, mergeDeps())
  })

  test('つないだコマンドでは、拒否を確認より先に返す', () => {
    assertDenied(`${MERGE} --auto && git push --force`, /force push/, mergeDeps())
  })

  test('読むだけの gh と、gh api graphql は通す', () => {
    for (const command of [
      'gh pr view 246',
      'gh pr checks 246',
      'gh api repos/roger88106/ec-site/pulls/246',
      'gh api repos/roger88106/ec-site/pulls/246/merge',
      `gh api graphql -f query='mutation { mergePullRequest(input: {pullRequestId: "x"}) { clientMutationId } }'`,
      'echo gh pr merge 246',
    ]) {
      assertPassed(command)
    }
  })
})

describe('tag の作成と push で確認を求める', () => {
  test('tag の作成、tag の push、gh release create で確認を求める', () => {
    for (const command of [
      'git tag v1.0.0',
      'git tag -a v1.0.0 -m リリース',
      'git tag -s v1.0.0 -m リリース',
      'git tag -m リリース v1.0.0',
      'git tag -f v1.0.0 HEAD',
      'git push --tags',
      'git push origin --tags',
      'git push --follow-tags origin x',
      'git push origin refs/tags/v1.0.0',
      'git push origin HEAD:refs/tags/v1.0.0',
      'git push origin tag v1.0.0',
      'git push origin v1.0.0',
      'gh release create v1.0.0',
      '"/c/Program Files/GitHub CLI/gh.exe" release create v1.0.0 --notes x',
    ]) {
      assertAsked(command, /tag/, fakeDeps({ tags: ['v1.0.0'] }))
    }
  })

  test('tag の一覧と削除、tag でない名前の push、読むだけの gh release は通す', () => {
    for (const command of [
      'git tag',
      'git tag -l',
      'git tag --list "v*"',
      'git tag -l v1.*',
      'git tag -n5',
      'git tag --contains HEAD',
      'git tag -d v1.0.0',
      'git push origin 246-enforce-hooks',
      'git push -u origin HEAD',
      'gh release list',
      'gh release view v1.0.0',
    ]) {
      assertPassed(command, fakeDeps({ tags: ['v1.0.0'] }))
    }
  })

  test('push の名前が tag かは、対象のディレクトリで git show-ref で調べる', () => {
    const deps = fakeDeps({ tags: ['v1.0.0'] })
    assertAsked('git -C sub push origin v1.0.0', /tag/, deps)
    const showRef = deps.calls.find((call) => call.args[0] === 'show-ref')
    assert.ok(showRef?.args.includes('refs/tags/v1.0.0'))
    assert.equal(showRef.cwd, path.join(CWD, 'sub'))
  })
})

describe('ruleset とブランチの保護の変更で確認を求める', () => {
  test('GET 以外のメソッド（-f・-F・--input による POST を含む）で確認を求める', () => {
    for (const command of [
      'gh api -X PUT repos/roger88106/ec-site/rulesets/123',
      'gh api -XPATCH /repos/roger88106/ec-site/rulesets/123',
      'gh api --method DELETE repos/roger88106/ec-site/rulesets/123',
      'gh api --method=PUT repos/roger88106/ec-site/rulesets/123 --input ruleset.json',
      'gh api repos/roger88106/ec-site/rulesets -f name=x',
      'gh api repos/roger88106/ec-site/rulesets --field enforcement=disabled',
      'gh api repos/roger88106/ec-site/rulesets --raw-field name=x',
      'gh api repos/roger88106/ec-site/rulesets --input ruleset.json',
      'gh api orgs/roger88106/rulesets -F x=1',
      'gh api -X PUT repos/roger88106/ec-site/branches/main/protection',
      'gh api -X DELETE repos/roger88106/ec-site/branches/develop/protection/required_status_checks',
      '"/c/Program Files/GitHub CLI/gh.exe" api -X PUT repos/roger88106/ec-site/rulesets/123',
    ]) {
      assertAsked(command, /ruleset/)
    }
  })

  test('読むだけの gh api、ruleset でないパス、gh api graphql は通す', () => {
    for (const command of [
      'gh api repos/roger88106/ec-site/rulesets',
      'gh api -X GET repos/roger88106/ec-site/rulesets -f includes_parents=true',
      'gh api repos/roger88106/ec-site/branches/main/protection',
      'gh api -X POST repos/roger88106/ec-site/issues -f title=x',
      `gh api graphql -f query='mutation { updateRepositoryRuleset(input: {}) { clientMutationId } }'`,
    ]) {
      assertPassed(command)
    }
  })
})

describe('実際のリポジトリと作業ツリーで、pre-commit hook を調べる', () => {
  test('作業ツリーの中の commit は、共通の .git の hooks を調べる', () => {
    const root = realpathSync.native(mkdtempSync(path.join(tmpdir(), 'agent-guard-test-')))
    try {
      const env = withoutGitEnv()
      const main = path.join(root, 'main')
      const worktree = path.join(root, 'wt')
      mkdirSync(main)
      const git = (cwd, ...args) => {
        const result = spawnSync('git', args, { cwd, env, encoding: 'utf8' })
        assert.equal(result.status, 0, result.stderr)
        return result.stdout
      }
      git(main, 'init', '-q')
      git(main, '-c', 'user.name=t', '-c', 'user.email=t@example.com', 'commit', '-q', '--allow-empty', '-m', 'init')
      git(main, 'worktree', 'add', '-q', worktree, '-b', 'work')
      const deps = createDeps(env)
      const hook = path.join(main, '.git', 'hooks', 'pre-commit')

      // 作業ツリーでも --git-path は共通の .git の hooks を返す
      const resolved = path.resolve(worktree, git(worktree, 'rev-parse', '--git-path', 'hooks/pre-commit').trim())
      assert.equal(resolved.toLowerCase(), hook.toLowerCase())

      rmSync(hook, { force: true })
      assert.equal(judgeCommand('git commit -m x', worktree, deps)?.decision, 'deny')

      writeFileSync(hook, INSTALLED_HOOK)
      assert.equal(judgeCommand('git commit -m x', worktree, deps), null)
      assert.equal(judgeCommand('cd wt && git commit -m x', root, deps), null)
      assert.equal(judgeCommand('git -C wt commit -m x', root, deps), null)
      assert.equal(judgeCommand('git commit -m x', main, deps), null)

      // GIT_DIR で、hook のない別のリポジトリに commit する形は拒否する
      const other = path.join(root, 'other')
      mkdirSync(other)
      git(other, 'init', '-q')
      assert.equal(judgeCommand(`GIT_DIR=${other.replaceAll('\\', '/')}/.git git commit -m x`, main, deps)?.decision, 'deny')

      // リポジトリでないディレクトリでは、対象を決められないので拒否する
      assert.equal(judgeCommand('git commit -m x', root, deps)?.decision, 'deny')
    } finally {
      rmSync(root, { recursive: true, force: true })
    }
  })
})

describe('.claude/settings.json の hook のコマンドで、script を探して呼ぶ', () => {
  /** リポジトリ（作業ツリー）の根 */
  const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')
  /** settings.json に書いた PreToolUse の hook のコマンド */
  const hookCommand = JSON.parse(readFileSync(path.join(repoRoot, '.claude/settings.json'), 'utf8')).hooks.PreToolUse[0].hooks[0].command

  /** hook のコマンドを bash で、`cwd` を作業ディレクトリにして実行する。CLAUDE_PROJECT_DIR は `projectDir` があるときだけ渡す */
  function runHook(command, cwd, projectDir) {
    const env = withoutGitEnv()
    delete env.CLAUDE_PROJECT_DIR
    if (projectDir !== undefined) env.CLAUDE_PROJECT_DIR = projectDir
    const input = JSON.stringify({ tool_name: 'Bash', tool_input: { command }, cwd, hook_event_name: 'PreToolUse' })
    return spawnSync('bash', ['-c', hookCommand], { cwd, env, input, encoding: 'utf8' })
  }

  /** Git の外の一時ディレクトリで `body` を実行する */
  function inTempDir(body) {
    const dir = realpathSync.native(mkdtempSync(path.join(tmpdir(), 'agent-guard-outside-')))
    try {
      body(dir)
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  }

  test('作業ツリーの中では、CLAUDE_PROJECT_DIR より先に、作業ツリーの script に標準入力を渡す', () => {
    // commit の前の検査は Git の外の写しでテストを動かすので、一時リポジトリに、標準入力を返すだけの script を置く
    inTempDir((dir) => {
      const git = spawnSync('git', ['init', '-q'], { cwd: dir, env: withoutGitEnv(), encoding: 'utf8' })
      assert.equal(git.status, 0, git.stderr)
      const hooks = path.join(dir, 'scripts', 'dev', 'hooks')
      mkdirSync(hooks, { recursive: true })
      writeFileSync(path.join(hooks, 'agent-guard.mjs'), "import { readFileSync } from 'node:fs'\nprocess.stdout.write('stub:' + readFileSync(0, 'utf8'))\n")
      const result = runHook('git push --force', path.join(dir, 'scripts'), repoRoot)
      assert.equal(result.status, 0, result.stderr)
      assert.match(result.stdout, /^stub:\{.*git push --force/)
    })
  })

  test('script が見つからないとき、git・gh を含む入力は終了コード 2 で止め、理由を標準エラーに出す', () => {
    inTempDir((dir) => {
      const result = runHook('git push --force', dir)
      assert.equal(result.status, 2, result.stderr)
      assert.match(result.stderr, /agent-guard\.mjs/)
    })
  })

  test('script が見つからないとき、git・gh を含まない入力は通す', () => {
    inTempDir((dir) => {
      const result = runHook('ls', dir)
      assert.equal(result.status, 0, result.stderr)
      assert.equal(result.stdout, '')
    })
  })

  test('Git の外では、CLAUDE_PROJECT_DIR の script が判定する', () => {
    inTempDir((dir) => {
      const result = runHook('git push --force', dir, repoRoot)
      assert.equal(result.status, 0, result.stderr)
      assert.equal(JSON.parse(result.stdout).hookSpecificOutput.permissionDecision, 'deny')
    })
  })
})

describe('hook の入力と出力', () => {
  /** PreToolUse の hook の入力の JSON を作る */
  const input = (command, toolName = 'Bash') => JSON.stringify({ tool_name: toolName, tool_input: { command }, cwd: CWD, hook_event_name: 'PreToolUse' })

  test('拒否するときは、PreToolUse の deny と理由を JSON で返す', () => {
    const output = JSON.parse(handleInput(input('git push --force'), fakeDeps()))
    assert.equal(output.hookSpecificOutput.hookEventName, 'PreToolUse')
    assert.equal(output.hookSpecificOutput.permissionDecision, 'deny')
    assert.match(output.hookSpecificOutput.permissionDecisionReason, /force push/)
  })

  test('確認を求めるときは、PreToolUse の ask と理由を JSON で返す', () => {
    const output = JSON.parse(handleInput(input('git tag v1.0.0'), fakeDeps()))
    assert.equal(output.hookSpecificOutput.permissionDecision, 'ask')
    assert.match(output.hookSpecificOutput.permissionDecisionReason, /tag/)
  })

  test('先頭に BOM が付いた入力も読み、判定する（Windows PowerShell 5.1 はパイプで BOM を付ける）', () => {
    const output = JSON.parse(handleInput(`﻿${input('git push --force')}`, fakeDeps()) || '{}')
    assert.equal(output.hookSpecificOutput?.permissionDecision, 'deny')
  })

  test('通すとき、Bash と PowerShell 以外のツール、読めない入力では、何も返さない', () => {
    assert.equal(handleInput(input('git push'), fakeDeps()), '')
    assert.equal(handleInput(input('git push --force', 'Edit'), fakeDeps()), '')
    assert.equal(handleInput('{', fakeDeps()), '')
    assert.equal(handleInput(JSON.stringify({ tool_name: 'Bash', tool_input: {} }), fakeDeps()), '')
  })

  test('PowerShell のツールで、git・gh と操作の語を含むコマンドは、Bash で実行し直すよう返して拒否する', () => {
    const commands = [
      'git push --force',
      'git tag v1',
      'gh pr merge 1 --admin',
      '& "C:\\Program Files\\GitHub CLI\\gh.exe" api -X PUT repos/o/r/rulesets/1',
      'git -c core.hooksPath=x commit',
      'git commit -m x',
      'git.exe PUSH origin',
    ]
    for (const command of commands) {
      const output = JSON.parse(handleInput(input(command, 'PowerShell'), fakeDeps()) || '{}')
      assert.equal(output.hookSpecificOutput?.permissionDecision, 'deny', command)
      assert.match(output.hookSpecificOutput.permissionDecisionReason, /Bash/, command)
    }
  })

  test('PowerShell のツールで、操作の語のない git・gh と、git・gh を含まないコマンドは通す', () => {
    const commands = ['git status', 'git log --oneline -3', 'gh pr view 1', 'Get-ChildItem', 'Write-Output digit', 'Write-Output digit push']
    for (const command of commands) {
      assert.equal(handleInput(input(command, 'PowerShell'), fakeDeps()), '', command)
    }
  })

  test('判定の中の例外では、git を含むコマンドは拒否し、含まないコマンドは通す', () => {
    const broken = () => {
      throw new Error('壊れた判定')
    }
    const denied = JSON.parse(handleInput(input('git status'), fakeDeps(), broken))
    assert.equal(denied.hookSpecificOutput.permissionDecision, 'deny')
    assert.match(denied.hookSpecificOutput.permissionDecisionReason, /壊れた判定/)
    assert.equal(handleInput(input('ls -la'), fakeDeps(), broken), '')
  })

  test('引用符や $(…) が閉じていないコマンドは、git を含めば拒否し、含まなければ通す', () => {
    const denied = JSON.parse(handleInput(input("git commit -m \"$(cat <<'EOF'\nx\nEOF\n"), fakeDeps()))
    assert.equal(denied.hookSpecificOutput.permissionDecision, 'deny')
    assert.equal(handleInput(input('echo "abc'), fakeDeps()), '')
  })

  test('script を実行すると、標準入力を読み、終了コード 0 で判定を出す', () => {
    const script = path.join(path.dirname(fileURLToPath(import.meta.url)), 'agent-guard.mjs')
    const run = (command) => spawnSync(process.execPath, [script], { input: input(command), encoding: 'utf8', env: withoutGitEnv() })
    const denied = run('git push --force')
    assert.equal(denied.status, 0, denied.stderr)
    assert.equal(JSON.parse(denied.stdout).hookSpecificOutput.permissionDecision, 'deny')
    const passed = run('ls')
    assert.equal(passed.status, 0, passed.stderr)
    assert.equal(passed.stdout, '')
  })
})
