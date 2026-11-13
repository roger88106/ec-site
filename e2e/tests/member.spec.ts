import { expect, test } from '@playwright/test'
import {
  fillNewPassword,
  IDP_STORE_BASE_URL,
  idpBackToApplication,
  idpContinueLink,
  idpLoginError,
  idpPasswordInput,
  idpPasswordRejected,
  idpPasswordSubmitButton,
  loginStore,
  logoutButton,
  newMemberAccount,
  openMailLink,
  setPasswordFromMail,
  submitIdpLogin,
  submitSignup,
} from '../src/auth'
import { createSimpleProduct } from '../src/data'
import { detailValue } from '../src/detail'
import { addToCart, checkoutButton, fillShipping, payOnMockPage, proceedToCheckout, submitOrder } from '../src/store'
import { STORE_URL } from '../src/urls'

test('AC-108、111、112、113、114、115、122、124、125、127 会員登録して確認のメールのリンクを開いてパスワードを設定すると、ログインした状態で戻ってカートが引き継がれ、注文が一覧に出る', async ({
  page,
}) => {
  const productName = '会員の注文の商品'
  const productId = await createSimpleProduct({
    code: 'MEMBER',
    name: productName,
    stock: 5,
  })
  const account = newMemberAccount('signup')

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
    await expect(page.getByRole('link', { name: productName })).toBeVisible()
    await expect(page.getByLabel('数量')).toHaveValue('2')
  })

  await test.step('注文し、注文の一覧に出る（AC-127）', async () => {
    await proceedToCheckout(page)
    await expect(page.getByText(account.email)).toBeVisible()
    await fillShipping(page, '100-0001')
    await submitOrder(page)
    await payOnMockPage(page)
    const orderNumber = (await detailValue(page, '注文番号').textContent()) ?? ''

    await page.getByRole('banner').getByRole('link', { name: 'ご注文履歴' }).click()
    await expect(page.getByRole('link', { name: orderNumber })).toBeVisible()
  })

  await test.step('ログアウトするとカートは空に見え、ログインせずに入れて注文手続きに進むと、ログインの後にカートに戻る（AC-113、124、125）', async () => {
    await logoutButton(page).click()
    await expect(page.getByRole('link', { name: 'ログイン', exact: true })).toBeVisible()
    await page.goto(`${STORE_URL}/cart`)
    await expect(page.getByText('カートに商品がありません。')).toBeVisible()

    await addToCart(page, productId, { quantity: '1' })
    await page.goto(`${STORE_URL}/cart`)
    await checkoutButton(page).click()
    await submitIdpLogin(page, account)
    await expect(page).toHaveURL(`${STORE_URL}/cart`)
    await expect(page.getByText('会員 登録 様')).toBeVisible()
    await expect(page.getByLabel('数量')).toHaveValue('1')
  })

  await test.step('ログアウトすると、パスワードなしではログインし直せない（AC-115）', async () => {
    await logoutButton(page).click()
    await page.getByRole('link', { name: 'ログイン', exact: true }).click()
    await expect(idpPasswordInput(page)).toBeVisible()
  })
})

test('AC-110 確認メールのリンク先の画面で、条件を満たさないパスワードの設定は拒否される', async ({ page }) => {
  const account = newMemberAccount('weakpass')
  await page.goto(STORE_URL)
  await submitSignup(page, account, { lastName: '会員', firstName: '拒否' })
  await openMailLink(page, account)

  await test.step('短すぎるパスワードは拒否され、パスワードの設定の画面に残る', async () => {
    await fillNewPassword(page, { ...account, password: 'Ab1-xyz' })
    await idpPasswordSubmitButton(page).click()
    await expect(idpPasswordRejected(page)).toBeVisible()
    await expect(logoutButton(page)).toBeHidden()
  })

  await test.step('メールアドレスと同じパスワードは拒否される', async () => {
    await fillNewPassword(page, { ...account, password: account.email })
    await idpPasswordSubmitButton(page).click()
    await expect(idpPasswordRejected(page)).toBeVisible()
  })

  await test.step('条件を満たすパスワードなら設定でき、ログインした状態で店舗に戻る', async () => {
    await fillNewPassword(page, account)
    await idpPasswordSubmitButton(page).click()
    await expect(page.getByText('会員 拒否 様')).toBeVisible()
  })
})

test('AC-111 確認メールのリンクでパスワードを設定するまでは、ログインできない', async ({ page, browser }) => {
  const account = newMemberAccount('unverified')
  await page.goto(STORE_URL)
  await submitSignup(page, account, { lastName: '会員', firstName: '未確認' })

  await test.step('パスワードを設定する前は、ログインできない（登録の手続きを壊さないよう、別のブラウザーで試す）', async () => {
    const other = await browser.newContext()
    try {
      const otherPage = await other.newPage()
      await otherPage.goto(`${STORE_URL}/login`)
      await submitIdpLogin(otherPage, account)
      await expect(idpLoginError(otherPage)).toBeVisible()
    } finally {
      await other.close()
    }
  })

  await test.step('パスワードを設定した後は、ログインできる', async () => {
    await setPasswordFromMail(page, account)
    await expect(page.getByText('会員 未確認 様')).toBeVisible()
  })
})

test('AC-112 登録を始めたのと別のブラウザーで確認メールのリンクを開くと、パスワードを設定した後、IdP の画面から店舗に戻ってログインする', async ({
  page,
  browser,
}) => {
  const account = newMemberAccount('otherbrowser')
  await page.goto(STORE_URL)
  await submitSignup(page, account, { lastName: '会員', firstName: '別端末' })

  const other = await browser.newContext()
  try {
    const otherPage = await other.newPage()
    await test.step('別のブラウザーでパスワードを設定すると、IdP の画面から店舗に戻る操作ができる', async () => {
      await openMailLink(otherPage, account)
      await idpContinueLink(otherPage).click()
      await fillNewPassword(otherPage, account)
      await idpPasswordSubmitButton(otherPage).click()
      await expect(idpBackToApplication(otherPage)).toHaveAttribute('href', `${IDP_STORE_BASE_URL}/`)
      // IdP の戻り先は開発用の店舗の URL で、E2E の店舗とはポートが違う。戻り先へのリクエストだけを E2E の店舗に振り替える
      await otherPage.route(`${IDP_STORE_BASE_URL}/**`, (route) =>
        route.fulfill({ status: 302, headers: { location: `${STORE_URL}/` } }),
      )
      await idpBackToApplication(otherPage).click()
      await expect(otherPage).toHaveURL(`${STORE_URL}/`)
    })

    await test.step('店舗ではログインしていないので、ログインする', async () => {
      await expect(otherPage.getByRole('link', { name: 'ログイン', exact: true })).toBeVisible()
      await loginStore(otherPage, account)
      await expect(otherPage.getByText('会員 別端末 様')).toBeVisible()
    })
  } finally {
    await other.close()
  }
})
