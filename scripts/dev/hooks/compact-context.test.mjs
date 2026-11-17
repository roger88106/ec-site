// compact-context.mjs（Claude Code の PreCompact と SessionStart の hook）のテスト（node --test scripts/dev/hooks/compact-context.test.mjs）
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'

/** テストする script */
const SCRIPT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), 'compact-context.mjs')

/** hook の入力（標準入力）を渡して script を実行する */
function run(input) {
  return spawnSync(process.execPath, [SCRIPT], { input, encoding: 'utf8' })
}

describe('compact-context.mjs', () => {
  test('PreCompact では、要約に残すものを出す', () => {
    const result = run(JSON.stringify({ hook_event_name: 'PreCompact', trigger: 'auto' }))
    assert.equal(result.status, 0)
    assert.match(result.stdout, /^要約には、次を必ず残す。/)
    assert.match(result.stdout, /利用者の決定と、その場限りの特例/)
  })

  test('圧縮の後の SessionStart では、読み直すものを出す', () => {
    const result = run(JSON.stringify({ hook_event_name: 'SessionStart', source: 'compact' }))
    assert.equal(result.status, 0)
    assert.match(result.stdout, /^文脈が圧縮された。/)
    assert.match(result.stdout, /「最初に読むもの」/)
  })

  test('圧縮の後でない SessionStart では、何も出さない', () => {
    for (const source of ['startup', 'resume', 'clear']) {
      const result = run(JSON.stringify({ hook_event_name: 'SessionStart', source }))
      assert.equal(result.status, 0)
      assert.equal(result.stdout, '', source)
    }
  })

  test('手動の圧縮（trigger が manual）でも、要約に残すものを出す', () => {
    const result = run(JSON.stringify({ hook_event_name: 'PreCompact', trigger: 'manual', custom_instructions: null }))
    assert.equal(result.status, 0)
    assert.match(result.stdout, /^要約には、次を必ず残す。/)
  })

  test('source のない SessionStart と、対象外のイベントでは、何も出さない', () => {
    for (const input of [{ hook_event_name: 'SessionStart' }, { hook_event_name: 'Stop', source: 'compact' }, {}]) {
      const result = run(JSON.stringify(input))
      assert.equal(result.status, 0)
      assert.equal(result.stdout, '', JSON.stringify(input))
    }
  })

  test('入力が JSON でないときは、何も出さずに終了コード 0 で終わる', () => {
    const result = run('{')
    assert.equal(result.status, 0)
    assert.equal(result.stdout, '')
  })
})
