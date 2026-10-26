import { expect, test } from '@playwright/test'
import { ACCOUNTS, loginAdmin } from '../src/auth'
import { createCategory, productCode, RUN_ID, taxCategoryWithRate } from '../src/data'
import { productCard } from '../src/store'
import { STORE_URL } from '../src/urls'
import { CHECK_TIMEOUT, reopenUntil } from '../src/waits'

test('AC-29、58、62、63、64 管理画面で商品を登録し、バリアントと在庫を用意して公開すると、店舗画面の一覧・検索・詳細に出る', async ({
  page,
}) => {
  const taxCategory = await taxCategoryWithRate(10)
  const category = await createCategory('E2E カテゴリ')
  const code = productCode('FLOW')
  // 検索のキーワード。実行ごとの印を含め、他の商品に当たらないようにする
  const keyword = `公開の流れ${RUN_ID}`
  const name = `E2E ${keyword} T シャツ`

  await test.step('管理画面：商品を登録する（品番は小文字で入れても大文字になる）', async () => {
    await loginAdmin(page, ACCOUNTS.administrator, `/products/new`)
    await page.getByLabel('商品品番（登録後は変更できません）').fill(code.toLowerCase())
    await expect(page.getByLabel('商品品番（登録後は変更できません）')).toHaveValue(code)
    await page.getByLabel('商品名').fill(name)
    await page.getByRole('combobox', { name: '税区分' }).click()
    await page.getByRole('option', { name: `${taxCategory.name}（現時点の率：10%）` }).click()
    await page.getByLabel('税抜価格（円）').fill('1000')
    await page.getByRole('checkbox', { name: category.name }).click()
    await page.getByRole('button', { name: '軸を追加' }).click()
    await page.getByLabel('軸 1').fill('色')
    await page.getByRole('button', { name: '登録' }).click()
    await expect(page.getByText('商品を登録しました')).toBeVisible()
    await expect(page.getByRole('heading', { name: code })).toBeVisible()
  })

  await test.step('管理画面：赤と青のバリアントを追加する', async () => {
    for (const [suffix, color] of [
      ['RED', '赤'],
      ['BLUE', '青'],
    ]) {
      await page.getByLabel('バリアント品番').fill(`${code}-${suffix}`)
      await page.getByLabel('色', { exact: true }).fill(color)
      await page.getByRole('button', { name: 'バリアントを追加' }).click()
      await expect(page.getByRole('cell', { name: `${code}-${suffix}`, exact: true })).toBeVisible()
    }
  })

  await test.step('管理画面：赤だけ入荷する', async () => {
    const row = page.getByRole('row').filter({ hasText: `${code}-RED` })
    await row.getByRole('button', { name: '0', exact: true }).click()
    await page.getByLabel('増減量').fill('5')
    await page.getByRole('combobox', { name: '理由' }).click()
    await page.getByRole('option', { name: '入荷' }).click()
    await page.getByRole('button', { name: '調整する' }).click()
    await expect(page.getByText('+5 を反映し、在庫数は 5 になりました')).toBeVisible()
    await page.getByRole('button', { name: '商品へ戻る' }).click()
  })

  await test.step('管理画面：公開する', async () => {
    await page.getByRole('button', { name: '公開する' }).click()
    await expect(page.getByText('公開しました')).toBeVisible()
  })

  await test.step('店舗画面：カテゴリの一覧と、キーワードの検索に出る', async () => {
    await reopenUntil(page, `${STORE_URL}/products?categoryId=${category.id}`, async () => {
      await expect(productCard(page, name)).toBeVisible({ timeout: CHECK_TIMEOUT })
    })
    await expect(productCard(page, name)).toContainText('1,100 円（税抜 1,000 円）')
    await reopenUntil(page, `${STORE_URL}/search?q=${encodeURIComponent(keyword)}`, async () => {
      await expect(productCard(page, name)).toBeVisible({ timeout: CHECK_TIMEOUT })
    })
  })

  await test.step('店舗画面：詳細でバリアントを選ぶと、在庫状況を出す。在庫のない青は選べない', async () => {
    await productCard(page, name).click()
    await expect(page.getByRole('heading', { name })).toBeVisible()
    await expect(page.getByText('1,100 円（税抜 1,000 円）')).toBeVisible()
    await expect(page.getByText('色を選ぶと、在庫状況を表示します。')).toBeVisible()
    await expect(page.getByRole('button', { name: '青' })).toBeDisabled()

    await page.getByRole('button', { name: '赤' }).click()
    await expect(page.getByText('在庫あり')).toBeVisible()
    // 在庫数は出さない（AC-64）。カートに入れる数量の選択肢（1〜10）は在庫数ではないので除く
    const five = page.getByText('5', { exact: true })
    await expect(five).toHaveCount(await five.and(page.getByRole('option')).count())
  })
})
