import { useTranslations } from 'use-intl'
/** 存在しない URL */
export function NotFoundPage() {
  const t = useTranslations('app.notFound')
  return <p className="text-muted-foreground">{t('message') /* ページがない */}</p>
}
