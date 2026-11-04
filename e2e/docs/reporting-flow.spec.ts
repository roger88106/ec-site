import path from 'node:path'
import { test } from '@playwright/test'
import { ACCOUNTS, loginAdmin } from '../src/auth'
import { makeShot, SEED, SEED_ORDERS } from '../src/screenshot'
import { SHOWCASE_ADMIN_URL as ADMIN } from '../src/showcase-urls'

/** 撮った画像の置き場所（docs/01-概要/reporting.md が載せる） */
const OUT = path.resolve(import.meta.dirname, '../../docs/01-概要/img/reporting')

/** 画面を撮って OUT に置く */
const shot = makeShot(OUT)

test('レポートの流れのスクリーンショットを撮る', async ({ page }) => {
  await loginAdmin(page, ACCOUNTS.administrator, { adminUrl: ADMIN })

  await test.step('管理画面：売上の推移（seed の注文の期間を指定する）', async () => {
    await page.goto(`${ADMIN}/reports/sales-trend?from=${SEED_ORDERS.from}&to=${SEED_ORDERS.to}`)
    await shot(page, '01-sales-trend', page.getByRole('cell', { name: '2026 年 9 月' }))
  })

  await test.step('管理画面：バリアントの売れ行き', async () => {
    await page.goto(
      `${ADMIN}/reports/variant-sales?from=${SEED_ORDERS.lastYear}&to=${SEED_ORDERS.to}&productId=${SEED.mug}`,
    )
    await shot(page, '02-variant-sales', page.getByRole('cell', { name: 'MUG-001-WH', exact: true }))
  })

  await test.step('管理画面：地域・年齢層・性別', async () => {
    await page.goto(`${ADMIN}/reports/customer-breakdown?from=${SEED_ORDERS.lastYear}&to=${SEED_ORDERS.to}`)
    await shot(page, '03-customer-breakdown', page.getByRole('region', { name: '性別ごとの売上額' }))
  })
})
