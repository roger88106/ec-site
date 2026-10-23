// pre-commit.mjs のテスト（node --test scripts/dev/lib/pre-commit.test.mjs）
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { chmodSync, copyFileSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { CHECKOUT_FAILED, resolveIndexFile, runPreCommit } from './pre-commit.mjs'

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

/** 呼び出しを記録する外部の処理を作る */
function fakeDeps({ checkout = 0, check = 0, env = {} } = {}) {
  const calls = { checkout: [], runCheck: [], removed: [] }
  return {
    calls,
    deps: {
      root: '/repo',
      gitDir: '/repo/.git',
      env,
      checkout: (prefix, e) => (calls.checkout.push({ prefix, env: e }), checkout),
      runCheck: (script, e) => (calls.runCheck.push({ script, env: e }), check),
      makeTemp: () => '/tmp/staged',
      removeTemp: (dir) => calls.removed.push(dir),
    },
  }
}

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

describe('実際の hook', () => {
  const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')
  /** 実際のリポジトリと同じ置き場所に写す、hook とその hook が読む script */
  const hookFiles = ['scripts/dev/hooks/pre-commit', 'scripts/dev/lib/pre-commit.mjs', 'scripts/dev/lib/pre-commit-main.mjs']

  /** Git の環境変数を外した環境（hook の中でテストを実行しても、元のリポジトリを壊さないため） */
  function cleanEnv() {
    return Object.fromEntries(Object.entries(process.env).filter(([key]) => !key.startsWith('GIT_')))
  }

  /** 検査の script を差し替えた一時のリポジトリで、ファイルをステージして hook を実行する */
  function runHook(checkAll) {
    const dir = mkdtempSync(path.join(tmpdir(), 'pre-commit-test-'))
    try {
      const env = cleanEnv()
      const git = (...args) => spawnSync('git', args, { cwd: dir, env, encoding: 'utf8' })
      git('init', '-q')
      for (const file of hookFiles) {
        mkdirSync(path.dirname(path.join(dir, file)), { recursive: true })
        copyFileSync(path.join(repoRoot, file), path.join(dir, file))
      }
      mkdirSync(path.join(dir, 'scripts/ci'), { recursive: true })
      writeFileSync(path.join(dir, 'scripts/ci/check-all.sh'), checkAll)
      chmodSync(path.join(dir, 'scripts/ci/check-all.sh'), 0o755)
      writeFileSync(path.join(dir, 'staged.txt'), 'staged')
      git('add', '-A')
      writeFileSync(path.join(dir, 'unstaged.txt'), 'unstaged')
      const result = spawnSync('bash', ['scripts/dev/hooks/pre-commit'], { cwd: dir, env, encoding: 'utf8' })
      return { status: result.status, output: result.stdout }
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  }

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
})
