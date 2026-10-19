import { Card, CardContent, CardHeader, CardTitle } from '@ec-site/shared/ui/card'
import { useTranslations } from 'use-intl'
import type { Cancellation, CancellationKind, CancellationRequest } from '@/api/types'
import { DateTime } from '@/components/DateTime'
import { cancellationRequestResultLabel } from '@/features/order/order'

/** キャンセルのきっかけの文言のキー */
const TRIGGERS = {
  OPERATOR: 'byOperator',
  REQUEST_APPROVED: 'byRequest',
  PAYMENT_EXPIRED: 'byPaymentExpiry',
} as const satisfies Record<CancellationKind, string>

/**
 * キャンセルの申請と、キャンセルの内容。どちらもなければ「ありません」と出す。
 */
export function CancellationCard({
  request,
  cancellation,
}: {
  request: CancellationRequest | undefined
  cancellation: Cancellation | undefined
}) {
  const t = useTranslations('order.cancellation')
  const tApp = useTranslations('app.common')
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('title') /* キャンセル */}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4 text-sm">
        {!request && !cancellation && <p className="text-muted-foreground">{t('none') /* 申請なし */}</p>}
        {request && (
          <section className="space-y-2">
            <h3 className="font-medium">{t('request') /* お客様からの申請 */}</h3>
            <dl className="grid grid-cols-[7rem_1fr] gap-y-1">
              <dt className="text-muted-foreground">{t('requestedAt') /* 申請日時 */}</dt>
              <dd>
                <DateTime value={request.requestedAt} />
              </dd>
              <dt className="text-muted-foreground">{t('reason') /* 理由 */}</dt>
              <dd className="whitespace-pre-wrap">
                {request.reason || tApp('noneParenthesized') /* 理由がなければ（なし） */}
              </dd>
              <dt className="text-muted-foreground">{t('result') /* 結果 */}</dt>
              <dd>
                {cancellationRequestResultLabel(request.result)}
                {request.decidedAt && (
                  <span className="ml-2 text-muted-foreground">
                    {t.rich('decidedAt', { date: () => <DateTime value={request.decidedAt} /> }) /* 判断の日時 */}
                  </span>
                )}
              </dd>
              {request.decidedBy && (
                <>
                  <dt className="text-muted-foreground">{t('decidedBy') /* 判断した運営者 */}</dt>
                  <dd>{request.decidedBy}</dd>
                </>
              )}
            </dl>
          </section>
        )}
        {cancellation && (
          <section className="space-y-2">
            <h3 className="font-medium">{t('title') /* キャンセル */}</h3>
            <dl className="grid grid-cols-[7rem_1fr] gap-y-1">
              <dt className="text-muted-foreground">{t('cancelledAt') /* 日時 */}</dt>
              <dd>
                <DateTime value={cancellation.cancelledAt} />
              </dd>
              <dt className="text-muted-foreground">{t('trigger') /* きっかけ */}</dt>
              <dd>{t(TRIGGERS[cancellation.kind]) /* 運営者の判断、申請の承認、支払期限切れ */}</dd>
              {cancellation.kind === 'OPERATOR' && (
                <>
                  <dt className="text-muted-foreground">{t('reason') /* 理由 */}</dt>
                  <dd className="whitespace-pre-wrap">{cancellation.reason}</dd>
                </>
              )}
              {cancellation.cancelledBy && (
                <>
                  <dt className="text-muted-foreground">{t('cancelledBy') /* キャンセルした運営者 */}</dt>
                  <dd>{cancellation.cancelledBy}</dd>
                </>
              )}
            </dl>
          </section>
        )}
      </CardContent>
    </Card>
  )
}
