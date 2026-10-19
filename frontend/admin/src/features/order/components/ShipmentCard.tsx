import { Button } from '@ec-site/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@ec-site/shared/ui/card'
import { Input } from '@ec-site/shared/ui/input'
import { Controller, useForm } from 'react-hook-form'
import { useTranslations } from 'use-intl'
import { setServerFieldErrors } from '@/api/formErrors'
import { carrierValues } from '@/api/generated/admin'
import { schemas } from '@/api/generated/constraints'
import type { Carrier, OrderDetail, Shipment } from '@/api/types'
import { useApiMutation } from '@/api/useApiMutation'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { DateTime } from '@/components/DateTime'
import { ErrorMessage } from '@/components/ErrorMessage'
import { OptionSelect } from '@/components/OptionSelect'
import { Field, FieldError, FieldLabel } from '@/components/ui/field'
import { carrierLabel, shipmentStatusLabel } from '@/lib/labels'

/** 入力欄の値 */
type Values = { carrier: Carrier | null; trackingNumber: string }

/** サーバーの入力検証のエラーを、項目の下に出す欄 */
const FIELD_NAMES = ['carrier', 'trackingNumber'] as const

/** 配送業者の選択肢 */
const carrierOptions = carrierValues.map((carrier) => ({ value: carrier, label: carrierLabel(carrier) }))

/**
 * 注文の出荷。出荷後は配送状態・配送業者・送り状番号・出荷日時を示し、出荷できる注文には登録のフォームを出す。
 * どちらでもない注文には何も出さない。
 */
export function ShipmentCard({ order }: { order: OrderDetail }) {
  const t = useTranslations('order.shipment')
  if (!order.shipment && !order.actions.ship) {
    return null
  }
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('title') /* 出荷 */}</CardTitle>
      </CardHeader>
      <CardContent>
        {order.shipment ? (
          <ShipmentDetail shipment={order.shipment} />
        ) : (
          <ShipForm orderId={order.id} orderNumber={order.orderNumber} />
        )}
      </CardContent>
    </Card>
  )
}

/** 出荷の内容 */
function ShipmentDetail({ shipment }: { shipment: Shipment }) {
  const t = useTranslations('order.shipment')
  const tCommon = useTranslations('common')
  return (
    <dl className="grid grid-cols-[7rem_1fr] gap-y-1 text-sm">
      <dt className="text-muted-foreground">{t('status') /* 配送状態 */}</dt>
      <dd>{shipmentStatusLabel(shipment.status)}</dd>
      <dt className="text-muted-foreground">{tCommon('order.carrier') /* 配送業者 */}</dt>
      <dd>{carrierLabel(shipment.carrier)}</dd>
      <dt className="text-muted-foreground">{tCommon('order.trackingNumber') /* 送り状番号 */}</dt>
      <dd className="font-mono">{shipment.trackingNumber}</dd>
      <dt className="text-muted-foreground">{t('shippedAt') /* 出荷日時 */}</dt>
      <dd>
        <DateTime value={shipment.shippedAt} />
      </dd>
    </dl>
  )
}

/**
 * 出荷の登録。売上を確定するので取り消せず、確認してから送る。<br />
 * 売上確定の失敗（与信期限切れ、決済サービスに接続できない）は、サーバーのメッセージを出す。
 */
function ShipForm({ orderId, orderNumber }: { orderId: string; orderNumber: string }) {
  const t = useTranslations('order.shipment')
  const tCommon = useTranslations('common')
  const form = useForm<Values>({ defaultValues: { carrier: null, trackingNumber: '' } })
  const mutation = useApiMutation('post', '/orders/{orderId}/shipment', {
    success: t('registered'), // 登録の知らせ
    invalidate: ['/orders'],
    onError: (error) => setServerFieldErrors(error, form.setError, FIELD_NAMES),
  })
  const values = form.watch()
  const filled = values.carrier !== null && values.trackingNumber.trim() !== ''
  const submit = form.handleSubmit((submitted) =>
    mutation.mutate({
      params: { path: { orderId } },
      body: { carrier: submitted.carrier as Carrier, trackingNumber: submitted.trackingNumber.trim() },
    }),
  )

  return (
    <form className="grid gap-4" onSubmit={(event) => event.preventDefault()}>
      <div className="flex flex-wrap items-start gap-3">
        <Field className="w-40">
          <FieldLabel htmlFor="ship-carrier">{tCommon('order.carrier') /* 配送業者 */}</FieldLabel>
          <Controller
            control={form.control}
            name="carrier"
            render={({ field }) => (
              <OptionSelect
                className="w-40"
                id="ship-carrier"
                onChange={field.onChange}
                options={carrierOptions}
                value={field.value}
              />
            )}
          />
          <FieldError errors={[form.formState.errors.carrier]} />
        </Field>
        <Field className="w-60">
          <FieldLabel htmlFor="ship-tracking-number">{tCommon('order.trackingNumber') /* 送り状番号 */}</FieldLabel>
          <Input
            id="ship-tracking-number"
            inputMode="numeric"
            maxLength={schemas.ShipOrderRequest.trackingNumber.maxLength}
            placeholder="1234-5678-9012"
            {...form.register('trackingNumber')}
          />
          <FieldError errors={[form.formState.errors.trackingNumber]} />
        </Field>
      </div>
      <ErrorMessage error={mutation.error} shownFields={FIELD_NAMES} />
      <div>
        <ConfirmDialog
          confirmLabel={t('confirm') /* 出荷する */}
          description={t('description') /* 売上を確定する */}
          onConfirm={() => submit()}
          title={t('confirmTitle', { orderNumber }) /* 確認の題 */}
          trigger={
            <Button disabled={mutation.isPending || !filled} type="button">
              {t('submit') /* 出荷を登録する */}
            </Button>
          }
        />
      </div>
    </form>
  )
}
