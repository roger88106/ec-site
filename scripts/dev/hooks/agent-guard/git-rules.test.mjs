// git の規則（force push、検査の省略、pre-commit hook、tag）のテスト
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, realpathSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { withoutGitEnv } from '../../../ci/lib/git/git-env.mjs'
import { createDeps, judgeCommand } from '../agent-guard.mjs'
import { CWD, INSTALLED_HOOK, assertAsked, assertDenied, assertPassed, fakeDeps } from './test-support.mjs'

describe('push 済みの履歴を書き換える push を拒否する', () => {
  test('force のオプションと + で始まる refspec を拒否し、代わりに新しい commit を push するよう返す', () => {
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

  test('通常の push、upstream の設定、dry-run（-n）、値を取るオプションの値、: だけの refspec は通す', () => {
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
      'git push origin :',
    ]) {
      assertPassed(command)
    }
  })

  test('作業ブランチ、緊急の修正、リリースのブランチの名前に `/` を含む push は通し、agent/ 以外への force は拒否する', () => {
    for (const command of ['git push -u origin agent/367-x', 'git push origin hotfix/1-x', 'git push origin release/1.2.0']) {
      assertPassed(command)
    }
    for (const command of [
      'git push -f origin hotfix/1-x',
      'git push origin +release/1.2.0',
      'git push -f origin develop',
      'git push --force-with-lease origin main',
      'git push -f origin agent/367-x develop',
      'git push -f origin agent/367-x:refs/heads/main',
      'git push -f origin HEAD:refs/tags/agent/v1',
      'git push -f origin refs/heads/*:refs/heads/*',
    ]) {
      assertDenied(command, /force push/)
    }
  })
  test('リモートのブランチの削除は通す（develop と main の削除は ruleset が禁止する）', () => {
    for (const command of [
      'git push origin --delete feature',
      'git push -d origin hotfix/1-x',
      'git push origin :release/1.2.0',
      'git push --delete origin agent/367-x',
    ]) {
      assertPassed(command)
    }
  })

  test('tag の削除（tag を指す名前と refs/tags/ の refspec）は確認を求める', () => {
    for (const command of [
      'git push origin --delete v1.0.0',
      'git push -d origin refs/tags/v2',
      'git push origin :v1.0.0',
      'git push origin :refs/tags/v2',
    ]) {
      assertAsked(command, /tag/, fakeDeps({ tags: ['v1.0.0'] }))
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

  test('前に付ける timeout・nice・stdbuf・xargs・env（オプションを含む）を介した push を拒否する', () => {
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

describe('push の先が全て agent/ で始まる force push は通す', () => {
  test('refspec の先が agent/ で始まれば、今のブランチによらず通す', () => {
    for (const command of [
      'git push -f origin agent/367-x',
      'git push origin +agent/367-x',
      'git push --force-with-lease origin agent/367-x',
      'git push --force-with-lease=agent/367-x:abc123 origin agent/367-x',
      'git push -f origin HEAD:agent/367-x',
      'git push -f origin develop:refs/heads/agent/367-x',
      'git push -f origin agent/1-a agent/2-b',
      'git push -f --repo=origin agent/367-x',
    ]) {
      assertPassed(command, fakeDeps({ branch: 'develop' }))
    }
  })

  test('refspec がなければ今のブランチ（HEAD の refspec を含む）で決め、agent/ で始まれば通し、それ以外と detached は拒否する', () => {
    for (const command of ['git push -f', 'git push --force-with-lease origin', 'git push -f origin HEAD']) {
      assertPassed(command, fakeDeps({ branch: 'agent/367-x' }))
      assertDenied(command, /force push/, fakeDeps({ branch: 'develop' }))
      assertDenied(command, /force push/, fakeDeps({ branch: 'hotfix/1-x' }))
      assertDenied(command, /force push/, fakeDeps({ branch: null }))
    }
  })

  test('上流が agent/ で始まらないブランチ、matching の push.default、remote の push の設定、設定を読めないときは拒否する', () => {
    const cases = [
      { branch: 'agent/367-x', config: ['branch.agent/367-x.merge refs/heads/develop'] },
      { branch: 'agent/367-x', config: ['push.default matching'] },
      { branch: 'agent/367-x', config: ['remote.origin.push refs/heads/*:refs/heads/*'] },
      { branch: 'agent/367-x', config: null },
    ]
    for (const options of cases) {
      assertDenied('git push -f', /force push/, fakeDeps(options))
      assertDenied('git push -f origin agent/367-x', /force push/, fakeDeps(options))
    }
    assertPassed('git push -f', fakeDeps({ branch: 'agent/367-x', config: ['push.default simple', 'branch.agent/367-x.merge refs/heads/agent/367-x'] }))
  })

  test('--mirror・--all・--branches の force は、今のブランチによらず拒否する', () => {
    for (const command of ['git push --mirror origin', 'git push -f --all origin', 'git push -f --branches origin']) {
      assertDenied(command, /force push/, fakeDeps({ branch: 'agent/367-x' }))
    }
  })
})

describe('commit の前の検査の省略を拒否する', () => {
  test('commit の --no-verify と -n（短いオプションの組み合わせを含む）を拒否し、問題を直すよう返す', () => {
    for (const command of [
      'git commit --no-verify -m x',
      'git commit --amend --no-verify',
      'git commit --no-verif -m x',
      'git commit -n -m x',
      'git commit -an -m x',
      'git commit -anm x',
    ]) {
      assertDenied(command, /--no-verify.*直して/s)
    }
  })

  test('push の --no-verify は通す（push の前の hook を置いていないので、省略する検査がない）', () => {
    assertPassed('git push --no-verify origin x')
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
    assert.match(result.reason, /docs\/06-手順書\/setup\.md/)
  })

  test('hook のファイルが scripts/dev/hooks/pre-commit を呼んでいなければ拒否する', () => {
    const deps = fakeDeps({ files: { [path.join(CWD, '.git/hooks/pre-commit')]: '#!/bin/sh\nexit 0\n' } })
    assertDenied('git commit -m x', /docs\/06-手順書\/setup\.md/, deps)
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

  test('GET 以外の gh api で、Release と tag の参照（releases、git/refs、git/tags）を作成・変更・削除する形は確認を求める', () => {
    for (const command of [
      'gh api repos/roger88106/ec-site/releases -f tag_name=v1.0.0',
      'gh api repos/{owner}/{repo}/releases --input release.json',
      'gh api -X PATCH repos/roger88106/ec-site/releases/123 -f tag_name=v2',
      'gh api -X DELETE /repos/roger88106/ec-site/releases/123',
      'gh api repos/{owner}/{repo}/git/refs -f ref=refs/tags/v1 -f sha=abc',
      'gh api repos/roger88106/ec-site/git/tags -f tag=v1 -f message=x -f object=abc -f type=commit',
      'gh api -X PATCH repos/roger88106/ec-site/git/refs/tags/v1 -f sha=abc',
      'gh api --method DELETE repos/roger88106/ec-site/git/refs/tags/v1',
    ]) {
      assertAsked(command, /tag/)
    }
  })

  test('Release と tag の参照を読むだけの gh api は通す', () => {
    for (const command of [
      'gh api repos/roger88106/ec-site/releases',
      'gh api repos/roger88106/ec-site/releases/latest',
      'gh api repos/roger88106/ec-site/git/refs/tags',
      'gh api -X GET repos/roger88106/ec-site/git/tags/abc',
    ]) {
      assertPassed(command)
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
