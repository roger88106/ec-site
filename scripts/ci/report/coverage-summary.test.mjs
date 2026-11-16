import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { lowest, mutationMarkdown, readTotals } from './coverage-summary.mjs'

/** 実際の mutations.xml から切り出した 7 件（検出 4、生き残り 3：Order 2、Product 1） */
const PIT = path.join(path.dirname(fileURLToPath(import.meta.url)), 'testdata', 'pit-mutations.xml')

/** JaCoCo の XML の小さな例。パッケージの counter は、最後の sourcefile の後に並ぶ */
const JACOCO = [
  '<report name="ecsite">',
  '<package name="com/ecsite/a">',
  '<class name="com/ecsite/a/A"><counter type="LINE" missed="9" covered="1"/></class>',
  '<sourcefile name="A.java"><counter type="LINE" missed="9" covered="1"/></sourcefile>',
  '<counter type="LINE" missed="9" covered="1"/><counter type="BRANCH" missed="1" covered="1"/>',
  '</package>',
  '<package name="com/ecsite/b">',
  '<sourcefile name="B.java"><counter type="LINE" missed="1" covered="9"/></sourcefile>',
  '<counter type="LINE" missed="1" covered="9"/>',
  '</package>',
  '<package name="com/ecsite/c">',
  '<sourcefile name="C.java"><counter type="LINE" missed="5" covered="5"/></sourcefile>',
  '<counter type="LINE" missed="5" covered="5"/>',
  '</package>',
  '<package name="com/ecsite/empty">',
  '</package>',
  '<counter type="LINE" missed="15" covered="15"/><counter type="BRANCH" missed="1" covered="1"/>',
  '</report>',
].join('\n')

/** Vitest の json-summary の小さな例。キーは絶対のパス */
const VITEST = JSON.stringify({
  total: { lines: { covered: 12, total: 20 }, branches: { covered: 1, total: 2 } },
  '/repo/frontend/store/src/a.ts': { lines: { covered: 0, total: 4 }, branches: { covered: 0, total: 0 } },
  '/repo/frontend/store/src/b.tsx': { lines: { covered: 6, total: 6 }, branches: { covered: 0, total: 0 } },
  '/repo/frontend/store/src/c.ts': { lines: { covered: 6, total: 10 }, branches: { covered: 1, total: 2 } },
  '/repo/frontend/store/src/types.ts': { lines: { covered: 0, total: 0 }, branches: { covered: 0, total: 0 } },
})

describe('カバレッジの報告', () => {
  test('合計を読む（JaCoCo、Vitest）', () => {
    assert.deepEqual(readTotals(JACOCO, 'xml').lines, { covered: 15, total: 30 })
    assert.deepEqual(readTotals(VITEST, 'json').branches, { covered: 1, total: 2 })
  })

  test('JaCoCo はパッケージを、行のカバレッジの低い順に並べ、行のないものを除く', () => {
    assert.deepEqual(lowest(JACOCO, 'xml', { limit: 2 }), [
      { name: 'com/ecsite/a', covered: 1, total: 10 },
      { name: 'com/ecsite/c', covered: 5, total: 10 },
    ])
  })

  test('Vitest はファイルを、root からのパスで低い順に並べ、行のないものを除く', () => {
    assert.deepEqual(lowest(VITEST, 'json', { root: '/repo/frontend/store' }), [
      { name: 'src/a.ts', covered: 0, total: 4 },
      { name: 'src/c.ts', covered: 6, total: 10 },
      { name: 'src/b.tsx', covered: 6, total: 6 },
    ])
  })
})

describe('mutation score の報告', () => {
  test('割合と、生き残った変異が多いクラスを、多い順に書く', () => {
    const markdown = mutationMarkdown('domain', readFileSync(PIT, 'utf8'))
    assert.match(markdown, /### mutation score（domain）/)
    assert.match(markdown, /57[.]1% [|] 4 [/] 7/)
    assert.match(markdown, /status ごとの数：KILLED 3、NO_COVERAGE 2、SURVIVED 1、TIMED_OUT 1/)
    const order = markdown.indexOf('domain.order.Order`')
    const product = markdown.indexOf('domain.product.Product`')
    assert.ok(order > 0 && order < product, markdown)
  })

  test('読めない PIT の XML は、失敗にする', () => {
    assert.throws(() => mutationMarkdown('domain', '<mutations>'), /途中で切れ/)
  })
})

describe('script として実行する', () => {
  const script = path.join(path.dirname(fileURLToPath(import.meta.url)), 'coverage-summary.mjs')

  /**
   * 一時のディレクトリに報告を置いて、script を実行する。
   *
   * @param {string[]} args 名前と報告のファイル（報告のファイルは一時のディレクトリからの相対パス）
   * @param {Record<string, string>} [files] 一時のディレクトリに置く報告
   * @param {Record<string, string>} [env] 加える環境変数
   * @returns {import('node:child_process').SpawnSyncReturns<string>} 実行の結果
   */
  function run(args, files = {}, env = {}) {
    const dir = mkdtempSync(path.join(tmpdir(), 'coverage-summary-'))
    try {
      for (const [file, text] of Object.entries(files)) writeFileSync(path.join(dir, file), text)
      const base = Object.fromEntries(Object.entries(process.env).filter(([name]) => name !== 'GITHUB_STEP_SUMMARY'))
      return spawnSync(process.execPath, [script, ...args], { cwd: dir, encoding: 'utf8', env: { ...base, ...env } })
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  }

  test('報告を読めれば、終了コード 0 で、カバレッジの表を標準出力に書く', () => {
    const result = run(['バックエンド', 'report.xml'], { 'report.xml': JACOCO })
    assert.equal(result.status, 0, result.stderr)
    assert.match(result.stdout, /### カバレッジ（バックエンド）/)
    assert.match(result.stdout, /\| 行 \| 50\.0% \| 15 \/ 30 \|/)
  })

  test('PIT の報告は、mutation score の表を書く', () => {
    const result = run(['domain', 'mutations.xml'], { 'mutations.xml': readFileSync(PIT, 'utf8') })
    assert.equal(result.status, 0, result.stderr)
    assert.match(result.stdout, /### mutation score（domain）/)
  })

  test('報告のファイルがなければ、そのことを書いて、失敗にせず終了コード 0 で終わる', () => {
    const result = run(['バックエンド', 'none.xml'])
    assert.equal(result.status, 0, result.stderr)
    assert.match(result.stdout, /報告がありません（none\.xml）/)
  })

  test('名前か報告のファイルを渡さなければ、使い方を出して終了コード 2 で終わる', () => {
    const result = run(['バックエンド'])
    assert.equal(result.status, 2)
    assert.match(result.stderr, /使い方：/)
  })

  test('途中で切れた PIT の XML は、理由を 1 行出して終了コード 2 で終わる', () => {
    const result = run(['domain', 'mutations.xml'], { 'mutations.xml': '<mutations partial="true">\n<mutation detected=\'true\'>' })
    assert.equal(result.status, 2, result.stdout)
    assert.match(result.stderr, /^報告を読めない：PIT の報告が途中で切れています/)
    assert.equal(result.stderr.trim().split('\n').length, 1, result.stderr)
  })

  test('壊れた json の報告も、終了コード 2 で終わる', () => {
    const result = run(['店舗画面', 'coverage-summary.json'], { 'coverage-summary.json': '{"total":' })
    assert.equal(result.status, 2, result.stdout)
    assert.match(result.stderr, /^報告を読めない：/)
  })
})
