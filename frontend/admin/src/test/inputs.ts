import { fireEvent } from '@testing-library/react'

/**
 * 日時の入力（`datetime-local`）に値を入れる。
 * 例：`typeDateTime(screen.getByLabelText('開始'), '2027-04-01T09:30')`
 *
 * user-event は `datetime-local` の入力に文字を打てない。jsdom は直接の入力を受け付けないため、`fireEvent.change` で値を入れて
 * change を発火する。
 *
 * @param input 日時の入力
 * @param value 入れる値（`yyyy-MM-ddTHH:mm`）
 */
export function typeDateTime(input: HTMLElement, value: string): void {
  fireEvent.change(input, { target: { value } })
}
