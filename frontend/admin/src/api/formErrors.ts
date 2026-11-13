import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import { fieldErrors } from '@/api/errors'

/**
 * サーバーの入力検証エラー（errors[]）を、対応する入力欄に付ける（管理画面の構成の「フォーム」）。
 *
 * @param error    更新の操作が返したエラー
 * @param setError React Hook Form の setError
 * @param fields   入力欄の項目の名前
 * @returns エラーを付けた項目の名前。フォームにない項目のエラーは含まない
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
