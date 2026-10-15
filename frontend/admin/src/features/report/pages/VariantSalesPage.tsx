import { useTranslations } from 'use-intl'
import { PageHeader } from '@/components/PageHeader'

/** バリエーションの売れ行き */
export function VariantSalesPage() {
  const t = useTranslations('app.menu')
  return <PageHeader title={t('variantSales') /* バリエーションの売れ行き */} />
}
