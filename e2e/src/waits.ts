import { expect, type Page } from '@playwright/test'

/**
 * 条件で待つ時間（テストの書き方 第 5 節）。各テストで数字を書かず、ここの値を使う。
 * 長く待つ必要がある所は、名前を付けた値を追加し、理由を書く。
 */

/** 裏の処理（支払の通知、イベント、店舗画面のキャッシュの期限）の結果が出るまでに待つ時間の上限 */
export const ASYNC_TIMEOUT = 20_000

/** 開き直しながら待つときの、1 回の確認で表示を待つ時間 */
export const CHECK_TIMEOUT = 1_000

/** 開き直す間隔 */
export const RECHECK_INTERVALS = [500, 1_000]

/** メールが Mailpit に届くまでに待つ時間の上限。イベントの後に SMTP で送るので、裏の処理より長く待つ */
export const MAIL_TIMEOUT = 30_000

/** レポートの写しは注文のイベントから少し遅れて作られる（spec 010 NFR-14）。表示に出るまでに待つ時間の上限 */
export const REPORT_TIMEOUT = 30_000

/** 支払の期限切れが注文の画面に出るまでに待つ時間の上限。支払の期限（12 秒。application-e2e.yml）と、期限切れを見る間隔と、裏の処理の分 */
export const PAYMENT_EXPIRY_TIMEOUT = 12_000 + ASYNC_TIMEOUT

/** レポートの画面を開き直す間隔（写しの遅れを待つので、ほかより長い） */
export const REPORT_RECHECK_INTERVALS = [1_000, 2_000]

/**
 * 画面を開き直しながら、確認が通るまで待つ。裏の処理（支払の通知、イベント、店舗画面のキャッシュの期限）の結果は、
 * 開き直すうちに画面に出る。1 回の確認で表示を待つのは、`CHECK_TIMEOUT` までにする。
 *
 * @param page 使うページ
 * @param url 開く URL（省けば、今の画面を読み直す）
 * @param check 表示を確認する。満たさなければ投げる
 * @param options 待つ時間の上限と、開き直す間隔
 */
export async function reopenUntil(
  page: Page,
  url: string | undefined,
  check: () => Promise<void>,
  { timeout = ASYNC_TIMEOUT, intervals = RECHECK_INTERVALS }: { timeout?: number; intervals?: number[] } = {},
): Promise<void> {
  await expect(async () => {
    if (url === undefined) {
      await page.reload()
    } else {
      await page.goto(url)
    }
    await check()
  }).toPass({ timeout, intervals })
}
