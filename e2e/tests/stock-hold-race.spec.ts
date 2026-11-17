import { expect, test } from '@playwright/test'
import { ACCOUNTS, newMemberAccount, registerMember } from '../src/auth'
import { createSimpleProduct } from '../src/data'
import { addToCart, checkoutButton, loginWithCart, proceedToCheckout } from '../src/store'
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
  try {
    await loginWithCart(first, ACCOUNTS.member, [productId])
    await first.goto(`${STORE_URL}/cart`)
    // 後の方は、支払待ちの注文の確認の画面が出ないよう、支払待ちの注文のない、このテストで登録した会員にする
    await second.goto(STORE_URL)
    await registerMember(second, newMemberAccount('race'), { lastName: '在庫', firstName: '競合' })
    await addToCart(second, productId)
    await second.goto(`${STORE_URL}/cart`)

    await proceedToCheckout(first)

    await checkoutButton(second).click()
    await expect(second.getByText('在庫が足りない商品があります。カートを確かめてください。')).toBeVisible()
    await expect(second.getByText('在庫切れです')).toBeVisible()
    await expect(second).toHaveURL(`${STORE_URL}/cart`)
  } finally {
    await Promise.all([first.context().close(), second.context().close()])
  }
})
