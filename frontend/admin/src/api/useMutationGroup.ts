/** まとめる操作に必要な状態（TanStack Query の useMutation の戻り値の一部） */
type MutationState = {
  isPending: boolean
  error: unknown
  reset: () => void
}

/** まとめ方の指定 */
type MutationGroupOptions = {
  /** エラーをまとめて出さない操作（ダイアログの中など、別の場所でエラーを出すもの） */
  errorsShownElsewhere?: MutationState[]
}

/**
 * 1 つの場所で行う複数の更新の操作をまとめる（管理画面の構成の「API の呼び出し」）。
 *
 * どれかが待っている間はほかの操作も止め、エラーは最初に失敗している操作のものを 1 つだけ出す。
 * 別の操作を始めるときは、前の操作のエラーを消す（run か resetErrors）。
 *
 * @param mutations まとめる操作
 * @param options   まとめ方の指定
 * @returns 待っているか、最初のエラー、エラーを消す関数、エラーを消してから実行する関数
 */
export function useMutationGroup(mutations: MutationState[], options: MutationGroupOptions = {}) {
  const excluded = options.errorsShownElsewhere ?? []
  const resetErrors = () => {
    for (const mutation of mutations) {
      mutation.reset()
    }
  }
  return {
    /** どれかが待っている */
    pending: mutations.some((mutation) => mutation.isPending),
    /** 最初に失敗している操作のエラー */
    error: mutations.find((mutation) => !excluded.includes(mutation) && mutation.error)?.error ?? undefined,
    /** すべての操作のエラーを消す */
    resetErrors,
    /** すべての操作のエラーを消してから、操作を実行する */
    run: (action: () => void) => {
      resetErrors()
      action()
    },
  }
}
