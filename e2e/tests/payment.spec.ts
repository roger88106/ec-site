import { expect, type Page, test } from '@playwright/test'
import { ACCOUNTS, loginStore } from '../src/auth'
import { createPublishedProduct, taxCategoryWithRate } from '../src/data'
import { emptyCart, fillShipping, payOnMockPage } from '../src/store'
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
