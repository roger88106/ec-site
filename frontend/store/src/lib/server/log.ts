import 'server-only'
import { headers } from 'next/headers'
import { TRACEPARENT_HEADER, traceIdOf } from '@/lib/server/trace'

/** ログのレベル（店舗画面の構成の「サーバーの設定とログ」） */
type Level = 'info' | 'warn' | 'error'

/**
 * 1 行 1 件の JSON のログを書く。
 *
 * info は標準出力、warn・error は標準エラー出力。
 *
 * @param level   レベル
 * @param message メッセージ
 * @param fields  項目
 */
async function write(level: Level, message: string, fields: Record<string, unknown> = {}): Promise<void> {
  const entry = { time: new Date().toISOString(), level, message, traceId: await currentTraceId(), ...fields }
  const stream = level === 'info' ? process.stdout : process.stderr
  stream.write(`${JSON.stringify(entry)}\n`)
}

/**
 * いまのリクエストの追跡の ID（proxy がリクエストのヘッダーに付けた traceparent から）。
 *
 * @returns 追跡の ID。リクエストの外（起動のときなど）や、ヘッダーがなければ null
 */
async function currentTraceId(): Promise<string | null> {
  try {
    return traceIdOf((await headers()).get(TRACEPARENT_HEADER))
  } catch {
    // リクエストの外では headers() が使えない
    return null
  }
}

/**
 * 店舗画面のサーバー側のログ（店舗画面の構成の「サーバーの設定とログ」）。`console.*` の代わりに使う。
 *
 * info：調べるための記録。warn：利用者の操作が失敗したが続けられるもの。error：予期しない失敗。
 */
export const log = {
  info: (message: string, fields?: Record<string, unknown>) => write('info', message, fields),
  warn: (message: string, fields?: Record<string, unknown>) => write('warn', message, fields),
  error: (message: string, fields?: Record<string, unknown>) => write('error', message, fields),
}
