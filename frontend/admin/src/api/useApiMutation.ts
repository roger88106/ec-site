import type { UseMutationResult } from '@tanstack/react-query'
import type { FetchResponse, MaybeOptionalInit } from 'openapi-fetch'
import { toast } from 'sonner'
import { $api } from '@/api/client'
import type { paths } from '@/api/generated/admin'
import { type ApiPathPrefix, useInvalidate } from '@/api/invalidate'

/** 更新に使う HTTP のメソッド */
type Method = 'post' | 'put' | 'delete'

/** 指定したメソッドを持つ、管理 API のパス */
type PathWith<M extends Method> = {
  [P in keyof paths]: paths[P] extends { [K in M]: object } ? P : never
}[keyof paths]

/** 送る値（パス・クエリのパラメーターと本文） */
type Init<M extends Method, P extends PathWith<M>> = MaybeOptionalInit<paths[P], M>

/** 成功したときの応答の本文 */
type Data<M extends Method, P extends PathWith<M>> = Required<
  FetchResponse<paths[P][M] & Record<string, unknown>, Init<M, P>, `${string}/${string}`>
>['data']

/** 更新の操作の定型の指定 */
type ApiMutationOptions<TData, TInit> = {
  /** 成功の知らせ。応答や送った値から文を作るときは関数にする */
  success?: string | ((data: TData, init: TInit) => string)
  /** 成功の後に取り直す一覧 */
  invalidate?: ApiPathPrefix[]
  /** 知らせの後、一覧の取り直しの前に行う処理（入力欄を空に戻す、画面を移るなど） */
  onSuccess?: (data: TData, init: TInit) => unknown
  /** 失敗したときの処理（入力欄にエラーを付けるなど） */
  onError?: (error: unknown) => void
}

/**
 * 管理 API の更新の操作（管理画面の構成の「API の呼び出し」）。$api.useMutation に、更新の定型を追加する。<br />
 * 成功したら、知らせを出し、続きの処理を行い、関係する一覧を取り直す。失敗のエラーは、戻り値の error で画面に出す。
 *
 * @param method HTTP のメソッド
 * @param path   管理 API のパス
 * @param options 成功の知らせ、取り直す一覧、続きの処理
 */
export function useApiMutation<M extends Method, P extends PathWith<M>>(
  method: M,
  path: P,
  options: ApiMutationOptions<Data<M, P>, Init<M, P>> = {},
) {
  const invalidate = useInvalidate()
  const { success, invalidate: prefixes = [], onSuccess, onError } = options
  // パスとメソッドの組から送る値と応答の型を決める所は、$api.useMutation と同じ。ここでは型の引数を渡すだけにする
  const useMutation = $api.useMutation as unknown as (
    method: M,
    path: P,
    options: {
      onSuccess: (data: Data<M, P>, init: Init<M, P>) => Promise<void>
      onError: (error: unknown) => void
    },
  ) => UseMutationResult<Data<M, P>, unknown, Init<M, P>>
  return useMutation(method, path, {
    onSuccess: async (data, init) => {
      if (success !== undefined) {
        toast.success(typeof success === 'function' ? success(data, init) : success)
      }
      await onSuccess?.(data, init)
      if (prefixes.length > 0) {
        await invalidate(...prefixes)
      }
    },
    onError: (error) => onError?.(error),
  })
}
