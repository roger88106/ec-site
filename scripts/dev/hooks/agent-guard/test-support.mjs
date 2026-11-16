// agent-guard のテストが共有する補助（差し替えた依存と、判定の結果の確認）。テストのファイルではない
import assert from 'node:assert/strict'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { judgeCommand } from '../agent-guard.mjs'

/** pre-commit hook を置いたときの中身（開発環境の起動の置き方のとおり） */
export const INSTALLED_HOOK = '#!/bin/sh\n"$(git rev-parse --show-toplevel)/scripts/dev/hooks/pre-commit" || exit 1\n'

/** テストの起点のディレクトリ（OS の絶対パス） */
export const CWD = path.resolve(tmpdir(), 'agent-guard-cwd')

/**
 * git と gh の呼び出しと、ファイルの読み込みを差し替えた依存を作る。
 *
 * rev-parse は `.git/hooks/pre-commit` を返し、show-ref は `tags` にある tag だけを見つける（`tags` が null なら、タイムアウトの結果を返す）。ファイルは `files`（絶対パスをキー）から読む。
 * symbolic-ref は今のブランチ `branch`（null は detached）を、config --get-regexp は `config`（`キー 値` の行。null は失敗）を返す。
 * gh は `gh`（結果か、引数とオプションから結果を返す関数）を返す。既定は失敗。
 */
export function fakeDeps({
  files = { [path.join(CWD, '.git/hooks/pre-commit')]: INSTALLED_HOOK },
  revParse = { status: 0, stdout: '.git/hooks/pre-commit\n' },
  tags = [],
  branch = 'develop',
  config = [],
  gh = { status: 1, stdout: '', stderr: 'テストの gh は、この例では呼ばれない想定' },
} = {}) {
  const calls = []
  const ghCalls = []
  return {
    calls,
    ghCalls,
    runGit: (args, cwd, env = {}) => {
      calls.push({ args, cwd, env })
      if (args[0] === 'show-ref') {
        if (tags === null) return { status: null, stdout: '' }
        return { status: tags.some((tag) => args.includes(`refs/tags/${tag}`)) ? 0 : 1, stdout: '' }
      }
      if (args[0] === 'symbolic-ref') return branch === null ? { status: 1, stdout: '' } : { status: 0, stdout: `${branch}\n` }
      if (args[0] === 'config') {
        if (config === null) return { status: null, stdout: '' }
        return config.length === 0 ? { status: 1, stdout: '' } : { status: 0, stdout: `${config.join('\n')}\n` }
      }
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
export function assertDenied(command, pattern, deps = fakeDeps()) {
  const result = judgeCommand(command, CWD, deps)
  assert.equal(result?.decision, 'deny', `拒否されない：${command}`)
  assert.match(result.reason, pattern, `理由が違う：${command}`)
}

/** 判定の結果が確認を求めるもので、理由が `pattern` に一致することを確認する */
export function assertAsked(command, pattern, deps = fakeDeps()) {
  const result = judgeCommand(command, CWD, deps)
  assert.equal(result?.decision, 'ask', `確認を求めない：${command}（${JSON.stringify(result)}）`)
  assert.match(result.reason, pattern, `理由が違う：${command}（${result.reason}）`)
}

/** 判定の結果が通す（null）ことを確認する */
export function assertPassed(command, deps = fakeDeps()) {
  assert.equal(judgeCommand(command, CWD, deps), null, `通らない：${command}`)
}
