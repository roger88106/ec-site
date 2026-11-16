import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'

describe('検査の実行', () => {
  test('両方の画面のファイルの一覧は、検査の実行で 1 回だけ求める（定義のほかに呼び出しが 1 つ）', () => {
    const source = readFileSync(fileURLToPath(new URL('./frontend-rules.mjs', import.meta.url)), 'utf8')
    const code = source.replace(/^\s*\/\/.*$/gm, '').replace(/\/\*[\s\S]*?\*\//g, '')
    const calls = [...code.matchAll(/\bduplicatedFiles\(/g)].length - [...code.matchAll(/function duplicatedFiles\(/g)].length
    assert.equal(calls, 1)
  })
})
