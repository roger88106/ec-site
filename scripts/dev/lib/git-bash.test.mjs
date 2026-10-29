// git-bash.mjs（hook を実行するテストの bash を求める補助）のテスト（node --test scripts/dev/lib/git-bash.test.mjs）
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { describe, test } from 'node:test'
import { findRuleViolations, listScanScripts } from '../../ci/lib/scan-rules.mjs'
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

  test('scripts/ の下の script は、Git の bash を自分で求めず、この補助を使う（求める処理はこの補助だけに置く）', () => {
    const rules = [
      {
        guard: 'gitBash',
        // Git の bash は、git --exec-path の場所から求める
        forbidden: /--exec-path/,
        exceptions: {
          'dev/lib/git-bash.mjs': 'Git の bash を求める処理を定義する',
          'dev/lib/git-bash.test.mjs': '禁じる書き方を、この表に書く',
        },
      },
    ]
    assert.deepEqual(findRuleViolations(listScanScripts('all'), rules), [])
  })
})
