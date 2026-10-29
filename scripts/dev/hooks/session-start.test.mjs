// session-start.mjs（Claude Code の SessionStart の hook）のテスト（node --test scripts/dev/hooks/session-start.test.mjs）
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, realpathSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { afterEach, beforeEach, describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { withoutGitEnv } from '../../ci/lib/git-env.mjs'

/** テストする script */
const SCRIPT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), 'session-start.mjs')

/** environment.md の中身 */
const ENVIRONMENT = '返答は日本語で書く。\n'

/** 一時のディレクトリ（メインの作業ツリー `main` と、追加の作業ツリー `wt` を置く） */
let root
let main
let worktree

/** 一時リポジトリで git を実行し、標準出力を返す。失敗したら止める */
function git(cwd, ...args) {
  const result = spawnSync('git', args, { cwd, env: withoutGitEnv(), encoding: 'utf8' })
  assert.equal(result.status, 0, result.stderr)
  return result.stdout
}

/** hook の入力（標準入力）を渡して script を実行する */
function run(input) {
  return spawnSync(process.execPath, [SCRIPT], { input, encoding: 'utf8', env: withoutGitEnv() })
}

/** `cwd` を持つ hook の入力を作る */
function inputOf(cwd) {
  return JSON.stringify({ hook_event_name: 'SessionStart', cwd })
}

/** メインの作業ツリーに environment.md を置く */
function writeEnvironment() {
  mkdirSync(path.join(main, '.local', 'agent'), { recursive: true })
  writeFileSync(path.join(main, '.local', 'agent', 'environment.md'), ENVIRONMENT)
}

beforeEach(() => {
  root = realpathSync.native(mkdtempSync(path.join(tmpdir(), 'session-start-test-')))
  main = path.join(root, 'main')
  worktree = path.join(root, 'wt')
  mkdirSync(main)
  git(main, 'init', '-q')
  git(main, '-c', 'user.name=t', '-c', 'user.email=t@example.com', 'commit', '-q', '--allow-empty', '-m', 'init')
  git(main, 'worktree', 'add', '-q', worktree, '-b', 'work')
})

afterEach(() => {
  rmSync(root, { recursive: true, force: true })
})

describe('メインの作業ツリーの environment.md を文脈に入れる', () => {
  test('メインの作業ツリーの cwd から読む', () => {
    writeEnvironment()
    const result = run(inputOf(main))
    assert.equal(result.status, 0, result.stderr)
    assert.match(result.stdout, /environment\.md/)
    assert.ok(result.stdout.endsWith(ENVIRONMENT), result.stdout)
  })

  test('メインの作業ツリーの下のディレクトリの cwd からも読む', () => {
    writeEnvironment()
    const sub = path.join(main, 'sub')
    mkdirSync(sub)
    const result = run(inputOf(sub))
    assert.equal(result.status, 0, result.stderr)
    assert.ok(result.stdout.endsWith(ENVIRONMENT), result.stdout)
  })

  test('追加の作業ツリーの cwd からも、メインの作業ツリーのファイルを読む', () => {
    writeEnvironment()
    const result = run(inputOf(worktree))
    assert.equal(result.status, 0, result.stderr)
    assert.ok(result.stdout.endsWith(ENVIRONMENT), result.stdout)
  })

  test('CLAUDE_PROJECT_DIR を使わない', () => {
    writeEnvironment()
    const other = path.join(root, 'other')
    mkdirSync(path.join(other, '.local', 'agent'), { recursive: true })
    writeFileSync(path.join(other, '.local', 'agent', 'environment.md'), '別のファイル\n')
    const result = spawnSync(process.execPath, [SCRIPT], {
      input: inputOf(worktree),
      encoding: 'utf8',
      env: { ...withoutGitEnv(), CLAUDE_PROJECT_DIR: other },
    })
    assert.ok(result.stdout.endsWith(ENVIRONMENT), result.stdout)
  })
})

describe('読めないときは何も出さずに終了コード 0', () => {
  test('ファイルがない', () => {
    const result = run(inputOf(main))
    assert.equal(result.status, 0, result.stderr)
    assert.equal(result.stdout, '')
  })

  test('cwd が git のリポジトリでない', () => {
    writeEnvironment()
    const outside = realpathSync.native(mkdtempSync(path.join(tmpdir(), 'session-start-outside-')))
    try {
      const result = run(inputOf(outside))
      assert.equal(result.status, 0, result.stderr)
      assert.equal(result.stdout, '')
    } finally {
      rmSync(outside, { recursive: true, force: true })
    }
  })

  test('入力が JSON でない、または cwd がない', () => {
    writeEnvironment()
    for (const input of ['', 'これは JSON でない', '{}']) {
      const result = run(input)
      assert.equal(result.status, 0, result.stderr)
      assert.equal(result.stdout, '', input)
    }
  })
})
