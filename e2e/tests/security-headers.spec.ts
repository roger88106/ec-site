import { expect, type Page, type Response, test } from '@playwright/test'
import { ACCOUNTS } from '../src/auth'
import { createSimpleProduct } from '../src/data'
import { addToCart, loginWithCart, proceedToCheckout } from '../src/store'
import { BACKEND_URL, IDP_URL, IMAGE_ORIGIN, STORE_URL } from '../src/urls'

declare global {
  interface Window {
    /** テストの側がページに登録する、CSP の違反を記録する関数（`recordViolations` が `exposeFunction` で登録する） */
    reportCspViolation: (violation: string) => void
  }
}

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
  // 店舗画面の違反だけを集める（ログインで通る IdP の画面は、IdP 自身の CSP を持つため）
  const storeOrigin = new URL(STORE_URL).origin
  page.on('console', (message) => {
    const fromStore = message.location().url.startsWith(storeOrigin)
    if (fromStore && message.type() === 'error' && message.text().includes('Content Security Policy')) {
      violations.push(message.text())
    }
  })
  await page.exposeFunction('reportCspViolation', (violation: string) => {
    violations.push(violation)
  })
  await page.addInitScript((origin) => {
    if (location.origin !== origin) {
      return
    }
    document.addEventListener('securitypolicyviolation', (event) => {
      window.reportCspViolation(`${event.violatedDirective} ${event.blockedURI}`)
    })
  }, storeOrigin)
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

test('AC-418-1、AC-418-2、AC-418-3、AC-418-4 店舗画面の応答にセキュリティヘッダーが付き、主要な画面でブラウザーが CSP の違反を検出しない', async ({
  page,
  request,
}) => {
  const productId = await createSimpleProduct({ code: 'SECHDR', name: 'セキュリティヘッダーの確認の商品', stock: 10 })
  const violations = await recordViolations(page)

  await test.step('商品一覧：値が固定のヘッダーと CSP が付く（AC-418-1、AC-418-2、AC-418-3）', async () => {
    const response = await page.goto(`${STORE_URL}/products`)
    if (!response) {
      throw new Error('商品一覧の応答がない')
    }
    const headers = response.headers()
    for (const [header, value] of Object.entries(FIXED_HEADERS)) {
      expect(headers[header], header).toBe(value)
    }
    const directives = directivesOf(response)
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

  await test.step('proxy の対象外の応答にも、値が固定のヘッダーが付く（AC-418-1）', async () => {
    const html = await (await request.get(`${STORE_URL}/products`)).text()
    const staticPath = /\/_next\/static\/[^"'\s\\]+\.(?:js|css)/.exec(html)?.[0]
    if (!staticPath) {
      throw new Error('ページの HTML に /_next/static/ のファイルがない')
    }
    const targets = [
      // Auth.js のエンドポイントと静的なファイルは proxy を通らないので、CSP は付かない。存在しないパスは proxy を通るので付く
      { url: `${STORE_URL}/api/auth/session`, csp: false },
      { url: `${STORE_URL}${staticPath}`, csp: false },
      { url: `${STORE_URL}/no-such-page-418`, csp: true },
    ]
    for (const { url, csp } of targets) {
      const response = await request.get(url)
      const headers = response.headers()
      for (const [header, value] of Object.entries(FIXED_HEADERS)) {
        expect(headers[header], `${url} ${header}`).toBe(value)
      }
      expect(headers['content-security-policy'] !== undefined, `${url} CSP`).toBe(csp)
    }
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
