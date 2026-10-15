import type { ReactNode } from 'react'
import { useTranslations } from 'use-intl'
import { type Permission, usePermissions } from '@/auth/permissions'

/**
 * 権限のある運営者にだけ画面を出す。権限がなければ、URL で開いても「権限がない」を示す（API も呼ばない）。<br />
 * 権限の一覧を読み込む間は何も出さない。
 */
export function RequirePermission({ permission, children }: { permission: Permission; children: ReactNode }) {
  const t = useTranslations('auth')
  const permissions = usePermissions()
  if (permissions.isLoading) {
    return null
  }
  if (!permissions.has(permission)) {
    return <p className="text-muted-foreground">{t('forbidden') /* 権限がない */}</p>
  }
  return children
}
