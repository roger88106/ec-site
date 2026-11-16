import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { applyExclusions, exclusionKey, findTimeWaits, isTestFile, scanTestFiles } from './time-waits.mjs'

describe('時間で待つテストの検査', () => {
  test('Thread.sleep と TimeUnit の sleep を見つける', () => {
    const text = [
      '        Thread.sleep(1_000);',
      '        TimeUnit.SECONDS.sleep(1);',
      '  await expect(page.getByText("受付")).toBeVisible()',
    ].join('\n')
    assert.deepEqual(
      findTimeWaits(text).map((hit) => `${hit.line} ${hit.call}`),
      ['1 Thread.sleep(', '2 TimeUnit.SECONDS.sleep('],
    )
  })

  test('決まった時間だけ見る・待つ Awaitility の書き方（during・pollDelay）を見つける', () => {
    const text = [
      '        await().during(Duration.ofMillis(500)).atMost(TIMEOUT).until(() -> count() == 1);',
      '        await().pollDelay(Duration.ofSeconds(1)).until(() -> true);',
    ].join('\n')
    assert.deepEqual(
      findTimeWaits(text).map((hit) => `${hit.line} ${hit.call}`),
      ['1 .during(', '2 .pollDelay('],
    )
  })

  test('条件で待つ呼び出しは見つけない', () => {
    assert.deepEqual(findTimeWaits('latch.await(30, TimeUnit.SECONDS); await().atMost(TIMEOUT).until(() -> done);'), [])
  })

  test('バックエンドのテストだけを見る（画面と E2E は Biome の plugin が見る）', () => {
    assert.equal(isTestFile('backend/ecsite/src/test/java/com/ecsite/FooTest.java'), true)
    assert.equal(isTestFile('backend/ecsite/src/integrationTest/java/com/ecsite/FooTest.java'), true)
    assert.equal(isTestFile('backend/ecsite/src/main/java/com/ecsite/Foo.java'), false)
    assert.equal(isTestFile('frontend/admin/src/features/order/pages/OrderListPage.test.tsx'), false)
    assert.equal(isTestFile('e2e/tests/payment.spec.ts'), false)
  })

  test('ファイルごとに、時間で待つ行を集める（見つからないファイルは含めない）', () => {
    const files = {
      'backend/ecsite/src/integrationTest/java/com/ecsite/FooTest.java': '        Thread.sleep(Duration.ofMillis(100));',
      'backend/ecsite/src/test/java/com/ecsite/BarTest.java': 'int a = 1;',
    }
    const hits = scanTestFiles(Object.keys(files), (file) => files[file])
    assert.deepEqual([...hits.keys()], ['backend/ecsite/src/integrationTest/java/com/ecsite/FooTest.java'])
    assert.equal(hits.get('backend/ecsite/src/integrationTest/java/com/ecsite/FooTest.java')[0].line, 1)
  })

  test('見つけた行に、前後の空白を除いた行の文字を添える', () => {
    const hits = findTimeWaits('        Thread.sleep(Duration.ofMillis(100));\n')
    assert.equal(hits[0].text, 'Thread.sleep(Duration.ofMillis(100));')
  })
})

describe('除外は行の文字で当てる', () => {
  const SLEEP = 'Thread.sleep(Duration.ofMillis(100));'
  const file = 'backend/ecsite/src/integrationTest/java/com/ecsite/FooTest.java'
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
    const text = [`    ${SLEEP}`, '    TimeUnit.SECONDS.sleep(1);'].join('\n')
    const { found } = applyExclusions(hitsOf(text), { [key]: '理由' })
    assert.deepEqual(found.map((hit) => hit.line), [2])
  })

  test('除外した行と同じ文字の行を後から足しても、除外は 1 行だけに適用される', () => {
    const { found } = applyExclusions(hitsOf([`    ${SLEEP}`, `    ${SLEEP}`].join('\n')), { [key]: '理由' })
    assert.deepEqual(found.map((hit) => hit.line), [2])
  })

  test('当たる行がない除外は使われない（不要の検出に使う）', () => {
    const { found, used } = applyExclusions(hitsOf('int a = 1;'), { [key]: '理由' })
    assert.deepEqual(found, [])
    assert.equal(used.size, 0)
  })

  test('行の位置が動いても除外は当たる', () => {
    const { found } = applyExclusions(hitsOf(`\n\n\n// 追加\n    ${SLEEP}`), { [key]: '理由' })
    assert.deepEqual(found, [])
  })
})
