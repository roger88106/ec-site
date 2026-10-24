import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { findLiterals, isTarget } from './input-constraints.mjs'

describe('入力の制約の数字の検査', () => {
  test('長さと範囲の属性に、数字を直接書いた行を見つける', () => {
    const text = [
      '<Input maxLength={50} />',
      '<Input min="0" />',
      '<Input max={ -1 } />',
      '<Input minLength={1} />',
    ].join('\n')
    assert.deepEqual(
      findLiterals(text).map((hit) => hit.line),
      [1, 2, 3, 4],
    )
  })

  test('定数を使った属性と、ほかの属性の数字は見つけない', () => {
    const text = [
      '<Input maxLength={schemas.CreateTaxCategoryRequest.name.maxLength} />',
      '<Input min={parameters.listProducts.minPrice.minimum} step={1} />',
      '<Textarea rows={2} />',
      'const minimum = 0',
    ].join('\n')
    assert.deepEqual(findLiterals(text), [])
  })

  test('対象は画面の本番のソースと、src/test/ のテストの補助（.tsx）。テストそのものと生成物は読まない', () => {
    assert.equal(isTarget('frontend/admin/src/features/a/Form.tsx'), true)
    assert.equal(isTarget('frontend/store/src/test/render.tsx'), true)
    assert.equal(isTarget('frontend/shared/src/test/helper.tsx'), true)
    assert.equal(isTarget('frontend/admin/src/test/render.ts'), false)
    assert.equal(isTarget('frontend/admin/src/test/render.test.tsx'), false)
    assert.equal(isTarget('frontend/admin/src/features/a/Form.test.tsx'), false)
    assert.equal(isTarget('frontend/admin/src/api/generated/x.tsx'), false)
    assert.equal(isTarget('frontend/admin/src/components/ui/button.tsx'), false)
  })
})

/** Git の環境変数を外した環境（テストの中の git が、元のリポジトリを壊さないため） */
function cleanEnv() {
  return Object.fromEntries(Object.entries(process.env).filter(([key]) => !key.startsWith('GIT_')))
}

/** 画面のソースを 1 つ置いた一時の Git リポジトリで、検査を子プロセスとして実行する */
function runCheck(source, args = []) {
  const dir = mkdtempSync(path.join(tmpdir(), 'input-constraints-'))
  try {
    const env = cleanEnv()
    spawnSync('git', ['init', '-q'], { cwd: dir, env })
    const file = path.join(dir, 'frontend/store/src/Sample.tsx')
    mkdirSync(path.dirname(file), { recursive: true })
    writeFileSync(file, source)
    const script = fileURLToPath(new URL('./input-constraints.mjs', import.meta.url))
    return spawnSync(process.execPath, [script, ...args], { cwd: dir, env, encoding: 'utf8' })
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
}

describe('入力の制約の検査の実行（コマンドライン）', () => {
  test('違反があれば、違反の行と最後の要約の行を標準出力に出し、終了コード 1 にする', () => {
    const result = runCheck('<Input maxLength={50} />\n')
    assert.equal(result.status, 1)
    assert.equal(result.stderr, '')
    const lines = result.stdout.trim().split('\n')
    assert.equal(lines[0], 'frontend/store/src/Sample.tsx:1  <Input maxLength={50} />')
    assert.equal(lines.at(-1), '入力の制約に数字を直接書いた所：1')
  })

  test('違反がなければ、最後の要約の行だけを出して終了コード 0 にする', () => {
    const result = runCheck('<Input maxLength={MAX} />\n')
    assert.equal(result.status, 0)
    assert.equal(result.stdout.trim(), '入力の制約に数字を直接書いた所：0')
  })
})
