import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { ProductDetail } from '@/api/types'
import { PRODUCT_ID, productDetail, taxCategory, variant } from '@/test/fixtures'
import { renderApp } from '@/test/renderApp'
import { http, problem, server } from '@/test/server'

const URL = `/products/${PRODUCT_ID}`

/**
 * 商品の編集の画面が使う API に応答する。<br />
 * 基本情報の保存は、送ったバージョンが今のものと違えば 409 にし、同じなら保存してバージョンを上げる。
 * 状態の変更・削除の要求は calls に「メソッド パス」で取っておく。
 */
function serveProduct(initial: ProductDetail) {
  const state = { current: initial, versions: [] as number[], calls: [] as string[] }
  const conflict = () =>
    Response.json(problem(409, 'CONCURRENT_MODIFICATION', 'バージョンが一致しません。'), { status: 409 })
  server.use(
    http.get('/products/{productId}', ({ response }) => response(200).json(state.current)),
    http.get('/tax-categories', ({ response }) => response(200).json({ items: [taxCategory()] })),
    http.get('/categories', ({ response }) => response(200).json({ items: [] })),
    http.put('/products/{productId}', async ({ request }) => {
      const body = await request.json()
      state.versions.push(body.version)
      if (body.version !== state.current.version) {
        return conflict() as never
      }
      state.current = { ...state.current, ...body, version: state.current.version + 1 }
      return Response.json(state.current) as never
    }),
    http.put('/products/{productId}/discontinue', ({ response }) => {
      state.calls.push('PUT discontinue')
      state.current = { ...state.current, status: 'DISCONTINUED', version: state.current.version + 1 }
      return response(204).empty()
    }),
    http.delete('/products/{productId}', ({ query, response }) => {
      state.calls.push(`DELETE version=${query.get('version')}`)
      return response(204).empty()
    }),
    // 削除の後に移る、商品の一覧の画面
    http.get('/products', ({ response }) =>
      response(200).json({ items: [], page: 1, size: 20, totalItems: 0, totalPages: 0 }),
    ),
  )
  return state
}

describe('商品の編集', () => {
  it('入力中に他の人が基本情報を更新すると、取得し直しても入力は消えず、保存は 409 になり、再読み込みで最新の値に作り直してから保存できる', async () => {
    const state = serveProduct(productDetail())
    const { user } = renderApp(URL)
    const name = await screen.findByLabelText('商品名')

    await user.clear(name)
    await user.type(name, 'T シャツ（改）')
    // 入力中に、他の人が商品名と価格を変えた。利用者が別のタブから戻ると、画面は取得し直す
    state.current = productDetail({ name: 'T シャツ（新）', priceExclTax: 1200, priceInclTax: 1320, version: 1 })
    fireEvent(window, new Event('visibilitychange'))
    expect(await screen.findByText('1,320 円（税抜 1,200 円）')).toBeInTheDocument()
    expect(screen.getByLabelText('商品名')).toHaveValue('T シャツ（改）')
    await user.click(screen.getByRole('button', { name: '保存' }))

    expect(await screen.findByText('他の人が先に更新しました。再読み込みしてください')).toBeInTheDocument()
    expect(screen.getByLabelText('商品名')).toHaveValue('T シャツ（改）')

    await user.click(screen.getByRole('button', { name: '再読み込み' }))
    expect(await screen.findByDisplayValue('T シャツ（新）')).toBe(screen.getByLabelText('商品名'))

    await user.click(screen.getByRole('button', { name: '保存' }))
    expect(await screen.findByText('基本情報を保存しました')).toBeInTheDocument()
    expect(state.versions).toEqual([0, 1])
  })

  it('販売終了は確認のダイアログで確定したときだけ行い、やめれば何もしない', async () => {
    const state = serveProduct(productDetail({ status: 'PUBLISHED' }))
    const { user } = renderApp(URL)

    await user.click(await screen.findByRole('button', { name: '販売終了にする' }))
    const dialog = await screen.findByRole('alertdialog', { name: '商品「T シャツ」を販売終了にしますか？' })
    await user.click(within(dialog).getByRole('button', { name: 'やめる' }))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(state.calls).toEqual([])

    await user.click(screen.getByRole('button', { name: '販売終了にする' }))
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: '販売終了にする' }))

    expect(await screen.findByText('販売終了にしました')).toBeInTheDocument()
    expect(await screen.findByText('販売終了の商品は、公開に戻せません。')).toBeInTheDocument()
    expect(state.calls).toEqual(['PUT discontinue'])
  })

  it('AC-33 C 側に表示されたことのない商品は、確認のダイアログで確定すると削除し、一覧に移る', async () => {
    const state = serveProduct(productDetail({ version: 3 }))
    const { user, router } = renderApp(URL)

    await user.click(await screen.findByRole('button', { name: '削除' }))
    const dialog = await screen.findByRole('alertdialog', { name: '商品「T シャツ」を削除しますか？' })
    await user.click(within(dialog).getByRole('button', { name: '削除する' }))

    expect(await screen.findByText('商品を削除しました')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/products')
    expect(state.calls).toEqual(['DELETE version=3'])
  })

  it('AC-33 C 側に表示されたことのある商品には削除のボタンがなく、販売終了を案内する', async () => {
    serveProduct(productDetail({ status: 'PUBLISHED', firstShownAt: '2026-10-01T00:00:00+09:00' }))
    renderApp(URL)

    expect(
      await screen.findByText(
        '店舗に表示されたことがあるため、削除できません。取り扱いをやめるときは販売終了にしてください。',
      ),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '削除' })).not.toBeInTheDocument()
  })
  it('AC-24 軸を持たない商品は、バリアントがなければ追加の欄を出し、1 つあれば追加の欄を出さない', async () => {
    const state = serveProduct(productDetail({ axes: [] }))
    server.use(
      http.get('/inventory/variants/{variantId}', ({ params, response }) =>
        response(200).json({ variantId: params.variantId, quantity: 0 }),
      ),
      http.post('/products/{productId}/variants', ({ response }) => {
        state.current = { ...state.current, variants: [variant({ axisValues: [] })], version: 1 }
        return response(201).json(variant({ axisValues: [] }))
      }),
    )
    const { user } = renderApp(URL)

    await user.type(await screen.findByLabelText('バリアント品番'), 'abc-001-x')
    await user.click(screen.getByRole('button', { name: 'バリアントを追加' }))

    expect(await screen.findByText('軸を持たない商品のバリアントは 1 つだけです。')).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'ABC-001-RED' })).toBeInTheDocument()
    expect(screen.queryByLabelText('バリアント品番')).not.toBeInTheDocument()
  })
})
