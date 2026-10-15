import { useTranslations } from 'use-intl'
import { PageHeader } from '@/components/PageHeader'

/** 地域・年齢層・性別 */
export function CustomerBreakdownPage() {
  const t = useTranslations('app.menu')
  return <PageHeader title={t('customerBreakdown') /* 地域・年齢層・性別 */} />
}
