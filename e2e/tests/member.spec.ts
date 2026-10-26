import { expect, test } from '@playwright/test'
import { idpPasswordInput, memberEmail, setPasswordFromMail, submitIdpLogin, submitSignup } from '../src/auth'
import { createPublishedProduct, taxCategoryWithRate } from '../src/data'
import { detailValue } from '../src/detail'
import { addToCart, fillShipping, payOnMockPage } from '../src/store'
import { STORE_URL } from '../src/urls'

test('AC-108、111、112、113、114、115、122、124、125、127 会員登録して確認のメールのリンクを開くと、ログインした状態で戻ってカートが引き継がれ、注文が一覧に出る', async ({
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

  await test.step('ログインせずにカートに入れ、会員登録する。確認のメールを送ったと示される（AC-108、111）', async () => {
    await addToCart(page, productId, { quantity: '2' })
    await submitSignup(page, account, { lastName: '会員', firstName: '登録' })
  })

  await test.step('同じブラウザーで確認のメールのリンクを開いてパスワードを設定すると、ログインした状態で登録を始めた画面に戻る（AC-112、114）', async () => {
    await setPasswordFromMail(page, account)
    await expect(page).toHaveURL(`${STORE_URL}/products/${productId}`)
    await expect(page.getByText('会員 登録 様')).toBeVisible()
  })

  await test.step('ログインせずに入れたカートが、会員のカートに移っている（AC-122）', async () => {
    await page.goto(`${STORE_URL}/cart`)
    await expect(page.getByRole('link', { name })).toBeVisible()
    await expect(page.getByLabel('数量')).toHaveValue('2')
  })

  await test.step('注文し、注文の一覧に出る（AC-127）', async () => {
    await page.getByRole('button', { name: '注文手続きへ進む', exact: true }).click()
    await expect(page.getByText(account.email)).toBeVisible()
    await fillShipping(page, '100-0001')
    await page.getByRole('button', { name: /^注文する/ }).click()
    await expect(page.getByRole('heading', { name: 'mock の支払' })).toBeVisible()
    await payOnMockPage(page)
    const orderNumber = (await detailValue(page, '注文番号').textContent()) ?? ''

    await page.getByRole('banner').getByRole('link', { name: 'ご注文履歴' }).click()
    await expect(page.getByRole('link', { name: orderNumber })).toBeVisible()
  })

  await test.step('ログアウトするとカートは空に見え、ログインせずに入れて注文手続きに進むと、ログインの後にカートに戻る（AC-113、124、125）', async () => {
    await page.getByRole('button', { name: 'ログアウト', exact: true }).click()
    await expect(page.getByRole('link', { name: 'ログイン' })).toBeVisible()
    await page.goto(`${STORE_URL}/cart`)
    await expect(page.getByText('カートに商品がありません。')).toBeVisible()

    await addToCart(page, productId, { quantity: '1' })
    await page.goto(`${STORE_URL}/cart`)
    await page.getByRole('button', { name: '注文手続きへ進む', exact: true }).click()
    await submitIdpLogin(page, account)
    await expect(page).toHaveURL(`${STORE_URL}/cart`)
    await expect(page.getByText('会員 登録 様')).toBeVisible()
    await expect(page.getByLabel('数量')).toHaveValue('1')
  })

  await test.step('ログアウトすると、パスワードなしではログインし直せない（AC-115）', async () => {
    await page.getByRole('button', { name: 'ログアウト', exact: true }).click()
    await page.getByRole('link', { name: 'ログイン' }).click()
    await expect(idpPasswordInput(page)).toBeVisible()
  })
})
