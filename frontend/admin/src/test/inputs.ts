import { fireEvent } from '@testing-library/react'

/**
 * 日時の入力（`datetime-local`）に値を入れる。
 * 例：`typeDateTime(screen.getByLabelText('開始'), '2027-04-01T09:30')`
 *
 * `fireEvent.change` で値を入れて change を発火する。
 *
 * @param input 日時の入力
 * @param value 入れる値（`yyyy-MM-ddTHH:mm`）
 */
export function typeDateTime(input: HTMLElement, value: string): void {
  // datetime-local の入力欄は user-event で入力できない（jsdom が直接の入力を受け付けない）ため、fireEvent.change で値を入れる
  fireEvent.change(input, { target: { value } })
}

/**
 * 利用者が別のタブから戻ったことを表す。画面は表示中の情報を取得し直す。
 *
 * window の `visibilitychange` を発火する。
 */
export function returnToTab(): void {
  // window の visibilitychange（別のタブから戻る）に当たる user-event の操作がないため、fireEvent で起こす
  fireEvent(window, new Event('visibilitychange'))
}
