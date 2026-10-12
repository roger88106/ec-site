import { expect, type Page, test } from '@playwright/test'
import { latestIdpLink, memberEmail, submitIdpLogin } from '../src/auth'
import { createPublishedProduct, taxCategoryWithRate } from '../src/data'
import { fillShipping } from '../src/store'
import { STORE_URL } from '../src/urls'

/** 商品の詳細から、ログインせずにカートに入れる */
async function addToCart(page: Page, productId: string, quantity: string) {
  await page.goto(`${STORE_URL}/products/${productId}`)
  await page.getByLabel('数量').selectOption(quantity)
  await page.getByRole('button', { name: 'カートに入れる' }).click()
  await expect(page.getByText('カートに入れました。')).toBeVisible()
}

test('AC-108、111〜115、122、124、125、129 会員登録して確認のメールのリンクを開くと、ログインした状態で戻ってカートが引き継がれ、注文が一覧に出る', async ({
  page,
}) => {
  const { id: taxCategoryId } = await taxCategoryWithRate(10)
  const name = '会員の注文の商品'
  const productId = await createPublishedProduct({
    code: 'MEMBER',
    name,
    taxCategoryId,
    priceExclTax: 1000,
    variants: [{ code: 'A', stock: 5 }],
  })
  const account = { email: memberEmail('signup'), password: 'E2e-pass-1234' }

  await test.step('ログインせずにカートに入れ、会員登録する。確認のメールが届くまでログインできない（AC-108、111）', async () => {
    await addToCart(page, productId, '2')
    await page.getByRole('link', { name: '会員登録' }).click()
    await page.locator('#email').fill(account.email)
    await page.locator('#lastName').fill('会員')
    await page.locator('#firstName').fill('登録')
    await page.getByRole('button', { name: '登録' }).click()
    await expect(page.getByText(/メールアドレスを確認する手順を記載したメール/)).toBeVisible()
  })

  await test.step('同じブラウザーで確認のメールのリンクを開いてパスワードを設定すると、ログインした状態で登録を始めた画面に戻る（AC-112、114）', async () => {
    await page.goto(await latestIdpLink(account.email))
    await page.locator('#password-new').fill(account.password)
    await page.locator('#password-confirm').fill(account.password)
    await page.getByRole('button', { name: '送信' }).click()
    await expect(page).toHaveURL(`${STORE_URL}/products/${productId}`)
    await expect(page.getByText('会員 登録 様')).toBeVisible()
  })

  await test.step('ログインせずに入れたカートが、会員のカートに移っている（AC-122）', async () => {
    await page.goto(`${STORE_URL}/cart`)
    await expect(page.getByRole('link', { name })).toBeVisible()
    await expect(page.getByLabel('数量')).toHaveValue('2')
  })

  await test.step('注文し、注文の一覧に出る（AC-127、129）', async () => {
    await page.getByRole('button', { name: '注文手続きへ進む' }).click()
    await expect(page.getByText(account.email)).toBeVisible()
    await fillShipping(page, '100-0001')
    await page.getByRole('button', { name: /^注文する/ }).click()
    // 注文手続きの画面にも「ご注文の内容」の見出しがあるので、注文の画面へ移ったことを URL で確かめる
    await expect(page).toHaveURL(/\/orders\//)
    const orderNumber = (await page.locator('dd').first().textContent()) ?? ''

    await page.getByRole('link', { name: 'ご注文履歴' }).first().click()
    await expect(page.getByRole('link', { name: orderNumber })).toBeVisible()
  })

  await test.step('ログアウトするとカートは空に見え、ログインせずに入れて注文手続きに進むと、ログインの後にカートに戻る（AC-113、124、125）', async () => {
    await page.getByRole('button', { name: 'ログアウト' }).click()
    await expect(page.getByRole('link', { name: 'ログイン' })).toBeVisible()
    await page.goto(`${STORE_URL}/cart`)
    await expect(page.getByText('カートに商品がありません。')).toBeVisible()

    await addToCart(page, productId, '1')
    await page.goto(`${STORE_URL}/cart`)
    await page.getByRole('button', { name: '注文手続きへ進む' }).click()
    await submitIdpLogin(page, account)
    await expect(page).toHaveURL(`${STORE_URL}/cart`)
    await expect(page.getByText('会員 登録 様')).toBeVisible()
    await expect(page.getByLabel('数量')).toHaveValue('1')
  })

  await test.step('ログアウトすると、パスワードなしではログインし直せない（AC-115）', async () => {
    await page.getByRole('button', { name: 'ログアウト' }).click()
    await page.getByRole('link', { name: 'ログイン' }).click()
    await expect(page.locator('#password')).toBeVisible()
  })
})
