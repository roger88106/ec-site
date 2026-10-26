import { expect } from '@playwright/test'
import createClient from 'openapi-fetch'
import { ACCOUNTS, operatorAccessToken } from './auth'
import type { components, paths } from './generated/admin'
import { BACKEND_URL } from './urls'
import { ASYNC_TIMEOUT } from './waits'

/** バリアントの登録の要求の本文 */
type AddVariantRequest = components['schemas']['AddVariantRequest']

/**
 * 実行ごとの印。テストで作る品番・名称に付け、前の実行や手で作ったデータと混ざらないようにする。<br />
 * 品番に使えるよう、英大文字と数字だけにする（例：MG3K2J1A）。
 */
export const RUN_ID = Date.now().toString(36).toUpperCase()

/** 管理 API のクライアント。テストのデータを画面を通さずに作るときに使う。管理者の token を付ける */
const api = createClient<paths>({ baseUrl: `${BACKEND_URL}/api/admin` })
api.use({
  async onRequest({ request }) {
    request.headers.set('Authorization', `Bearer ${await administratorToken()}`)
    return request
  },
})

/** 管理者の access token と、取り直す時刻（有効期間 5 分より前に取り直す） */
let cachedToken: { value: string; refreshAt: number } | null = null

/** 管理者の access token（テストのデータを作るため。src/auth.ts） */
async function administratorToken(): Promise<string> {
  if (cachedToken === null || Date.now() >= cachedToken.refreshAt) {
    cachedToken = { value: await operatorAccessToken(ACCOUNTS.administrator), refreshAt: Date.now() + 4 * 60_000 }
  }
  return cachedToken.value
}

/** API の結果を取り出す。エラーなら、応答の本文を添えて投げる */
function unwrap<T>(result: { data?: T; error?: unknown; response: Response }): T {
  if (result.error !== undefined || !result.response.ok) {
    throw new Error(
      `${result.response.url} が ${result.response.status} を返した：${JSON.stringify(result.error ?? null)}`,
    )
  }
  return result.data as T
}

/** 税区分（管理画面の選択肢の表示に名称を使う） */
export type TaxCategoryRef = { id: string; name: string }

/**
 * 現時点の率が ratePercent の税区分を返す。E2E の DB の seed（db/seed/base）の「標準」（10%）「軽減」（8%）を使う。<br />
 * 作らずに、なければ失敗する。作ったばかりの税区分は、バックエンドの税率のキャッシュ（有効期間 30 秒）が切れるまで
 * 率がなく、時間で待つことになるため。
 *
 * @param ratePercent 現時点の率（%）
 * @returns 税区分の ID と名称
 */
export async function taxCategoryWithRate(ratePercent: number): Promise<TaxCategoryRef> {
  const { items } = unwrap(await api.GET('/tax-categories'))
  const found = items.find((item) => item.currentRatePercent === ratePercent)
  if (!found) {
    throw new Error(`現時点の率が ${ratePercent}% の税区分がない（E2E の DB に db/seed/base が入っているか確認する）`)
  }
  return { id: found.id, name: found.name }
}

/** カテゴリの名称に付ける通し番号。同じ実行の中で名称が重ならないようにする */
let categorySequence = 0

/**
 * 最上位のカテゴリを作る。名称には実行ごとの印と通し番号を付ける。
 *
 * @param name 名称（印と通し番号を後ろに付ける）
 * @returns カテゴリの ID と、印を付けた名称
 */
export async function createCategory(name: string): Promise<{ id: string; name: string }> {
  categorySequence += 1
  const unique = `${name} ${RUN_ID}-${categorySequence}`
  const { id } = unwrap(await api.POST('/categories', { body: { name: unique } }))
  return { id, name: unique }
}

