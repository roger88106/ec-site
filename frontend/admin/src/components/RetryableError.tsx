import { Alert, AlertDescription } from '@ec-site/shared/ui/alert'
import { Button } from '@ec-site/shared/ui/button'
import { useTranslations } from 'use-intl'
import { errorMessage } from '@/api/errors'

/**
 * 取得の失敗の文と、再試行のボタン
 *
 * 再取得している間は、ボタンを押せない。再試行しても結果が変わらない失敗（4xx）では、retryable を偽にして、ボタンを出さない。
 */
export function RetryableError({
  error,
  fetching,
  onRetry,
  retryable = true,
}: {
  error: unknown
  /** 再取得している */
  fetching: boolean
  onRetry: () => void
  /** 再試行のボタンを出すか */
  retryable?: boolean
}) {
  const t = useTranslations('components.retryableError')
  return (
    <Alert variant="destructive">
      <AlertDescription>
        <p>{errorMessage(error)}</p>
        {retryable && (
          <Button className="mt-2" disabled={fetching} onClick={onRetry} size="sm" variant="outline">
            {t('retry') /* 再試行 */}
          </Button>
        )}
      </AlertDescription>
    </Alert>
  )
}
