import type { ReactNode } from 'react'
import { useTranslations } from 'use-intl'
import { type Permission, usePermissions } from '@/auth/permissions'
import { RetryableError } from '@/components/RetryableError'

/**
 * 権限のある運営者にだけ出す枠
 *
 * 権限がなければ、URL で開いても「権限がない」を示す（API も呼ばない）。
 * 権限の一覧を読み込む間は何も出さない。取得に失敗したら、権限がないとは区別して、エラーと再試行のボタンを示す（取得し直す間はボタンを押せない）。
 */
export function RequirePermission({ permission, children }: { permission: Permission; children: ReactNode }) {
  const t = useTranslations('auth')
  const permissions = usePermissions()
  if (permissions.status === 'loading') {
    return null
  }
  if (permissions.status === 'failed') {
    return <RetryableError error={permissions.error} fetching={permissions.isFetching} onRetry={permissions.retry} />
  }
  if (!permissions.has(permission)) {
    return <p className="text-muted-foreground">{t('forbidden') /* 権限がない */}</p>
  }
  return children
}
