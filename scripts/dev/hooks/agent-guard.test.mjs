// agent-guard.mjs（Claude Code の PreToolUse の hook）のテスト（node --test scripts/dev/hooks/agent-guard.test.mjs）
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, realpathSync, rmSync, writeFileSync } from 'node:fs'
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
 * git の呼び出しとファイルの読み込みを差し替えた依存を作る。
 * rev-parse は `.git/hooks/pre-commit` を返し、ファイルは `files`（絶対パスをキー）から読む。
 */
function fakeDeps({ files = { [path.join(CWD, '.git/hooks/pre-commit')]: INSTALLED_HOOK }, revParse = { status: 0, stdout: '.git/hooks/pre-commit\n' } } = {}) {
  const calls = []
  return {
    calls,
    runGit: (args, cwd, env = {}) => {
      calls.push({ args, cwd, env })
      return revParse
    },
    readText: (file) => files[path.resolve(file)] ?? null,
  }
}

/** 判定の結果が拒否で、理由が `pattern` に一致することを確認する */
function assertDenied(command, pattern, deps = fakeDeps()) {
  const result = judgeCommand(command, CWD, deps)
  assert.equal(result?.decision, 'deny', `拒否されない：${command}`)
  assert.match(result.reason, pattern, `理由が違う：${command}`)
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
      'git push --follow-tags origin x',
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
    assert.deepEqual(deps.calls, [])
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

describe('hook の入力と出力', () => {
  /** PreToolUse の hook の入力の JSON を作る */
  const input = (command, toolName = 'Bash') => JSON.stringify({ tool_name: toolName, tool_input: { command }, cwd: CWD, hook_event_name: 'PreToolUse' })

  test('拒否するときは、PreToolUse の deny と理由を JSON で返す', () => {
    const output = JSON.parse(handleInput(input('git push --force'), fakeDeps()))
    assert.equal(output.hookSpecificOutput.hookEventName, 'PreToolUse')
    assert.equal(output.hookSpecificOutput.permissionDecision, 'deny')
    assert.match(output.hookSpecificOutput.permissionDecisionReason, /force push/)
  })

  test('通すとき、Bash 以外のツール、読めない入力では、何も返さない', () => {
    assert.equal(handleInput(input('git push'), fakeDeps()), '')
    assert.equal(handleInput(input('git push --force', 'Edit'), fakeDeps()), '')
    assert.equal(handleInput('{', fakeDeps()), '')
    assert.equal(handleInput(JSON.stringify({ tool_name: 'Bash', tool_input: {} }), fakeDeps()), '')
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
