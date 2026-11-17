import { screen } from '@testing-library/react'
import { expect } from 'vitest'
import { CONFLICT_MESSAGE } from '@/test/fixtures'
import { returnToTab } from '@/test/inputs'

/**
 * 入力中に他の人が更新した（409）流れの準備を進める。
 *
 * 入力し、他の人の更新に変え、画面が取得し直すのを待ち、保存して 409 の競合の知らせを出す。
 * 取得し直しと保存は、引数で省ける。
 *
 * 画面を開いて入力するところ（`edit`）が返す値を、そのまま返す。
 *
 * @param flow.edit 画面を開き、入力する。返す値に、操作の `user` を含める
 * @param flow.othersUpdate 他の人の更新に当たる状態に変える。`edit` が返した値を受ける
 * @param flow.refetched 画面が取得し直したことを待つ。省くと、取得し直さない
 * @param flow.saveButton 保存して 409 にするボタンの名前。省くと、保存しない
 * @returns `edit` が返した値
 */
export async function setUpConflictFlow<T extends { user: { click: (element: Element) => Promise<void> } }>(flow: {
  edit: () => Promise<T>
  othersUpdate: (context: T) => void
  refetched?: () => Promise<unknown>
  saveButton?: string
}): Promise<T> {
  const context = await flow.edit()
  flow.othersUpdate(context)
  if (flow.refetched) {
    returnToTab()
    await flow.refetched()
  }
  if (flow.saveButton) {
    await context.user.click(screen.getByRole('button', { name: flow.saveButton }))
    expect(await screen.findByText(CONFLICT_MESSAGE)).toBeInTheDocument()
  }
  return context
}
