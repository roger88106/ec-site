import { screen, within } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import type { components } from '@/api/generated/admin'
import type { TaxCategory } from '@/api/types'
import { CONFLICT_MESSAGE, TAX_CATEGORY_ID, taxCategory } from '@/test/fixtures'
import { returnToTab, typeDateTime } from '@/test/inputs'
import { getRow } from '@/test/queries'
import { renderApp } from '@/test/renderApp'
import { conflict, http, problem, type Reply, server } from '@/test/server'

/** 税率の一覧の置き換えの要求の本文（送った内容を確認する） */
type ReplaceTaxRatesRequest = components['schemas']['ReplaceTaxRatesRequest']

/** 既存の税率の ID（fixtures の taxCategory() の税率） */
const RATE_ID = taxCategory().rates[0].id
/** 税率の画面のパス */
const PAGE_PATH = `/tax-categories/${TAX_CATEGORY_ID}`

/**
 * 税区分の取得と税率の置き換えに応答する。置き換えの要求は requests に取っておく。<br />
 * 置き換えの応答は reply で決める。省略すると成功（204）にし、取得の結果も送った一覧に変える。
 */
function serveTaxCategory(
  initial: TaxCategory,
  reply?: Reply<'/tax-categories/{taxCategoryId}/rates', 'put', ReplaceTaxRatesRequest>,
): { current: TaxCategory; requests: ReplaceTaxRatesRequest[] } {
  const state = { current: initial, requests: [] as ReplaceTaxRatesRequest[] }
  server.use(
    http.get('/tax-categories/{taxCategoryId}', ({ response }) => response(200).json(state.current)),
    http.get('/tax-categories', ({ response }) => response(200).json({ items: [state.current] })),
    http.put('/tax-categories/{taxCategoryId}/rates', async ({ request, response }) => {
      const body = await request.json()
      state.requests.push(body)
      const replied = reply?.(body, response)
      if (replied) {
        return replied
      }
      state.current = {
        ...state.current,
        version: state.current.version + 1,
        rates: body.rates.map((rate, index) => ({
          id: rate.id ?? `00000000-0000-0000-0000-00000000010${index}`,
          ratePercent: rate.ratePercent,
          effectiveFrom: rate.effectiveFrom,
          effectiveTo: rate.effectiveTo,
        })),
      }
      return response(204).empty()
    }),
  )
  return state
}

/** n 行目（0 始まり）の入力欄。行の名前で行を探し、その中で入力欄を探す */
function rowInputs(index: number) {
  const row = within(getRow(`${index + 1} 行目の税率`))
  return {
    rate: row.getByLabelText<HTMLInputElement>('率（%）'),
    from: row.getByLabelText<HTMLInputElement>('適用開始'),
    to: row.getByLabelText<HTMLInputElement>('適用終了'),
  }
}

