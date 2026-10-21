import { axisValuesLabel } from '@ec-site/shared/lib/format'
import { translate } from '@/i18n/translate'

/** 知らせる品目の数の上限（Cookie の大きさを抑えるため。残りは数だけ示す） */
const MAX_ITEMS = 10

/** 知らせる品目 */
type NoticeItem = { label: string; quantity: number }

/** 知らせる品目の並び。more は上限を超えて省いた品目の数 */
type NoticeItems = { items: NoticeItem[]; more: number }

/** カートを移した結果の知らせ。カートの画面で一度だけ示す */
export type CartMergeNotice = {
  /** 数量を上限に切り詰めた品目（切り詰めた後の数量） */
  truncated: NoticeItems
  /** 品目の上限を超えたので移さなかった品目（移す元の数量） */
  skipped: NoticeItems
}

/** 移した品目（MergedCartItem） */
type MergedItem = { variantId: string; quantity: number }

/** 移す前のカートの品目（商品名とバリアントを引く） */
type SourceItem = { variantId: string; productName: string; axisValues: { name: string; value: string }[] }

/**
 * カートを移した結果（POST /me/cart/merge）から、知らせを作る。
 *
 * @param result      移した結果
 * @param sourceItems 移す前のカートの品目
 * @returns 切り詰めた品目も移せなかった品目もなければ null
 */
export function cartMergeNotice(
  result: { truncatedItems: MergedItem[]; skippedItems: MergedItem[] },
  sourceItems: SourceItem[],
): CartMergeNotice | null {
  if (result.truncatedItems.length === 0 && result.skippedItems.length === 0) {
    return null
  }
  const labels = new Map(sourceItems.map((item) => [item.variantId, itemLabel(item)]))
  const toNoticeItems = (items: MergedItem[]): NoticeItems => ({
    items: items.slice(0, MAX_ITEMS).map((item) => ({
      label: labels.get(item.variantId) ?? translate('cart.merge.unknownProduct'), // 商品名が分からなければ「商品」
      quantity: item.quantity,
    })),
    more: Math.max(items.length - MAX_ITEMS, 0),
  })
  return { truncated: toNoticeItems(result.truncatedItems), skipped: toNoticeItems(result.skippedItems) }
}

/** 知らせを Cookie の値にする */
export function serializeCartMergeNotice(notice: CartMergeNotice): string {
  return Buffer.from(JSON.stringify(notice)).toString('base64url')
}

/**
 * Cookie の値を知らせにする。
 *
 * @returns Cookie がない・形の違う値なら null
 */
export function parseCartMergeNotice(value: string | undefined): CartMergeNotice | null {
  if (value === undefined) {
    return null
  }
  try {
    const notice = JSON.parse(Buffer.from(value, 'base64url').toString())
    return isNoticeItems(notice?.truncated) && isNoticeItems(notice?.skipped) ? notice : null
  } catch {
    return null
  }
}

// ============================== プライベート ==============================

/** 品目の名前（「商品名（軸：値）」） */
function itemLabel(item: SourceItem): string {
  return item.axisValues.length === 0
    ? item.productName
    : translate('cart.merge.itemLabel', {
        name: item.productName,
        axes: axisValuesLabel(item.axisValues, translate('labels.axisSeparator')),
      })
}

/** 知らせる品目の並びの形か */
function isNoticeItems(value: unknown): value is NoticeItems {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const { items, more } = value as Partial<NoticeItems>
  return (
    Array.isArray(items) &&
    items.every((item) => typeof item?.label === 'string' && typeof item?.quantity === 'number') &&
    typeof more === 'number'
  )
}
