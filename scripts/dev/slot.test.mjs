// slot.mjs（入口）のテスト（node --test scripts/dev/slot.test.mjs）。docker と git の状態を変えない形だけを試す
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'

/** テストする script */
const SCRIPT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), 'slot.mjs')

test('知らないコマンドは、使い方を示して終了コード 2（使い方の誤り）で終わる', () => {
  const result = spawnSync(process.execPath, [SCRIPT, 'no-such-command'], { encoding: 'utf8' })
  assert.equal(result.status, 2, result.stderr)
  assert.match(result.stderr, /使い方：node scripts\/dev\/slot\.mjs <コマンド>/)
})

test('コマンドを省くと、使い方を示して終了コード 0 で終わる', () => {
  const result = spawnSync(process.execPath, [SCRIPT], { encoding: 'utf8' })
  assert.equal(result.status, 0, result.stderr)
  assert.match(result.stderr, /使い方：/)
})
