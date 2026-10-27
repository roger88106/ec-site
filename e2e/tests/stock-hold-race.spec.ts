import { expect, test } from '@playwright/test'
import { ACCOUNTS } from '../src/auth'
import { createPublishedProduct, taxCategoryWithRate } from '../src/data'
import { loginWithCart } from '../src/store'
import { STORE_URL } from '../src/urls'

test('AC-82、83 在庫 1 の商品を 2 つのブラウザーで注文手続きに進むと、後の方は在庫不足と示される', async ({
  browser,
}) => {
  const { id: taxCategoryId } = await taxCategoryWithRate(10)
  const productId = await createPublishedProduct({
    code: 'RACE',
    name: '在庫 1 の商品',
    taxCategoryId,
    priceExclTax: 1000,
    variants: [{ code: 'A', stock: 1 }],
  })
  // 注文手続きに進むにはログインが必要だ。会員のカートは会員ごとなので、別々の会員が別々のコンテキストで操作する
  const [first, second] = await Promise.all([browser.newPage(), browser.newPage()])
  for (const [page, account] of [
    [first, ACCOUNTS.member],
    [second, ACCOUNTS.member2],
  ] as const) {
    await loginWithCart(page, account, [productId])
    await page.goto(`${STORE_URL}/cart`)
  }

  await first.getByRole('button', { name: '注文手続きへ進む', exact: true }).click()
  await expect(first.getByRole('heading', { name: 'ご注文手続き' })).toBeVisible()

  await second.getByRole('button', { name: '注文手続きへ進む', exact: true }).click()
  await expect(second.getByText('在庫が足りない商品があります。カートを確かめてください。')).toBeVisible()
  await expect(second.getByText('在庫切れです')).toBeVisible()
  await expect(second).toHaveURL(`${STORE_URL}/cart`)
  await Promise.all([first.context().close(), second.context().close()])
})
