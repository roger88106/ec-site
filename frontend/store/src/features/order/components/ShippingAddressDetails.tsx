import { formatPostalCode } from '@ec-site/shared/lib/format'
import { useTranslations } from 'next-intl'
import type { ShippingAddress } from '@/api/types'

/**
 * 注文のお届け先と連絡先。住所は郵便番号、都道府県名・市区町村名（注文の時点の写し）、市区町村より下の住所の順に出す。
 */
export function ShippingAddressDetails({ address, email }: { address: ShippingAddress; email: string }) {
  const t = useTranslations('order.detail')
  const tCommon = useTranslations('common')
  return (
    <address className="space-y-1 text-sm not-italic">
      <p>{t('addressName', { name: address.name }) /* 宛名 */}</p>
      <p>
        {
          tCommon('order.postalAddress', {
            postalCode: formatPostalCode(address.postalCode),
            prefecture: address.prefectureName,
            municipality: address.municipalityName,
            address: address.address,
          }) /* 郵便番号と住所 */
        }
      </p>
      <p>{t('phone', { phone: address.phone }) /* 電話番号 */}</p>
      <p>{t('email', { email }) /* メールアドレス */}</p>
    </address>
  )
}
