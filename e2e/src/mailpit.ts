import { expect } from '@playwright/test'
import { MAILPIT_URL } from './urls'
import { MAIL_TIMEOUT } from './waits'

/** Mailpit に届いたメール */
export type Mail = {
  /** 件名 */
  subject: string
  /** 宛先のメールアドレス */
  to: string[]
  /** 本文（テキスト） */
  text: string
}

/** メールを探す条件 */
export type MailQuery = {
  /** Mailpit の検索の式（`to:宛先`、`"注文番号"` など） */
  search: string
  /** 件名に含まれる文字（省けば絞り込まない） */
  subject?: string
  /** 検索の結果の最新のメールのうち、この条件を満たすものだけを返す（満たさなければ待ち続ける。省けば絞り込まない） */
  accept?: (mail: Mail) => boolean
  /** 届かなかったときの失敗の説明 */
  message: string
}

/**
 * Mailpit の API を呼び、JSON の応答を返す。失敗した応答（HTTP の成功でないもの）はエラーにする。
 *
 * @param path API のパス（`/api/v1/…`）
 * @returns 応答の JSON
 */
async function mailpitJson<T>(path: string): Promise<T> {
  const response = await fetch(`${MAILPIT_URL}${path}`)
  if (!response.ok) {
    throw new Error(`Mailpit の ${path} が失敗した（HTTP ${response.status}）`)
  }
  return (await response.json()) as T
}

/**
 * Mailpit に条件のメールが届くまで待ち、最新のものを返す。メールは消さないので、
 * 前の実行のメールと混ざらないよう、実行ごとに違う宛先や注文番号で探す。
 *
 * @param query 探す条件
 * @returns 見つけたメール
 */
export async function waitForMail(query: MailQuery): Promise<Mail> {
  let found: Mail | undefined
  await expect
    .poll(
      async () => {
        const { messages } = await mailpitJson<{ messages: { ID: string; Subject: string }[] }>(
          `/api/v1/search?query=${encodeURIComponent(query.search)}`,
        )
        const summary = messages.find(
          (candidate) => query.subject === undefined || candidate.Subject.includes(query.subject),
        )
        if (summary === undefined) {
          return undefined
        }
        const detail = await mailpitJson<{ Subject: string; To: { Address: string }[]; Text: string }>(
          `/api/v1/message/${summary.ID}`,
        )
        const mail = { subject: detail.Subject, to: detail.To.map((to) => to.Address), text: detail.Text }
        found = query.accept === undefined || query.accept(mail) ? mail : undefined
        return found
      },
      { message: query.message, timeout: MAIL_TIMEOUT },
    )
    .toBeDefined()
  return found as Mail
}
