import { useTranslations } from 'use-intl'
import { PageHeader } from '@/components/PageHeader'

/** 売上の推移 */
export function SalesTrendPage() {
  const t = useTranslations('app.menu')
  return <PageHeader title={t('salesTrend') /* 売上の推移 */} />
}
