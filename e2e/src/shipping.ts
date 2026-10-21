import { expect, type Page } from '@playwright/test'
import { BACKEND_URL, MAILPIT_URL } from './urls'
import { MAIL_TIMEOUT } from './waits'

/** mock の物流の画面で通知できる配送状態（ボタンの名前） */
export type CarrierNotification = '配送中' | '配達完了' | '返送'

/**
 * 管理画面の注文詳細で、配送業者と送り状番号を入れて出荷を登録する（確認のダイアログで確定する）。
 * mock の物流の画面で通知できるのはヤマト運輸の出荷だけなので、ヤマト運輸にする。
 *
 * @param page 管理画面で、出荷できる注文の詳細を開いているページ
 * @param trackingNumber 送り状番号
 */
export async function shipOnAdmin(page: Page, trackingNumber: string): Promise<void> {
  await page.getByRole('combobox', { name: '配送業者' }).click()
  await page.getByRole('option', { name: 'ヤマト運輸' }).click()
  await page.getByLabel('送り状番号').fill(trackingNumber)
  await page.getByRole('button', { name: '出荷を登録する' }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '出荷する' }).click()
  await expect(page.getByText('出荷を登録しました')).toBeVisible()
}

/**
 * mock の物流の画面（E2E のバックエンド）で、注文の出荷の配送状態を通知し、一覧に反映されるのを待つ。<br />
 * mock は画面を出したバックエンドのエンドポイントに通知するので、E2E の設定は必要ない。
 *
 * @param page 使うページ（mock の物流の画面に移る）
 * @param orderId 注文 ID
 * @param status 通知する配送状態
 */
export async function notifyCarrier(page: Page, orderId: string, status: CarrierNotification): Promise<void> {
  await page.goto(`${BACKEND_URL}/mock-carrier`)
  const row = page.getByRole('row').filter({ hasText: orderId })
  await row.getByRole('button', { name: status }).click()
  await expect(row.getByRole('cell', { name: status, exact: true })).toBeVisible()
}

/**
 * 注文番号を書いた出荷通知メールを、Mailpit に届くまで待って返す。<br />
 * 注文番号は注文ごとに違うので、前の実行のメールと混ざらない（メールを消さずに探す）。
 *
 * @param orderNumber 注文番号
 * @return 件名、宛先、本文
 */
export async function shipmentMail(orderNumber: string): Promise<{ subject: string; to: string[]; text: string }> {
  let found: { subject: string; to: string[]; text: string } | undefined
  await expect
    .poll(
      async () => {
        const search = await fetch(`${MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(`"${orderNumber}"`)}`)
        const { messages } = (await search.json()) as { messages: { ID: string; Subject: string }[] }
        const message = messages.find((candidate) => candidate.Subject.includes('出荷'))
        if (message === undefined) {
          return undefined
        }
        const detail = (await (await fetch(`${MAILPIT_URL}/api/v1/message/${message.ID}`)).json()) as {
          Subject: string
          To: { Address: string }[]
          Text: string
        }
        found = { subject: detail.Subject, to: detail.To.map((to) => to.Address), text: detail.Text }
        return found
      },
      { message: `注文 ${orderNumber} の出荷通知メールが Mailpit に届かない`, timeout: MAIL_TIMEOUT },
    )
    .toBeDefined()
  return found as { subject: string; to: string[]; text: string }
}
