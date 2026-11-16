// git-bash.mjs（hook を実行するテストの bash を求める補助）のテスト（node --test scripts/dev/lib/git-bash.test.mjs）
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { describe, test } from 'node:test'
import { gitBash } from './git-bash.mjs'

describe('hook を実行するテストの bash を求める', () => {
  test('絶対パスの bash を返し、その bash でコマンドを実行できる', () => {
    const bash = gitBash()
    assert.ok(path.isAbsolute(bash), bash)
    const result = spawnSync(bash, ['-c', 'printf %s "$BASH_VERSION"'], { encoding: 'utf8' })
    assert.equal(result.status, 0, result.stderr)
    assert.notEqual(result.stdout, '')
  })

  test('Windows では、WSL でなく Git の bash（uname が MINGW・MSYS）を返す', { skip: process.platform !== 'win32' }, () => {
    const result = spawnSync(gitBash(), ['-c', 'uname -s'], { encoding: 'utf8' })
    assert.match(result.stdout, /^(MINGW|MSYS)/)
  })
})
