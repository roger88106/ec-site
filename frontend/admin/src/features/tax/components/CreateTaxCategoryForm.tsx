import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { $api } from '@/api/client'
import { setServerFieldErrors } from '@/api/formErrors'
import { useInvalidate } from '@/api/invalidate'
import { ErrorMessage } from '@/components/ErrorMessage'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldError, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'

type Values = { name: string }

/** 税区分の登録（AC-1） */
export function CreateTaxCategoryForm() {
  const invalidate = useInvalidate()
  const form = useForm<Values>({ defaultValues: { name: '' } })
  const mutation = $api.useMutation('post', '/tax-categories', {
    onSuccess: async () => {
      toast.success('税区分を登録しました')
      form.reset()
      await invalidate('/tax-categories')
    },
    onError: (error) => setServerFieldErrors(error, form.setError, ['name']),
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>税区分の登録</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <form
          className="flex items-start gap-2"
          onSubmit={form.handleSubmit((values) => mutation.mutate({ body: values }))}
        >
          <Field className="w-64">
            <FieldLabel htmlFor="tax-category-name">名称</FieldLabel>
            <Input id="tax-category-name" maxLength={50} {...form.register('name', { required: true })} />
            <FieldError errors={[form.formState.errors.name]} />
          </Field>
          <Button className="mt-6" disabled={mutation.isPending || !form.watch('name').trim()} type="submit">
            登録
          </Button>
        </form>
        <ErrorMessage error={mutation.error} shownFields={['name']} />
      </CardContent>
    </Card>
  )
}
