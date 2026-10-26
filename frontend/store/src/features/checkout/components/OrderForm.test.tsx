// @vitest-environment jsdom
import { act, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import { findPostalCodeAddresses, listMunicipalities } from '@/actions/region'
import { OrderForm, OrderFormView } from '@/features/checkout/components/OrderForm'
import type { ActionResult } from '@/lib/actionResult'
import { input, select } from '@/test/elements'
import { MUNICIPALITIES, PREFECTURES, postalCodeAddress as postal } from '@/test/fixtures'
import { renderInBrowser, renderStatic } from '@/test/render'

// 一覧と郵便番号は読むだけのサーバーアクションなので、結果を差し替えてフォームの操作を確認する（店舗画面の構成 第 7 節 ※6）
vi.mock('@/actions/region', () => ({ listMunicipalities: vi.fn(), findPostalCodeAddresses: vi.fn() }))

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

/** 欄の見出し、API の項目名、補足の文（ない欄は null）。氏名・郵便番号・都道府県・市区町村・住所・電話番号 */
const FIELDS = [
  ['氏名', 'shippingAddress.name', null],
  ['郵便番号', 'shippingAddress.postalCode', '例：100-0001'],
  ['都道府県', 'shippingAddress.prefectureCode', null],
  ['市区町村', 'shippingAddress.municipalityCode', null],
  ['住所', 'shippingAddress.address', '町名、番地、建物名'],
  ['電話番号', 'shippingAddress.phone', '例：03-1234-5678'],
] as const

/** 指定した項目だけに誤りがある、入力の検証の誤り（400）の結果 */
function fieldError(path: string): ActionResult {
  return {
    ok: false,
    message: '入力内容に誤りがあります。',
    errorCode: 'INVALID_REQUEST',
    fieldErrors: { [path]: `${path} の誤りの文` },
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
    expect(fields).toHaveLength(4)
    expect(fields[0]).toHaveAttribute('name', 'postalCode')
    expect(fields[1]).toHaveAttribute('name', 'prefectureCode')
    expect(fields[2]).toHaveAttribute('name', 'municipalityCode')
    expect(fields[3]).toHaveAttribute('name', 'address')
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

    expect(view.getByLabelText('郵便番号')).toHaveAccessibleDescription(
      '例：100-0001 郵便番号は 7 桁で入力してください。',
    )
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

    expect(view.getByLabelText('電話番号')).toHaveAccessibleDescription('例：03-1234-5678 API が決めた電話番号の文')
  })

  test('AC-251 市区町村の誤りは、市区町村の欄の下に出す', () => {
    const view = renderStatic(
      form({ ok: false, message: '市区町村を選んでください。', errorCode: 'INVALID_MUNICIPALITY' }),
    )

    expect(view.getByLabelText('市区町村')).toHaveAccessibleDescription('市区町村を選んでください。')
  })

  test('AC-87 在庫が足りなければ、フォームの下にメッセージとカートへのリンクを出す', () => {
    const view = renderStatic(form({ ok: false, message: '在庫が足りない商品があります。', errorCode: 'OUT_OF_STOCK' }))

    const message = view.getByText('在庫が足りない商品があります。')
    expect(message).toHaveAttribute('role', 'status')
    expect(within(message).getByRole('link', { name: 'カートを見る' })).toHaveAttribute('href', '/cart')
  })

  test('AC-193-1 失敗の文は、送る前からある状態の知らせ（status）の領域に入る。領域は 1 つだけ', () => {
    // 送る前も、領域は 1 つあり、文はない
    const before = renderStatic(form(null))
    expect(before.getAllByRole('status')).toHaveLength(1)
    expect(before.getByRole('status')).toBeEmptyDOMElement()

    const after = renderStatic(
      form({ ok: false, message: '在庫が足りない商品があります。', errorCode: 'OUT_OF_STOCK' }),
    )

    // 失敗の後は、同じ領域に文が入る
    expect(after.getAllByRole('status')).toHaveLength(1)
    expect(after.getByRole('status')).toHaveTextContent('在庫が足りない商品があります。')
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

describe('欄の誤りと補足を入力欄に結び付ける', () => {
  test.each(FIELDS)(
    'AC-193-4、AC-193-5 %s の誤りの文は入力欄の説明になり、その欄は誤りの状態になる',
    (label, path, hint) => {
      const view = renderStatic(form(fieldError(path)))

      expect(view.getByLabelText(label)).toHaveAccessibleDescription(
        [hint, `${path} の誤りの文`].filter(Boolean).join(' '),
      )
      expect(view.getByLabelText(label)).toBeInvalid()
      // 誤りのない他の欄は、誤りの状態を伝えない
      for (const [otherLabel] of FIELDS.filter(([other]) => other !== label)) {
        expect(view.getByLabelText(otherLabel)).toBeValid()
        expect(view.getByLabelText(otherLabel)).not.toHaveAttribute('aria-invalid')
      }
    },
  )

  test.each(FIELDS)('AC-193-6 %s は誤りがないとき、補足の文だけが入力欄の説明になる', (label, _path, hint) => {
    const view = renderStatic(form())

    expect(view.getByLabelText(label)).toHaveAccessibleDescription(hint ?? '')
    expect(view.getByLabelText(label)).toBeValid()
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
    expect(select('市区町村')).toHaveValue('13102')

    await user.selectOptions(screen.getByLabelText('都道府県'), '14')
    await screen.findByRole('option', { name: '横浜市中区' })
    expect(screen.queryByRole('option', { name: '中央区' })).toBeNull()
    expect(select('市区町村')).toHaveValue('')
  })
})

describe('郵便番号で補う', () => {
  test('AC-255 7 桁になると引き、1 件なら都道府県・市区町村を選び、空の住所の欄に町域を入れる', async () => {
    vi.mocked(findPostalCodeAddresses).mockResolvedValue([postal('13', '13101', '千代田')])
    const { user } = renderInBrowser(form())

    await user.type(screen.getByLabelText('郵便番号'), '100-000')
    expect(findPostalCodeAddresses).not.toHaveBeenCalled()
    await user.type(screen.getByLabelText('郵便番号'), '1')

    await waitFor(() => expect(select('市区町村')).toHaveValue('13101'))
    expect(findPostalCodeAddresses).toHaveBeenCalledWith('1000001')
    expect(select('都道府県')).toHaveValue('13')
    expect(input('住所')).toHaveValue('千代田')
  })

  test('AC-255 住所の欄に入力があれば、町域を入れない', async () => {
    vi.mocked(findPostalCodeAddresses).mockResolvedValue([postal('13', '13101', '千代田')])
    const { user } = renderInBrowser(form())

    await user.type(screen.getByLabelText('住所'), '丸の内 1-1')
    await user.type(screen.getByLabelText('郵便番号'), '1000001')

    await waitFor(() => expect(select('市区町村')).toHaveValue('13101'))
    expect(input('住所')).toHaveValue('丸の内 1-1')
  })

  test('複数の結果で都道府県と市区町村が同じなら選び、町域は入れない。違えば何も補わない', async () => {
    vi.mocked(findPostalCodeAddresses).mockResolvedValue([postal('13', '13102', '銀座'), postal('13', '13102', '築地')])
    const { user } = renderInBrowser(form())

    await user.type(screen.getByLabelText('郵便番号'), '1040061')
    await waitFor(() => expect(select('市区町村')).toHaveValue('13102'))
    expect(input('住所')).toHaveValue('')

    vi.mocked(findPostalCodeAddresses).mockResolvedValue([postal('01', '01101', '甲'), postal('14', '14104', '乙')])
    await user.clear(screen.getByLabelText('郵便番号'))
    await user.type(screen.getByLabelText('郵便番号'), '9999998')
    await waitFor(() => expect(findPostalCodeAddresses).toHaveBeenCalledWith('9999998'))
    expect(select('都道府県')).toHaveValue('13')
    expect(select('市区町村')).toHaveValue('13102')
  })

  test('AC-256 結果がなければ（通信の失敗も空で届く）何も補わず、メッセージも出さない', async () => {
    const { user } = renderInBrowser(form())
    const textBefore = document.body.textContent

    await user.type(screen.getByLabelText('郵便番号'), '9999999')

    await waitFor(() => expect(findPostalCodeAddresses).toHaveBeenCalledWith('9999999'))
    // 結果（空の配列）の処理が終わってから、変わらないことを確認する
    await vi.mocked(findPostalCodeAddresses).mock.results[0]?.value
    await act(async () => {})
    expect(select('都道府県')).toHaveValue('')
    expect(input('住所')).toHaveValue('')
    expect(document.body.textContent).toBe(textBefore)
  })

  test('AC-257 補った後も、どの欄も変えられる', async () => {
    vi.mocked(findPostalCodeAddresses).mockResolvedValue([postal('13', '13101', '千代田')])
    const { user } = renderInBrowser(form())
    await user.type(screen.getByLabelText('郵便番号'), '1000001')
    await waitFor(() => expect(select('市区町村')).toHaveValue('13101'))

    await user.selectOptions(screen.getByLabelText('市区町村'), '13102')
    await user.type(screen.getByLabelText('住所'), ' 1-1')
    await user.selectOptions(screen.getByLabelText('都道府県'), '14')
    await screen.findByRole('option', { name: '横浜市中区' })
    await user.selectOptions(screen.getByLabelText('市区町村'), '14104')

    expect(select('都道府県')).toHaveValue('14')
    expect(select('市区町村')).toHaveValue('14104')
    expect(input('住所')).toHaveValue('千代田 1-1')
  })
})
