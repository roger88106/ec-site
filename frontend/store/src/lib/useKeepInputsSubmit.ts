import { type FormEvent, startTransition } from 'react'

/**
 * 送信の後も入力を消さないフォームの `action` と `onSubmit` を返す。
 *
 * `action` は JavaScript の読み込み前の送信に使う（ないと、ブラウザーが入力を今の URL のクエリに載せて送る）。
 * 読み込み後は `onSubmit` から送る。`action` で送ると、React が送った後にフォームを初期値に戻し、
 * 失敗したときに入力した値が消えるためである。
 *
 * @param formAction 入力を送る関数（`useActionState` が返すもの）
 * @param confirm    送る前に確認する関数。false を返したら送らない（省くと確認しない）
 * @returns フォームに展開する `action` と `onSubmit`
 */
export function useKeepInputsSubmit(formAction: (formData: FormData) => void, confirm?: () => boolean) {
  return {
    action: formAction,
    onSubmit: (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault()
      if (confirm !== undefined && !confirm()) {
        return
      }
      const formData = new FormData(event.currentTarget)
      startTransition(() => formAction(formData))
    },
  }
}
