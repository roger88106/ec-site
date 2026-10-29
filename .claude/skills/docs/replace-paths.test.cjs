const assert = require('node:assert/strict')
const { describe, test } = require('node:test')
const { isExcluded, parseMap, replacePaths } = require('./replace-paths.cjs')

describe('リンクでないパスの置き換え', () => {
  test('適用済みのマイグレーションと seed は置き換えない', () => {
    assert.equal(isExcluded('backend/ecsite/src/main/resources/db/migration/V1__init.sql'), true)
    assert.equal(isExcluded('backend/ecsite/src/main/resources/db/seed/R__seed.sql'), true)
  })

  test('テストのデータ（*.test.*）は置き換えない', () => {
    assert.equal(isExcluded('scripts/ci/doc-refs.test.mjs'), true)
    assert.equal(isExcluded('frontend/store/src/lib/format.test.ts'), true)
    assert.equal(isExcluded('.claude/skills/docs/replace-paths.test.cjs'), true)
  })

  test('アーカイブした spec の本文は置き換えない', () => {
    assert.equal(isExcluded('docs/08-spec/archive/mvp/001-catalog-and-inventory/spec.md'), true)
  })

  test('それ以外のファイルは置き換えの対象にする', () => {
    assert.equal(isExcluded('docs/03-機能仕様/cart.md'), false)
    assert.equal(isExcluded('.claude/skills/review/SKILL.md'), false)
    assert.equal(isExcluded('scripts/ci/ac-check.mjs'), false)
    assert.equal(isExcluded('docs/08-spec/019-mvp-completion/plan.md'), false)
  })

  test('旧パスと、その下のパスを新パスに置き換える', () => {
    const map = parseMap(['docs/specs/001-catalog=docs/specs/archive/mvp/001-catalog'])
    const text = '`docs/specs/001-catalog/` と docs/specs/001-catalog/plan.md と docs/specs/001-catalog'
    assert.equal(
      replacePaths(text, map),
      '`docs/specs/archive/mvp/001-catalog/` と docs/specs/archive/mvp/001-catalog/plan.md と docs/specs/archive/mvp/001-catalog',
    )
  })

  test('旧パスで始まる別の名前（001-catalog-x）は置き換えない', () => {
    const map = parseMap(['docs/specs/001-catalog=docs/specs/archive/mvp/001-catalog'])
    assert.equal(replacePaths('docs/specs/001-catalog-x/spec.md', map), 'docs/specs/001-catalog-x/spec.md')
  })

  test('旧パスの前に別のパスが続くもの（x/docs/specs/...）は置き換えない', () => {
    const map = parseMap(['docs/specs/001-catalog=docs/specs/archive/mvp/001-catalog'])
    assert.equal(replacePaths('old/docs/specs/001-catalog', map), 'old/docs/specs/001-catalog')
  })

  test('置き換えた後のパスを、別の組で再度置き換えない', () => {
    const map = parseMap(['a/b=a/c', 'a/c=a/d'])
    assert.equal(replacePaths('a/b と a/c', map), 'a/c と a/d')
  })

  test('引数の形が 旧パス=新パス でなければ失敗する', () => {
    assert.throws(() => parseMap(['docs/specs/001-catalog']))
    assert.throws(() => parseMap([]))
  })
})
