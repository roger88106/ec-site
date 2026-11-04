import path from 'node:path'
import { expect, type Locator, type Page, test } from '@playwright/test'
import { ACCOUNTS, loginAdmin } from '../src/auth'
import { makeShot } from '../src/screenshot'
import { SHOWCASE_ADMIN_URL as ADMIN, SHOWCASE_STORE_URL as STORE } from '../src/showcase-urls'
import { productCard } from '../src/store'
import { ASYNC_TIMEOUT, CHECK_TIMEOUT, reopenUntil } from '../src/waits'

/** 撮った画像の置き場所（docs/01-概要/catalog.md が載せる） */
const OUT = path.resolve(import.meta.dirname, '../../docs/01-概要/img/catalog')

/** 画面を撮って OUT に置く */
const shot = makeShot(OUT)

/**
 * 商品に登録する画像。seed の画像（オックスフォードシャツの写真）を、リポジトリのファイルのまま指す。
 * 管理画面から登録すると変換して別のファイルとして保存するので、seed の画像は変わらない。
 */
const PHOTO = path.resolve(
  import.meta.dirname,
  '../../docker/seaweedfs/seed/assets/00000000-0000-0000-0000-000000007008.webp',
)

/** 登録する商品。品番は固定なので、撮り直す前に DB を再作成する（同じ品番は 2 度登録できない） */
const PRODUCT = {
  code: 'SHIRT-100',
  name: 'ボタンダウンシャツ',
  priceExclTax: '5000',
  variants: [
    { suffix: 'BL', color: 'ブルー' },
    { suffix: 'WH', color: 'ホワイト' },
  ],
}

/**
 * 通知（トースト）が消えるのを待つ。通知が次の画像に重ならないようにする。固定の時間は待たない。
 *
 * @param page 管理画面のページ
 * @param text 消えるのを待つ通知の文
 */
async function expectToastGone(page: Page, text: string): Promise<void> {
  // 同じ文の通知が重なって出ることがある（バリアントを 2 つ追加したとき）ので、数が 0 になるのを待つ
  await expect(page.getByText(text)).toHaveCount(0, { timeout: ASYNC_TIMEOUT })
}

/**
 * ボタンの色の変化（不透明度の遷移）が終わるのを待つ。薄い途中の状態を撮らないようにする。
 *
 * @param button 撮るボタン
 */
async function expectOpaque(button: Locator): Promise<void> {
  await expect(button).toBeEnabled()
  await expect.poll(() => button.evaluate((element) => getComputedStyle(element).opacity)).toBe('1')
}

