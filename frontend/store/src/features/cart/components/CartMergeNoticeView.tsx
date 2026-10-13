'use client'

import { Alert, AlertDescription, AlertTitle } from '@ec-site/shared/ui/alert'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'
import { dismissCartMergeNotice } from '@/actions/cart'
import type { CartMergeNotice } from '@/lib/cartMergeNotice'

/**
 * カートを移した結果の知らせ。切り詰めた品目と移せなかった品目を示す。<br />
 * 一度だけ示すため、表示したら知らせの Cookie を消す。消すと画面が作り直されて知らせは null で届くので、
 * 最初に受け取った知らせを持ち続ける（画面を移ったり読み込み直したりしたら出ない）。
 */
export function CartMergeNoticeView({ notice }: { notice: CartMergeNotice | null }) {
  const t = useTranslations('cart.merge')
  // 知らせは一度だけ出す。表示は最初に受けた値で続け、Cookie の知らせは描画の後に消す
  const [shown] = useState(notice)
  useEffect(() => {
    if (notice !== null) {
      void dismissCartMergeNotice()
    }
  }, [notice])

  if (shown === null) {
    return null
  }
  return (
    <Alert>
      <AlertTitle>{t('title') /* 移した知らせ */}</AlertTitle>
      <AlertDescription className="space-y-3">
        {(shown.truncated.items.length > 0 || shown.truncated.more > 0) && (
          <div>
            <p>{t('truncated') /* 数量を減らした */}</p>
            <NoticeList items={shown.truncated.items} more={shown.truncated.more} />
          </div>
        )}
        {(shown.skipped.items.length > 0 || shown.skipped.more > 0) && (
          <div>
            <p>{t('skipped') /* 移せなかった */}</p>
            <NoticeList items={shown.skipped.items} more={shown.skipped.more} />
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
