import { Controller } from 'react-hook-form'
import { toast } from 'sonner'
import { $api } from '@/api/client'
import { useInvalidate } from '@/api/invalidate'
import type { ProductDetail } from '@/api/types'
import { DateTimeInput } from '@/components/DateTimeInput'
import { ErrorMessage } from '@/components/ErrorMessage'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldLabel } from '@/components/ui/field'
import { useVersionedForm } from '@/hooks/useVersionedForm'

/** 入力欄の値（日時は ISO 8601 の文字列。空は限りがない） */
type Values = { publishStart: string | null; publishEnd: string | null }

/** 商品の公開期間を、入力欄の値にする */
function toValues(product: ProductDetail): Values {
  return { publishStart: product.publishStart ?? null, publishEnd: product.publishEnd ?? null }
}

/**
 * 公開期間の設定（T1008。AC-34〜36）。日時は日本時間で、空なら限りがない。<br />
 * 公開中でも期間の外は C 側に表示されず、その時刻で切り替わる（AC-35）。終了が開始より後であること（AC-36）はサーバーが検証する。
 * 送るバージョンは hooks/useVersionedForm で扱う（管理画面の構成 第 5 節）。
 */
export function PublicationPeriodCard({
  product,
  refetch,
}: {
  product: ProductDetail
  refetch: () => Promise<unknown>
}) {
  const invalidate = useInvalidate()
  const { form, baseVersion, markSaved, reload } = useVersionedForm(product, toValues, refetch)
  const mutation = $api.useMutation('put', '/products/{productId}/publication-period', {
    onSuccess: async () => {
      toast.success('公開期間を保存しました')
      markSaved()
      await invalidate('/products')
    },
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>公開期間</CardTitle>
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
          <p className="text-muted-foreground text-sm">
            日時は日本時間。公開中でも、開始より前と終了以降は店舗に表示されません。空なら限りがありません。
          </p>
          <div className="flex flex-wrap gap-4">
            <Field className="w-60">
              <FieldLabel htmlFor="publish-start">公開開始</FieldLabel>
              <Controller
                control={form.control}
                name="publishStart"
                render={({ field }) => (
                  <DateTimeInput id="publish-start" onChange={field.onChange} value={field.value} />
                )}
              />
            </Field>
            <Field className="w-60">
              <FieldLabel htmlFor="publish-end">公開終了（この日時は含まない）</FieldLabel>
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
              公開期間を保存
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}
