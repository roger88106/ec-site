import { axisValuesLabel } from '@ec-site/shared/lib/format'
import { Badge } from '@ec-site/shared/ui/badge'
import { ImageIcon } from 'lucide-react'
import Link from 'next/link'
import { useTranslations } from 'next-intl'
import type { ReactNode } from 'react'
import type { QuoteItem } from '@/api/types'
import { PriceText } from '@/components/PriceText'
import { unpurchasableReasonLabel } from '@/lib/labels'

/**
 * カート・注文手続きの品目。画像、商品名、軸の値、単価を出し、購入できなければ理由を示す。<br />
 * 数量と小計は、使う画面が children に出す（カートは数量を変えられる）。
 */
export function QuoteItemSummary({ item, children }: { item: QuoteItem; children?: ReactNode }) {
  const t = useTranslations('components.quoteItem')
  const tLabels = useTranslations('labels')
  return (
    <div className="flex gap-4">
      <div className="flex size-20 shrink-0 items-center justify-center overflow-hidden rounded-md bg-muted">
        {item.imageUrl ? (
          // biome-ignore lint/performance/noImgElement: 画像は配信の側（CDN）で最適化する
          <img alt="" className="size-full object-cover" src={item.imageUrl} />
        ) : (
          <ImageIcon aria-hidden className="size-8 text-muted-foreground" />
        )}
      </div>
      <div className="flex-1 space-y-1">
        <Link className="block font-medium hover:underline" href={`/products/${item.productId}`}>
          {item.productName}
        </Link>
        {item.axisValues.length > 0 && (
          <p className="text-muted-foreground text-sm">
            {axisValuesLabel(item.axisValues, tLabels('axisSeparator') /* 軸の区切り */)}
          </p>
        )}
        <PriceText price={item.unitPrice} />
        {!item.purchasable && item.unpurchasableReason && (
          <p className="flex items-center gap-2 text-destructive text-sm">
            <Badge variant="destructive">{t('unpurchasable') /* 購入できない */}</Badge>
            {unpurchasableReasonLabel(item.unpurchasableReason)}
          </p>
        )}
      </div>
      {children}
    </div>
  )
}
