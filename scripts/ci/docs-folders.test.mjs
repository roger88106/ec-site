import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { describe, test } from 'node:test'
import { ALLOWED_DOCS_FOLDERS, findDocsFolderProblems } from './docs-folders.mjs'

describe('docs/ の下のフォルダー名の検査', () => {
  test('許可する 8 つの名前のフォルダーと constitution.md は通る', () => {
    const files = [
      'docs/constitution.md',
      'docs/01-概要/system.md',
      'docs/02-設計/apps/admin.md',
      'docs/03-機能仕様/glossary.md',
      'docs/04-決定記録/0001-x.md',
      'docs/05-開発規則/workflow.md',
      'docs/06-手順書/ci.md',
      'docs/07-記録/results.md',
      'docs/08-spec/279-docs-folders/spec.md',
    ]
    assert.deepEqual(findDocsFolderProblems(files), [])
    assert.equal(ALLOWED_DOCS_FOLDERS.length, 8)
  })

  test('繁体字の 05-開發規則 と 07-記錄 は失敗にする', () => {
    assert.deepEqual(findDocsFolderProblems(['docs/05-開發規則/x.md', 'docs/07-記錄/y.md']), ['docs/05-開發規則/x.md', 'docs/07-記錄/y.md'])
  })

  test('一覧にない名前のフォルダー（design/）は失敗にする', () => {
    assert.deepEqual(findDocsFolderProblems(['docs/design/overview.md']), ['docs/design/overview.md'])
  })

  test('constitution.md 以外の直下のファイルは失敗にする', () => {
    assert.deepEqual(findDocsFolderProblems(['docs/README.md', 'docs/constitution.md']), ['docs/README.md'])
  })

  test('docs/ の外のファイルは見ない', () => {
    assert.deepEqual(findDocsFolderProblems(['README.md', 'scripts/ci/x.mjs']), [])
  })

  test('許可する名前は全て、constitution.md の第 3 節に「<名前>/」の形で出る', () => {
    const constitution = readFileSync(new URL('../../docs/constitution.md', import.meta.url), 'utf8')
    const start = constitution.search(/^## 3\./m)
    const rest = constitution.slice(start + 1)
    const next = rest.search(/^## /m)
    const section = next === -1 ? rest : rest.slice(0, next)
    assert.notEqual(start, -1)
    const missing = ALLOWED_DOCS_FOLDERS.filter((name) => !section.includes(`${name}/`))
    assert.deepEqual(missing, [])
  })
})
