import { expect, type Page, type Response } from '@playwright/test'

/**
 * 条件で待つ時間（テストの書き方 第 5 節）。各テストで数字を書かず、ここの値を使う。
 * 長く待つ必要がある所は、名前を付けた値を追加し、理由を書く。
 */

/** 1 つのテストの時間の上限。Playwright の既定（30 秒）より長い。店舗画面のキャッシュの反映や、支払の通知を開き直しながら待つテストがあるため */
export const TEST_TIMEOUT = 60_000

/** バックエンドの起動を待つ時間の上限。Playwright の既定（60 秒）より長い。Gradle のコンパイルを含めて、初回は 1〜2 分かかるため */
export const BACKEND_START_TIMEOUT = 300_000

/** 画面のサーバー（管理画面、店舗画面）の起動を待つ時間の上限。Playwright の既定（60 秒）より長い。起動の前にビルドをするため */
export const FRONTEND_START_TIMEOUT = 180_000

/** 機能紹介の撮影の 1 つのテストの時間の上限。Playwright の既定（30 秒）より長い。注文と支払、出荷を含め、14 枚を 1 つのテストで撮るため */
export const SHOWCASE_TEST_TIMEOUT = 180_000

/** 裏の処理（支払の通知、イベント、店舗画面のキャッシュの期限）の結果が出るまでに待つ時間の上限 */
export const ASYNC_TIMEOUT = 20_000

/** 開き直しながら待つときの、1 回の確認で表示を待つ時間 */
export const CHECK_TIMEOUT = 1_000

/** 開き直す間隔 */
export const RECHECK_INTERVALS = [500, 1_000]

/** メールが Mailpit に届くまでに待つ時間の上限。イベントの後に SMTP で送るので、裏の処理より長く待つ */
export const MAIL_TIMEOUT = 30_000

/** レポートの写しは注文のイベントから少し遅れて作られる（spec 010 NFR-14）。表示に出るまでに待つ時間の上限 */
const REPORT_TIMEOUT = 30_000

/** 支払の期限切れが注文の画面に出るまでに待つ時間の上限。支払の期限（12 秒。application-e2e.yml）と、期限切れを見る間隔と、裏の処理の分 */
export const PAYMENT_EXPIRY_TIMEOUT = 12_000 + ASYNC_TIMEOUT

/** レポートの画面を開き直す間隔（写しの遅れを待つので、ほかより長い） */
const REPORT_RECHECK_INTERVALS = [1_000, 2_000]

/** レポートの画面を開き直しながら待つときの、待つ時間の上限と開き直す間隔（`reopenUntil` の引数） */
export const REPORT_WAIT = { timeout: REPORT_TIMEOUT, intervals: REPORT_RECHECK_INTERVALS }

/**
 * 画面を開き直しながら、確認が通るまで待つ。裏の処理（支払の通知、イベント、店舗画面のキャッシュの期限）の結果は、
 * 開き直すうちに画面に出る。1 回の確認で表示を待つのは、`CHECK_TIMEOUT` までにする。<br />
 * 店舗画面は API の結果をキャッシュし、期限の切れた後の最初の表示では古い結果を返して裏で取り直す。
 * そのため、管理画面での変更は、開き直すうちに店舗画面に反映される。
 *
 * @param page 使うページ
 * @param url 開く URL（省けば、今の画面を読み直す）
 * @param check 表示を確認する。満たさなければ投げる。開いたときの応答（HTTP の状態など）を受け取る
 * @param options 待つ時間の上限と、開き直す間隔
 */
export async function reopenUntil(
  page: Page,
  url: string | undefined,
  check: (response: Response | null) => Promise<void>,
  { timeout = ASYNC_TIMEOUT, intervals = RECHECK_INTERVALS }: { timeout?: number; intervals?: number[] } = {},
): Promise<void> {
  await expect(async () => {
    const response = url === undefined ? await page.reload() : await page.goto(url)
    await check(response)
  }).toPass({ timeout, intervals })
}
