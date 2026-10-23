import { screen, within } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import type { components } from '@/api/generated/admin'
import type { TaxCategory } from '@/api/types'
import { taxCategory } from '@/test/fixtures'
import { findRow, getRow } from '@/test/queries'
import { renderApp } from '@/test/renderApp'
import { http, problem, type Reply, server } from '@/test/server'

/** 税区分の登録の要求の本文（送った内容を確認する） */
type CreateTaxCategoryRequest = components['schemas']['CreateTaxCategoryRequest']

/** 税区分の画面の URL */
const URL = '/tax-categories'

/** 税率のない税区分「軽減」 */
const REDUCED = taxCategory({
  id: '00000000-0000-0000-0000-000000000002',
  name: '軽減',
  currentRatePercent: null,
  rates: [],
})

/**
 * 税区分の一覧と登録に応答する。登録の要求は requests に取っておく。<br />
 * 登録の応答は reply で決める。省略すると成功（201）にし、一覧に追加する。
 */
function serveTaxCategories(
  initial: TaxCategory[],
  reply?: Reply<'/tax-categories', 'post', CreateTaxCategoryRequest>,
) {
  const state = { items: initial, requests: [] as CreateTaxCategoryRequest[] }
  server.use(
    http.get('/tax-categories', ({ response }) => response(200).json({ items: state.items })),
    http.post('/tax-categories', async ({ request, response }) => {
      const body = await request.json()
      state.requests.push(body)
      if (reply) {
        return reply(body, response)
      }
      const created = taxCategory({
        id: '00000000-0000-0000-0000-000000000003',
        name: body.name,
        currentRatePercent: null,
        rates: [],
      })
      state.items = [...state.items, created]
      return response(201).json(created)
    }),
  )
  return state
}

describe('税区分の一覧', () => {
  test('税区分ごとに、名称・現時点の率・税率の件数と、税率の編集へ移るボタンを出す', async () => {
    serveTaxCategories([taxCategory(), REDUCED])
    renderApp(URL)

    const standardRow = within(await findRow('標準'))
    expect(standardRow.getByRole('cell', { name: '10%' })).toBeInTheDocument()
    expect(standardRow.getByRole('cell', { name: '1' })).toBeInTheDocument()
    expect(standardRow.getByRole('button', { name: '税率を編集' })).toHaveAttribute(
      'href',
      `/tax-categories/${taxCategory().id}`,
    )
    expect(within(getRow('軽減')).getByRole('cell', { name: '0' })).toBeInTheDocument()
  })
})

describe('税区分の登録', () => {
  test('AC-1 名称を入れて登録すると、知らせを出し、入力を空にして一覧に追加する', async () => {
    const state = serveTaxCategories([taxCategory()])
    const { user } = renderApp(URL)

    const name = await screen.findByLabelText('名称')
    expect(screen.getByRole('button', { name: '登録' })).toBeDisabled()
    await user.type(name, '軽減')
    await user.click(screen.getByRole('button', { name: '登録' }))

    expect(await screen.findByText('税区分を登録しました')).toBeInTheDocument()
    expect(await screen.findByRole('cell', { name: '軽減' })).toBeInTheDocument()
    expect(name).toHaveValue('')
    expect(state.requests).toEqual([{ name: '軽減' }])
  })

  test('AC-1 名称が重複していると、サーバーのエラーを出し、入力を残す', async () => {
    serveTaxCategories([taxCategory()], (_, response) =>
      response(409).json(problem(409, 'TAX_CATEGORY_NAME_ALREADY_EXISTS', 'この名称の税区分はすでにあります。')),
    )
    const { user } = renderApp(URL)

    const name = await screen.findByLabelText('名称')
    await user.type(name, '標準')
    await user.click(screen.getByRole('button', { name: '登録' }))

    expect(await screen.findByText('この名称の税区分はすでにあります。')).toBeInTheDocument()
    expect(name).toHaveValue('標準')
  })

  test('空白だけの名称では、登録できない', async () => {
    serveTaxCategories([taxCategory()])
    const { user } = renderApp(URL)

    await user.type(await screen.findByLabelText('名称'), '   ')

    expect(screen.getByRole('button', { name: '登録' })).toBeDisabled()
  })
})
