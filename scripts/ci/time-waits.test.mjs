import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findTimeWaits, isTestFile } from './time-waits.mjs'

describe('時間で待つテストの検査', () => {
  test('Thread.sleep、TimeUnit の sleep、waitForTimeout を見つける', () => {
    const text = [
      '        Thread.sleep(1_000);',
      '        TimeUnit.SECONDS.sleep(1);',
      "  await page.waitForTimeout(500)",
      '  await expect(page.getByText("受付")).toBeVisible()',
    ].join('\n')
    assert.deepEqual(
      findTimeWaits(text).map((hit) => `${hit.line} ${hit.call}`),
      ['1 Thread.sleep(', '2 TimeUnit.SECONDS.sleep(', '3 waitForTimeout('],
    )
  })

  test('条件で待つ呼び出しは見つけない', () => {
    assert.deepEqual(findTimeWaits('latch.await(30, TimeUnit.SECONDS); await expect(x).toPass({ timeout: 20_000 })'), [])
  })

  test('バックエンド・画面・E2E のテストだけを見る', () => {
    assert.equal(isTestFile('backend/ecsite/src/test/java/com/ecsite/FooTest.java'), true)
    assert.equal(isTestFile('backend/ecsite/src/integrationTest/java/com/ecsite/FooTest.java'), true)
    assert.equal(isTestFile('frontend/admin/src/features/order/pages/OrderListPage.test.tsx'), true)
    assert.equal(isTestFile('e2e/tests/payment.spec.ts'), true)
    assert.equal(isTestFile('e2e/src/store.ts'), true)
    assert.equal(isTestFile('backend/ecsite/src/main/java/com/ecsite/Foo.java'), false)
    assert.equal(isTestFile('frontend/store/src/lib/price.ts'), false)
  })
})
