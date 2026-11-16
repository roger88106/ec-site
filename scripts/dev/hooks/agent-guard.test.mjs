// agent-guard.mjs の入口（hook の入力と出力）と、.claude/settings.json の hook の設定のテスト。
// 判定のテストは、agent-guard/ の下にファイルごとに置く
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, readFileSync, realpathSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { withoutGitEnv } from '../../ci/lib/git/git-env.mjs'
import { gitBash } from '../lib/git-bash.mjs'
import { GIT_TIMEOUT_MS, handleInput } from './agent-guard.mjs'
import { GH_BUDGET_MS } from './agent-guard/merge-rules.mjs'
import { CWD, fakeDeps } from './agent-guard/test-support.mjs'

/** リポジトリ（作業ツリー）の根 */
const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')

/** .claude/settings.json の中身 */
const SETTINGS = JSON.parse(readFileSync(path.join(REPO_ROOT, '.claude/settings.json'), 'utf8'))

/** settings.json の PreToolUse の hook の設定（matcher ごとの、hook の一覧） */
const PRE_TOOL_USE = SETTINGS.hooks.PreToolUse

/** settings.json の PreToolUse の全てのハンドラー */
const HANDLERS = PRE_TOOL_USE.flatMap((entry) => entry.hooks)

describe('.claude/settings.json の hook と permission の設定', () => {
  test('hook は、Bash と PowerShell のツールで、コマンドに git か gh を含むときだけ起動する（if 欄）', () => {
    const rules = PRE_TOOL_USE.flatMap((entry) => entry.hooks.map((hook) => `${entry.matcher}：${hook.if}`))
    assert.deepEqual(rules.sort(), ['Bash：Bash(*gh*)', 'Bash：Bash(*git*)', 'PowerShell：PowerShell(*gh*)', 'PowerShell：PowerShell(*git*)'])
  })

  test('どのハンドラーも、同じコマンドで script を呼ぶ', () => {
    assert.equal(new Set(HANDLERS.map((hook) => hook.command)).size, 1)
  })

  test('hook のタイムアウトは、script の中の gh の合計と git のタイムアウトを足した値より長い', () => {
    assert.ok(Number.isInteger(GH_BUDGET_MS) && Number.isInteger(GIT_TIMEOUT_MS), `gh：${GH_BUDGET_MS}、git：${GIT_TIMEOUT_MS}`)
    for (const hook of HANDLERS) {
      const timeout = hook.timeout * 1000
      assert.ok(Number.isFinite(timeout) && timeout > GH_BUDGET_MS + GIT_TIMEOUT_MS, `hook：${timeout}、gh と git：${GH_BUDGET_MS + GIT_TIMEOUT_MS}`)
    }
  })

  test('自動マージの有効化と、settings.json・settings.local.json の Edit で確認を求める', () => {
    const ask = SETTINGS.permissions.ask
    for (const rule of [
      'mcp__ccd_pr__set_auto_merge',
      'Edit(/.claude/settings.json)',
      'Edit(/.claude/settings.local.json)',
      'Edit(//**/.claude/settings.json)',
      'Edit(//**/.claude/settings.local.json)',
    ]) {
      assert.ok(ask.includes(rule), rule)
    }
  })
})

