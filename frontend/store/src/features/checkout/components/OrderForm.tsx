'use client'

import { Button } from '@ec-site/shared/ui/button'
import { Input } from '@ec-site/shared/ui/input'
import { Label } from '@ec-site/shared/ui/label'
import Link from 'next/link'
import { useTranslations } from 'next-intl'
import { type ReactNode, startTransition, useActionState } from 'react'
import { placeOrder } from '@/actions/checkout'
import { schemas } from '@/api/generated/constraints'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { type OrderField, placeOrderErrors } from '@/features/checkout/placeOrderErrors'
import { PREFECTURES } from '@/features/checkout/prefectures'
import { formatYen } from '@/lib/orderDisplay'

/**
 * お届け先の入力と、注文。連絡先のメールアドレスは会員のものを示すだけで、入力しない。<br />
 * 送信中はボタンを押せなくする（二重押しの防止）。エラーのときも入力した値を消さないよう、
 * フォームの action ではなく onSubmit から送る（action で送ると、React が送信の後にフォームを空にする）。
 */
export function OrderForm({
  checkoutId,
  expectedTotal,
  email,
}: {
  checkoutId: string
  expectedTotal: number
  /** 会員のメールアドレス（注文の連絡先） */
  email: string
}) {
  const t = useTranslations('checkout.form')
  const [result, formAction, pending] = useActionState(placeOrder, null)
  const errors = placeOrderErrors(result)

  return (
    <form
      className="space-y-4"
      noValidate
      onSubmit={(event) => {
        event.preventDefault()
        const formData = new FormData(event.currentTarget)
        startTransition(() => formAction(formData))
      }}
    >
      <input name="checkoutId" type="hidden" value={checkoutId} />
      <input name="expectedTotal" type="hidden" value={expectedTotal} />
      <div className="space-y-1.5 text-sm">
        <p className="font-medium">{t('email') /* メールアドレス */}</p>
        <p>{email}</p>
        <p className="text-muted-foreground text-xs">{t('emailNote') /* 連絡の宛先 */}</p>
      </div>
      <Field error={errors.fields.name} label={t('name') /* 氏名 */} name="name">
        <Input autoComplete="name" id="name" maxLength={schemas.ShippingAddress.name.maxLength} name="name" />
      </Field>
      <Field
        error={errors.fields.postalCode}
        hint={t('postalCodeHint') /* 例 */}
        label={t('postalCode') /* 郵便番号 */}
        name="postalCode"
      >
        <Input autoComplete="postal-code" className="w-40" id="postalCode" inputMode="numeric" name="postalCode" />
      </Field>
      <Field error={errors.fields.prefecture} label={t('prefecture') /* 都道府県 */} name="prefecture">
        <NativeSelect autoComplete="address-level1" defaultValue="" id="prefecture" name="prefecture">
          <NativeSelectOption disabled value="">
            {t('prefecturePlaceholder') /* 選択を促す */}
          </NativeSelectOption>
          {PREFECTURES.map((prefecture) => (
            <NativeSelectOption key={prefecture} value={prefecture}>
              {prefecture}
            </NativeSelectOption>
          ))}
        </NativeSelect>
      </Field>
      <Field
        error={errors.fields.address}
        hint={t('addressHint') /* 書くもの */}
        label={t('address') /* 住所 */}
        name="address"
      >
        <Input
          autoComplete="street-address"
          id="address"
          maxLength={schemas.ShippingAddress.address.maxLength}
          name="address"
        />
      </Field>
      <Field error={errors.fields.phone} hint={t('phoneHint') /* 例 */} label={t('phone') /* 電話番号 */} name="phone">
        <Input autoComplete="tel" className="w-48" id="phone" inputMode="tel" name="phone" type="tel" />
      </Field>
      {errors.message && (
        <p aria-live="polite" className="text-destructive text-sm">
          {errors.message}
          {errors.backToCart && (
            <Link className="ml-2 underline" href="/cart">
              {t('viewCart') /* カートを見る */}
            </Link>
          )}
        </p>
      )}
      <Button className="w-full" disabled={pending} size="lg" type="submit">
        {pending ? t('placing') : t('submit', { total: formatYen(expectedTotal) }) /* 注文中か、総額を添えて注文する */}
      </Button>
    </form>
  )
}

// ============================== 部品 ==============================

/** 入力欄の見出し、補足、エラー */
function Field({
  name,
  label,
  hint,
  error,
  children,
}: {
  name: OrderField
  label: string
  hint?: string
  error?: string
  children: ReactNode
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={name}>{label}</Label>
      {children}
      {hint && <p className="text-muted-foreground text-xs">{hint}</p>}
      {error && <p className="text-destructive text-sm">{error}</p>}
    </div>
  )
}
