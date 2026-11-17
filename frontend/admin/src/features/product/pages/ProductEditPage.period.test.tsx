import { screen } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import type { components } from '@/api/generated/admin'
import { CONFLICT_MESSAGE, productDetail } from '@/test/fixtures'
import { returnToTab, typeDateTime } from '@/test/inputs'
import { PAGE_PATH, serveProduct } from '@/test/productEditServer'
import { renderApp } from '@/test/renderApp'
import { conflict, http, problem, server } from '@/test/server'

/** 公開期間の設定のリクエストの本文（送った内容を確認する） */
type SetPublicationPeriodRequest = components['schemas']['SetPublicationPeriodRequest']

describe('公開期間', () => {
  /**
   * 公開期間の設定に応答する。
   *
   * バージョンが今のものと違えば 409、終了が開始以前なら 400 にする。
   *
   * @param state 商品の編集の画面が使う API の状態
   * @returns 送った公開期間の設定
   */
  function servePeriod(state: ReturnType<typeof serveProduct>) {
    const requests: SetPublicationPeriodRequest[] = []
    server.use(
      http.put('/products/{productId}/publication-period', async ({ request, response }) => {
        const body = await request.json()
        requests.push(body)
        if (body.version !== state.current.version) {
          return conflict(response)
        }
        if (body.publishStart && body.publishEnd && body.publishStart >= body.publishEnd) {
          return response(400).json(
            problem(400, 'INVALID_PUBLICATION_PERIOD', '公開終了日時は、公開開始日時より後にしてください。'),
          )
        }
        state.current = {
          ...state.current,
          publishStart: body.publishStart ?? null,
          publishEnd: body.publishEnd ?? null,
          version: state.current.version + 1,
        }
        return response(204).empty()
      }),
    )
    return requests
  }

  test('AC-34 日本時間で入れた公開開始・終了を保存し、保存を知らせる', async () => {
    const state = serveProduct(productDetail())
    const requests = servePeriod(state)
    const { user } = renderApp(PAGE_PATH)

    typeDateTime(await screen.findByLabelText('公開開始'), '2026-11-01T10:00')
    typeDateTime(screen.getByLabelText('公開終了（この日時は含まない）'), '2026-12-01T00:00')
    await user.click(screen.getByRole('button', { name: '公開期間を保存' }))

    expect(await screen.findByText('公開期間を保存しました')).toBeInTheDocument()
    expect(requests).toEqual([
      { publishStart: '2026-11-01T10:00:00+09:00', publishEnd: '2026-12-01T00:00:00+09:00', version: 0 },
    ])
  })

  test('AC-36 終了が開始より後でなければ、サーバーのエラーのメッセージを出す', async () => {
    const state = serveProduct(productDetail())
    servePeriod(state)
    const { user } = renderApp(PAGE_PATH)

    typeDateTime(await screen.findByLabelText('公開開始'), '2026-11-01T10:00')
    typeDateTime(screen.getByLabelText('公開終了（この日時は含まない）'), '2026-11-01T10:00')
    await user.click(screen.getByRole('button', { name: '公開期間を保存' }))

    expect(await screen.findByText('公開終了日時は、公開開始日時より後にしてください。')).toBeInTheDocument()
  })

  describe('入力中に他の人が更新した（409）', () => {
    /** 公開開始を入力し、他の人が商品名と公開終了を変えた後に、画面が取得し直した状態にする。 */
    async function inputStartAfterOthersUpdate() {
      const state = serveProduct(productDetail())
      const requests = servePeriod(state)
      const { user } = renderApp(PAGE_PATH)
      typeDateTime(await screen.findByLabelText('公開開始'), '2026-11-01T10:00')
      state.current = productDetail({ name: 'T シャツ（新）', version: 1, publishEnd: '2026-12-31T15:00:00Z' })
      returnToTab()
      expect(await screen.findByDisplayValue('T シャツ（新）')).toBeInTheDocument()
      return { user, requests }
    }

    /** 取得し直した後に保存して 409 にし、競合の知らせが出た状態にする。 */
    async function saveIntoConflict() {
      const context = await inputStartAfterOthersUpdate()
      await context.user.click(screen.getByRole('button', { name: '公開期間を保存' }))
      expect(await screen.findByText(CONFLICT_MESSAGE)).toBeInTheDocument()
      return context
    }

    test('取得し直しても、入力した公開開始は消えない', async () => {
      await inputStartAfterOthersUpdate()

      expect(screen.getByLabelText('公開開始')).toHaveValue('2026-11-01T10:00')
    })

    test('保存すると 409 になり、競合を知らせる', async () => {
      const { requests } = await saveIntoConflict()

      expect(requests.map((request) => request.version)).toEqual([0])
    })

    test('再読み込みすると、最新の期間に再作成され、競合の知らせが消える', async () => {
      const { user } = await saveIntoConflict()

      await user.click(screen.getByRole('button', { name: '再読み込み' }))

      expect(await screen.findByDisplayValue('2027-01-01T00:00')).toBe(
        screen.getByLabelText('公開終了（この日時は含まない）'),
      )
      expect(screen.getByLabelText('公開開始')).toHaveValue('')
      expect(screen.queryByText(CONFLICT_MESSAGE)).not.toBeInTheDocument()
    })

    test('再読み込みした後は、最新のバージョンで保存できる', async () => {
      const { user, requests } = await saveIntoConflict()
      await user.click(screen.getByRole('button', { name: '再読み込み' }))
      await screen.findByDisplayValue('2027-01-01T00:00')

      typeDateTime(screen.getByLabelText('公開開始'), '2026-11-01T10:00')
      await user.click(screen.getByRole('button', { name: '公開期間を保存' }))

      expect(await screen.findByText('公開期間を保存しました')).toBeInTheDocument()
      expect(requests.map((request) => request.version)).toEqual([0, 1])
    })
  })
})
