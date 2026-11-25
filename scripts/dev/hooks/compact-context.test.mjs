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

  test('PreCompact では、作業中のセッションの行に、セッションごとに書く項目の語が全て入る', () => {
    const result = run(JSON.stringify({ hook_event_name: 'PreCompact', trigger: 'auto' }))
    const lines = result.stdout.split('\n').filter((line) => line.startsWith('- ') && line.includes('報告先のセッション'))
    assert.equal(lines.length, 1)
    const words = ['作業中のセッション', '返事や再開を待つ', 'サブエージェント', '報告先のセッション', 'なければ「なし」', '1 つごとに', '識別子', '担当の issue', '作業ツリーのパス', 'ブランチ', '実際の値', 'ない項目は「なし」']
    for (const word of words) {
      assert.ok(lines[0].includes(word), `作業中のセッションの行に「${word}」がない：${lines[0]}`)
    }
  })

  test('PreCompact では、担当の作業ブランチと作業ツリーのパス、引き継ぎの文書とメモの行を出す', () => {
    const result = run(JSON.stringify({ hook_event_name: 'PreCompact', trigger: 'auto' }))
    const lines = result.stdout.split('\n')
    const assigned = lines.find((line) => line.includes('担当の issue と spec の番号'))
    assert.ok(assigned, '担当の issue と spec の番号の行がない')
    assert.ok(assigned.includes('作業ブランチ'), '作業ブランチがない')
    assert.ok(assigned.includes('作業ツリーのパス'), '作業ツリーのパスがない')
    const handoff = lines.find((line) => line.includes('引き継ぎの文書'))
    assert.ok(handoff, '引き継ぎの文書の行がない')
    assert.ok(handoff.includes('状態を書いているメモのパス'), '引き継ぎの文書の行に「状態を書いているメモのパス」がない')
  })

  test('圧縮の後の SessionStart では、1 行目に答える前の指示があり、箇条に読むものが全て入る', () => {
    const result = run(JSON.stringify({ hook_event_name: 'SessionStart', source: 'compact' }))
    const [head, ...rest] = result.stdout.split('\n')
    for (const word of ['他のセッションのメッセージ', '答える前', '読み直す', 'ファイルを読む']) {
      assert.ok(head.includes(word), `1 行目に「${word}」がない：${head}`)
    }
    const items = rest.filter((line) => line.startsWith('- '))
    for (const word of ['最初に読むもの', '担当の issue', '引き継ぎの文書とメモ']) {
      assert.ok(items.some((line) => line.includes(word)), `箇条に「${word}」の行がない`)
    }
    const first = items.find((line) => line.includes('最初に読むもの'))
    assert.ok(first.includes('hook が圧縮の後に文脈に入れた文書は除く'), '「最初に読むもの」の行に「hook が圧縮の後に文脈に入れた文書は除く」がない')
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
