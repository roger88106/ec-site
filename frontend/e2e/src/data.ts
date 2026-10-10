import createClient from 'openapi-fetch'
import type { components, paths } from './generated/admin'
import { BACKEND_URL } from './urls'

/** バリアントの登録の要求の本文 */
type AddVariantRequest = components['schemas']['AddVariantRequest']

/**
 * 実行ごとの印。テストで作る品番・名称に付け、前の実行や手で作ったデータと混ざらないようにする。<br />
 * 品番に使えるよう、英大文字と数字だけにする（例：MG3K2J1A）。
 */
export const RUN_ID = Date.now().toString(36).toUpperCase()

/** 管理 API のクライアント。テストのデータを画面を通さずに作るときに使う */
const api = createClient<paths>({ baseUrl: `${BACKEND_URL}/api/admin` })

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
 * 現時点の率が ratePercent の税区分を返す。local profile の seed の「標準」（10%）「軽減」（8%）などを使う。<br />
 * なければ作り、バックエンドの税率のキャッシュ（有効期間 30 秒。docs/modules/catalog.md）が切れるまで待つ。
 * 作ったばかりの税区分の商品は、キャッシュが切れるまで率がなく、店舗画面に出ないため。
 */
export async function taxCategoryWithRate(ratePercent: number): Promise<TaxCategoryRef> {
  const { items } = unwrap(await api.GET('/tax-categories'))
  const found = items.find((item) => item.currentRatePercent === ratePercent)
  if (found) {
    return { id: found.id, name: found.name }
  }
  const name = `E2E ${ratePercent}% ${RUN_ID}`
  const { id } = unwrap(await api.POST('/tax-categories', { body: { name } }))
  unwrap(
    await api.PUT('/tax-categories/{taxCategoryId}/rates', {
      params: { path: { taxCategoryId: id } },
      body: { version: 0, rates: [{ ratePercent }] },
    }),
  )
  await new Promise((resolve) => setTimeout(resolve, TAX_RATE_CACHE_MILLIS))
  return { id, name }
}

/** バックエンドの税率のキャッシュの有効期間（30 秒）に、余裕を足したもの */
const TAX_RATE_CACHE_MILLIS = 32_000

/** カテゴリの名称に付ける通し番号。同じ実行の中で名称が重ならないようにする */
let categorySequence = 0

/** 最上位のカテゴリを作る。名称には実行ごとの印と通し番号を付ける */
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

/** 実行ごとの印を付けた品番（例：E2E-MG3K2J1A-SHIRT） */
export function productCode(code: string): string {
  return `E2E-${RUN_ID}-${code}`
}

/** 商品を登録し、バリアントを追加して在庫を入荷し、公開する。商品 ID を返す */
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
    unwrap(
      await api.PUT('/products/{productId}/publication-period', {
        params: { path: { productId: id } },
        body: { publishStart: product.publishStart.toISOString(), version: await productVersion(id) },
      }),
    )
  }
  unwrap(await api.PUT('/products/{productId}/publish', { params: { path: { productId: id } } }))
  return id
}

/** 商品の今のバージョン */
async function productVersion(productId: string): Promise<number> {
  return unwrap(await api.GET('/products/{productId}', { params: { path: { productId } } })).version
}

/** 入荷する。在庫はバリアントの登録のイベントで作られるので、在庫ができるのを待ってから調整する */
async function arrive(variantId: string, quantity: number): Promise<void> {
  for (let attempt = 0; attempt < 20; attempt++) {
    const stock = await api.GET('/inventory/variants/{variantId}', { params: { path: { variantId } } })
    if (stock.response.ok) {
      unwrap(
        await api.POST('/inventory/variants/{variantId}/adjustments', {
          params: { path: { variantId } },
          body: { delta: quantity, reason: 'ARRIVAL' },
        }),
      )
      return
    }
    await new Promise((resolve) => setTimeout(resolve, 250))
  }
  throw new Error(`バリアント ${variantId} の在庫が作られない`)
}

/** 非公開にする */
export async function unpublish(productId: string): Promise<void> {
  unwrap(await api.PUT('/products/{productId}/unpublish', { params: { path: { productId } } }))
}

/** 販売終了にする */
export async function discontinue(productId: string): Promise<void> {
  unwrap(await api.PUT('/products/{productId}/discontinue', { params: { path: { productId } } }))
}
