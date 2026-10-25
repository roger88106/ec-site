import { describe, test } from 'vitest'
import { ShippingAddressDetails } from '@/features/order/components/ShippingAddressDetails'
import { expectInOrder } from '@/test/elements'
import { SHIPPING_ADDRESS } from '@/test/fixtures'
import { renderStatic } from '@/test/render'

describe('注文のお届け先', () => {
  test('AC-252 宛名、郵便番号と都道府県名・市区町村名・住所の順、電話番号、メールアドレスを項目名と合わせて出す', () => {
    const view = renderStatic(<ShippingAddressDetails address={SHIPPING_ADDRESS} email="member@example.com" />)

    // 項目名と値は 1 つの文として出るので、文全体の一致で対応を確認する
    const recipient = view.getByText('山田 太郎 様')
    const address = view.getByText('〒100-0001 東京都千代田区千代田 1-1')
    const phone = view.getByText('電話番号 0312345678')
    const email = view.getByText('メールアドレス member@example.com')
    // 宛名、住所、電話番号、メールアドレスの順に出る
    expectInOrder(recipient, address, phone, email)
  })
})
