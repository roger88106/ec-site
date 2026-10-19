import { describe, expect, test } from 'vitest'
import { ShippingAddressDetails } from '@/features/order/components/ShippingAddressDetails'
import { renderMarkup } from '@/test/render'

describe('注文のお届け先', () => {
  test('AC-252 宛名、郵便番号と都道府県名・市区町村名・住所の順、電話番号、メールアドレスを出す', () => {
    const html = renderMarkup(
      <ShippingAddressDetails
        address={{
          name: '山田 太郎',
          postalCode: '1000001',
          prefectureCode: '13',
          prefectureName: '東京都',
          municipalityCode: '13101',
          municipalityName: '千代田区',
          address: '千代田 1-1',
          phone: '0312345678',
        }}
        email="member@example.com"
      />,
    )

    expect(html).toContain('山田 太郎 様')
    expect(html).toContain('〒100-0001 東京都千代田区千代田 1-1')
    expect(html).toContain('0312345678')
    expect(html).toContain('member@example.com')
  })
})
