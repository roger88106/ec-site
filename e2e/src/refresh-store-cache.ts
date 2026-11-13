import { chromium, type Page } from '@playwright/test'
import { SHOWCASE_BACKEND_URL as BACKEND, SHOWCASE_STORE_URL as STORE } from './showcase-urls'
import { reopenUntil, SHOWCASE_CACHE_TIMEOUT } from './waits'

/** 撮影で開く、商品のカードを表示する店舗画面のパス（トップと商品一覧） */
const PAGES_WITH_CARDS = ['/', '/products']

/** 商品のカードのリンクの形。キャプチャーグループは商品 ID */
const PRODUCT_LINK = /^\/products\/([^/?]+)$/

/**
 * 開いている画面の商品のカードが、全て今の DB の商品であることを確認する。
 *
 * @param page 商品のカードを表示する画面を開いた Playwright の Page
 * @throws 商品のカードがないとき、DB にない商品のカードがあるとき
 */
async function expectNoStaleCard(page: Page): Promise<void> {
  const hrefs = await page
    .locator('a[href^="/products/"]')
    .evaluateAll((links) => links.map((link) => link.getAttribute('href') ?? ''))
  const productIds = hrefs.map((href) => PRODUCT_LINK.exec(href)?.[1]).filter((id) => id !== undefined)
  // カードのない画面（エラーの画面など）では、再作成の前の商品が残っているかを確認できない
  if (productIds.length === 0) throw new Error(`商品のカードが表示されていない：${page.url()}`)
  for (const productId of productIds) {
    const response = await fetch(`${BACKEND}/api/store/products/${productId}`)
    if (!response.ok) throw new Error(`DB にない商品のカードが表示されている：${productId}（${response.status}）`)
  }
}

/**
 * 撮影の前に、DB を再作成する前の商品を店舗画面のキャッシュから除く（Playwright の globalSetup）。
 *
 * 店舗画面のキャッシュは DB の再作成の後も残り、再作成の前の商品のカードが画像に写り込むことがある。
 * 開き直すうちに新しい内容に更新されるので（`reopenUntil`）、カードが全て今の DB の商品になるまで開き直す。
 */
export default async function refreshStoreCache(): Promise<void> {
  const browser = await chromium.launch()
  try {
    const page = await browser.newPage()
    for (const path of PAGES_WITH_CARDS) {
      await reopenUntil(page, `${STORE}${path}`, () => expectNoStaleCard(page), { timeout: SHOWCASE_CACHE_TIMEOUT })
    }
  } finally {
    await browser.close()
  }
}