describe('.claude/settings.json の hook のコマンドで、script を探して呼ぶ', () => {
  /** リポジトリ（作業ツリー）の根 */
  const repoRoot = REPO_ROOT
  /** settings.json に書いた PreToolUse の hook のコマンド */
  const hookCommand = HANDLERS[0].command

  /**
   * hook のコマンドを Git の bash で、`cwd` を作業ディレクトリにして実行する（hook のコマンドは Git Bash で動く形で書くので、その bash で確認する）。
   *
   * CLAUDE_PROJECT_DIR は `projectDir` があるときだけ渡す。git・gh を含むかの絞り込みは Claude Code が if 欄で行うので、ここでは見ない
   */
  function runHook(command, cwd, projectDir) {
    const env = withoutGitEnv()
    delete env.CLAUDE_PROJECT_DIR
    if (projectDir !== undefined) env.CLAUDE_PROJECT_DIR = projectDir
    const input = JSON.stringify({ tool_name: 'Bash', tool_input: { command }, cwd, hook_event_name: 'PreToolUse' })
    return spawnSync(gitBash(env), ['-c', hookCommand], { cwd, env, input, encoding: 'utf8' })
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
    // commit の前の検査は Git の外のコピーでテストを実行するので、一時リポジトリに、標準入力を返すだけの script を置く
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

  test('script が見つからないとき、終了コード 2 で拒否し、理由を標準エラーに出力する', () => {
    inTempDir((dir) => {
      for (const command of ['git push --force', 'gh pr merge 1']) {
        const result = runHook(command, dir)
        assert.equal(result.status, 2, `${command}：${result.stderr}`)
        assert.match(result.stderr, /agent-guard\.mjs/, command)
      }
    })
  })

  test('script が判定の前に異常終了したとき、終了コード 2 で拒否する', () => {
    const scripts = {
      // 構文の誤りで、node が読み込みの時点で終了コード 1 で終わる
      構文の誤り: 'const = 1\n',
      // 途中まで出力してから異常終了する（途中の出力を判定として渡さない）
      出力の途中の異常終了: "process.stdout.write('{\"partial\":')\nprocess.exit(1)\n",
    }
    for (const [name, script] of Object.entries(scripts)) {
      inTempDir((dir) => {
        const git = spawnSync('git', ['init', '-q'], { cwd: dir, env: withoutGitEnv(), encoding: 'utf8' })
        assert.equal(git.status, 0, git.stderr)
        const hooks = path.join(dir, 'scripts', 'dev', 'hooks')
        mkdirSync(hooks, { recursive: true })
        writeFileSync(path.join(hooks, 'agent-guard.mjs'), script)
        for (const command of ['git push --force', 'gh pr merge 1']) {
          const stopped = runHook(command, dir)
          assert.equal(stopped.status, 2, `${name}、${command}：${stopped.stderr}`)
          assert.match(stopped.stderr, /agent-guard\.mjs/, `${name}、${command}`)
          assert.equal(stopped.stdout, '', `${name}、${command}`)
        }
      })
    }
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

  test('PowerShell のツールで、git・gh のサブコマンドが操作のコマンドは、Bash で実行するよう返して拒否する', () => {
    const commands = [
      'git push --force',
      'git tag v1',
      'gh pr merge 1 --admin',
      '& "C:\\Program Files\\GitHub CLI\\gh.exe" api -X PUT repos/o/r/rulesets/1',
      'git -c core.hooksPath=x commit',
      'git -c core.hooksPath=x status',
      'git commit -m x',
      'git.exe PUSH origin',
      'git -C C:\\work push',
      'Set-Location C:\\work; git push',
      'gh release create v1',
    ]
    for (const command of commands) {
      const output = JSON.parse(handleInput(input(command, 'PowerShell'), fakeDeps()) || '{}')
      assert.equal(output.hookSpecificOutput?.permissionDecision, 'deny', command)
      assert.match(output.hookSpecificOutput.permissionDecisionReason, /Bash/, command)
    }
  })

  test('PowerShell のツールで、操作の語のない git・gh と、git・gh を含まないコマンドは通す', () => {
    const commands = [
      'git status',
      'git log --oneline -3',
      'gh pr view 1',
      'Get-ChildItem',
      'Write-Output digit',
      'Write-Output digit push',
      'git diff -- api/openapi/admin.yaml',
      'git log --grep merge',
      'git log -- docs/release.md',
      'gh pr view 1 --json mergeable',
    ]
    for (const command of commands) {
      assert.equal(handleInput(input(command, 'PowerShell'), fakeDeps()), '', command)
    }
  })

  test('判定の中で例外が発生したときは、git を含むコマンドは拒否し、含まないコマンドは通す', () => {
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

  test('script を実行すると、標準入力を読み、終了コード 0 で判定を出力する', () => {
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
