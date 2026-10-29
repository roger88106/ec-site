// backslash-guard.mjs（Claude Code の PreToolUse の hook、Bash）のテスト（node --test scripts/dev/hooks/backslash-guard.test.mjs）
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'

/** テストする script */
const SCRIPT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), 'backslash-guard.mjs')

/** Bash のコマンドを hook の入力にして script を実行する */
function run(command) {
  const input = JSON.stringify({ hook_event_name: 'PreToolUse', tool_name: 'Bash', tool_input: { command } })
  return spawnSync(process.execPath, [SCRIPT], { input, encoding: 'utf8' })
}

/** 拒否（終了コード 2、標準エラー出力に理由）を確認する */
function assertDenied(command) {
  const result = run(command)
  assert.equal(result.status, 2, `拒否されない：${command}`)
  assert.match(result.stderr, /\\/, '理由がない')
}

/** 通す（終了コード 0、出力なし）ことを確認する */
function assertPassed(command) {
  const result = run(command)
  assert.equal(result.status, 0, `通らない：${command}（${result.stderr}）`)
  assert.equal(result.stderr, '')
}

describe('heredoc と node -e の中の \\ を拒否する', () => {
  test('heredoc の本文に \\ がある', () => {
    assertDenied("cat <<'EOF'\nconst re = /\\d+/\nEOF")
  })

  test('インデント付きの heredoc（<<-）の本文に \\ がある', () => {
    assertDenied("cat <<-EOF\n\techo a\\nb\n\tEOF")
  })

  test('node -e の単引用符の引数に \\ がある', () => {
    assertDenied("node -e 'console.log(\"a\\nb\")'")
  })

  test('node --eval の二重引用符の引数に \\ がある', () => {
    assertDenied('node --eval "console.log(1)\\n"')
  })
})

describe('他の箇所の \\ と、\\ のないコマンドは通す', () => {
  test('grep の \\| は通す', () => {
    assertPassed("grep 'a\\|b' file.txt")
  })

  test('行末の \\ による継続は通す', () => {
    assertPassed('echo a \\\n  b')
  })

  test('\\ のない heredoc と node -e は通す', () => {
    assertPassed("cat <<'EOF'\nhello\nEOF")
    assertPassed("node -e 'console.log(1)'")
  })

  test('heredoc の外に \\ があっても、本文になければ通す', () => {
    assertPassed("grep 'a\\|b' f && cat <<'EOF'\nhello\nEOF")
  })
})

describe('入力が読めないときは通す', () => {
  test('JSON でない', () => {
    const result = spawnSync(process.execPath, [SCRIPT], { input: 'これは JSON でない', encoding: 'utf8' })
    assert.equal(result.status, 0)
  })

  test('コマンドがない', () => {
    const result = spawnSync(process.execPath, [SCRIPT], { input: '{}', encoding: 'utf8' })
    assert.equal(result.status, 0)
  })
})
