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

/** IdleLogout の props */
type Props = {
  /** ログアウトする（IdP のログアウトへ移る） */
  onExpire: () => void
}

/**
 * 無操作のログアウトと警告
 *
 * 操作がないまま IDLE_LIMIT_MS の時間がたつと onExpire でログアウトし、IDLE_WARNING_BEFORE_MS 前に警告のダイアログを表示する。
 * ログインしているときだけ置く（ログインしていないときに動かさないため）。
 */
export function IdleLogout({ onExpire }: Props) {
  const t = useTranslations('auth.idleWarning')
  const { warning, logoutAt, keepWorking } = useIdleLogout({ onExpire })
  return (
    // Esc で閉じる処理は置かない。Esc の keydown も操作に数え、フックが警告を閉じる
    <AlertDialog open={warning}>
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
