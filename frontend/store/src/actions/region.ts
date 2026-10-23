'use server'

import { withActionResult } from '@/actions/withActionResult'
import { getApi } from '@/api/client'
import type { Municipality, PostalCodeAddress } from '@/api/types'
import { normalizePostalCode } from '@/lib/postalCode'

/**
 * 都道府県の市区町村の一覧を取る（店舗画面の構成 第 4.8 節）。読むだけなので、失敗は投げずに null で返す
 * （一覧を出せなくても、消費者は都道府県を選び直せる）。
 *
 * @param prefectureCode 都道府県のコード
 * @returns コードの順の市区町村。ない都道府県や通信の失敗なら null
 */
export async function listMunicipalities(prefectureCode: string): Promise<Municipality[] | null> {
  const items = await withActionResult(async () => {
    const api = await getApi()
    const { data } = await api.GET('/prefectures/{prefectureCode}/municipalities', {
      params: { path: { prefectureCode } },
    })
    return data?.items ?? null
  })
  return Array.isArray(items) ? items : null
}

/**
 * 郵便番号の住所を引く（店舗画面の構成 第 4.8 節）。読むだけなので、失敗は投げずに空で返す
 * （補えなくても、消費者は一覧から選べる）。
 *
 * @param postalCode 郵便番号（ハイフンは任意）
 * @returns 並び順の住所。形が違う、ない、通信の失敗なら空
 */
export async function findPostalCodeAddresses(postalCode: string): Promise<PostalCodeAddress[]> {
  const digits = normalizePostalCode(postalCode)
  if (digits === null) {
    return []
  }
  const items = await withActionResult(async () => {
    const api = await getApi()
    const { data } = await api.GET('/postal-codes/{postalCode}', { params: { path: { postalCode: digits } } })
    return data?.items ?? []
  })
  return Array.isArray(items) ? items : []
}
