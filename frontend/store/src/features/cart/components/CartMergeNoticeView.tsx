'use client'

import { Alert } from '@ec-site/shared/ui/alert'
import { useEffect, useState } from 'react'
import { dismissCartMergeNotice } from '@/actions/cart'
import { CartMergeNoticeBody } from '@/features/cart/components/CartMergeNoticeBody'
import type { CartMergeNotice } from '@/lib/cartMergeNotice'

/**
 * カートを移した結果の知らせ。切り詰めた品目と移せなかった品目を示す。
 *
 * 一度だけ示すため、表示したら知らせの Cookie を消す。消すと画面が再作成されて知らせは null で届くので、
 * 最初に受け取った知らせを持ち続ける（画面を移ったり読み込み直したりしたら出ない）。
 *
 * ページを開いたときに表示する知らせなので、`Alert` で包む。
 */
export function CartMergeNoticeView({ notice }: { notice: CartMergeNotice | null }) {
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
      <CartMergeNoticeBody notice={shown} />
    </Alert>
  )
}
