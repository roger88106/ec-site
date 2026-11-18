import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { isExcluded } from './excluded-files.mjs'

describe('見ないファイルの判定', () => {
  test('進行中の spec は見て、アーカイブした spec は見ない', () => {
    assert.equal(isExcluded('docs/08-spec/019-mvp-completion/plan.md'), false)
    assert.equal(isExcluded('docs/08-spec/mvp.md'), false)
    assert.equal(isExcluded('docs/08-spec/archive/mvp/002-cart-and-order/spec.md'), true)
    assert.equal(isExcluded('docs/08-spec/archive/mvp/mvp.md'), false)
    assert.equal(isExcluded('docs/08-spec/archive/1to10/mvp.md'), true)
    assert.equal(isExcluded('backend/ecsite/src/main/resources/db/migration/V1__a.sql'), true)
  })

  test('切れた参照をわざと書く、この検査と ja-text のテストは見ない', () => {
    assert.equal(isExcluded('scripts/ci/docs/doc-refs.test.mjs'), true)
    assert.equal(isExcluded('scripts/ci/ja/ja-text.test.mjs'), true)
    assert.equal(isExcluded('scripts/ci/docs/doc-refs.mjs'), false)
  })
})
