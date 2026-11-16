import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findButtonNameChecks, findTimeoutLiterals, timeoutTargets } from './e2e.mjs'

describe('E2E の書き方の検査', () => {
  const lines = (hits) => hits.map((hit) => hit.line)

  test('ボタンを文字列の名前で探すとき、exact: true がない箇所を見つける（連鎖の途中、複数の行、変数、テンプレート文字列を含む）', () => {
    const text = [
      "await page.getByRole('button', { name: '保存' }).click()",
      "await row.getByRole('button', { name: status }).click()",
      'await page.getByRole("button", {',
      '  name: `出荷 ${id}`,',
      '}).click()',
      "await page.getByRole('button', { name: '保存', exact: false }).click()",
      "await page.getByRole('button', { name }).click()",
      "await page.getByRole('button', { exact: true, name: '保存' }).click()",
      "await page.getByRole('button', { name: '保存', exact: true }).click()",
      "await page.getByRole('button', { name: `出荷 ${id}`, exact: true }).click()",
      "await page.getByRole('button', { name: status, exact: true }).click()",
    ].join('\n')
    assert.deepEqual(lines(findButtonNameChecks(text)), [1, 2, 3, 6, 7])
  })

  test('ボタンを正規表現の名前で探すとき、先頭の ^ と末尾の $ がなければ見つける（フラグ、カンマや括弧を含む正規表現でも読む）', () => {
    const text = [
      "await page.getByRole('button', { name: /注文する/ }).click()",
      "await page.getByRole('button', { name: /^注文する/ }).click()",
      "await page.getByRole('button', { name: /注文する$/ }).click()",
      "await page.getByRole('button', { name: /^注文する（[\\d,]+ 円）$/ }).click()",
      "await page.getByRole('button', { name: /^保存$/i }).click()",
      "await page.getByRole('button', { name: /^a, b$/, exact: true }).click()",
      "await page.getByRole('button', { name: /a, b/, exact: true }).click()",
    ].join('\n')
    assert.deepEqual(lines(findButtonNameChecks(text)), [1, 2, 3, 7])
  })

  test('name のキーを引用符や [...] で書いても、name として読む', () => {
    const text = [
      "await page.getByRole('button', { 'name': '保存' }).click()",
      'await page.getByRole("button", { "name": 保存 }).click()',
      "await page.getByRole('button', { ['name']: '保存' }).click()",
      "await page.getByRole('button', { 'name': '保存', exact: true }).click()",
      "await page.getByRole('button', { 'name': /^保存$/ }).click()",
      "await page.getByRole('button', { ['name']: /保存/ }).click()",
    ].join('\n')
    assert.deepEqual(lines(findButtonNameChecks(text)), [1, 2, 3, 6])
  })

  test('正規表現の最上位に | があれば固定していないとみなす。グループ・文字クラス・エスケープの中の | は固定', () => {
    const text = [
      "await page.getByRole('button', { name: /^a|b$/ }).click()",
      "await page.getByRole('button', { name: /^(?:a|b)$/ }).click()",
      "await page.getByRole('button', { name: /^[a|b]$/ }).click()",
      "await page.getByRole('button', { name: /^a\\|b$/ }).click()",
      "await page.getByRole('button', { name: /^(a|b)c|d$/ }).click()",
    ].join('\n')
    assert.deepEqual(lines(findButtonNameChecks(text)), [1, 5])
  })

  test('末尾の $ がエスケープされていれば固定していないとみなす。\\ の後の $ は固定', () => {
    const text = [
      "await page.getByRole('button', { name: /^合計\\$/ }).click()",
      "await page.getByRole('button', { name: /^a\\\\$/ }).click()",
      "await page.getByRole('button', { name: /^a\\\\\\$/ }).click()",
      "await page.getByRole('button', { name: /^合計\\$$/ }).click()",
    ].join('\n')
    assert.deepEqual(lines(findButtonNameChecks(text)), [1, 3])
  })

  test('オプションに展開（...）を含み、name が読めないときは見つける。name が読めれば展開があっても判定する', () => {
    const text = [
      "await page.getByRole('button', { ...opts }).click()",
      "await page.getByRole('button', { ...opts, exact: true }).click()",
      "await page.getByRole('button', { ...opts, name: '保存', exact: true }).click()",
      "await page.getByRole('button', { name: '保存', ...opts }).click()",
      "await page.getByRole('button', { ...opts, name: /^保存$/ }).click()",
    ].join('\n')
    assert.deepEqual(lines(findButtonNameChecks(text)), [1, 2, 4])
  })

  test('ボタン以外の役割と、名前で探さない呼び出しと、コメントと文字列の中は見つけない', () => {
    const text = [
      "await page.getByRole('cell', { name: '2,200 円' }).click()",
      "await page.getByRole('button').click()",
      "await page.getByRole('button', { pressed: true }).click()",
      "// page.getByRole('button', { name: '保存' })",
      'const text = "page.getByRole(\'button\', { name: \'保存\' })"',
    ].join('\n')
    assert.deepEqual(findButtonNameChecks(text), [])
  })

  test('名前の中に括弧の文字列や別の呼び出しがあっても、閉じ括弧を取り違えない', () => {
    const text = [
      "await page.getByRole('button', { name: label('a)'), exact: true }).click()",
      "await page.getByRole('button', { name: label('a)') }).click()",
      "await page.getByRole('button', { name: '保存', exact: true }).filter({ has: page.getByRole('button', { name: '削除' }) })",
    ].join('\n')
    assert.deepEqual(lines(findButtonNameChecks(text)), [2, 3])
  })

  test('タイムアウトの数字を見つける（timeout の項目、setTimeout の引数、intervals の項目）', () => {
    const text = [
      'await expect(x).toBeVisible({ timeout: 10_000 })',
      'await expect(x).toBeVisible({ timeout: 5 * 1000 })',
      'test.setTimeout(60_000)',
      'test.setTimeout(test.info().timeout + 5000)',
      'setTimeout(() => done(), 100)',
      '}).toPass({ timeout: ASYNC_TIMEOUT, intervals: [500, 1_000] })',
      'await expect(x).toBeVisible({',
      '  timeout: ASYNC_TIMEOUT * 2,',
      '})',
      'await expect(x).toBeVisible({ timeout: 1000 }, { intervals: [CHECK_TIMEOUT] })',
    ].join('\n')
    assert.deepEqual(lines(findTimeoutLiterals(text)), [1, 2, 3, 4, 5, 6, 8, 10])
  })

  test('timeout を含む項目（引用符のキーを含む）と、setDefaultTimeout・setDefaultNavigationTimeout の引数も見つける', () => {
    const text = [
      'use: { actionTimeout: 10_000 }',
      'use: { navigationTimeout: 10000, expectTimeout: ASYNC_TIMEOUT }',
      "{ 'timeout': 1000 }",
      '{ "actionTimeout": 5 * 1000 }',
      'page.setDefaultTimeout(5000)',
      'page.setDefaultNavigationTimeout(30_000)',
      'page.setDefaultTimeout(ASYNC_TIMEOUT)',
      "{ 'timeout': ASYNC_TIMEOUT }",
      "{ message: 'x', 'intervals': [100] }",
    ].join('\n')
    assert.deepEqual(lines(findTimeoutLiterals(text)), [1, 2, 3, 4, 5, 6, 9])
  })

  test('timeout の後に語が続く項目（timeoutMs、navigationTimeoutMs など。大小文字を区別しない）も見つける', () => {
    const text = [
      'use: { timeoutMs: 10_000 }',
      'use: { navigationTimeoutMs: 5000 }',
      "{ 'timeoutMs': 1000 }",
      '{ TimeoutSeconds: 30 }',
      '{ timeoutMs: ASYNC_TIMEOUT }',
      'const value = ok ? timeoutMs : 5000',
    ].join('\n')
    assert.deepEqual(lines(findTimeoutLiterals(text)), [1, 2, 3, 4])
  })

  test('型の注釈つきのデフォルト引数と、三項演算子の timeout は項目として見つけない', () => {
    const text = [
      'function wait(timeout: number = 5000) {}',
      'const wait2 = (page: Page, timeout: number = 5_000) => timeout',
      'async function poll(options: { timeout?: number }, actionTimeout: number = 100) {}',
      'const value = ok ? timeout : 5000',
      'const value2 = ok ? actionTimeout : 5 * 1000',
      'await expect(x).toBeVisible({ timeout: ok ? ASYNC_TIMEOUT : 5000 })',
    ].join('\n')
    assert.deepEqual(lines(findTimeoutLiterals(text)), [6])
  })

  test('文字列の中の引用符つきのキーの形は見つけない', () => {
    const text = ['const note = "a \'timeout\': 5000"', "const other = 'x \"actionTimeout\": 1000'"].join('\n')
    assert.deepEqual(findTimeoutLiterals(text), [])
  })

  test('waits.ts の定数、名前だけの式、型の注釈、文字列とコメントの中の数字は見つけない', () => {
    const text = [
      'await expect(x).toBeVisible({ timeout: CHECK_TIMEOUT })',
      'test.setTimeout(test.info().timeout + PAYMENT_EXPIRY_TIMEOUT)',
      '}).toPass({ timeout, intervals })',
      '{ timeout: RESERVATION_MILLIS + ASYNC_TIMEOUT }',
      '{ message: `在庫が作られない`, timeout: ASYNC_TIMEOUT }',
      'options: { timeout?: number; intervals?: number[] }',
      "{ message: 'timeout: 5000 は書かない', timeout: MAIL_TIMEOUT }",
      '// { timeout: 5000 }',
      'const REPORT_TIMEOUT2 = ASYNC_TIMEOUT',
      '{ timeout: TIMEOUT2 }',
      'await expect(x).toHaveText("a", { timeout: CHECK_TIMEOUT }) // 1000',
      'await page.waitForTimeout(ms)',
    ].join('\n')
    assert.deepEqual(findTimeoutLiterals(text), [])
  })

  test('報告に行の番号と元の行の文字を含む', () => {
    assert.deepEqual(findTimeoutLiterals('x\n  page.click(a, { timeout: 5000 })  '), [{ line: 2, text: 'page.click(a, { timeout: 5000 })' }])
  })

  test('タイムアウトの数字の検査は、タイムアウトの定数を定義する waits.ts を対象にしない', () => {
    const files = ['e2e/src/waits.ts', 'e2e/src/store.ts', 'e2e/tests/order.spec.ts', 'e2e/playwright.config.ts', 'e2e/src/waits.test.ts']
    assert.deepEqual(timeoutTargets(files), ['e2e/src/store.ts', 'e2e/tests/order.spec.ts', 'e2e/playwright.config.ts', 'e2e/src/waits.test.ts'])
  })
})
