import { Alert, AlertDescription } from '@ec-site/shared/ui/alert'
import { Button } from '@ec-site/shared/ui/button'
import { useTranslations } from 'use-intl'
import { ApiError, errorMessage, fieldErrors } from '@/api/errors'

/**
 * 操作した場所に出すエラー（T703。管理画面の構成 第 6 節）。<br />
 * エラーレスポンスの message をそのまま出し、入力欄に付けていない項目のエラーを並べる。
 * バージョン不一致（409）では、取得し直してフォームを作り直すボタンを出す（onReload）。
 */
export function ErrorMessage({
  error,
  onReload,
  shownFields = [],
}: {
  error: unknown
  /** 再読み込みのボタンで呼ぶ。取得し直しとフォームの作り直しを行う */
  onReload?: () => void
  /** 入力欄の下にすでに出している項目 */
  shownFields?: readonly string[]
}) {
  const t = useTranslations('components.errorMessage')
  if (!error) {
    return null
  }
  const fields = fieldErrors(error).filter((item) => !shownFields.includes(item.field))
  const concurrent = error instanceof ApiError && error.isConcurrentModification
  return (
    <Alert variant="destructive">
      <AlertDescription>
        <p>{errorMessage(error)}</p>
        {fields.length > 0 && (
          <ul className="list-inside list-disc">
            {fields.map((item) => (
              <li key={item.field}>{item.message}</li>
            ))}
          </ul>
        )}
        {concurrent && onReload && (
          <Button className="mt-2" onClick={onReload} size="sm" variant="outline">
            {t('reload') /* 再読み込み */}
          </Button>
        )}
      </AlertDescription>
    </Alert>
  )
}
