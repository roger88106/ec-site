import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findMainJudgments, isMainTarget } from './main-entry.mjs'

/** 見つけた判定を「行 書き方」で並べる */
const judgmentsOf = (text) => findMainJudgments(text).map((hit) => `${hit.line} ${hit.call}`)

describe('直接実行の判定の検査', () => {
  test('共通の関数を使う書き方は、見つけない', () => {
    const text = ["import { isMain } from './lib/is-main.mjs'", '', 'if (isMain(import.meta.url)) {', '  run()', '}'].join('\n')
    assert.deepEqual(judgmentsOf(text), [])
  })

  test('argv[1] を直接比べる判定を見つける', () => {
    const text = ['import { fileURLToPath } from "node:url"', 'if (process.argv[1] === fileURLToPath(import.meta.url)) run()'].join('\n')
    assert.deepEqual(judgmentsOf(text), ['2 argv[1]'])
  })

  test('import.meta.url との比較と、import.meta.main、require.main を見つける', () => {
    const text = ['if (import.meta.url === pathToFileURL(file).href) a()', 'if (import.meta.main) b()', 'if (require.main === module) c()'].join('\n')
    assert.deepEqual(judgmentsOf(text), ['1 import.meta.url ===', '2 import.meta.main', '3 require.main'])
  })

  test('import.meta.url を、ファイルの位置を求めるために使うのは見つけない', () => {
    const text = ['const here = path.dirname(fileURLToPath(import.meta.url))', "const list = new URL('./a.json', import.meta.url)"].join('\n')
    assert.deepEqual(judgmentsOf(text), [])
  })

  test('コメントの中の書き方は見つけない', () => {
    const text = ['// process.argv[1] と比べない', '/* import.meta.main も使わない */', 'const x = 1'].join('\n')
    assert.deepEqual(judgmentsOf(text), [])
  })

  test('対象は、scripts の下のテストでない .mjs と .cjs。共通の関数の定義は除く', () => {
    assert.equal(isMainTarget('scripts/ci/time-waits.mjs'), true)
    assert.equal(isMainTarget('scripts/dev/slot.mjs'), true)
    assert.equal(isMainTarget('scripts/ci/lib/git-files.mjs'), true)
    assert.equal(isMainTarget('scripts/ci/lib/is-main.mjs'), false)
    assert.equal(isMainTarget('scripts/ci/time-waits.test.mjs'), false)
    assert.equal(isMainTarget('scripts/ci/check-all.sh'), false)
    assert.equal(isMainTarget('backend/a.mjs'), false)
  })
})
