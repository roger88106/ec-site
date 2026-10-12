import { expect } from 'vitest'

/**
 * サーバーの処理のテストで、Next.js の要求の口（next/headers、next/cache）の代わりに使う入れ物（店舗画面の構成 第 7 節）。<br />
 * setup.ts が next/headers・next/cache をこれに置き換える。next/navigation（redirect、notFound、unstable_rethrow）は
 * 置き換えず、本物を使う（redirect が例外で移動を知らせ、unstable_rethrow がそれを通すことまで確かめるため）。
 */

/** Cookie に書いた値と属性 */
type StoredCookie = { value: string; options?: Record<string, unknown> }

/** 要求の Cookie（サーバーアクションが書いた値も、ここに残る） */
const cookieJar = new Map<string, StoredCookie>()

/** 要求のヘッダー */
let requestHeaders = new Headers()

/** revalidatePath で表示し直しを知らせたパス */
export const revalidatedPaths: string[] = []

/** next/headers の cookies() が返す入れ物。Next.js の Cookie の口のうち、店舗画面が使う分だけを持つ */
export const cookieStore = {
  get: (name: string) => {
    const cookie = cookieJar.get(name)
    return cookie === undefined ? undefined : { name, value: cookie.value }
  },
  has: (name: string) => cookieJar.has(name),
  set: (name: string, value: string, options?: Record<string, unknown>) => {
    cookieJar.set(name, { value, options })
  },
  delete: (name: string) => {
    cookieJar.delete(name)
  },
}

/** next/headers の headers() が返すヘッダー */
export function currentHeaders(): Headers {
  return requestHeaders
}

/**
 * 要求の状態を作り直す（各テストの前に setup.ts が呼ぶ）。
 *
 * @param init.cookies Cookie（名前 → 値）
 * @param init.headers ヘッダー
 */
export function resetRequest(init: { cookies?: Record<string, string>; headers?: Record<string, string> } = {}) {
  cookieJar.clear()
  for (const [name, value] of Object.entries(init.cookies ?? {})) {
    cookieJar.set(name, { value })
  }
  requestHeaders = new Headers(init.headers)
  revalidatedPaths.length = 0
}

/**
 * Cookie の値（テストで確かめる）。
 *
 * @param name Cookie の名前
 * @returns 値。なければ undefined
 */
export function cookieValue(name: string): string | undefined {
  return cookieJar.get(name)?.value
}

/**
 * Cookie の属性（テストで確かめる）。
 *
 * @param name Cookie の名前
 */
export function cookieOptions(name: string): Record<string, unknown> | undefined {
  return cookieJar.get(name)?.options
}

/**
 * 処理が redirect したことを確かめ、移動先を返す。<br />
 * Next.js の redirect は、移動先を digest（`NEXT_REDIRECT;<種類>;<URL>;<状態>;`）に持つ例外を投げる。
 *
 * @param run 処理
 * @returns 移動先の URL
 */
export async function redirectedTo(run: () => Promise<unknown>): Promise<string> {
  const error = await run().then(
    () => undefined,
    (thrown: unknown) => thrown,
  )
  const digest = (error as { digest?: unknown } | undefined)?.digest
  expect(
    typeof digest === 'string' && digest.startsWith('NEXT_REDIRECT;'),
    `redirect していない：${String(error)}`,
  ).toBe(true)
  return (digest as string).split(';').slice(2, -2).join(';')
}
