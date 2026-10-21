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
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { type OrderField, placeOrderErrors } from '@/features/checkout/placeOrderErrors'
import { postalCodeFill } from '@/features/checkout/postalCodeFill'

/** 郵便番号の形（ハイフンを除いて数字 7 桁） */
const POSTAL_CODE_DIGITS = /^[0-9]{7}$/

/**
 * お届け先の入力と、注文。連絡先のメールアドレスは会員のものを示すだけで、入力しない。<br />
 * 都道府県と市区町村は一覧から選び、郵便番号が 7 桁になったら補う（店舗画面の構成 第 4.8 節）。
 * 送信中はボタンを押せなくする（二重押しの防止）。エラーのときも入力した値を消さないよう、
 * フォームの action ではなく onSubmit から送る（action で送ると、React が送信の後にフォームを空にする）。
 */
export function OrderForm({
  checkoutId,
  expectedTotal,
  email,
  prefectures,
}: {
  checkoutId: string
  expectedTotal: number
  /** 会員のメールアドレス（注文の連絡先） */
  email: string
  /** 都道府県の一覧（店舗 API から取ったもの） */
  prefectures: Prefecture[]
}) {
  const t = useTranslations('checkout.form')
  const [result, formAction, pending] = useActionState(placeOrder, null)
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
        <Input autoComplete="name" id="name" maxLength={schemas.ShippingAddressInput.name.maxLength} name="name" />
      </Field>
      <Field
        error={errors.fields.postalCode}
        hint={t('postalCodeHint') /* 例 */}
        label={t('postalCode') /* 郵便番号 */}
        name="postalCode"
      >
        <Input
          autoComplete="postal-code"
          className="w-40"
          id="postalCode"
          inputMode="numeric"
          name="postalCode"
          onChange={(event) => area.changePostalCode(event.currentTarget.value)}
        />
      </Field>
      <Field error={errors.fields.prefecture} label={t('prefecture') /* 都道府県 */} name="prefecture">
        <NativeSelect
          autoComplete="address-level1"
          id="prefecture"
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
      </Field>
      <Field error={errors.fields.municipality} label={t('municipality') /* 市区町村 */} name="municipality">
        <NativeSelect
          autoComplete="address-level2"
          disabled={area.municipalities.length === 0}
          id="municipality"
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
      </Field>
      <Field
        error={errors.fields.address}
        hint={t('addressHint') /* 書くもの */}
        label={t('address') /* 住所 */}
        name="address"
      >
        <Input
          autoComplete="address-line1"
          id="address"
          maxLength={schemas.ShippingAddressInput.address.maxLength}
          name="address"
          onChange={(event) => area.changeAddress(event.currentTarget.value)}
          value={area.address}
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
    const digits = value.replace('-', '')
    if (!POSTAL_CODE_DIGITS.test(digits)) {
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
