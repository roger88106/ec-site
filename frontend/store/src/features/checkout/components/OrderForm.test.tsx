// @vitest-environment jsdom
import { act, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import { findPostalCodeAddresses, listMunicipalities } from '@/actions/region'
import type { PostalCodeAddress, Prefecture } from '@/api/types'
import { OrderForm, OrderFormView } from '@/features/checkout/components/OrderForm'
import type { ActionResult } from '@/lib/actionResult'
import { input, select } from '@/test/elements'
import { renderInBrowser, renderStatic } from '@/test/render'

// 一覧と郵便番号は読むだけのサーバーアクションなので、結果を差し替えてフォームの操作を確認する（店舗画面の構成 第 7 節 ※6）
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

/** 注文のサーバーアクションの結果（null は送る前）を渡した、注文のフォームの表示 */
function form(result: ActionResult | null = null, formAction: (formData: FormData) => void = () => {}) {
  return (
    <OrderFormView
      checkoutId="checkout-1"
      email="member@example.com"
      expectedTotal={3200}
      formAction={formAction}
      pending={false}
      prefectures={PREFECTURES}
      result={result}
    />
  )
}

/** 選んでいる値 */
function selected(label: string): string {
  return select(label).value
}

/**
 * エラーの文が、その欄の入力欄の後で、次の欄の入力欄より前に出ることを確認する（見える文言の順）。
 *
 * @param view      HTML に描画したフォーム
 * @param message   エラーの文
 * @param label     エラーが出る欄の見出し
 * @param nextLabel 次の欄の見出し（最後の欄は省く。入力欄の後にあることだけを確認する）
 */
function expectErrorUnder(view: ReturnType<typeof renderStatic>, message: string, label: string, nextLabel?: string) {
  const error = view.getByText(message)
  expect(view.getByLabelText(label).compareDocumentPosition(error) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  if (nextLabel) {
    expect(
      error.compareDocumentPosition(view.getByLabelText(nextLabel)) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy()
  }
}

/**
 * 送る前（結果がない）の初期表示で、文字を直接持つ要素の文言を、文書の順に並べたもの
 * （見出し、メールアドレスと案内、各欄の見出しとヒント、選択肢、ボタン）。エラーの文は含まない。
 */
const INITIAL_TEXTS = [
  'メールアドレス',
  'member@example.com',
  'ご注文の連絡は、会員のメールアドレスにお送りします。',
  '氏名',
  '郵便番号',
  '例：100-0001',
  '都道府県',
  '選んでください',
  '北海道',
  '東京都',
  '神奈川県',
  '市区町村',
  '都道府県を先に選んでください',
  '住所',
  '町名、番地、建物名',
  '電話番号',
  '例：03-1234-5678',
  '注文する（3,200 円）',
]

/** 住所の欄の値 */
function addressValue(): string {
  return input('住所').value
}

beforeEach(() => {
  vi.mocked(listMunicipalities).mockImplementation(async (code) => MUNICIPALITIES[code] ?? null)
  vi.mocked(findPostalCodeAddresses).mockResolvedValue([])
})

describe('お届け先の入力と注文', () => {
  test('AC-127 会員のメールアドレスを示し、お届け先の欄と、総額を添えた注文のボタンを出す', () => {
    const view = renderStatic(form())

    expect(view.getByText('member@example.com')).toBeInTheDocument()
    expect(view.getByText('ご注文の連絡は、会員のメールアドレスにお送りします。')).toBeInTheDocument()
    expect(view.getByDisplayValue('checkout-1')).toHaveAttribute('name', 'checkoutId')
    expect(view.getByDisplayValue('3200')).toHaveAttribute('name', 'expectedTotal')
    expect(view.getByRole('textbox', { name: '氏名' })).toHaveAttribute('name', 'name')
    expect(view.getByRole('textbox', { name: '郵便番号' })).toHaveAttribute('name', 'postalCode')
    expect(view.getByRole('combobox', { name: '都道府県' })).toHaveAttribute('name', 'prefectureCode')
    expect(view.getByRole('combobox', { name: '市区町村' })).toHaveAttribute('name', 'municipalityCode')
    expect(view.getByRole('textbox', { name: '住所' })).toHaveAttribute('name', 'address')
    expect(view.getByRole('textbox', { name: '電話番号' })).toHaveAttribute('name', 'phone')
    expect(view.getByRole('button', { name: '注文する（3,200 円）' })).toBeInTheDocument()
    // 初期表示には、エラーメッセージも欄ごとの誤りもない（文字を直接持つ要素は、これだけ）
    expect(view.queryAllByText(/./).map((element) => element.textContent)).toEqual(INITIAL_TEXTS)
  })

  test('AC-248、250 欄は郵便番号・都道府県・市区町村・住所の順で、都道府県は API の一覧から選び、初めは選んでいない', () => {
    const view = renderStatic(form())

    // 欄は、画面に出る順（文書の順）に返る
    const fields = view.getAllByLabelText(/^(郵便番号|都道府県|市区町村|住所)$/)
    expect(fields.map((field) => field.getAttribute('name'))).toEqual([
      'postalCode',
      'prefectureCode',
      'municipalityCode',
      'address',
    ])
    const prefecture = within(view.getByRole('combobox', { name: '都道府県' }))
    expect(prefecture.getByRole('option', { name: '東京都' })).toHaveAttribute('value', '13')
    expect(prefecture.getByRole('option', { name: '神奈川県' })).toHaveAttribute('value', '14')
    // 先頭の「選んでください」は、選んでいて、選べない
    expect(view.getByRole('combobox', { name: '都道府県' })).toHaveValue('')
    expect(prefecture.getByRole('option', { name: '選んでください' })).toBeDisabled()
  })

  test('AC-86 入力の誤りは、その欄の下に出す', () => {
    const view = renderStatic(
      form({ ok: false, message: '郵便番号は 7 桁で入力してください。', errorCode: 'INVALID_POSTAL_CODE' }),
    )

    expectErrorUnder(view, '郵便番号は 7 桁で入力してください。', '郵便番号', '都道府県')
    expect(view.queryByRole('link', { name: 'カートを見る' })).not.toBeInTheDocument()
  })

  test('AC-264 入力の検証の誤り（400）は、欄ごとに API の文をそのまま出す', () => {
    const view = renderStatic(
      form({
        ok: false,
        message: '入力内容に誤りがあります。',
        errorCode: 'INVALID_REQUEST',
        fieldErrors: { 'shippingAddress.phone': 'API が決めた電話番号の文' },
      }),
    )

    expectErrorUnder(view, 'API が決めた電話番号の文', '電話番号')
  })

  test('AC-251 市区町村の誤りは、市区町村の欄の下に出す', () => {
    const view = renderStatic(
      form({ ok: false, message: '市区町村を選んでください。', errorCode: 'INVALID_MUNICIPALITY' }),
    )

    expectErrorUnder(view, '市区町村を選んでください。', '市区町村', '住所')
  })

  test('AC-87 在庫が足りなければ、フォームの下にメッセージとカートへのリンクを出す', () => {
    const view = renderStatic(form({ ok: false, message: '在庫が足りない商品があります。', errorCode: 'OUT_OF_STOCK' }))

    const message = view.getByText('在庫が足りない商品があります。')
    expect(message).toHaveAttribute('aria-live', 'polite')
    expect(within(message).getByRole('link', { name: 'カートを見る' })).toHaveAttribute('href', '/cart')
  })

  test('結果を受ける前の OrderForm は、表示と同じ初期表示を出す', () => {
    const view = renderStatic(
      <OrderForm checkoutId="checkout-1" email="member@example.com" expectedTotal={3200} prefectures={PREFECTURES} />,
    )

    expect(view.queryAllByText(/./).map((element) => element.textContent)).toEqual(INITIAL_TEXTS)
    expect(view.getByRole('button', { name: '注文する（3,200 円）' })).toBeEnabled()
  })

  test('注文のボタンを押すと、入力した値と隠しの値を送る関数に渡す', async () => {
    const formAction = vi.fn<(formData: FormData) => void>()
    const { user } = renderInBrowser(form(null, formAction))

    await user.type(screen.getByLabelText('氏名'), '山田 太郎')
    await user.click(screen.getByRole('button', { name: '注文する（3,200 円）' }))

    expect(formAction).toHaveBeenCalledTimes(1)
    const sent = formAction.mock.calls[0]?.[0]
    expect(sent?.get('name')).toBe('山田 太郎')
    expect(sent?.get('checkoutId')).toBe('checkout-1')
    expect(sent?.get('expectedTotal')).toBe('3200')
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
    const textBefore = document.body.textContent

    await user.type(screen.getByLabelText('郵便番号'), '9999999')

    await waitFor(() => expect(findPostalCodeAddresses).toHaveBeenCalledWith('9999999'))
    // 結果（空の配列）の処理が終わってから、変わらないことを確認する
    await vi.mocked(findPostalCodeAddresses).mock.results[0]?.value
    await act(async () => {})
    expect(selected('都道府県')).toBe('')
    expect(addressValue()).toBe('')
    expect(document.body.textContent).toBe(textBefore)
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
