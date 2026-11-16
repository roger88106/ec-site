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
  const supportTest = 'frontend/store/src/test/auth.test.ts'
  const others = ['frontend/store/package.json', 'frontend/store/src/styles.css', 'e2e/playwright.config.js', 'backend/ecsite/build.gradle']
  const all = [store, storeTest, admin, adminTest, shared, sharedTest, support, nestedSupport, e2e, ...others]

  test('本番のソースには、店舗画面・管理画面・共有のテストでないソースとテストの補助が入る', () => {
    assert.deepEqual(groupTargets(all).sources, [store, admin, shared, support, nestedSupport])
  })

  test('画面のテストには、店舗画面・管理画面・共有の単体・結合テストだけが入る', () => {
    assert.deepEqual(groupTargets(all).frontendTests, [storeTest, adminTest, sharedTest])
  })

  test('テストの補助には、src の下の test/ ディレクトリ（src/test/ と機能の中の test/）の下のファイルだけが入る', () => {
    assert.deepEqual(groupTargets(all).testSupport, [support, nestedSupport])
  })

  test('E2E には、e2e/ の下の TypeScript のファイルだけが入る', () => {
    assert.deepEqual(groupTargets(all).e2e, [e2e])
  })

  test('テストの種類のファイルには、画面のテストと E2E が入り、テストの補助は入らない', () => {
    assert.deepEqual(groupTargets(all).tests, [storeTest, adminTest, sharedTest, e2e])
  })

  test('テストと補助のファイルには、画面のテストとテストの補助が入り、E2E は入らない', () => {
    assert.deepEqual(groupTargets(all).testsWithSupport, [storeTest, adminTest, sharedTest, support, nestedSupport])
  })

  test('テストと補助と E2E のファイルには、重なるファイル（src/test/ の下の .test.ts）が 1 回だけ入る', () => {
    const grouped = groupTargets([...all, supportTest])
    assert.deepEqual(grouped.testsWithSupportAndE2e, [storeTest, adminTest, sharedTest, support, nestedSupport, e2e, supportTest])
    assert.equal(grouped.frontendTests.includes(supportTest) && grouped.testSupport.includes(supportTest), true)
  })

  test('ファイルの順を保ち、どのまとまりにも入らないファイルは捨てる', () => {
    assert.deepEqual(groupTargets([e2e, storeTest, store]).tests, [e2e, storeTest])
    assert.deepEqual(groupTargets(others), { sources: [], frontendTests: [], testSupport: [], e2e: [], tests: [], testsWithSupport: [], testsWithSupportAndE2e: [] })
  })
})
