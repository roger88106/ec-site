import { useEffect, useRef, useState } from 'react'
import { type DefaultValues, type FieldValues, type UseFormReturn, useForm } from 'react-hook-form'

/** 同時に編集されうるリソース（constitution 9 の version を持つ） */
type Versioned = { version: number }

/**
 * 同時に編集されうるリソースのフォーム（管理画面の構成 第 5 節）。<br />
 * 送るバージョン（{@link baseVersion}）は、フォームを作ったときのリソースのもの。
 * <ul>
 *   <li>入力を変える前にリソースが変わったら（自分の別の操作など）、フォームを最新の値で作り直す。</li>
 *   <li>入力を変えた後に他の人が更新していれば、送ると 409 になる。古い入力で黙って上書きしないため。</li>
 *   <li>409 の後は {@link reload} で取得し直し、フォームを最新の値で作り直す。</li>
 * </ul>
 * 保存に成功したら {@link markSaved} を呼ぶ。入力を変えていない状態に戻し、取得し直した値で作り直せるようにする。
 *
 * @param resource 取得したリソース
 * @param toValues リソースからフォームの値を作る
 * @param refetch リソースを取得し直す
 */
export function useVersionedForm<R extends Versioned, T extends FieldValues>(
  resource: R,
  toValues: (resource: R) => T,
  refetch: () => Promise<unknown>,
): {
  form: UseFormReturn<T>
  baseVersion: number
  markSaved: () => void
  reload: () => Promise<void>
} {
  const toValuesRef = useRef(toValues)
  toValuesRef.current = toValues
  const form = useForm<T>({ defaultValues: toValues(resource) as DefaultValues<T> })
  const [baseVersion, setBaseVersion] = useState(resource.version)
  const [reloading, setReloading] = useState(false)
  const { isDirty } = form.formState

  useEffect(() => {
    if ((reloading || !isDirty) && resource.version !== baseVersion) {
      form.reset(toValuesRef.current(resource))
      setBaseVersion(resource.version)
      setReloading(false)
    }
  }, [resource, isDirty, reloading, baseVersion, form])

  return {
    form,
    baseVersion,
    markSaved: () => form.reset(form.getValues()),
    reload: async () => {
      setReloading(true)
      await refetch()
    },
  }
}
