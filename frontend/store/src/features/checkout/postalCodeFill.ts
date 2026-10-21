import type { PostalCodeAddress } from '@/api/types'

/** 郵便番号で補うもの。address が null なら住所の欄を変えない */
export type PostalCodeFill = {
  prefectureCode: string
  municipalityCode: string
  address: string | null
}

/**
 * 郵便番号を引いた結果から、補うものを決める（店舗画面の構成 第 4.8 節）。<br />
 * 都道府県と市区町村が 1 つに決まるときだけ補い、町域は 1 件で住所の欄が空のときだけ入れる。
 *
 * @param addresses      郵便番号の住所
 * @param currentAddress 住所の欄の今の値
 * @returns 補うもの。補わないなら null
 */
export function postalCodeFill(addresses: PostalCodeAddress[], currentAddress: string): PostalCodeFill | null {
  const [first] = addresses
  if (!first) {
    return null
  }
  // 1 つの郵便番号が 2 つの市区町村にまたがるときは、誤りを補わないよう消費者に選ばせる
  if (addresses.some((address) => address.municipality.code !== first.municipality.code)) {
    return null
  }
  const fillsTown = addresses.length === 1 && first.townName !== '' && currentAddress.trim() === ''
  return {
    prefectureCode: first.prefecture.code,
    municipalityCode: first.municipality.code,
    address: fillsTown ? first.townName : null,
  }
}
