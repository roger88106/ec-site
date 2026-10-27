/** 追跡の ID を運ぶ要求のヘッダー（W3C Trace Context） */
export const TRACEPARENT_HEADER = 'traceparent'

/** traceparent の形（バージョン 00、32 桁の追跡の ID、16 桁の区間の ID、印） */
const TRACEPARENT = /^00-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$/

/**
 * 新しい traceparent を作る（サンプリングする印を付ける）。
 *
 * @returns traceparent
 */
export function newTraceparent(): string {
  return `00-${randomHex(16)}-${randomHex(8)}-01`
}

/**
 * traceparent から追跡の ID を取り出す。
 *
 * @param traceparent traceparent
 * @returns 追跡の ID。形が違えば null
 */
export function traceIdOf(traceparent: string | null | undefined): string | null {
  return TRACEPARENT.exec(traceparent ?? '')?.[1] ?? null
}

/**
 * 乱数を 16 進の文字にする。
 *
 * @param bytes バイト数
 * @returns 16 進の文字（バイト数の 2 倍の長さ）
 */
function randomHex(bytes: number): string {
  return Array.from(crypto.getRandomValues(new Uint8Array(bytes)), (byte) => byte.toString(16).padStart(2, '0')).join(
    '',
  )
}
