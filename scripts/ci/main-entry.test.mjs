import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { adviceOf, findMainJudgments, isMainTarget } from './main-entry.mjs'

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

  test('argv の 2 つ目の要素を取り出す別の書き方（空白、at、分割代入）を見つける', () => {
    for (const code of [
      'if (process.argv[ 1 ] === x) run()',
      'if (argv [1] === x) run()',
      'const entry = process.argv.at(1)',
      'const [, entry] = process.argv',
      'const [node, entry] = process.argv',
      'const [ , entry, ...rest] = process.argv',
    ]) {
      assert.equal(findMainJudgments(code).length, 1, code)
    }
  })

  test('argv の 3 つ目以降（引数）を取り出す書き方は、見つけない', () => {
    for (const code of ['const x = process.argv[2]', 'const [, , arg] = process.argv', 'const args = process.argv.slice(2)', 'const a = process.argv.at(2)', 'const [command, ...rest] = argv', 'const [name, file] = process.argv.slice(2)']) {
      assert.deepEqual(findMainJudgments(code), [], code)
    }
  })

  test('import.meta の filename・url の比較と、url の endsWith を見つける', () => {
    for (const code of [
      'if (import.meta.filename === process.argv0) run()',
      'if (file !== import.meta.filename) return',
      'if (import.meta.url.endsWith(name)) run()',
      'if (import.meta.url === href) run()',
      'if (href === import.meta.url) run()',
      'if (require.main === module) run()',
      'const isEntry = require.main',
    ]) {
      assert.equal(findMainJudgments(code).length, 1, code)
    }
  })

  test('文字列の中の書き方は、見つけない', () => {
    const text = ['const a = "require.main を使わない"', "const b = 'import.meta.main'", 'const c = `process.argv[1] と ${x} import.meta.url ===`'].join('\n')
    assert.deepEqual(findMainJudgments(text), [])
  })

  test('import.meta.url を、ファイルの位置を求めるために使うのは見つけない', () => {
    const text = ['const here = path.dirname(fileURLToPath(import.meta.url))', "const list = new URL('./a.json', import.meta.url)"].join('\n')
    assert.deepEqual(judgmentsOf(text), [])
  })

  test('コメントの中の書き方は見つけない', () => {
    const text = ['// process.argv[1] と比べない', '/* import.meta.main も使わない */', 'const x = 1'].join('\n')
    assert.deepEqual(judgmentsOf(text), [])
  })

  test('直し方は、.mjs では isMain(import.meta.url)、.cjs では .mjs にして isMain を使うと示す', () => {
    assert.match(adviceOf('scripts/ci/a.mjs'), /isMain\(import\.meta\.url\)/)
    const cjs = adviceOf('scripts/dev/a.cjs')
    assert.match(cjs, /\.mjs にして/)
    assert.match(cjs, /CommonJS/)
  })

  test('対象は、scripts と frontend/shared/scripts の下のテストでない .mjs と .cjs。共通の関数の定義は除く', () => {
    assert.equal(isMainTarget('scripts/ci/time-waits.mjs'), true)
    assert.equal(isMainTarget('scripts/dev/slot.mjs'), true)
    assert.equal(isMainTarget('scripts/ci/lib/git-files.mjs'), true)
    assert.equal(isMainTarget('scripts/ci/lib/is-main.mjs'), false)
    assert.equal(isMainTarget('scripts/ci/time-waits.test.mjs'), false)
    assert.equal(isMainTarget('scripts/ci/check-all.sh'), false)
    assert.equal(isMainTarget('frontend/shared/scripts/export-constraints.mjs'), true)
    assert.equal(isMainTarget('frontend/shared/scripts/export-constraints.test.mjs'), false)
    assert.equal(isMainTarget('frontend/shared/scripts/a.cjs'), true)
    assert.equal(isMainTarget('frontend/shared/src/a.mjs'), false)
    assert.equal(isMainTarget('e2e/scripts/a.mjs'), false)
    assert.equal(isMainTarget('backend/a.mjs'), false)
  })
})