test('商品の登録と公開の流れのスクリーンショットを撮る', async ({ page }) => {
  let productUrl = ''
  let productId = ''

  await test.step('管理画面：商品を登録する', async () => {
    await loginAdmin(page, ACCOUNTS.administrator, { adminUrl: ADMIN, path: '/products/new' })
    await page.getByLabel('商品品番（登録後は変更できません）').fill(PRODUCT.code)
    await page.getByLabel('商品名').fill(PRODUCT.name)
    await page.getByRole('combobox', { name: '税区分' }).click()
    await page.getByRole('option', { name: /^標準/ }).click()
    await page.getByLabel('税抜価格（円）').fill(PRODUCT.priceExclTax)
    await page.getByRole('button', { name: '軸を追加', exact: true }).click()
    await page.getByLabel('軸 1').fill('色')
    await shot(page, '01-register', page.getByRole('button', { name: '登録', exact: true }))

    await page.getByRole('button', { name: '登録', exact: true }).click()
    await expect(page.getByText('商品を登録しました')).toBeVisible()
    await expect(page.getByRole('heading', { name: PRODUCT.code })).toBeVisible()
    productUrl = page.url()
    productId = new URL(productUrl).pathname.split('/').at(-1) ?? ''
  })

  await test.step('管理画面：画像を登録する', async () => {
    await expectToastGone(page, '商品を登録しました')
    await page.getByLabel('画像のファイル').setInputFiles(PHOTO)
    await expect(page.getByText('画像を 1 枚登録しました')).toBeVisible()
    await expectToastGone(page, '画像を 1 枚登録しました')
    await shot(page, '02-image', page.getByRole('img', { name: '画像 1' }))
  })

  await test.step('管理画面：バリアントを追加する', async () => {
    for (const { suffix, color } of PRODUCT.variants) {
      await page.getByLabel('バリアント品番').fill(`${PRODUCT.code}-${suffix}`)
      await page.getByLabel('色', { exact: true }).fill(color)
      await page.getByRole('button', { name: 'バリアントを追加', exact: true }).click()
      await expect(page.getByRole('cell', { name: `${PRODUCT.code}-${suffix}`, exact: true })).toBeVisible()
    }
    await expectToastGone(page, 'バリアントを追加しました')
    await shot(page, '03-variants', page.getByRole('cell', { name: `${PRODUCT.code}-WH`, exact: true }))
  })

  await test.step('管理画面：入荷して在庫を調整する', async () => {
    for (const [index, { suffix }] of PRODUCT.variants.entries()) {
      const row = page.getByRole('row').filter({ hasText: `${PRODUCT.code}-${suffix}` })
      await row.getByRole('button', { name: '0', exact: true }).click()
      await page.getByLabel('増減量').fill('10')
      await page.getByRole('combobox', { name: '理由' }).click()
      await page.getByRole('option', { name: '入荷' }).click()
      const adjust = page.getByRole('button', { name: '調整する', exact: true })
      if (index === 0) {
        await expectToastGone(page, 'バリアントを追加しました')
        await expectOpaque(adjust)
        await shot(page, '04-stock', adjust)
      }
      await adjust.click()
      await expect(page.getByText('+10 を反映し、在庫数は 10 になりました')).toBeVisible()
      await page.getByRole('button', { name: '商品へ戻る', exact: true }).click()
    }
    await expect(page).toHaveURL(productUrl)
  })

  await test.step('管理画面：公開する', async () => {
    await expectToastGone(page, '+10 を反映し、在庫数は 10 になりました')
    await page.getByRole('button', { name: '公開する', exact: true }).click()
    await expect(page.getByText('公開しました')).toBeVisible()
    // 通知は写さない。通知が消え、状態のバッジが「公開」になって色の変化が終わってから撮る
    await expectToastGone(page, '公開しました')
    const badge = page.getByText('公開', { exact: true })
    await expectOpaque(badge)
    await shot(page, '05-publish', badge)
  })

  await test.step('店舗画面：一覧に出る', async () => {
    // 一覧は店舗画面のキャッシュを通る。DB を再作成する前に撮った同名の商品のカード（古い商品 ID を指す）が残っていることがあるので、
    // カードの表示だけでなく、リンクの先が今回の商品 ID になるまで、開き直しながら待つ
    await reopenUntil(page, `${STORE}/products`, async () => {
      await expect(productCard(page, PRODUCT.name)).toHaveAttribute('href', `/products/${productId}`, {
        timeout: CHECK_TIMEOUT,
      })
    })
    await shot(page, '06-store-list', productCard(page, PRODUCT.name))
  })

  await test.step('店舗画面：詳細でバリアントを選ぶ', async () => {
    // 詳細も店舗画面のキャッシュを通るので、公開の直後は開き直しながら待つ（カードは押さず、今回の商品 ID で開く）
    await reopenUntil(page, `${STORE}/products/${productId}`, async () => {
      await expect(page.getByRole('heading', { name: PRODUCT.name })).toBeVisible({ timeout: CHECK_TIMEOUT })
    })
    await page.getByRole('button', { name: PRODUCT.variants[0].color, exact: true }).click()
    const addToCartButton = page.getByRole('button', { name: 'カートに入れる', exact: true })
    await expect(addToCartButton).toBeEnabled()
    await expectOpaque(addToCartButton)
    await shot(page, '07-store-detail', page.getByText('在庫あり', { exact: true }))
  })
})
