import { useTranslations } from 'next-intl'
import { CartMergeNoticeBody } from '@/features/cart/components/CartMergeNoticeBody'
import type { CartMergeRetryResult } from '@/lib/cartMergeRetryResult'

/**
 * カートの画面での、カートを移す処理の再試行の表示（状態を持たない）。<br />
 * 文がなくても、`status` の役割を持つ領域を常に置く。後から追加された領域は読み上げられない支援技術があるため、
 * 領域を先に画面に置き、再試行している間と結果の文だけを、その中に入れる（結果は表示の後に画面へ反映されるので、`Alert` は使わない）。
 * 文のない領域は画面から隠す（`sr-only`）。
 *
 * @param state retrying（再試行している間）か、再試行の結果
 */
export function CartMergeRetryView({ state }: { state: 'retrying' | CartMergeRetryResult }) {
  const t = useTranslations('cart.merge')
  return (
    <div className="space-y-3 text-sm empty:sr-only" role="status">
      {state === 'retrying' && <p className="text-muted-foreground">{t('retrying') /* 移している */}</p>}
      {state !== 'retrying' && state.kind === 'failed' && (
        <p className="text-destructive">{t('failed') /* 移せなかった */}</p>
      )}
      {state !== 'retrying' && state.kind === 'merged' && state.notice !== null && (
        <CartMergeNoticeBody notice={state.notice} />
      )}
    </div>
  )
}
