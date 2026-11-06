import { expect, test } from '@playwright/test'
import { ACCOUNTS } from '../src/auth'
import { createSimpleProduct } from '../src/data'
import { loginWithCart, proceedToCheckout } from '../src/store'
import { STORE_URL } from '../src/urls'

test('AC-82、83 在庫 1 の商品を 2 つのブラウザーで注文手続きに進むと、後の方は在庫不足と示される', async ({
  browser,
}) => {
  const productId = await createSimpleProduct({
    code: 'RACE',
    name: '在庫 1 の商品',
    stock: 1,
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

  await proceedToCheckout(first)

  await second.getByRole('button', { name: '注文手続きへ進む', exact: true }).click()
  await expect(second.getByText('在庫が足りない商品があります。カートを確かめてください。')).toBeVisible()
  await expect(second.getByText('在庫切れです')).toBeVisible()
  await expect(second).toHaveURL(`${STORE_URL}/cart`)
  await Promise.all([first.context().close(), second.context().close()])
})
