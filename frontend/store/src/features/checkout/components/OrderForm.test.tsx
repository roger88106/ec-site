// @vitest-environment jsdom
import { screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import { findPostalCodeAddresses, listMunicipalities } from '@/actions/region'
import type { PostalCodeAddress, Prefecture } from '@/api/types'
import { OrderForm } from '@/features/checkout/components/OrderForm'
import type { ActionResult } from '@/lib/actionResult'
import { renderInBrowser, renderMarkup } from '@/test/render'

/** 注文のサーバーアクションの結果として、フォームに渡す値（null は送る前） */
let actionResult: ActionResult | null = null

// 注文の送信はサーバーアクションを通るので、送った結果の表示だけを、結果を差し替えて HTML で確かめる（テストの書き方 第 1 節）
vi.mock('react', async (importOriginal) => {
  const react = await importOriginal<typeof import('react')>()
  return { ...react, useActionState: () => [actionResult, () => {}, false] }
})

// 一覧と郵便番号は読むだけのサーバーアクションなので、結果を差し替えてフォームの操作を確かめる（店舗画面の構成 第 7 節 ※7）
vi.mock('@/actions/region', () => ({ listMunicipalities: vi.fn(), findPostalCodeAddresses: vi.fn() }))

/** API から取った都道府県の一覧（テストでは 3 つ） */
const PREFECTURES: Prefecture[] = [
  { code: '01', name: '北海道' },
  { code: '13', name: '東京都' },
  { code: '14', name: '神奈川県' },
]

/** 都道府県ごとの市区町村 */
const MUNICIPALITIES: Record<string, { code: string; name: string }[]> = {
  '01': [{ code: '01101', name: '札幌市中央区' }],
  '13': [
    { code: '13101', name: '千代田区' },
    { code: '13102', name: '中央区' },
  ],
  '14': [{ code: '14104', name: '横浜市中区' }],
}

/** 郵便番号の住所 */
function postal(prefectureCode: string, municipalityCode: string, townName: string): PostalCodeAddress {
  const prefecture = PREFECTURES.find((found) => found.code === prefectureCode)
  const municipality = MUNICIPALITIES[prefectureCode]?.find((found) => found.code === municipalityCode)
  if (!prefecture || !municipality) {
    throw new Error(`テストのデータにない：${prefectureCode} ${municipalityCode}`)
  }
  return { prefecture, municipality, townName }
}

function form(result: ActionResult | null = null) {
  actionResult = result
  return <OrderForm checkoutId="checkout-1" email="member@example.com" expectedTotal={3200} prefectures={PREFECTURES} />
}

/** 選んでいる値 */
function selected(label: string): string {
  return (screen.getByLabelText(label) as HTMLSelectElement).value
}

/** 住所の欄の値 */
function addressValue(): string {
  return (screen.getByLabelText('住所') as HTMLInputElement).value
}

beforeEach(() => {
  vi.mocked(listMunicipalities).mockImplementation(async (code) => MUNICIPALITIES[code] ?? null)
  vi.mocked(findPostalCodeAddresses).mockResolvedValue([])
})

describe('お届け先の入力と注文', () => {
  test('AC-127 会員のメールアドレスを示し、お届け先の欄と、総額を添えた注文のボタンを出す', () => {
    const html = renderMarkup(form())

    expect(html).toContain('member@example.com')
    expect(html).toContain('ご注文の連絡は、会員のメールアドレスにお送りします。')
    expect(html).toMatch(/<input [^>]*name="checkoutId"[^>]*value="checkout-1"/)
    expect(html).toMatch(/<input [^>]*name="expectedTotal"[^>]*value="3200"/)
    for (const name of ['name', 'postalCode', 'prefectureCode', 'municipalityCode', 'address', 'phone']) {
      expect(html).toContain(`name="${name}"`)
    }
    expect(html).toContain('>注文する（3,200 円）</button>')
    expect(html).not.toContain('text-destructive')
  })

  test('AC-248、250 欄は郵便番号・都道府県・市区町村・住所の順で、都道府県は API の一覧から選び、初めは選んでいない', () => {
    const html = renderMarkup(form())

    const positions = ['postalCode', 'prefectureCode', 'municipalityCode', 'address'].map((name) =>
      html.indexOf(`name="${name}"`),
    )
    expect(positions).toEqual([...positions].sort((a, b) => a - b))
    expect(html).toMatch(/<option[^>]* value="13"[^>]*>東京都<\/option>/)
    expect(html).toMatch(/<option[^>]* value="14"[^>]*>神奈川県<\/option>/)
    expect(html).toMatch(/<option[^>]* disabled="" value="" selected="">選んでください<\/option>/)
  })

  test('AC-86 入力の誤りは、その欄の下に出す', () => {
    const html = renderMarkup(
      form({ ok: false, message: '郵便番号は 7 桁で入力してください。', errorCode: 'INVALID_POSTAL_CODE' }),
    )

    expect(html).toMatch(
      /name="postalCode"[^>]*\/>.*<p class="text-destructive text-sm">郵便番号は 7 桁で入力してください。<\/p>/,
    )
    expect(html).not.toContain('カートを見る')
  })

  test('AC-251 市区町村の誤りは、市区町村の欄の下に出す', () => {
    const html = renderMarkup(
      form({ ok: false, message: '市区町村を選んでください。', errorCode: 'INVALID_MUNICIPALITY' }),
    )

    expect(html).toMatch(
      /name="municipalityCode".*<\/select>.*<p class="text-destructive text-sm">市区町村を選んでください。<\/p>.*name="address"/,
    )
  })

  test('AC-87 在庫が足りなければ、フォームの下にメッセージとカートへのリンクを出す', () => {
    const html = renderMarkup(form({ ok: false, message: '在庫が足りない商品があります。', errorCode: 'OUT_OF_STOCK' }))

    expect(html).toContain('在庫が足りない商品があります。')
    expect(html).toContain('href="/cart">カートを見る</a>')
  })
})

describe('都道府県と市区町村を選ぶ', () => {
  test('AC-249 都道府県を選ぶと、その市区町村だけが一覧に出る。選び直すと、市区町村の選択が外れる', async () => {
    const { user } = renderInBrowser(form())

    await user.selectOptions(screen.getByLabelText('都道府県'), '13')
    await screen.findByRole('option', { name: '中央区' })
    await user.selectOptions(screen.getByLabelText('市区町村'), '13102')
    expect(listMunicipalities).toHaveBeenCalledWith('13')
    expect(screen.queryByRole('option', { name: '横浜市中区' })).toBeNull()
    expect(selected('市区町村')).toBe('13102')

    await user.selectOptions(screen.getByLabelText('都道府県'), '14')
    await screen.findByRole('option', { name: '横浜市中区' })
    expect(screen.queryByRole('option', { name: '中央区' })).toBeNull()
    expect(selected('市区町村')).toBe('')
  })
})

describe('郵便番号で補う', () => {
  test('AC-255 7 桁になると引き、1 件なら都道府県・市区町村を選び、空の住所の欄に町域を入れる', async () => {
    vi.mocked(findPostalCodeAddresses).mockResolvedValue([postal('13', '13101', '千代田')])
    const { user } = renderInBrowser(form())

    await user.type(screen.getByLabelText('郵便番号'), '100-000')
    expect(findPostalCodeAddresses).not.toHaveBeenCalled()
    await user.type(screen.getByLabelText('郵便番号'), '1')

    await waitFor(() => expect(selected('市区町村')).toBe('13101'))
    expect(findPostalCodeAddresses).toHaveBeenCalledWith('1000001')
    expect(selected('都道府県')).toBe('13')
    expect(addressValue()).toBe('千代田')
  })

  test('AC-255 住所の欄に入力があれば、町域を入れない', async () => {
    vi.mocked(findPostalCodeAddresses).mockResolvedValue([postal('13', '13101', '千代田')])
    const { user } = renderInBrowser(form())

    await user.type(screen.getByLabelText('住所'), '丸の内 1-1')
    await user.type(screen.getByLabelText('郵便番号'), '1000001')

    await waitFor(() => expect(selected('市区町村')).toBe('13101'))
    expect(addressValue()).toBe('丸の内 1-1')
  })

  test('複数の結果で都道府県と市区町村が同じなら選び、町域は入れない。違えば何も補わない', async () => {
    vi.mocked(findPostalCodeAddresses).mockResolvedValue([postal('13', '13102', '銀座'), postal('13', '13102', '築地')])
    const { user } = renderInBrowser(form())

    await user.type(screen.getByLabelText('郵便番号'), '1040061')
    await waitFor(() => expect(selected('市区町村')).toBe('13102'))
    expect(addressValue()).toBe('')

    vi.mocked(findPostalCodeAddresses).mockResolvedValue([postal('01', '01101', '甲'), postal('14', '14104', '乙')])
    await user.clear(screen.getByLabelText('郵便番号'))
    await user.type(screen.getByLabelText('郵便番号'), '9999998')
    await waitFor(() => expect(findPostalCodeAddresses).toHaveBeenCalledWith('9999998'))
    expect(selected('都道府県')).toBe('13')
    expect(selected('市区町村')).toBe('13102')
  })

  test('AC-256 結果がなければ（通信の失敗も空で届く）何も補わず、メッセージも出さない', async () => {
    const { user } = renderInBrowser(form())

    await user.type(screen.getByLabelText('郵便番号'), '9999999')

    await waitFor(() => expect(findPostalCodeAddresses).toHaveBeenCalledWith('9999999'))
    expect(selected('都道府県')).toBe('')
    expect(addressValue()).toBe('')
    expect(document.querySelector('.text-destructive')).toBeNull()
  })

  test('AC-257 補った後も、どの欄も変えられる', async () => {
    vi.mocked(findPostalCodeAddresses).mockResolvedValue([postal('13', '13101', '千代田')])
    const { user } = renderInBrowser(form())
    await user.type(screen.getByLabelText('郵便番号'), '1000001')
    await waitFor(() => expect(selected('市区町村')).toBe('13101'))

    await user.selectOptions(screen.getByLabelText('市区町村'), '13102')
    await user.type(screen.getByLabelText('住所'), ' 1-1')
    await user.selectOptions(screen.getByLabelText('都道府県'), '14')
    await screen.findByRole('option', { name: '横浜市中区' })
    await user.selectOptions(screen.getByLabelText('市区町村'), '14104')

    expect(selected('都道府県')).toBe('14')
    expect(selected('市区町村')).toBe('14104')
    expect(addressValue()).toBe('千代田 1-1')
  })
})
