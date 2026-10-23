import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { isScreenSource, SCREEN_APPS } from './screen-sources.mjs'

describe('画面の本番のソースの定義', () => {
  test('店舗画面・管理画面・共有パッケージの src の .ts と .tsx を対象にする', () => {
    assert.deepEqual(SCREEN_APPS, ['admin', 'store', 'shared'])
    assert.equal(isScreenSource('frontend/admin/src/features/tax/pages/TaxListPage.tsx'), true)
    assert.equal(isScreenSource('frontend/store/src/lib/labels.ts'), true)
    assert.equal(isScreenSource('frontend/shared/src/lib/locale.ts'), true)
  })

  test('画面の外、src の外、.ts と .tsx 以外は対象にしない', () => {
    assert.equal(isScreenSource('frontend/other/src/a.ts'), false)
    assert.equal(isScreenSource('frontend/admin/scripts/a.ts'), false)
    assert.equal(isScreenSource('frontend/admin/src/messages/ja.json'), false)
    assert.equal(isScreenSource('e2e/tests/a.ts'), false)
  })

  test('テスト、テストの補助、生成物、shadcn/ui の部品、共有の ui は除く', () => {
    assert.equal(isScreenSource('frontend/admin/src/features/tax/pages/TaxListPage.test.tsx'), false)
    assert.equal(isScreenSource('frontend/store/src/lib/price.test.ts'), false)
    assert.equal(isScreenSource('frontend/store/src/test/render.tsx'), false)
    assert.equal(isScreenSource('frontend/admin/src/api/generated/constraints.ts'), false)
    assert.equal(isScreenSource('frontend/admin/src/components/ui/sidebar.tsx'), false)
    assert.equal(isScreenSource('frontend/shared/src/ui/button.tsx'), false)
  })
})