describe('税率の一覧の編集', () => {
  test('AC-4 期間限定の税率に切り替える一覧を作って保存すると、追加した行の開始は直前の行の終了から始まり、保存を知らせる', async () => {
    const state = serveTaxCategory(taxCategory())
    const { user } = renderApp(PAGE_PATH)
    await screen.findByRole('heading', { name: '税区分「標準」の税率' })

    typeDateTime(rowInputs(0).to, '2027-04-01T00:00')
    await user.click(screen.getByRole('button', { name: '税率を追加' }))
    expect(rowInputs(1).from).toHaveValue('2027-04-01T00:00')
    await user.type(rowInputs(1).rate, '8')
    typeDateTime(rowInputs(1).to, '2028-04-01T00:00')
    await user.click(screen.getByRole('button', { name: '税率を追加' }))
    expect(rowInputs(2).from).toHaveValue('2028-04-01T00:00')
    await user.type(rowInputs(2).rate, '10')
    await user.click(screen.getByRole('button', { name: '保存' }))

    expect(await screen.findByText('税率を保存しました')).toBeInTheDocument()
    expect(state.requests).toEqual([
      {
        version: 0,
        rates: [
          { id: RATE_ID, ratePercent: 10, effectiveFrom: null, effectiveTo: '2027-04-01T00:00:00+09:00' },
          {
            id: null,
            ratePercent: 8,
            effectiveFrom: '2027-04-01T00:00:00+09:00',
            effectiveTo: '2028-04-01T00:00:00+09:00',
          },
          { id: null, ratePercent: 10, effectiveFrom: '2028-04-01T00:00:00+09:00', effectiveTo: null },
        ],
      },
    ])
  })

  test('AC-3 期間が途切れる一覧はサーバーに拒否され、そのメッセージを出し、入力は残る', async () => {
    serveTaxCategory(taxCategory(), (_, response) =>
      response(400).json(problem(400, 'TAX_RATE_PERIODS_GAP', '税率の期間に途切れがあります。')),
    )
    const { user } = renderApp(PAGE_PATH)
    await screen.findByRole('heading', { name: '税区分「標準」の税率' })

    typeDateTime(rowInputs(0).to, '2027-04-01T00:00')
    await user.click(screen.getByRole('button', { name: '税率を追加' }))
    await user.type(rowInputs(1).rate, '8')
    typeDateTime(rowInputs(1).from, '2027-05-01T00:00')
    await user.click(screen.getByRole('button', { name: '保存' }))

    expect(await screen.findByText('税率の期間に途切れがあります。')).toBeInTheDocument()
    expect(rowInputs(1).from).toHaveValue('2027-05-01T00:00')
    expect(screen.queryByText('税率を保存しました')).not.toBeInTheDocument()
  })

  describe('入力中に他の人が更新したとき', () => {
    /** 率を 9 に入力した後で、他の人が 8% に変えたのを、画面が取得し直した状態にする */
    async function editThenRefetch() {
      const state = serveTaxCategory(taxCategory(), (body, response) =>
        body.version === state.current.version ? undefined : conflict(response),
      )
      const { user } = renderApp(PAGE_PATH)
      await screen.findByRole('heading', { name: '税区分「標準」の税率' })
      await user.clear(rowInputs(0).rate)
      await user.type(rowInputs(0).rate, '9')
      state.current = taxCategory({
        version: 1,
        currentRatePercent: 8,
        rates: [{ id: RATE_ID, ratePercent: 8, effectiveFrom: null, effectiveTo: null }],
      })
      returnToTab()
      await screen.findByText('現時点の率：8%')
      return { state, user }
    }

    /** 保存して 409 の表示を出し、その表示を返す */
    async function saveToConflict(user: Awaited<ReturnType<typeof editThenRefetch>>['user']) {
      await user.click(screen.getByRole('button', { name: '保存' }))
      return screen.findByRole('alert')
    }

    test('取得し直しても、入力した率は消えない', async () => {
      await editThenRefetch()

      expect(rowInputs(0).rate).toHaveValue(9)
    })

    test('保存は 409 になり、他の人が先に更新したことを知らせ、入力は残る', async () => {
      const { user } = await editThenRefetch()

      const alert = await saveToConflict(user)

      expect(within(alert).getByText(CONFLICT_MESSAGE)).toBeInTheDocument()
      expect(rowInputs(0).rate).toHaveValue(9)
    })

    test('再読み込みすると、最新の一覧に再作成され、知らせは消える', async () => {
      const { user } = await editThenRefetch()
      const alert = await saveToConflict(user)

      await user.click(within(alert).getByRole('button', { name: '再読み込み' }))

      expect(await screen.findByDisplayValue('8')).toBe(rowInputs(0).rate)
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })

    test('再読み込みの後は、最新のバージョンで保存できる', async () => {
      const { state, user } = await editThenRefetch()
      const alert = await saveToConflict(user)
      await user.click(within(alert).getByRole('button', { name: '再読み込み' }))
      await screen.findByDisplayValue('8')

      await user.click(screen.getByRole('button', { name: '保存' }))

      expect(await screen.findByText('税率を保存しました')).toBeInTheDocument()
      expect(state.requests.map((request) => request.version)).toEqual([0, 1])
    })
  })
})
