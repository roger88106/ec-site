'use client'

import { formatYen } from '@ec-site/shared/lib/format'
import { Button } from '@ec-site/shared/ui/button'
import { Input } from '@ec-site/shared/ui/input'
import { Label } from '@ec-site/shared/ui/label'
import Link from 'next/link'
import { useTranslations } from 'next-intl'
import { type ReactNode, startTransition, useActionState, useRef, useState } from 'react'
import { placeOrder } from '@/actions/checkout'
import { findPostalCodeAddresses, listMunicipalities } from '@/actions/region'
import { schemas } from '@/api/generated/constraints'
import type { Municipality, Prefecture } from '@/api/types'
import { ActionError } from '@/components/ActionError'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { type OrderField, placeOrderErrors } from '@/features/checkout/placeOrderErrors'
import { postalCodeFill } from '@/features/checkout/postalCodeFill'
import type { ActionResult } from '@/lib/actionResult'
import { normalizePostalCode } from '@/lib/postalCode'

/** 注文のフォームの props（注文の連絡先、金額、都道府県の一覧） */
type OrderFormProps = {
  checkoutId: string
  expectedTotal: number
  /** 会員のメールアドレス（注文の連絡先） */
  email: string
  /** 都道府県の一覧（店舗 API から取ったもの） */
  prefectures: Prefecture[]
}

/**
 * お届け先の入力と、注文。注文のサーバーアクションの結果を受け、表示（{@link OrderFormView}）に渡す。
 */
export function OrderForm(props: OrderFormProps) {
  const [result, formAction, pending] = useActionState(placeOrder, null)

  return <OrderFormView {...props} formAction={formAction} pending={pending} result={result} />
}

/**
 * お届け先の入力と、注文の表示。連絡先のメールアドレスは会員のものを示すだけで、入力しない。<br />
 * 都道府県と市区町村は一覧から選び、郵便番号が 7 桁になったら補う（店舗画面の構成 第 4.8 節）。
 * 送信中はボタンを押せなくする（二重押しの防止）。エラーのときも入力した値を消さないよう、
 * フォームの action ではなく onSubmit から送る（action で送ると、React が送信の後にフォームを空にする）。<br />
 * サーバーアクションの結果を props で受けるので、結果ごとの表示をテストで確かめられる。
 *
 * @param result     注文のサーバーアクションの結果（送る前は null）
 * @param formAction 入力を送る関数（`useActionState` が返すもの）
 * @param pending    送信中か
 */
export function OrderFormView({
  checkoutId,
  expectedTotal,
  email,
  prefectures,
  result,
  formAction,
  pending,
}: OrderFormProps & {
  result: ActionResult | null
  formAction: (formData: FormData) => void
  pending: boolean
}) {
  const t = useTranslations('checkout.form')
  const errors = placeOrderErrors(result)
  const area = useAddressArea()

  return (
    <form
      className="space-y-4"
      noValidate
      // action で送ると、React が送った後にフォームを空にし、誤りのときに入力が消える。onSubmit から送る
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
        {(control) => (
          <Input {...control} autoComplete="name" maxLength={schemas.ShippingAddressInput.name.maxLength} name="name" />
        )}
      </Field>
      <Field
        error={errors.fields.postalCode}
        hint={t('postalCodeHint') /* 例 */}
        label={t('postalCode') /* 郵便番号 */}
        name="postalCode"
      >
        {(control) => (
          <Input
            {...control}
            autoComplete="postal-code"
            className="w-40"
            inputMode="numeric"
            name="postalCode"
            onChange={(event) => area.changePostalCode(event.currentTarget.value)}
          />
        )}
      </Field>
      <Field error={errors.fields.prefecture} label={t('prefecture') /* 都道府県 */} name="prefecture">
        {(control) => (
          <NativeSelect
            {...control}
            autoComplete="address-level1"
            name="prefectureCode"
            onChange={(event) => area.selectPrefecture(event.currentTarget.value)}
            value={area.prefectureCode}
          >
            <NativeSelectOption disabled value="">
              {t('prefecturePlaceholder') /* 選択を促す */}
            </NativeSelectOption>
            {prefectures.map((prefecture) => (
              <NativeSelectOption key={prefecture.code} value={prefecture.code}>
                {prefecture.name}
              </NativeSelectOption>
            ))}
          </NativeSelect>
        )}
      </Field>
      <Field error={errors.fields.municipality} label={t('municipality') /* 市区町村 */} name="municipality">
        {(control) => (
          <NativeSelect
            {...control}
            autoComplete="address-level2"
            disabled={area.municipalities.length === 0}
            name="municipalityCode"
            onChange={(event) => area.selectMunicipality(event.currentTarget.value)}
            value={area.municipalityCode}
          >
            <NativeSelectOption disabled value="">
              {
                area.prefectureCode === ''
                  ? t('municipalityNeedsPrefecture') /* 都道府県を先に選ぶよう促す */
                  : t('municipalityPlaceholder') /* 選択を促す */
              }
            </NativeSelectOption>
            {area.municipalities.map((municipality) => (
              <NativeSelectOption key={municipality.code} value={municipality.code}>
                {municipality.name}
              </NativeSelectOption>
            ))}
          </NativeSelect>
        )}
      </Field>
      <Field
        error={errors.fields.address}
        hint={t('addressHint') /* 書くもの */}
        label={t('address') /* 住所 */}
        name="address"
      >
        {(control) => (
          <Input
            {...control}
            autoComplete="address-line1"
            maxLength={schemas.ShippingAddressInput.address.maxLength}
            name="address"
            onChange={(event) => area.changeAddress(event.currentTarget.value)}
            value={area.address}
          />
        )}
      </Field>
      <Field error={errors.fields.phone} hint={t('phoneHint') /* 例 */} label={t('phone') /* 電話番号 */} name="phone">
        {(control) => (
          <Input {...control} autoComplete="tel" className="w-48" inputMode="tel" name="phone" type="tel" />
        )}
      </Field>
      <ActionError message={errors.message}>
        {errors.backToCart && (
          <Link className="ml-2 underline" href="/cart">
            {t('viewCart') /* カートを見る */}
          </Link>
        )}
      </ActionError>
      <Button className="w-full" disabled={pending} size="lg" type="submit">
        {pending ? t('placing') : t('submit', { total: formatYen(expectedTotal) }) /* 注文中か、総額を添えて注文する */}
      </Button>
    </form>
  )
}

