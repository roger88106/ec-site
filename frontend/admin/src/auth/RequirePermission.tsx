import { Alert, AlertDescription } from '@ec-site/shared/ui/alert'
import { Button } from '@ec-site/shared/ui/button'
import type { ReactNode } from 'react'
import { useTranslations } from 'use-intl'
import { errorMessage } from '@/api/errors'
import { type Permission, usePermissions } from '@/auth/permissions'

/**
 * 権限のある運営者にだけ画面を出す。権限がなければ、URL で開いても「権限がない」を示す（API も呼ばない）。<br />
 * 権限の一覧を読み込む間は何も出さない。取得に失敗したら、権限がないとは区別して、エラーと再試行のボタンを示す（取得し直す間はボタンを押せない）。
 */
export function RequirePermission({ permission, children }: { permission: Permission; children: ReactNode }) {
  const t = useTranslations('auth')
  const permissions = usePermissions()
  if (permissions.status === 'loading') {
    return null
  }
  if (permissions.status === 'failed') {
    return (
      <Alert variant="destructive">
        <AlertDescription>
          <p>{errorMessage(permissions.error)}</p>
          <Button
            className="mt-2"
            disabled={permissions.isFetching}
            onClick={permissions.retry}
            size="sm"
            variant="outline"
          >
            {t('permissionsFailed.retry') /* 再試行 */}
          </Button>
        </AlertDescription>
      </Alert>
    )
  }
  if (!permissions.has(permission)) {
    return <p className="text-muted-foreground">{t('forbidden') /* 権限がない */}</p>
  }
  return children
}
