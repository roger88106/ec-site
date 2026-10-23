import { describe, expect, test } from 'vitest'
import { ShippingAddressDetails } from '@/features/order/components/ShippingAddressDetails'
import { SHIPPING_ADDRESS } from '@/test/fixtures'
import { renderMarkup } from '@/test/render'

describe('注文のお届け先', () => {
  test('AC-252 宛名、郵便番号と都道府県名・市区町村名・住所の順、電話番号、メールアドレスを項目名と合わせて出す', () => {
    const html = renderMarkup(<ShippingAddressDetails address={SHIPPING_ADDRESS} email="member@example.com" />)

    expect(html).toContain('山田 太郎 様')
    expect(html).toContain('〒100-0001 東京都千代田区千代田 1-1')
    expect(html).toContain('電話番号 0312345678')
    expect(html).toContain('メールアドレス member@example.com')
  })
})
