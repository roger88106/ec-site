import { formatDateTime } from '@ec-site/shared/lib/format'
import { useTranslations } from 'use-intl'
import { useIdleLogout } from '@/auth/useIdleLogout'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'

/**
 * 操作がないまま 30 分たつと onExpire でログアウトし、その 2 分前に警告のダイアログを表示する。<br />
 * ログインしているときだけ置く（ログインしていないときに動かさないため）。
 *
 * @param props.onExpire ログアウトする（IdP のログアウトへ移る）
 */
export function IdleLogout({ onExpire }: { onExpire: () => void }) {
  const t = useTranslations('auth.idleWarning')
  const { warning, logoutAt, keepWorking } = useIdleLogout({ onExpire })
  return (
    <AlertDialog
      // Esc で閉じたときも、操作を続けるとして扱う
      onOpenChange={(open) => {
        if (!open) {
          keepWorking()
        }
      }}
      open={warning}
    >
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t('title') /* まもなくログアウト */}</AlertDialogTitle>
          <AlertDialogDescription>
            {
              t('description', {
                logoutAt: formatDateTime(new Date(logoutAt).toISOString()),
              }) /* ログアウトする時刻、続ける方法、未保存の入力が失われること */
            }
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogAction onClick={keepWorking}>{t('keepWorking') /* 操作を続ける */}</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
