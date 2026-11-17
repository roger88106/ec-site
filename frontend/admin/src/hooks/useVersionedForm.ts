import { useEffect, useRef, useState } from 'react'
import { type DefaultValues, type FieldValues, type UseFormReturn, useForm } from 'react-hook-form'
import { deepEqual } from '@/lib/equal'

/** 同時に編集されうるリソース（API の規約の version を持つ） */
type Versioned = { version: number }

/** リソースを取得し直す関数。取得し直したリソースを data に持つ結果を返す（TanStack Query の refetch と合う形） */
export type Refetch<R> = () => Promise<{ data?: R }>

/**
 * 同時に編集されうるリソースのフォームを作る（管理画面の構成の「フォーム」）。
 *
 * 送るバージョン（{@link baseVersion}）は、フォームの基準にしたリソースのもの。
 *
 * - 入力を変える前にリソースが変わったら（自分の別の操作など）、フォームを最新の値で再作成する。
 * - 入力を変えた後にリソースが変わったときは、基準のリソースと新しいリソースのフォームの値を比べる。
 *   同じなら（自分の別の操作でバージョンだけ進んだ場合など）、入力を残して基準を新しいリソースに進める。
 *   違えば（他の人が更新した場合）、入力を残して基準を進めない。送ると 409 になり、古い入力で黙って上書きしない。
 * - 409 の後は {@link reloadAfter} で更新の状態を戻して取得し直し、バージョンが変わらなくても、フォームを最新の値で再作成する。
 *
 * 保存に成功したら {@link markSaved} を呼ぶ。入力を変えていない状態に戻し、取得し直した値で再作成できるようにする。
 *
 * @param resource 取得したリソース
 * @param toValues リソースからフォームの値を作る
 * @param refetch  リソースを取得し直す
 * @returns フォームと、送るバージョン、保存の後と 409 の後に呼ぶ関数
 */
export function useVersionedForm<R extends Versioned, T extends FieldValues>(
  resource: R,
  toValues: (resource: R) => T,
  refetch: Refetch<R>,
): {
  form: UseFormReturn<T>
  baseVersion: number
  markSaved: () => void
  reloadAfter: (mutation: { reset: () => void }) => Promise<void>
} {
  // 呼ぶ側が毎回新しい関数を渡しても、effect が走り直さないよう、最新の関数を ref に持つ
  const toValuesRef = useRef(toValues)
  toValuesRef.current = toValues
  const form = useForm<T>({ defaultValues: toValues(resource) as DefaultValues<T> })
  const [base, setBase] = useState(resource)
  const [reloading, setReloading] = useState(false)
  // 取得し直しの途中に、effect が再作成を済ませたか（reloadAfter での二重の再作成を避ける）
  const recreatedRef = useRef(false)
  const { isDirty } = form.formState

  useEffect(() => {
    if (resource.version === base.version) {
      return
    }
    if (reloading || !isDirty) {
      // 取得し直しの途中か、入力を変えていないときは再作成する（入力中の値を黙って捨てない）
      form.reset(toValuesRef.current(resource))
      if (reloading) {
        recreatedRef.current = true
      }
      setBase(resource)
      setReloading(false)
    } else if (deepEqual(toValuesRef.current(base), toValuesRef.current(resource))) {
      // 入力を変えた後でも、リソースの値が基準と同じなら、入力を残してバージョンだけ進める
      setBase(resource)
    }
  }, [resource, base, isDirty, reloading, form])

  return {
    form,
    baseVersion: base.version,
    markSaved: () => form.reset(form.getValues()),
    reloadAfter: async (mutation) => {
      mutation.reset()
      recreatedRef.current = false
      setReloading(true)
      const { data } = await refetch()
      if (data && !recreatedRef.current) {
        form.reset(toValuesRef.current(data))
        setBase(data)
      }
      setReloading(false)
    },
  }
}
