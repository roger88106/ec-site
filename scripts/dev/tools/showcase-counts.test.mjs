// showcase-counts.mjs のテスト（node --test scripts/dev/tools/showcase-counts.test.mjs）
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'

/** テストする script */
const SCRIPT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), 'showcase-counts.mjs')

test('リポジトリの外から実行しても、リポジトリの spec・AC・テストの数を出力する', () => {
  const result = spawnSync(process.execPath, [SCRIPT], { cwd: tmpdir(), encoding: 'utf8' })
  assert.equal(result.status, 0, result.stderr)
  for (const label of ['spec', 'AC', 'テスト']) {
    const count = new RegExp(`^${label}：(\\d+)`, 'm').exec(result.stdout)
    assert.ok(count && Number(count[1]) > 0, `${label}：${result.stdout}`)
  }
})
