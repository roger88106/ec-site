import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { applyExclusions, exclusionKey, findTimeWaits, isTestFile, scanTestFiles } from './time-waits.mjs'

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

  test('決まった時間だけ見る・待つ書き方（Awaitility の during・pollDelay、Promise で包んだ setTimeout）を見つける', () => {
    const text = [
      '        await().during(Duration.ofMillis(500)).atMost(TIMEOUT).until(() -> count() == 1);',
      '        await().pollDelay(Duration.ofSeconds(1)).until(() -> true);',
      '    await new Promise((resolve) => setTimeout(resolve, 250))',
      '    await new Promise((r) =>',
      '      setTimeout(r, 250))',
    ].join('\n')
    assert.deepEqual(
      findTimeWaits(text).map((hit) => `${hit.line} ${hit.call}`),
      ['1 .during(', '2 .pollDelay(', '3 setTimeout(', '5 setTimeout('],
    )
  })

  test('時間で待たない setTimeout（タイマーの登録だけ）は見つけない', () => {
    assert.deepEqual(findTimeWaits('const timer = setTimeout(() => controller.abort(), 5_000)'), [])
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

  test('E2E の実行の script、設定のファイル、画面のテストの補助も見る', () => {
    assert.equal(isTestFile('e2e/scripts/run.ts'), true)
    assert.equal(isTestFile('e2e/playwright.config.ts'), true)
    assert.equal(isTestFile('e2e/playwright.showcase.config.ts'), true)
    assert.equal(isTestFile('frontend/admin/vite.config.ts'), true)
    assert.equal(isTestFile('frontend/store/vitest.config.ts'), true)
    assert.equal(isTestFile('frontend/shared/vitest.config.ts'), true)
    assert.equal(isTestFile('frontend/store/src/test/render.tsx'), true)
    assert.equal(isTestFile('frontend/admin/src/test/server.ts'), true)
    assert.equal(isTestFile('frontend/store/next.config.ts'), false)
  })

  test('ファイルごとに、時間で待つ行を集める（見つからないファイルは含めない）', () => {
    const files = {
      'e2e/scripts/run.ts': 'await new Promise((resolve) => setTimeout(resolve, 100))',
      'frontend/store/src/test/setup.ts': 'const a = 1',
    }
    const hits = scanTestFiles(Object.keys(files), (file) => files[file])
    assert.deepEqual([...hits.keys()], ['e2e/scripts/run.ts'])
    assert.equal(hits.get('e2e/scripts/run.ts')[0].line, 1)
  })

  test('見つけた行に、前後の空白を除いた行の文字を添える', () => {
    const hits = findTimeWaits('  sleep: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),\n')
    assert.equal(hits[0].text, 'sleep: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),')
  })
})

describe('除外は行の文字で当てる', () => {
  const SLEEP = 'sleep: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),'
  const file = 'e2e/scripts/run.ts'
  const key = `${file}:${SLEEP}`
  const hitsOf = (text) => new Map([[file, findTimeWaits(text)]])

  test('キーは <ファイル>:<行の文字>', () => {
    assert.equal(exclusionKey(file, `  ${SLEEP}  `), key)
  })

  test('除外した行は通り、使った除外は使われたものに数える', () => {
    const { found, used } = applyExclusions(hitsOf(`    ${SLEEP}`), { [key]: '理由' })
    assert.deepEqual(found, [])
    assert.deepEqual([...used], [key])
  })

  test('同じファイルに後から書いた別の待ちは失敗にする', () => {
    const text = [`    ${SLEEP}`, '  await new Promise((resolve) => setTimeout(resolve, 500))'].join('\n')
    const { found } = applyExclusions(hitsOf(text), { [key]: '理由' })
    assert.deepEqual(found.map((hit) => hit.line), [2])
  })

  test('除外した行と同じ文字の行を後から足しても、除外は 1 行だけに有効になる', () => {
    const { found } = applyExclusions(hitsOf([`    ${SLEEP}`, `    ${SLEEP}`].join('\n')), { [key]: '理由' })
    assert.deepEqual(found.map((hit) => hit.line), [2])
  })

  test('当たる行がない除外は使われない（不要の検出に使う）', () => {
    const { found, used } = applyExclusions(hitsOf('const a = 1'), { [key]: '理由' })
    assert.deepEqual(found, [])
    assert.equal(used.size, 0)
  })

  test('行の位置が動いても除外は当たる', () => {
    const { found } = applyExclusions(hitsOf(`\n\n\n// 追加\n    ${SLEEP}`), { [key]: '理由' })
    assert.deepEqual(found, [])
  })
})
