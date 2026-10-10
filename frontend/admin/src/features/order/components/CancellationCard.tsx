import type { Cancellation, CancellationRequest } from '@/api/types'
import { DateTime } from '@/components/DateTime'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { cancellationRequestResultLabel } from '@/features/order/order'

/**
 * キャンセルの申請と、キャンセルの内容（AC-100）。どちらもなければ「ありません」と出す。
 */
export function CancellationCard({
  request,
  cancellation,
}: {
  request: CancellationRequest | undefined
  cancellation: Cancellation | undefined
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>キャンセル</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4 text-sm">
        {!request && !cancellation && <p className="text-muted-foreground">キャンセルの申請はありません。</p>}
        {request && (
          <section className="space-y-2">
            <h3 className="font-medium">お客様からの申請</h3>
            <dl className="grid grid-cols-[7rem_1fr] gap-y-1">
              <dt className="text-muted-foreground">申請日時</dt>
              <dd>
                <DateTime value={request.requestedAt} />
              </dd>
              <dt className="text-muted-foreground">理由</dt>
              <dd className="whitespace-pre-wrap">{request.reason || '（なし）'}</dd>
              <dt className="text-muted-foreground">結果</dt>
              <dd>
                {cancellationRequestResultLabel(request.result)}
                {request.decidedAt && (
                  <span className="ml-2 text-muted-foreground">
                    （<DateTime value={request.decidedAt} />）
                  </span>
                )}
              </dd>
            </dl>
          </section>
        )}
        {cancellation && (
          <section className="space-y-2">
            <h3 className="font-medium">キャンセル</h3>
            <dl className="grid grid-cols-[7rem_1fr] gap-y-1">
              <dt className="text-muted-foreground">日時</dt>
              <dd>
                <DateTime value={cancellation.cancelledAt} />
              </dd>
              <dt className="text-muted-foreground">きっかけ</dt>
              <dd>{cancellation.byOperator ? '運営者の判断' : 'お客様の申請の承認'}</dd>
              {cancellation.byOperator && (
                <>
                  <dt className="text-muted-foreground">理由</dt>
                  <dd className="whitespace-pre-wrap">{cancellation.reason}</dd>
                </>
              )}
            </dl>
          </section>
        )}
      </CardContent>
    </Card>
  )
}
