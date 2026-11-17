import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { groupTargets } from './files.mjs'

describe('検査の対象のファイルの振り分け', () => {
  const store = 'frontend/store/src/features/cart/components/CartLine.tsx'
  const storeTest = 'frontend/store/src/features/cart/components/CartLine.test.tsx'
  const admin = 'frontend/admin/src/features/order/pages/OrderListPage.tsx'
  const adminTest = 'frontend/admin/src/features/order/pages/OrderListPage.test.tsx'
  const shared = 'frontend/shared/src/lib/format.ts'
  const sharedTest = 'frontend/shared/src/lib/format.test.ts'
  const support = 'frontend/store/src/test/render.tsx'
  const nestedSupport = 'frontend/admin/src/features/order/test/factory.ts'
  const e2e = 'e2e/src/cart.spec.ts'
  const others = ['frontend/store/package.json', 'frontend/store/src/styles.css', 'e2e/playwright.config.js', 'backend/ecsite/build.gradle']
  const all = [store, storeTest, admin, adminTest, shared, sharedTest, support, nestedSupport, e2e, ...others]

  test('本番のソースには、店舗画面・管理画面・共有のテストでないソースとテストの補助が入る', () => {
    assert.deepEqual(groupTargets(all).sources, [store, admin, shared, support, nestedSupport])
  })

  test('画面のテストには、店舗画面・管理画面・共有の単体・結合テストだけが入り、E2E は入らない', () => {
    assert.deepEqual(groupTargets(all).frontendTests, [storeTest, adminTest, sharedTest])
  })

  test('ファイルの順を保ち、どのまとまりにも入らないファイルは捨てる', () => {
    assert.deepEqual(groupTargets([sharedTest, storeTest, store]).frontendTests, [sharedTest, storeTest])
    assert.deepEqual(groupTargets([e2e, ...others]), { sources: [], frontendTests: [] })
  })
})
