import { expect, type Page, test } from '@playwright/test'
import { ACCOUNTS, loginAdmin, memberEmail, registerMember } from '../src/auth'
import { createPublishedProduct, taxCategoryWithRate } from '../src/data'
import { orderCart, payOnMockPage } from '../src/store'
import { ADMIN_URL, STORE_URL } from '../src/urls'
import { CHECK_TIMEOUT, REPORT_RECHECK_INTERVALS, REPORT_TIMEOUT, reopenUntil } from '../src/waits'

/** 今日の日本時間の年と月 */
function thisMonthInJapan(): { year: number; month: number } {
  const [year, month] = new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Tokyo' }).split('-').map(Number)
  return { year, month }
}

/**
 * レポートの画面を開き直しながら、表示が期待どおりになるのを待つ（写しの遅れを待つ）。
 *
 * @param page 管理画面のページ
 * @param url 開く URL
 * @param check 表示を確かめる。満たさなければ投げる
 */
async function expectReport(page: Page, url: string, check: () => Promise<void>) {
  await reopenUntil(page, url, check, { timeout: REPORT_TIMEOUT, intervals: REPORT_RECHECK_INTERVALS })
}

/** 分け方の表で、区分の行 */
function segmentRow(page: Page, dimension: string, segment: string) {
  return page
    .getByRole('region', { name: `${dimension}ごとの売上額` })
    .getByRole('row')
    .filter({ has: page.getByRole('cell', { name: segment, exact: true }) })
}

test('AC-197、205、206、209、219、221、222、223、224 生年月日と性別を入れて登録した会員の注文が、管理職のレポートに数えられる', async ({
  browser,
}) => {
  const { id: taxCategoryId } = await taxCategoryWithRate(10)
  const productId = await createPublishedProduct({
    code: 'REPORT',
    name: 'レポートの確認の商品',
    taxCategoryId,
    priceExclTax: 1000,
    variants: [{ code: 'A', stock: 5 }],
  })
  const { year, month } = thisMonthInJapan()

  await test.step('店舗画面：生年月日（35 年前の 1 月 1 日。30 代）と性別（女性）を入れて会員登録し、2 個注文して支払う（AC-209、221、222）', async () => {
    const store = await browser.newPage()
    await store.goto(`${STORE_URL}/products/${productId}`)
    await registerMember(
      store,
      { email: memberEmail('report'), password: 'E2e-pass-1234' },
      { lastName: 'レポート', firstName: '会員' },
      { birthdate: `${year - 35}-01-01`, gender: '女性' },
    )
    await store.goto(`${STORE_URL}/products/${productId}`)
    await store.getByLabel('数量').selectOption('2')
    await store.getByRole('button', { name: 'カートに入れる' }).click()
    await expect(store.getByText('カートに入れました。')).toBeVisible()
    await orderCart(store)
    await payOnMockPage(store)
    await store.context().close()
  })

  const admin = await browser.newPage()
  await loginAdmin(admin, ACCOUNTS.manager)

  await test.step('管理職：売上の推移で、商品を選ぶと今月に 2 個・2,200 円が数えられる（AC-197、199、223、224）', async () => {
    await expectReport(admin, `${ADMIN_URL}/reports/sales-trend?productId=${productId}`, async () => {
      const row = admin.getByRole('row').filter({ has: admin.getByRole('cell', { name: `${year} 年 ${month} 月` }) })
      await expect(row.getByRole('cell', { name: '2,200 円' })).toBeVisible({ timeout: CHECK_TIMEOUT })
      await expect(row.getByRole('cell', { name: '2', exact: true })).toBeVisible({ timeout: CHECK_TIMEOUT })
    })
  })

  await test.step('管理職：地域・年齢層・性別で、商品を選ぶと東京都・30 代・女性に 1 件ずつ数えられる（AC-205、206、207、219）', async () => {
    await expectReport(admin, `${ADMIN_URL}/reports/customer-breakdown?productId=${productId}`, async () => {
      for (const [dimension, segment] of [
        ['都道府県', '東京都'],
        ['年齢層', '30 代'],
        ['性別', '女性'],
      ]) {
        const row = segmentRow(admin, dimension, segment)
        await expect(row.getByRole('cell', { name: '2,200 円' })).toBeVisible({ timeout: CHECK_TIMEOUT })
        await expect(row.getByRole('cell', { name: '1', exact: true })).toBeVisible({ timeout: CHECK_TIMEOUT })
      }
    })
  })
})

test('AC-214、215、217 営業は、メニューに売上の推移がなく、URL で開くと「権限がない」と示され、他の 2 つは開ける', async ({
  page,
}) => {
  await loginAdmin(page, ACCOUNTS.sales)

  const menu = page.getByRole('navigation', { name: '機能のメニュー' })
  await expect(menu.getByText('レポート', { exact: true })).toBeVisible()
  await expect(menu.getByRole('link', { name: '売上の推移' })).toHaveCount(0)

  await menu.getByRole('link', { name: 'バリエーションの売れ行き' }).click()
  await expect(page.getByRole('heading', { name: 'バリエーションの売れ行き' })).toBeVisible()
  await expect(page.getByText('商品を選ぶと、バリエーションごとの売れ行きを出します。')).toBeVisible()

  await menu.getByRole('link', { name: '地域・年齢層・性別' }).click()
  await expect(page.getByRole('region', { name: '都道府県ごとの売上額' })).toBeVisible()

  await page.goto(`${ADMIN_URL}/reports/sales-trend`)
  await expect(page.getByText('この画面を見る権限がありません。')).toBeVisible()
})
