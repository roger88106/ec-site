'use client'

import { useEffect, useRef, useState } from 'react'
import { retryCartMerge } from '@/actions/cart'
import { CartMergeRetryView } from '@/features/cart/components/CartMergeRetryView'
import type { CartMergeRetryResult } from '@/lib/cartMergeRetryResult'

/**
 * カートの画面での、カートを移す処理の再試行
 *
 * 最初に受けた `pending` が true なら、表示の後にサーバーアクションを 1 回だけ呼び、結果を示す。
 * 成功するとサーバーアクションがカートの画面を再表示し、Cookie のカート ID がなくなって `pending` は false で届くので、
 * 最初の値を持ち続けて結果の表示を続ける。
 */
export function CartMergeRetry({
  pending,
}: {
  /** 再試行が必要か（ログインしていて Cookie のカート ID がある） */
  pending: boolean
}) {
  const [initiallyPending] = useState(pending)
  const [result, setResult] = useState<CartMergeRetryResult | null>(null)
  // Strict Mode の二重実行でも 1 回だけ呼ぶ
  const started = useRef(false)
  useEffect(() => {
    if (!initiallyPending || started.current) {
      return
    }
    started.current = true
    // 呼び出しが拒否されても、移せなかったことを示す（画面を「移しています」のまま止めない）
    retryCartMerge()
      .catch((): CartMergeRetryResult => ({ kind: 'failed' }))
      .then(setResult)
  }, [initiallyPending])

  if (!initiallyPending) {
    return null
  }
  return <CartMergeRetryView state={result ?? 'retrying'} />
}
