import { Button } from '@ec-site/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@ec-site/shared/ui/card'
import { Controller } from 'react-hook-form'
import { useTranslations } from 'use-intl'
import type { ProductDetail } from '@/api/types'
import { useApiMutation } from '@/api/useApiMutation'
import { DateTimeInput } from '@/components/DateTimeInput'
import { ErrorMessage } from '@/components/ErrorMessage'
import { Field, FieldLabel } from '@/components/ui/field'
import { useVersionedForm } from '@/hooks/useVersionedForm'

/** 入力欄の値（日時は ISO 8601 の文字列。空は限りがない） */
type Values = { publishStart: string | null; publishEnd: string | null }

/** 商品の公開期間を、入力欄の値にする */
function toValues(product: ProductDetail): Values {
  return { publishStart: product.publishStart ?? null, publishEnd: product.publishEnd ?? null }
}

/**
 * 公開期間の設定。日時は日本時間で、空なら限りがない。<br />
 * 公開中でも期間の外は C 側に表示されず、その時刻で切り替わる。終了が開始より後であることはサーバーが検証する。
 * 送るバージョンは hooks/useVersionedForm で扱う（管理画面の構成 第 5 節）。
 */
export function PublicationPeriodCard({
  product,
  refetch,
}: {
  product: ProductDetail
  refetch: () => Promise<unknown>
}) {
  const { form, baseVersion, markSaved, reload } = useVersionedForm(product, toValues, refetch)
  const t = useTranslations('product.period')
  const mutation = useApiMutation('put', '/products/{productId}/publication-period', {
    success: t('saved'), // 保存の知らせ
    invalidate: ['/products'],
    onSuccess: markSaved,
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('title') /* 公開期間 */}</CardTitle>
      </CardHeader>
      <CardContent>
        <form
          className="grid gap-4"
          onSubmit={form.handleSubmit((values) =>
            mutation.mutate({
              params: { path: { productId: product.id } },
              body: { ...values, version: baseVersion },
            }),
          )}
        >
          <p className="text-muted-foreground text-sm">{t('help') /* 期間の決まり */}</p>
          <div className="flex flex-wrap gap-4">
            <Field className="w-60">
              <FieldLabel htmlFor="publish-start">{t('start') /* 公開開始 */}</FieldLabel>
              <Controller
                control={form.control}
                name="publishStart"
                render={({ field }) => (
                  <DateTimeInput id="publish-start" onChange={field.onChange} value={field.value} />
                )}
              />
            </Field>
            <Field className="w-60">
              <FieldLabel htmlFor="publish-end">{t('end') /* 公開終了 */}</FieldLabel>
              <Controller
                control={form.control}
                name="publishEnd"
                render={({ field }) => <DateTimeInput id="publish-end" onChange={field.onChange} value={field.value} />}
              />
            </Field>
          </div>
          <ErrorMessage
            error={mutation.error}
            onReload={async () => {
              mutation.reset()
              await reload()
            }}
          />
          <div>
            <Button disabled={mutation.isPending} type="submit">
              {t('save') /* 公開期間を保存 */}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}