// ============================== 部品 ==============================

/**
 * お届け先の都道府県・市区町村・住所の入力の状態（店舗画面の構成 第 4.8 節）。<br />
 * 引いている間に都道府県や郵便番号を変えたら、古い応答は捨てる。
 */
function useAddressArea() {
  const [prefectureCode, setPrefectureCode] = useState('')
  const [municipalityCode, setMunicipalityCode] = useState('')
  const [municipalities, setMunicipalities] = useState<Municipality[]>([])
  const [address, setAddress] = useState('')
  /** 住所の欄の今の値（郵便番号の応答を待つ間に変わることがあるため、状態とは別に持つ） */
  const addressRef = useRef('')
  /** 最後に頼んだ市区町村の一覧の番号（古い応答を捨てるため） */
  const municipalitiesRequest = useRef(0)
  /** 最後に頼んだ郵便番号の引き当ての番号（同上） */
  const postalCodeRequest = useRef(0)
  /** 最後に引いた郵便番号（同じ番号で引き直さないため） */
  const lookedUpPostalCode = useRef('')

  /** 都道府県を選び、その市区町村の一覧を取る。市区町村の選択は外す。取れたら true */
  const selectPrefecture = async (code: string): Promise<boolean> => {
    setPrefectureCode(code)
    setMunicipalityCode('')
    setMunicipalities([])
    const request = ++municipalitiesRequest.current
    const found = await listMunicipalities(code)
    if (request !== municipalitiesRequest.current) {
      return false
    }
    setMunicipalities(found ?? [])
    return found !== null
  }

  const changeAddress = (value: string) => {
    addressRef.current = value
    setAddress(value)
  }

  /** 郵便番号が 7 桁になったら引いて補う。補えなければ何もしない */
  const changePostalCode = async (value: string) => {
    const digits = normalizePostalCode(value)
    if (digits === null) {
      lookedUpPostalCode.current = ''
      ++postalCodeRequest.current
      return
    }
    if (digits === lookedUpPostalCode.current) {
      return
    }
    lookedUpPostalCode.current = digits
    const request = ++postalCodeRequest.current
    const addresses = await findPostalCodeAddresses(digits)
    if (request !== postalCodeRequest.current) {
      return
    }
    const fill = postalCodeFill(addresses, addressRef.current)
    if (fill === null || !(await selectPrefecture(fill.prefectureCode)) || request !== postalCodeRequest.current) {
      return
    }
    setMunicipalityCode(fill.municipalityCode)
    if (fill.address !== null) {
      changeAddress(fill.address)
    }
  }

  return {
    prefectureCode,
    municipalityCode,
    municipalities,
    address,
    selectPrefecture,
    selectMunicipality: setMunicipalityCode,
    changePostalCode,
    changeAddress,
  }
}

/** 入力欄に付ける、見出し・補足・誤りとの結び付け */
type FieldControlProps = {
  /** 見出しの `htmlFor` と結び付ける、入力欄の id */
  id: OrderField
  /** 補足とエラーの文の id（どちらもないときは付けない） */
  'aria-describedby': string | undefined
  /** エラーのある欄だけ `true`（ないときは付けない） */
  'aria-invalid': true | undefined
}

/**
 * 入力欄の見出し、補足、エラー。補足とエラーの文を入力欄の説明（補足、エラーの順）に結び付け、
 * エラーのある欄は誤りの状態にする。入力欄には、子の関数が受ける props を展開して渡す。
 */
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
  children: (control: FieldControlProps) => ReactNode
}) {
  const hintId = `${name}-hint`
  const errorId = `${name}-error`
  const describedBy = [hint && hintId, error && errorId].filter(Boolean).join(' ')
  return (
    <div className="space-y-1.5">
      <Label htmlFor={name}>{label}</Label>
      {children({ id: name, 'aria-describedby': describedBy || undefined, 'aria-invalid': error ? true : undefined })}
      {hint && (
        <p className="text-muted-foreground text-xs" id={hintId}>
          {hint}
        </p>
      )}
      {error && (
        <p className="text-destructive text-sm" id={errorId}>
          {error}
        </p>
      )}
    </div>
  )
}
