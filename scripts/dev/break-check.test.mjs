// break-check.mjs（コードをわざと壊してテストが失敗するかを確認する script）のテスト（node --test scripts/dev/break-check.test.mjs）
// 一時ディレクトリの中だけで行い、Gradle や pnpm は使わない
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { chmodSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, before, describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'

const script = path.join(path.dirname(fileURLToPath(import.meta.url)), 'break-check.mjs')
const original = 'const ok = true\n'

let dir
let target

before(() => {
  dir = mkdtempSync(path.join(tmpdir(), 'break-check-'))
  target = path.join(dir, 'target.txt')
  // 対象のファイルに "ok = true" がなければ失敗する、`./` で始まるシェルのコマンド
  const check = path.join(dir, 'check.sh')
  writeFileSync(check, '#!/bin/sh\ngrep -q "ok = true" target.txt || { echo "FAILED check"; exit 1; }\n')
  chmodSync(check, 0o755)
})

after(() => {
  rmSync(dir, { recursive: true, force: true })
})

function run(from, to, command) {
  writeFileSync(target, original)
  return spawnSync('node', [script, target, from, to, dir, command], { encoding: 'utf8' })
}

describe('壊しての確認', () => {
  test('置き換える文字列が当たらないと、終了コード 2 で止まり、ファイルは変わらない', () => {
    const result = run('ok = false', 'ok = true', './check.sh')
    assert.equal(result.status, 2, result.stderr)
    assert.match(result.stderr, /0 か所/)
    assert.equal(readFileSync(target, 'utf8'), original)
  })

  test('置き換える文字列が 2 か所に当たると、終了コード 2 で止まり、ファイルは変わらない', () => {
    writeFileSync(target, 'ok\nok\n')
    const result = spawnSync('node', [script, target, 'ok', 'ng', dir, './check.sh'], { encoding: 'utf8' })
    assert.equal(result.status, 2, result.stderr)
    assert.match(result.stderr, /2 か所/)
    assert.equal(readFileSync(target, 'utf8'), 'ok\nok\n')
  })

  test('対象のファイルがないと、終了コード 2 で止まり、理由を標準エラー出力に出す', () => {
    const missing = path.join(dir, 'missing.txt')
    const result = spawnSync('node', [script, missing, 'ok', 'ng', dir, './check.sh'], { encoding: 'utf8' })
    assert.equal(result.status, 2, result.stdout + result.stderr)
    assert.match(result.stderr, /読めない/)
  })

  test('コマンドが見つからないときは、テストの失敗と区別して終了コード 2 で止まり、ファイルが元に戻る', () => {
    const result = run('ok = true', 'ok = false', './no-such-command.sh')
    assert.equal(result.status, 2, result.stdout + result.stderr)
    assert.match(result.stderr, /127/)
    assert.match(result.stderr, /見つからない/)
    assert.doesNotMatch(result.stdout, /壊すと検出できる/)
    assert.equal(readFileSync(target, 'utf8'), original)
  })

  test('コマンドを実行できないとき（終了コード 126）は、終了コード 2 で止まり、ファイルが元に戻る', () => {
    const result = run('ok = true', 'ok = false', 'exit 126')
    assert.equal(result.status, 2, result.stdout + result.stderr)
    assert.match(result.stderr, /126/)
    assert.match(result.stderr, /実行できない/)
    assert.doesNotMatch(result.stdout, /壊すと検出できる/)
    assert.equal(readFileSync(target, 'utf8'), original)
  })

  test('壊したときにコマンドが失敗すれば、終了コード 0 になり、ファイルが元に戻る', () => {
    const result = run('ok = true', 'ok = false', './check.sh')
    assert.equal(result.status, 0, result.stdout + result.stderr)
    assert.match(result.stdout, /FAILED check/)
    assert.match(result.stdout, /失敗した/)
    assert.equal(readFileSync(target, 'utf8'), original)
  })

  test('壊してもコマンドが通ると、終了コード 1 になり、ファイルが元に戻る', () => {
    const result = run('const', 'let', './check.sh')
    assert.equal(result.status, 1, result.stdout + result.stderr)
    assert.match(result.stdout, /通った/)
    assert.equal(readFileSync(target, 'utf8'), original)
  })

  test('`./` で始まるシェルのコマンドを実行できる', () => {
    const result = run('ok = true', 'ok = false', './check.sh')
    assert.doesNotMatch(result.stdout, /not found|No such file|command not found/)
    assert.match(result.stdout, /FAILED check/)
  })
})