/** 公開した商品の作り方 */
export type PublishedProduct = {
  /** 品番の後ろの部分。前に「E2E-<実行ごとの印>-」を付ける */
  code: string
  name: string
  taxCategoryId: string
  priceExclTax: number
  categoryIds?: string[]
  axes?: string[]
  /** バリアント。code は品番の後ろの部分で、商品品番の後ろに付ける */
  variants: { code: string; axisValues?: string[]; stock: number }[]
  /** 公開開始日時（公開の予約。AC-34）。省略すると、公開した時刻から表示される */
  publishStart?: Date
}

/**
 * 実行ごとの印を付けた品番（例：E2E-MG3K2J1A-SHIRT）。
 *
 * @param code 品番の後ろの部分
 * @returns 印を付けた商品品番
 */
export function productCode(code: string): string {
  return `E2E-${RUN_ID}-${code}`
}

/**
 * 商品を登録し、バリアントを追加して在庫を入荷し、公開する。
 *
 * @param product 商品の作り方
 * @returns 商品 ID
 */
export async function createPublishedProduct(product: PublishedProduct): Promise<string> {
  const code = productCode(product.code)
  const { id } = unwrap(
    await api.POST('/products', {
      body: {
        productCode: code,
        name: product.name,
        description: '',
        taxCategoryId: product.taxCategoryId,
        priceExclTax: product.priceExclTax,
        categoryIds: product.categoryIds ?? [],
        axes: product.axes ?? [],
      },
    }),
  )
  for (const variant of product.variants) {
    const body: AddVariantRequest = {
      variantCode: `${code}-${variant.code}`,
      axisValues: variant.axisValues ?? [],
      version: await productVersion(id),
    }
    const created = unwrap(
      await api.POST('/products/{productId}/variants', { params: { path: { productId: id } }, body }),
    )
    if (variant.stock > 0) {
      await arrive(created.id, variant.stock)
    }
  }
  if (product.publishStart) {
    await changePublishStart(id, product.publishStart)
  }
  unwrap(await api.PUT('/products/{productId}/publish', { params: { path: { productId: id } } }))
  return id
}

/**
 * 公開開始日時を変える（公開した後でも変えられる）。
 *
 * @param productId 商品 ID
 * @param publishStart 新しい公開開始日時
 */
export async function changePublishStart(productId: string, publishStart: Date): Promise<void> {
  unwrap(
    await api.PUT('/products/{productId}/publication-period', {
      params: { path: { productId } },
      body: { publishStart: publishStart.toISOString(), version: await productVersion(productId) },
    }),
  )
}

/** 商品の今のバージョン */
async function productVersion(productId: string): Promise<number> {
  return unwrap(await api.GET('/products/{productId}', { params: { path: { productId } } })).version
}

/** 入荷する。在庫はバリアントの登録のイベントで作られるので、在庫ができるのを待ってから調整する */
async function arrive(variantId: string, quantity: number): Promise<void> {
  await expect
    .poll(
      async () => (await api.GET('/inventory/variants/{variantId}', { params: { path: { variantId } } })).response.ok,
      { message: `バリアント ${variantId} の在庫が作られない`, timeout: ASYNC_TIMEOUT },
    )
    .toBe(true)
  unwrap(
    await api.POST('/inventory/variants/{variantId}/adjustments', {
      params: { path: { variantId } },
      body: { delta: quantity, reason: 'ARRIVAL' },
    }),
  )
}

/**
 * 非公開にする。
 *
 * @param productId 商品 ID
 */
export async function unpublish(productId: string): Promise<void> {
  unwrap(await api.PUT('/products/{productId}/unpublish', { params: { path: { productId } } }))
}

/**
 * 販売終了にする。
 *
 * @param productId 商品 ID
 */
export async function discontinue(productId: string): Promise<void> {
  unwrap(await api.PUT('/products/{productId}/discontinue', { params: { path: { productId } } }))
}

/**
 * 商品のバリアントの ID（登録した順）。
 *
 * @param productId 商品 ID
 * @returns バリアント ID の並び
 */
export async function variantIds(productId: string): Promise<string[]> {
  const product = unwrap(await api.GET('/products/{productId}', { params: { path: { productId } } }))
  return product.variants.map((variant) => variant.id)
}
