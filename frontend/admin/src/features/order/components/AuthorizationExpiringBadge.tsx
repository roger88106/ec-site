import { Badge } from '@ec-site/shared/ui/badge'
import { useTranslations } from 'use-intl'

/** 与信期限が近い「受付」の注文の印。期限までに出荷しないと売上を確定できないので目立たせる */
export function AuthorizationExpiringBadge() {
  const t = useTranslations('order')
  return <Badge variant="destructive">{t('authorizationExpiring') /* 期限が近い */}</Badge>
}
