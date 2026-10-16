import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { lowest, readTotals } from './coverage-summary.mjs'

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
