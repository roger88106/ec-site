import { Button } from '@ec-site/shared/ui/button'
import { Input } from '@ec-site/shared/ui/input'
import { useForm } from 'react-hook-form'
import { useTranslations } from 'use-intl'
import { setServerFieldErrors } from '@/api/formErrors'
import { schemas } from '@/api/generated/constraints'
import { useApiMutation } from '@/api/useApiMutation'
import { ErrorMessage } from '@/components/ErrorMessage'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldError, FieldLabel } from '@/components/ui/field'

/** 入力欄の値 */
type Values = { name: string }

/** 税区分の登録 */
export function CreateTaxCategoryForm() {
  const t = useTranslations('tax.create')
  const tApp = useTranslations('app.common')
  const form = useForm<Values>({ defaultValues: { name: '' } })
  const mutation = useApiMutation('post', '/tax-categories', {
    success: t('registered'), // 登録の知らせ
    invalidate: ['/tax-categories'],
    onSuccess: () => form.reset(),
    onError: (error) => setServerFieldErrors(error, form.setError, ['name']),
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('title') /* 税区分の登録 */}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <form
          className="flex items-start gap-2"
          onSubmit={form.handleSubmit((values) => mutation.mutate({ body: values }))}
        >
          <Field className="w-64">
            <FieldLabel htmlFor="tax-category-name">{tApp('name') /* 名称 */}</FieldLabel>
            <Input
              id="tax-category-name"
              maxLength={schemas.CreateTaxCategoryRequest.name.maxLength}
              {...form.register('name', { required: true })}
            />
            <FieldError errors={[form.formState.errors.name]} />
          </Field>
          <Button className="mt-6" disabled={mutation.isPending || !form.watch('name').trim()} type="submit">
            {tApp('register') /* 登録 */}
          </Button>
        </form>
        <ErrorMessage error={mutation.error} shownFields={['name']} />
      </CardContent>
    </Card>
  )
}
