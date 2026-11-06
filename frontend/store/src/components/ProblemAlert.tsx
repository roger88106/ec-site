import { Alert, AlertDescription } from '@ec-site/shared/ui/alert'

/**
 * API が 400 で返した問題の表示。API のメッセージがあればそれを、なければ代わりの文を出す。
 *
 * @param error    API の問題の本文（なければ代わりの文）
 * @param fallback 代わりの文
 */
export function ProblemAlert({ error, fallback }: { error?: { message?: string } | null; fallback: string }) {
  return (
    <Alert variant="destructive">
      <AlertDescription>{error?.message ?? fallback}</AlertDescription>
    </Alert>
  )
}
