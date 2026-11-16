import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { E2E_FILE, FRONTEND_TEST, FRONTEND_TEST_SUPPORT, isScreenSource, SCREEN_APPS } from './screen-sources.mjs'

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

describe('画面のテスト・テストの補助・E2E のファイルの定義', () => {
  test('画面のテストは、画面のパッケージの src の .test.ts(x)', () => {
    assert.equal(FRONTEND_TEST.test('frontend/store/src/lib/price.test.ts'), true)
    assert.equal(FRONTEND_TEST.test('frontend/shared/src/a/b.test.tsx'), true)
    assert.equal(FRONTEND_TEST.test('frontend/store/src/lib/price.ts'), false)
    assert.equal(FRONTEND_TEST.test('frontend/other/src/a.test.ts'), false)
  })

  test('画面のテストの補助は、画面のパッケージの src の下の test/ の下の .ts(x)', () => {
    assert.equal(FRONTEND_TEST_SUPPORT.test('frontend/admin/src/test/render.tsx'), true)
    assert.equal(FRONTEND_TEST_SUPPORT.test('frontend/store/src/features/cart/test/fixtures.ts'), true)
    assert.equal(FRONTEND_TEST_SUPPORT.test('frontend/store/src/lib/latest.ts'), false)
    assert.equal(FRONTEND_TEST_SUPPORT.test('frontend/other/src/test/a.ts'), false)
  })

  test('E2E は、e2e/ の下の .ts(x)', () => {
    assert.equal(E2E_FILE.test('e2e/tests/order.spec.ts'), true)
    assert.equal(E2E_FILE.test('e2e/playwright.config.json'), false)
    assert.equal(E2E_FILE.test('frontend/e2e/a.ts'), false)
  })
})
