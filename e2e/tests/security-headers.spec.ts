import { expect, type Page, type Response, test } from '@playwright/test'
import { ACCOUNTS } from '../src/auth'
import { createSimpleProduct } from '../src/data'
import { addToCart, loginWithCart, proceedToCheckout } from '../src/store'
import { BACKEND_URL, IDP_URL, IMAGE_ORIGIN, STORE_URL } from '../src/urls'

/** E2E の店舗画面は本番モード（next start）なので、HSTS も付く */
const FIXED_HEADERS = {
  'x-frame-options': 'DENY',
  'x-content-type-options': 'nosniff',
  'referrer-policy': 'strict-origin-when-cross-origin',
  'strict-transport-security': 'max-age=31536000',
}

/**
 * ページで起きた CSP の違反を記録する。違反のイベント（securitypolicyviolation）と、コンソールのエラーの両方を集める。
 *
 * @param page 店舗画面のページ
 * @returns 記録した違反（テストの終わりに読む）
 */
async function recordViolations(page: Page): Promise<string[]> {
  // 画面を移ってもページの外に残るよう、テストの側の配列に集める
  const violations: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error' && message.text().includes('Content Security Policy')) {
      violations.push(message.text())
    }
  })
  await page.exposeFunction('reportCspViolation', (violation: string) => {
    violations.push(violation)
  })
  await page.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', (event) => {
      const report = (window as unknown as { reportCspViolation: (violation: string) => void }).reportCspViolation
      report(`${event.violatedDirective} ${event.blockedURI}`)
    })
  })
  return violations
}

/**
 * CSP の値を、ディレクティブの名前から値の一覧への対応にする。
 *
 * @param response 画面の応答
 * @returns ディレクティブごとの値
 */
function directivesOf(response: Response): Record<string, string[]> {
  const policy = response.headers()['content-security-policy'] ?? ''
  return Object.fromEntries(
    policy
      .split(';')
      .map((part) => part.trim().split(' '))
      .filter(([name]) => name)
      .map(([name, ...values]) => [name, values]),
  )
}

test('AC-418-1、AC-418-2、AC-418-3、AC-418-4 店舗画面の応答にセキュリティヘッダーが付き、主要な画面で CSP の違反が報告されない', async ({
  page,
}) => {
  const productId = await createSimpleProduct({ code: 'SECHDR', name: 'セキュリティヘッダーの確認の商品', stock: 10 })
  const violations = await recordViolations(page)

  await test.step('商品一覧：値が固定のヘッダーと CSP が付く（AC-418-1、AC-418-2、AC-418-3）', async () => {
    const response = await page.goto(`${STORE_URL}/products`)
    expect(response).not.toBeNull()
    const headers = response?.headers() ?? {}
    for (const [header, value] of Object.entries(FIXED_HEADERS)) {
      expect(headers[header], header).toBe(value)
    }
    const directives = directivesOf(response as Response)
    expect(directives['frame-ancestors']).toEqual(["'none'"])
    expect(directives['script-src']).toEqual(["'self'", "'unsafe-inline'"])
    expect(directives['img-src']).toEqual(["'self'", 'data:', IMAGE_ORIGIN])
    expect(directives['form-action']).toEqual([
      "'self'",
      new URL(IDP_URL).origin,
      'https://checkout.stripe.com',
      new URL(BACKEND_URL).origin,
    ])
  })

  await test.step('商品の詳細とカート（AC-418-4）', async () => {
    await addToCart(page, productId)
    await page.goto(`${STORE_URL}/cart`)
    await expect(page.getByRole('button', { name: '注文手続きへ進む', exact: true })).toBeVisible()
  })

  await test.step('注文手続き（AC-418-4）', async () => {
    await loginWithCart(page, ACCOUNTS.member2, [productId])
    await page.goto(`${STORE_URL}/cart`)
    await proceedToCheckout(page)
  })

  expect(violations).toEqual([])
})
