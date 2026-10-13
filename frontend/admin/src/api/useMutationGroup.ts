/** まとめる操作に要る状態（TanStack Query の useMutation の戻り値の一部） */
type MutationState = {
  isPending: boolean
  error: unknown
  reset: () => void
}

/**
 * 1 つの場所で行う複数の更新の操作をまとめる（管理画面の構成 第 4 節）。<br />
 * どれかが待っている間はほかの操作も止め、エラーは最初に失敗している操作のものを 1 つだけ出す。
 * 別の操作を始めるときは、前の操作のエラーを消す（resetErrors）。
 *
 * @param mutations まとめる操作
 */
export function useMutationGroup(...mutations: MutationState[]) {
  return {
    /** どれかが待っている */
    pending: mutations.some((mutation) => mutation.isPending),
    /** 最初に失敗している操作のエラー */
    error: mutations.find((mutation) => mutation.error)?.error ?? undefined,
    /** すべての操作のエラーを消す */
    resetErrors: () => {
      for (const mutation of mutations) {
        mutation.reset()
      }
    },
  }
}
