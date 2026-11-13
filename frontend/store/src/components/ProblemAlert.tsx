import { Alert, AlertDescription } from '@ec-site/shared/ui/alert'

/**
 * API が 400 で返した問題の表示
 *
 * API のメッセージがあればそれを、なければ代わりの文を出す。
 */
export function ProblemAlert({
  error,
  fallback,
}: {
  /** API の問題の本文 */
  error?: { message?: string } | null
  /** API のメッセージがないときに出す文 */
  fallback: string
}) {
  return (
    <Alert variant="destructive">
      <AlertDescription>{error?.message ?? fallback}</AlertDescription>
    </Alert>
  )
}
