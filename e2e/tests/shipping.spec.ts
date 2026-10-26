import { expect, type Page, test } from '@playwright/test'
import { ACCOUNTS, loginAdmin } from '../src/auth'
import { createPublishedProduct, taxCategoryWithRate, variantIds } from '../src/data'
import { detailValue } from '../src/detail'
import { notifyCarrier, shipmentMail, shipOnAdmin } from '../src/shipping'
import { orderAsMember, orderIdOf, payOnMockPage } from '../src/store'
import { ADMIN_URL, STORE_URL } from '../src/urls'
import { CHECK_TIMEOUT, reopenUntil } from '../src/waits'

/**
 * 出荷と配送状況の E2E。E2E のバックエンドは mock の決済サービスと mock の物流サービスで動き、
 * 出荷通知メールは compose の Mailpit に届く。
 */

/** 作った商品と、会員が支払って受付になった注文 */
type AcceptedOrder = { productId: string; variantId: string; orderId: string; orderUrl: string }

/**
 * 在庫 3 の公開の商品を作り、会員として 1 つ注文して mock で支払う（受付になる）。
 *
 * @param code 品番の一部
 */
async function acceptedOrder(page: Page, code: string): Promise<AcceptedOrder> {
  const { id: taxCategoryId } = await taxCategoryWithRate(10)
  const productId = await createPublishedProduct({
    code,
    name: `出荷の商品 ${code}`,
    taxCategoryId,
    priceExclTax: 1000,
    variants: [{ code: 'A', stock: 3 }],
  })
  const [variantId] = await variantIds(productId)
  await orderAsMember(page, ACCOUNTS.member, [productId])
  const orderUrl = await payOnMockPage(page)
  return { productId, variantId, orderId: orderIdOf(orderUrl), orderUrl }
}

/** 管理画面の注文詳細の見出しから、注文番号を読む（読み込むまでは「…」なので、番号が出るのを待つ） */
async function orderNumberOnAdmin(page: Page): Promise<string> {
  const heading = page.getByRole('heading', { name: /^注文：\d{8}-\d{6}$/ })
  await expect(heading).toBeVisible()
  return ((await heading.textContent()) ?? '').replace('注文：', '').trim()
}

test('AC-167、174、179、180、183 管理画面で出荷を登録し、mock の物流で配送中・配達完了にすると、店舗画面に配送状態が出て、出荷通知メールが届く', async ({
  page,
}) => {
  const order = await acceptedOrder(page, 'SHIP')
  const trackingNumber = '1234-5678-9012'
  let orderNumber = ''

  await test.step('管理画面：受付の注文に、配送業者と送り状番号を入れて出荷を登録する（AC-167）', async () => {
    await loginAdmin(page, ACCOUNTS.administrator, `/orders/${order.orderId}`)
    orderNumber = await orderNumberOnAdmin(page)
    await shipOnAdmin(page, trackingNumber)
    await expect(detailValue(page, '状態')).toHaveText('出荷済み')
    await expect(page.getByText('売上確定済み')).toBeVisible()
  })

  await test.step('出荷通知メールが、注文のメールアドレスに、注文番号・配送業者・送り状番号を書いて届く（AC-183）', async () => {
    const mail = await shipmentMail(orderNumber)
    expect(mail.to).toEqual([ACCOUNTS.member.email])
    expect(mail.text).toContain(orderNumber)
    expect(mail.text).toContain('ヤマト運輸')
    expect(mail.text).toContain(trackingNumber.replaceAll('-', ''))
  })

  for (const status of ['配送中', '配達完了'] as const) {
    await test.step(`mock の物流で${status}を通知すると、店舗の注文詳細と一覧に${status}が出る（AC-174、179、180）`, async () => {
      await notifyCarrier(page, order.orderId, status)

      await page.goto(order.orderUrl)
      await expect(detailValue(page, '状態')).toHaveText(status)
      await expect(detailValue(page, '配送業者')).toHaveText('ヤマト運輸')
      await expect(detailValue(page, '送り状番号')).toHaveText(trackingNumber.replaceAll('-', ''))

      await page.goto(`${STORE_URL}/orders`)
      const row = page.getByRole('row').filter({ hasText: orderNumber })
      await expect(row.getByText(status, { exact: true })).toBeVisible()
    })
  }
})

test('AC-189、190、191、192 返送の注文を、管理画面で絞り込んで在庫に戻してキャンセルすると、返金済みになり在庫数が戻る', async ({
  page,
}) => {
  const order = await acceptedOrder(page, 'RETURN')
  let orderNumber = ''

  await test.step('管理画面で出荷を登録し、mock の物流で返送を通知する', async () => {
    await loginAdmin(page, ACCOUNTS.administrator, `/orders/${order.orderId}`)
    orderNumber = await orderNumberOnAdmin(page)
    await shipOnAdmin(page, '9876-5432-1098')
    await notifyCarrier(page, order.orderId, '返送')
  })

  await test.step('管理画面：返送の注文で絞り込むと、その注文が出る（AC-189）', async () => {
    await page.goto(`${ADMIN_URL}/orders?orderNumber=${orderNumber}`)
    await page.getByRole('checkbox', { name: '返送' }).click()
    await page.getByRole('button', { name: '絞り込む', exact: true }).click()
    await expect(page).toHaveURL(/returned=true/)
    await expect(page.getByRole('link', { name: orderNumber })).toBeVisible()
  })

  await test.step('管理画面：理由を入れ、在庫に戻してキャンセルすると、返金済みになる（AC-190、191、192）', async () => {
    await page.getByRole('link', { name: orderNumber }).click()
    await page.getByRole('button', { name: 'キャンセルする', exact: true }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByLabel('理由（必須）').fill('受取拒否で返送された')
    await dialog.getByRole('radio', { name: '在庫に戻す' }).check()
    await dialog.getByRole('button', { name: 'キャンセルする', exact: true }).click()
    await expect(page.getByText('キャンセルしました')).toBeVisible()
    // 返金はキャンセルのコミットの後に、裏で行う
    await reopenUntil(page, undefined, () => expect(page.getByText('返金済み')).toBeVisible({ timeout: CHECK_TIMEOUT }))
  })

  await test.step('管理画面：在庫数が注文の前（3）に戻る（AC-191）', async () => {
    await page.goto(`${ADMIN_URL}/products/${order.productId}/variants/${order.variantId}/stock`)
    await expect(page.getByRole('status', { name: '在庫数' })).toHaveText('3')
  })

  await test.step('店舗画面：注文詳細がキャンセルになる', async () => {
    await page.goto(order.orderUrl)
    await expect(detailValue(page, '状態')).toHaveText('キャンセル')
  })
})
