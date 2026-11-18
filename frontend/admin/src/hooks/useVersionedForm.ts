import { useEffect, useRef, useState } from 'react'
import { type DefaultValues, type FieldValues, type UseFormReturn, useForm } from 'react-hook-form'
import { deepEqual } from '@/lib/equal'

/** 同時に編集されうるリソース（API の規約の version を持つ） */
type Versioned = { version: number }

/** リソースを再取得する関数。取得の成否と、取得したリソース（data）を持つ結果を返す（TanStack Query の refetch と合う形） */
export type Refetch<R> = () => Promise<{ data?: R; isError: boolean }>

/**
 * 同時に編集されうるリソースのフォームを作る（管理画面の構成の「フォーム」）。
 *
 * 送るバージョン（{@link baseVersion}）は、フォームの基準にしたリソースのもの。
 *
 * - 入力を変える前にリソースが変わったら（自分の別の操作など）、フォームを最新の値で再作成する。
 * - 入力を変えた後にリソースが変わったときは、基準のリソースと新しいリソースのフォームの値を比べる。
 *   同じなら（自分の別の操作でバージョンだけ進んだ場合など）、入力を残して基準を新しいリソースに進める。
 *   違えば（他の人が更新した場合）、入力を残して基準を進めない。送ると 409 になり、古い入力で黙って上書きしない。
 * - 409 の後は {@link reloadAfter} で再取得し、取得できたら、バージョンが変わらなくても、フォームを最新の値で再作成して更新の状態を戻す。
 *   取得できなかったら、入力と更新の状態（409 の表示）を残す。
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
  const { isDirty } = form.formState

  useEffect(() => {
    if (resource.version === base.version) {
      return
    }
    if (!isDirty) {
      // 入力を変えていないときは再作成する（入力中の値を黙って捨てない）
      form.reset(toValuesRef.current(resource))
      setBase(resource)
    } else if (deepEqual(toValuesRef.current(base), toValuesRef.current(resource))) {
      // 入力を変えた後でも、リソースの値が基準と同じなら、入力を残してバージョンだけ進める
      setBase(resource)
    }
  }, [resource, base, isDirty, form])

  return {
    form,
    baseVersion: base.version,
    markSaved: () => form.reset(form.getValues()),
    reloadAfter: async (mutation) => {
      const { data, isError } = await refetch()
      // 取得できなかったときは、入力と更新の失敗の表示を残す（古い値で再作成しない）
      if (isError || !data) {
        return
      }
      form.reset(toValuesRef.current(data))
      setBase(data)
      mutation.reset()
    },
  }
}
