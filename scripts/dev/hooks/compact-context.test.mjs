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

  test('PreCompact では、動いているセッションの行に、セッションごとに書く項目の語が全て入る', () => {
    const result = run(JSON.stringify({ hook_event_name: 'PreCompact', trigger: 'auto' }))
    const lines = result.stdout.split('\n').filter((line) => line.startsWith('- ') && line.includes('動いているセッション'))
    assert.equal(lines.length, 1)
    for (const word of ['サブエージェント', '報告先のセッション', '識別子', '担当の issue', '作業ツリーのパス', 'ブランチ', 'なければ「なし」']) {
      assert.ok(lines[0].includes(word), `動いているセッションの行に「${word}」がない：${lines[0]}`)
    }
  })

  test('PreCompact では、担当の作業ブランチと作業ツリーのパス、引き継ぎの文書とメモの行を出す', () => {
    const result = run(JSON.stringify({ hook_event_name: 'PreCompact', trigger: 'auto' }))
    const lines = result.stdout.split('\n')
    const assigned = lines.find((line) => line.includes('担当の issue と spec の番号'))
    assert.ok(assigned, '担当の issue と spec の番号の行がない')
    assert.ok(assigned.includes('作業ブランチ'), '作業ブランチがない')
    assert.ok(assigned.includes('作業ツリーのパス'), '作業ツリーのパスがない')
    const handoff = lines.find((line) => line.includes('引き継ぎの文書') && line.includes('メモ'))
    assert.ok(handoff, '引き継ぎの文書とメモの行がない')
  })

  test('圧縮の後の SessionStart では、答える前に読み直す指示が、読むものの箇条より前にある', () => {
    const result = run(JSON.stringify({ hook_event_name: 'SessionStart', source: 'compact' }))
    const out = result.stdout
    const before = out.indexOf('答える前')
    const first = out.indexOf('最初に読むもの')
    const tasks = out.indexOf('作業中の tasks')
    const handoff = out.indexOf('引き継ぎの文書とメモ')
    assert.notEqual(before, -1, '「答える前」がない')
    assert.notEqual(first, -1, '「最初に読むもの」がない')
    assert.notEqual(tasks, -1, '「作業中の tasks」がない')
    assert.notEqual(handoff, -1, '「引き継ぎの文書とメモ」がない')
    assert.ok(before < first, '「答える前」が「最初に読むもの」より後にある')
    assert.ok(first < tasks && tasks < handoff, '箇条の順が違う')
    assert.ok(out.includes('他のセッションのメッセージ'), '「他のセッションのメッセージ」がない')
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
