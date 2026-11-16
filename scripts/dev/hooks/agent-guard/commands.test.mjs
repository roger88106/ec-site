// 語の分割（shell-words）と、前に付けたコマンドとディレクトリ（commands）のテスト
import assert from 'node:assert/strict'
import path from 'node:path'
import { describe, test } from 'node:test'
import { judgeCommand } from '../agent-guard.mjs'
import { parseCommands, resolveDir } from './commands.mjs'
import { parseGitInvocation } from './git-rules.mjs'
import { splitCommands } from './shell-words.mjs'
import { CWD, assertDenied, assertPassed, fakeDeps } from './test-support.mjs'

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

  test('<<- のヒアドキュメントは、行の先頭のタブを除いた終わりの語で閉じる', () => {
    assert.deepEqual(splitCommands("cat <<-EOF\n\tgit push -f\n\tEOF\necho done"), [['cat'], ['echo', 'done']])
  })

  test('<<< はヒアドキュメントとして読まず、次の行をコマンドとして読む', () => {
    assert.deepEqual(splitCommands('cat <<< x\ngit push -f'), [['cat'], ['git', 'push', '-f']])
  })

  test('$((…)) は、コマンドの置き換えとして読まない', () => {
    assert.deepEqual(splitCommands('echo "$((1+2))"'), [['echo', '$((1+2))']])
    assert.ok(!splitCommands('echo $((1+2))').flat().includes('$(…)'))
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

  test('引用符、$(…)、バッククォートが閉じていなければ、例外を投げる', () => {
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

  test('cd・pushd・git -C の先が変数かコマンドの置き換えを含むときは、ディレクトリを変えずに、入力の cwd のリポジトリで判定する', () => {
    for (const command of [
      'cd "$(git rev-parse --show-toplevel)" && git commit -m x',
      'cd "$D" && git commit -m x',
      'cd $D/sub && git commit -m x',
      'cd `pwd` && git commit -m x',
      'pushd "$D" && git commit -m x',
      'git -C "$W" commit -m x',
      'git -C "$(pwd)" commit -m x',
    ]) {
      const deps = fakeDeps()
      assert.equal(judgeCommand(command, CWD, deps), null, command)
      assert.deepEqual(deps.calls.map((call) => call.cwd), [CWD], command)
    }
  })

  test('popd は、対応する pushd の前のディレクトリに戻す', () => {
    const deps = fakeDeps()
    assert.equal(judgeCommand('pushd ../no-hook && ls; popd; git commit -m x', CWD, deps), null)
    assert.deepEqual(deps.calls.map((call) => call.cwd), [CWD])
    const dirs = parseCommands('pushd a; pushd b; popd; git status; popd; git log', CWD).map((c) => c.dir)
    assert.deepEqual(dirs, [CWD, path.join(CWD, 'a'), path.join(CWD, 'a', 'b'), path.join(CWD, 'a'), path.join(CWD, 'a'), CWD])
    // 先のない pushd は、今のディレクトリと積んだ最後のディレクトリを入れ替える
    const swapped = parseCommands('pushd a; pushd; git status; pushd; git log', CWD).map((c) => c.dir)
    assert.deepEqual(swapped, [CWD, path.join(CWD, 'a'), CWD, CWD, path.join(CWD, 'a')])
  })

  test('cd - と、引数のある popd は、移る先を追わずに今のディレクトリのままにする', () => {
    assert.deepEqual(parseCommands('cd sub; cd -; git status', CWD).map((c) => c.dir), [CWD, path.join(CWD, 'sub'), path.join(CWD, 'sub')])
    assert.deepEqual(parseCommands('pushd a; popd +1; git status', CWD).map((c) => c.dir), [CWD, path.join(CWD, 'a'), path.join(CWD, 'a')])
  })

  test('env -S の値は、語に分けてコマンドとして読む', () => {
    assert.deepEqual(parseCommands('env -S "git push -f" x', CWD).map((c) => c.words), [['git', 'push', '-f', 'x']])
    assertDenied('env -S "git push -f"', /force push/)
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

  test('git の前のオプション（-c、--git-dir など）を飛ばして、サブコマンドを読む', () => {
    const invocation = parseGitInvocation({ words: ['git', '-c', 'user.name=a', '--no-pager', '--git-dir', 'x/.git', 'commit', '-m', 'y'], dir: CWD })
    assert.equal(invocation.subcommand, 'commit')
    assert.deepEqual(invocation.configs, ['user.name=a'])
    assert.deepEqual(invocation.args, ['-m', 'y'])
  })
})

describe('シェルの予約語の後の git・gh も判定する', () => {
  test('for・if・while の本体、{ } の中、! を前に付けた git・gh を拒否する', () => {
    const cases = [
      ['for b in a b; do git push --force origin $b; done', /force push/],
      ['if true; then git commit -n -m x; fi', /--no-verify/],
      ['if false; then :; else git push -f; fi', /force push/],
      ['if false; then :; elif git push -f; then :; fi', /force push/],
      ['while git push -f; do :; done', /force push/],
      ['until git push -f; do :; done', /force push/],
      ['{ git push -f; }', /force push/],
      ['! gh pr merge 1', /--match-head-commit/],
      ['for n in 1 2; do gh pr merge $n --squash; done', /--match-head-commit/],
      ['function p { git push --force origin x; }; p', /force push/],
      ['function p() { git push --force origin x; }; p', /force push/],
    ]
    for (const [command, pattern] of cases) {
      assertDenied(command, pattern)
    }
  })

  test('予約語の後の、読むだけの git は通す', () => {
    assertPassed('for f in a; do git status; done')
    assertPassed('if git diff --quiet; then echo same; fi')
    assertPassed('function s { git status; }; s')
  })
})
