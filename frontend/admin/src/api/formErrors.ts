import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import { fieldErrors } from '@/api/errors'

/**
 * サーバーの入力検証エラー（errors[]）を、対応する入力欄に付ける（管理画面の構成の「フォーム」）。<br />
 * 付けた項目の名前を返す。フォームにない項目のエラーは、呼び出し側がフォームの下に出す。
 */
export function setServerFieldErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: readonly Path<T>[],
): string[] {
  const applied: string[] = []
  for (const item of fieldErrors(error)) {
    const field = fields.find((name) => name === item.field)
    if (field) {
      setError(field, { type: 'server', message: item.message })
      applied.push(item.field)
    }
  }
  return applied
}
