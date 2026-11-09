import { Alert, AlertDescription, AlertTitle } from '@ec-site/shared/ui/alert'
import { useTranslations } from 'next-intl'
import { schemas } from '@/api/generated/constraints'
import type { CartMergeNotice } from '@/lib/cartMergeNotice'

/**
 * カートを移した結果の知らせの中身。切り詰めた品目と移せなかった品目を示す（状態を持たない）。<br />
 * ログインの直後の知らせ（CartMergeNoticeView）と、カートの画面での再試行の結果（CartMergeRetryView）が使う。
 */
export function CartMergeNoticeBody({ notice }: { notice: CartMergeNotice }) {
  const t = useTranslations('cart.merge')
  return (
    <Alert>
      <AlertTitle>{t('title') /* 移した知らせ */}</AlertTitle>
      <AlertDescription className="space-y-3">
        {(notice.truncated.items.length > 0 || notice.truncated.more > 0) && (
          <div>
            <p>{t('truncated', { max: schemas.Quantity.maximum }) /* 数量を減らした */}</p>
            <NoticeList items={notice.truncated.items} more={notice.truncated.more} />
          </div>
        )}
        {(notice.skipped.items.length > 0 || notice.skipped.more > 0) && (
          <div>
            <p>{t('skipped') /* 移せなかった */}</p>
            <NoticeList items={notice.skipped.items} more={notice.skipped.more} />
          </div>
        )}
      </AlertDescription>
    </Alert>
  )
}

/** 知らせる品目の並び（省いた品目は数だけ） */
function NoticeList({ items, more }: CartMergeNotice['truncated']) {
  const t = useTranslations('cart.merge')
  return (
    <ul className="list-disc pl-5">
      {items.map((item, index) => (
        // 同じ商品名・数量の品目もありうるので、並びの位置も鍵にする
        // biome-ignore lint/suspicious/noArrayIndexKey: 並びは変わらない
        <li key={`${index}-${item.label}`}>
          {t('item', { label: item.label, quantity: item.quantity }) /* 品目と数量 */}
        </li>
      ))}
      {more > 0 && <li>{t('more', { more }) /* 省いた数 */}</li>}
    </ul>
  )
}
