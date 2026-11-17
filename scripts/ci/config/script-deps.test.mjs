import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { areaOf, findDependencyProblems, readSpecifiers } from './script-deps.mjs'

/** ファイルの中身の表から、読む関数を作る */
const reader = (contents) => (file) => contents[file]

describe('置き場所の区分', () => {
  test('scripts の 3 つのフォルダと e2e を区分にし、他は null', () => {
    assert.equal(areaOf('scripts/ci/config/risk.mjs'), 'ci')
    assert.equal(areaOf('scripts/dev/slot.mjs'), 'dev')
    assert.equal(areaOf('scripts/shared/lock.mjs'), 'shared')
    assert.equal(areaOf('e2e/scripts/run.ts'), 'e2e')
    assert.equal(areaOf('frontend/store/src/a.ts'), null)
  })
})

describe('指定の読み取り', () => {
  test('from・import()・import の文・require()・new URL() の相対パスを行番号つきで読み、コメントの中とパッケージ名は読まない', () => {
    const text = [
      "import { a } from './a.mjs'",
      "export { b } from '../b.mjs'",
      "import './c.mjs'",
      "const d = await import('./d.mjs')",
      "const e = require('../e.cjs')",
      "const f = new URL('../f.json', import.meta.url)",
      "// import { g } from './g.mjs'",
      "import { h } from 'node:fs'",
    ].join('\n')
    assert.deepEqual(readSpecifiers(text), [
      { specifier: './a.mjs', line: 1 },
      { specifier: '../b.mjs', line: 2 },
      { specifier: './c.mjs', line: 3 },
      { specifier: './d.mjs', line: 4 },
      { specifier: '../e.cjs', line: 5 },
      { specifier: '../f.json', line: 6 },
    ])
  })
})

describe('依存の向き', () => {
  test('ci と dev の間、shared から ci と dev、e2e から ci と dev への指定を違反にする', () => {
    const files = {
      'scripts/ci/a.mjs': "import { x } from '../dev/x.mjs'",
      'scripts/dev/b.mjs': "import { y } from '../ci/y.mjs'",
      'scripts/shared/c.mjs': "import { z } from '../ci/z.mjs'\nconst w = new URL('../dev/w.json', import.meta.url)",
      'e2e/scripts/d.ts': "import { v } from '../../scripts/dev/v.mjs'",
    }
    assert.deepEqual(findDependencyProblems(Object.keys(files), reader(files)), [
      { file: 'scripts/ci/a.mjs', line: 1, target: 'scripts/dev/x.mjs' },
      { file: 'scripts/dev/b.mjs', line: 1, target: 'scripts/ci/y.mjs' },
      { file: 'scripts/shared/c.mjs', line: 1, target: 'scripts/ci/z.mjs' },
      { file: 'scripts/shared/c.mjs', line: 2, target: 'scripts/dev/w.json' },
      { file: 'e2e/scripts/d.ts', line: 1, target: 'scripts/dev/v.mjs' },
    ])
  })

  test('同じフォルダの中と、shared への指定と、区分の外への指定は違反にしない', () => {
    const files = {
      'scripts/ci/a.mjs': "import { x } from './lib/x.mjs'\nimport { y } from '../shared/y.mjs'\nconst z = new URL('../../docs/z.md', import.meta.url)",
      'scripts/dev/b.mjs': "import { y } from '../shared/y.mjs'",
      'e2e/scripts/c.ts': "import { y } from '../../scripts/shared/y.mjs'",
      'scripts/shared/d.mjs': "import { e } from './e.mjs'",
    }
    assert.deepEqual(findDependencyProblems(Object.keys(files), reader(files)), [])
  })
})
