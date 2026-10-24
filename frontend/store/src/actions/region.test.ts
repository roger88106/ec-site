import { describe, expect, test } from 'vitest'
import { findPostalCodeAddresses, listMunicipalities } from '@/actions/region'
import { MUNICIPALITIES, postalCodeAddress } from '@/test/fixtures'
import { http, networkError, problem, STORE_API, server } from '@/test/server'

describe('都道府県の市区町村の一覧を取る', () => {
  test('AC-249 選んだ都道府県の市区町村を、API の順のまま返す', async () => {
    let requested: unknown
    server.use(
      http.get('/prefectures/{prefectureCode}/municipalities', ({ params, response }) => {
        requested = params.prefectureCode
        return response(200).json({ items: MUNICIPALITIES['13'] })
      }),
    )

    expect(await listMunicipalities('13')).toEqual(MUNICIPALITIES['13'])
    expect(requested).toBe('13')
  })

  test('ない都道府県（404）や通信の失敗は null を返す', async () => {
    server.use(
      http.get('/prefectures/{prefectureCode}/municipalities', ({ response }) =>
        response(404).json(problem(404, 'NOT_FOUND', '見つかりません。')),
      ),
    )
    expect(await listMunicipalities('48')).toBeNull()

    server.use(networkError('get', `${STORE_API}/prefectures/:prefectureCode/municipalities`))
    expect(await listMunicipalities('13')).toBeNull()
  })
})

describe('郵便番号で住所を引く', () => {
  test('AC-254 郵便番号の住所を、ハイフンを除いて引き、API の順のまま返す', async () => {
    let requested: unknown
    server.use(
      http.get('/postal-codes/{postalCode}', ({ params, response }) => {
        requested = params.postalCode
        return response(200).json({ items: [postalCodeAddress('13', '13101', '千代田')] })
      }),
    )

    expect(await findPostalCodeAddresses('100-0001')).toEqual([postalCodeAddress('13', '13101', '千代田')])
    expect(requested).toBe('1000001')
  })

  test('AC-256 ない郵便番号は空の一覧を返す', async () => {
    server.use(http.get('/postal-codes/{postalCode}', ({ response }) => response(200).json({ items: [] })))

    expect(await findPostalCodeAddresses('9999999')).toEqual([])
  })

  test('AC-256 通信に失敗したら空の一覧を返す', async () => {
    server.use(networkError('get', `${STORE_API}/postal-codes/:postalCode`))

    expect(await findPostalCodeAddresses('1000001')).toEqual([])
  })

  test('ハイフンを除いて 7 桁の数字でなければ、API を呼ばずに空の一覧を返す', async () => {
    // ハンドラーを置かないので、呼べばテストが失敗する（setup.ts）
    expect(await findPostalCodeAddresses('100000')).toEqual([])
    expect(await findPostalCodeAddresses('10000a1')).toEqual([])
  })
})
