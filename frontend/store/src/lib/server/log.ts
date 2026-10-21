import 'server-only'
import { headers } from 'next/headers'
import { TRACEPARENT_HEADER, traceIdOf } from '@/lib/server/trace'

/** ログのレベル（フロントの書き方 第 8.2 節） */
type Level = 'info' | 'warn' | 'error'

/**
 * 1 行 1 件の JSON のログを書く。info は標準出力、warn・error は標準エラー出力。
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
 * いまの要求の追跡の ID（proxy が要求のヘッダーに付けた traceparent から）。
 *
 * @returns 追跡の ID。要求の外（起動のときなど）や、ヘッダーがなければ null
 */
async function currentTraceId(): Promise<string | null> {
  try {
    return traceIdOf((await headers()).get(TRACEPARENT_HEADER))
  } catch {
    // 要求の外では headers() が使えない
    return null
  }
}

/**
 * 店舗画面のサーバー側のログ（フロントの書き方 第 8.2 節）。`console.*` の代わりに使う。<br />
 * info：調べるための記録。warn：利用者の操作が失敗したが続けられるもの。error：予期しない失敗。
 */
export const log = {
  info: (message: string, fields?: Record<string, unknown>) => write('info', message, fields),
  warn: (message: string, fields?: Record<string, unknown>) => write('warn', message, fields),
  error: (message: string, fields?: Record<string, unknown>) => write('error', message, fields),
}
