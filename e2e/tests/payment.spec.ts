import { expect, type Page, test } from '@playwright/test'
import { ACCOUNTS, loginAdmin, loginStore } from '../src/auth'
import { createPublishedProduct, taxCategoryWithRate } from '../src/data'
import { emptyCart, expectOrderPage, fillShipping, orderCart, payOnMockPage } from '../src/store'
import { BACKEND_URL, STORE_URL } from '../src/urls'

/**
 * 支払の E2E。E2E のバックエンドは mock の決済サービスで動き、支払の期限は 30 秒（application-e2e.yml）。
 * 会員のカートは実行をまたいで残るので、各テストはカートを空にしてから始める。
 */

/**
 * 公開の商品を作り、会員としてログインして、空にしたカートに 1 つずつ入れる。
 *
 * @param code 品番の一部
 * @param count 作る商品の数
 */
async function memberCartWith(page: Page, code: string, count = 1) {
  const { id: taxCategoryId } = await taxCategoryWithRate(10)
  const productIds: string[] = []
  for (let index = 1; index <= count; index++) {
    productIds.push(
      await createPublishedProduct({
        code: `${code}${index}`,
        name: `支払の商品 ${code}${index}`,
        taxCategoryId,
        priceExclTax: 1000,
        variants: [{ code: 'A', stock: 3 }],
      }),
    )
  }
  await loginStore(page, ACCOUNTS.member)
  await emptyCart(page)
  for (const productId of productIds) {
    await page.goto(`${STORE_URL}/products/${productId}`)
    await page.getByRole('button', { name: 'カートに入れる' }).click()
    await expect(page.getByText('カートに入れました。')).toBeVisible()
  }
}

test('AC-143、145、146、147、149 注文すると mock の支払の画面に移り、支払うと注文の画面に戻って受付になり、注文した品目がカートから除かれる', async ({
  page,
}) => {
  // 品目が複数の注文でも、与信の後にカートから除ける（T7407）
  await memberCartWith(page, 'PAY', 2)

  await test.step('注文手続き：カードの入力はなく、注文すると mock の支払の画面に移る（AC-143、145）', async () => {
    await page.goto(`${STORE_URL}/cart`)
    await page.getByRole('button', { name: '注文手続きへ進む' }).click()
    await expect(page.getByRole('heading', { name: 'ご注文手続き' })).toBeVisible()
    await expect(page.getByLabel(/カード/)).toHaveCount(0)
    await fillShipping(page, '100-0001')
    await page.getByRole('button', { name: /^注文する/ }).click()
    await expect(page).toHaveURL(new RegExp(`^${BACKEND_URL}/mock-payment/`))
    await expect(page.getByRole('heading', { name: 'mock の支払' })).toBeVisible()
  })

  await test.step('支払うと注文の画面に戻り、受付になる（AC-146、149）', async () => {
    await payOnMockPage(page)
  })

  await test.step('注文した品目がカートから除かれる（AC-147）', async () => {
    await page.goto(`${STORE_URL}/cart`)
    await expect(page.getByText('カートに商品がありません。')).toBeVisible()
  })
})

test('AC-148 mock でカードを拒否させても注文は支払待ちのままで、やり直して支払える', async ({ context, page }) => {
  await memberCartWith(page, 'DECLINE')
  await orderCart(page)

  await test.step('カードを拒否させると、支払の画面に拒否が出て、注文は支払待ちのまま', async () => {
    await page.getByRole('button', { name: 'カードを拒否させる' }).click()
    await expect(page.getByText('カードが拒否されました。別のカードでやり直してください。')).toBeVisible()
    // 支払の画面を開いたまま、注文の一覧の最も新しい注文（いま注文したもの）を別のページで開く
    const order = await context.newPage()
    await order.goto(`${STORE_URL}/orders`)
    await order
      .getByRole('link', { name: /^\d{8}-\d{6}$/ })
      .first()
      .click()
    await expect(order.getByText('支払待ち', { exact: true })).toBeVisible()
    await order.close()
  })

  await test.step('やり直して支払うと、受付になる', async () => {
    await payOnMockPage(page)
  })
})

test('AC-153、155 支払わずに期限を過ぎると、注文が支払期限切れでキャンセルになる', async ({ page }) => {
  // 支払の期限（30 秒）と、期限切れを見る間隔を待つ
  test.setTimeout(120_000)
  await memberCartWith(page, 'EXPIRE')
  await orderCart(page)
  let orderUrl = ''

  await test.step('支払の画面でやめると、注文の画面に戻り、注文は支払待ちのまま', async () => {
    await page.getByRole('button', { name: 'やめる' }).click()
    await expect(page).toHaveURL(/\/orders\/[^/?]+\?payment=returned$/)
    orderUrl = page.url().split('?')[0]
    await page.goto(orderUrl)
    await expect(page.getByText('支払待ち', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '支払う' })).toBeVisible()
  })

  await test.step('期限を過ぎると、支払期限切れのキャンセルになる', async () => {
    await expectOrderPage(
      page,
      orderUrl,
      page.getByText('お支払い期限までにお支払いがなかったため、このご注文はキャンセルされました。'),
      90_000,
    )
    await expect(page.getByText('キャンセル', { exact: true }).first()).toBeVisible()
    await expect(page.getByRole('button', { name: '支払う' })).toHaveCount(0)
  })
})

test('AC-162 管理画面で受付の注文をキャンセルすると、支払の状態が与信の取消済みになる', async ({ page }) => {
  await memberCartWith(page, 'VOID')
  await orderCart(page)
  const orderId = new URL(await payOnMockPage(page)).pathname.split('/').at(-1)

  await test.step('管理画面：受付の注文は与信済みで、運営者がキャンセルする', async () => {
    await loginAdmin(page, ACCOUNTS.administrator, `/orders/${orderId}`)
    await expect(paymentStatus(page)).toHaveText('与信済み')
    await page.getByRole('button', { name: 'キャンセルする' }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByLabel('理由（必須）').fill('E2E の与信の取消')
    await dialog.getByRole('button', { name: 'キャンセルする' }).click()
    await expect(page.getByText('キャンセルしました')).toBeVisible()
  })

  await test.step('与信の取消は裏で行うので、開き直しながら与信の取消済みになるのを待つ', async () => {
    await expect(async () => {
      await page.reload()
      await expect(paymentStatus(page)).toHaveText('与信の取消済み', { timeout: 1_000 })
    }).toPass({ timeout: 20_000, intervals: [500, 1_000] })
  })
})

/** 管理画面の注文の詳細の、支払の状態 */
function paymentStatus(page: Page) {
  return page.locator('dt', { hasText: /^支払$/ }).locator('xpath=following-sibling::dd[1]')
}
