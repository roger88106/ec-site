import { Alert, AlertDescription } from '@ec-site/shared/ui/alert'
import { useTranslations } from 'next-intl'
import { CartMergeNoticeBody } from '@/features/cart/components/CartMergeNoticeBody'
import type { CartMergeRetryResult } from '@/lib/cartMergeRetryResult'

/**
 * カートの画面での、カートを移す処理の再試行の表示（状態を持たない）。<br />
 * 状態の知らせ（`status` の役割）の領域として、文がなくても常に描く。後から描かれた領域は読み上げられない支援技術があるため、
 * 領域を先に画面に置き、再試行している間だけ文を入れる。文のない領域は画面から隠す（`sr-only`）。
 *
 * @param state retrying（再試行している間）か、再試行の結果
 */
export function CartMergeRetryView({ state }: { state: 'retrying' | CartMergeRetryResult }) {
  const t = useTranslations('cart.merge')
  return (
    <>
      <p className="text-muted-foreground text-sm empty:sr-only" role="status">
        {state === 'retrying' && t('retrying') /* 移している */}
      </p>
      {state !== 'retrying' && state.kind === 'failed' && (
        <Alert>
          <AlertDescription>{t('failed') /* 移せなかった */}</AlertDescription>
        </Alert>
      )}
      {state !== 'retrying' && state.kind === 'merged' && state.notice !== null && (
        <CartMergeNoticeBody notice={state.notice} />
      )}
    </>
  )
}
