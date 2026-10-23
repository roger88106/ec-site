import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, before, describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { compare, percent2 } from './coverage-baseline.mjs'

const SCRIPT = path.join(path.dirname(fileURLToPath(import.meta.url)), 'coverage-baseline.mjs')

/** Vitest の json-summary の小さな例（行 80.00%、分岐 50.00%） */
const REPORT = JSON.stringify({
  total: { lines: { covered: 80, total: 100 }, branches: { covered: 1, total: 2 } },
})

/** 基準のファイルの内容を作る（行 80、分岐 50、許容の幅は引数） */
const baselineOf = (tolerance = 0, line = 80, branch = 50) =>
  JSON.stringify({
    targets: {
      store: { line: { value: line, tolerance }, branch: { value: branch, tolerance } },
    },
  })

let dir
before(() => {
  dir = mkdtempSync(path.join(tmpdir(), 'coverage-baseline-'))
  writeFileSync(path.join(dir, 'report.json'), REPORT)
})
after(() => rmSync(dir, { recursive: true, force: true }))

/** script を流す。基準のファイルは、渡されたときだけ dir の中に書く。summary は標準出力に出る */
function run(baseline, args = ['store', 'report.json']) {
  if (baseline !== undefined) writeFileSync(path.join(dir, 'baseline.json'), baseline)
  const env = { ...process.env }
  delete env.GITHUB_STEP_SUMMARY
  const result = spawnSync(process.execPath, [SCRIPT, ...args, '--baseline', 'baseline.json'], {
    cwd: dir,
    env,
    encoding: 'utf8',
  })
  return { status: result.status, out: `${result.stdout}${result.stderr}` }
}

describe('割合の丸め', () => {
  test('小数第 2 位にし、全体が 0 のときは 100 にする', () => {
    assert.equal(percent2(5876, 5991), 98.08)
    assert.equal(percent2(1, 3), 33.33)
    assert.equal(percent2(0, 0), 100)
  })
})

describe('基準との比較', () => {
  const entry = (tolerance) => ({ line: { value: 80, tolerance }, branch: { value: 50, tolerance } })
  const actual = (line, branch) => ({ line, branch })

  test('許容の幅の中なら成功、幅より下なら失敗にする', () => {
    assert.equal(compare(entry(1), actual(79, 50)).failures.length, 0)
    assert.equal(compare(entry(1), actual(78.99, 50)).failures.length, 1)
  })

  test('失敗の内容に、種類、基準、今回、差を持つ', () => {
    const [failure] = compare(entry(0), actual(79.5, 50)).failures
    assert.deepEqual(failure, { metric: 'line', baseline: 80, actual: 79.5, diff: -0.5 })
  })
})

describe('基準の検査（CLI）', () => {
  test('基準を下回ると失敗にして、対象・基準・今回・差・直し方を出す', () => {
    const { status, out } = run(baselineOf(0, 90, 50))
    assert.equal(status, 1)
    assert.match(out, /store/)
    assert.match(out, /90/)
    assert.match(out, /80/)
    assert.match(out, /-10/)
    assert.match(out, /--write/)
  })

  test('基準と同じなら成功にする', () => {
    const { status, out } = run(baselineOf())
    assert.equal(status, 0)
    assert.doesNotMatch(out, /警告：/)
  })

  test('基準を上回ると成功にして、警告を出す', () => {
    const { status, out } = run(baselineOf(0, 70, 50))
    assert.equal(status, 0)
    assert.match(out, /^警告：.*--write/m)
  })

  test('許容の幅より低いと失敗にし、幅の中なら成功にする', () => {
    assert.equal(run(baselineOf(5, 84, 50)).status, 0)
    assert.equal(run(baselineOf(5, 86, 50)).status, 1)
  })

  test('報告がないと失敗にする', () => {
    const { status, out } = run(baselineOf(), ['store', 'missing.json'])
    assert.equal(status, 1)
    assert.match(out, /missing\.json/)
  })

  test('基準のファイルに対象がないと失敗にする', () => {
    const { status, out } = run(baselineOf(), ['admin', 'report.json'])
    assert.equal(status, 1)
    assert.match(out, /admin/)
  })

  test('基準のファイルの値の形が違うと失敗にする', () => {
    const entryOf = (line) => JSON.stringify({ targets: { store: { line, branch: { value: 50, tolerance: 0 } } } })
    assert.equal(run(entryOf({ value: '80', tolerance: 0 })).status, 1)
    assert.equal(run(entryOf({ value: 80, tolerance: -1 })).status, 1)
    assert.equal(run(entryOf({ value: 80 })).status, 1)
    assert.equal(run('{ not json').status, 1)
  })

  test('引数が足りないと、使い方を出して終了コード 2 にする', () => {
    assert.equal(run(baselineOf(), ['store']).status, 2)
  })
})

describe('--write', () => {
  const written = () => JSON.parse(readFileSync(path.join(dir, 'baseline.json'), 'utf8')).targets

  test('報告の値で基準を書き換える（上げるとき）', () => {
    const { status } = run(baselineOf(0, 70, 40), ['store', 'report.json', '--write'])
    assert.equal(status, 0)
    assert.deepEqual(written().store, { line: { value: 80, tolerance: 0 }, branch: { value: 50, tolerance: 0 } })
  })

  test('報告の値で基準を書き換える（下げるとき）。許容の幅と、ほかの対象は変えない', () => {
    const base = JSON.stringify({
      targets: {
        store: { line: { value: 95, tolerance: 2 }, branch: { value: 90, tolerance: 0 } },
        admin: { line: { value: 70, tolerance: 0 }, branch: { value: 60, tolerance: 0 } },
      },
    })
    const { status } = run(base, ['store', 'report.json', '--write'])
    assert.equal(status, 0)
    assert.deepEqual(written().store, { line: { value: 80, tolerance: 2 }, branch: { value: 50, tolerance: 0 } })
    assert.deepEqual(written().admin, { line: { value: 70, tolerance: 0 }, branch: { value: 60, tolerance: 0 } })
  })

  test('書き換えた後は、同じ報告で成功し、警告も出ない', () => {
    run(baselineOf(0, 99, 99), ['store', 'report.json', '--write'])
    const { status, out } = run(undefined)
    assert.equal(status, 0)
    assert.doesNotMatch(out, /警告：/)
  })

  test('報告がないと、書き換えずに失敗にする', () => {
    const original = baselineOf(0, 99, 99)
    const { status } = run(original, ['store', 'missing.json', '--write'])
    assert.equal(status, 1)
    assert.equal(readFileSync(path.join(dir, 'baseline.json'), 'utf8'), original)
  })
})
