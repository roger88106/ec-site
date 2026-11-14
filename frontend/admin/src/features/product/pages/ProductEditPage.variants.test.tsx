import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import type { components } from '@/api/generated/admin'
import { CONFLICT_MESSAGE, productActions, productDetail, variant } from '@/test/fixtures'
import { returnToTab } from '@/test/inputs'
import { PAGE_PATH, serveProduct } from '@/test/productEditServer'
import { findRow, getRow } from '@/test/queries'
import { renderApp } from '@/test/renderApp'
import { conflict, http, server, stockHandler } from '@/test/server'

/** バリアントの追加のリクエストの本文（送った内容を確認する） */
type AddVariantRequest = components['schemas']['AddVariantRequest']

/** バリアントの並び替えのリクエストの本文（送った内容を確認する） */
type ReorderVariantsRequest = components['schemas']['ReorderVariantsRequest']

describe('商品の編集', () => {
  test('AC-21、24 軸を持たない商品は、バリアントがなければ品番を大文字にして追加でき、1 つあれば追加の欄を出さない', async () => {
    const state = serveProduct(productDetail({ axes: [] }))
    const requests: AddVariantRequest[] = []
    server.use(
      stockHandler(0),
      http.post('/products/{productId}/variants', async ({ request, response }) => {
        requests.push(await request.json())
        state.current = {
          ...state.current,
          variants: [variant({ axisValues: [] })],
          actions: productActions({ addVariant: false }),
          version: 1,
        }
        return response(201).json({ id: variant().id })
      }),
    )
    const { user } = renderApp(PAGE_PATH)

    await user.type(await screen.findByLabelText('バリアント品番'), 'abc-001-x')
    await user.click(screen.getByRole('button', { name: 'バリアントを追加' }))

    expect(await screen.findByText('軸を持たない商品のバリアントは 1 つだけです。')).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'ABC-001-RED' })).toBeInTheDocument()
    expect(screen.queryByLabelText('バリアント品番')).not.toBeInTheDocument()
    expect(requests).toEqual([{ variantCode: 'ABC-001-X', axisValues: [], version: 0 }])
  })

  test('AC-20、22 軸が 2 つの商品にバリアントを追加すると、品番と軸の値を軸の順に、バージョンとともに送る', async () => {
    const state = serveProduct(productDetail({ axes: ['色', 'サイズ'], variants: [] }))
    const requests: AddVariantRequest[] = []
    server.use(
      stockHandler(0),
      http.post('/products/{productId}/variants', async ({ request, response }) => {
        requests.push(await request.json())
        state.current = { ...state.current, variants: [variant()], version: state.current.version + 1 }
        return response(201).json({ id: variant().id })
      }),
    )
    const { user } = renderApp(PAGE_PATH)

    await user.type(await screen.findByLabelText('バリアント品番'), 'abc-001-wh-m')
    await user.type(screen.getByLabelText('色'), ' 白 ')
    await user.type(screen.getByLabelText('サイズ'), 'M')
    await user.click(screen.getByRole('button', { name: 'バリアントを追加' }))

    expect(await screen.findByText('バリアントを追加しました')).toBeInTheDocument()
    expect(requests).toEqual([{ variantCode: 'ABC-001-WH-M', axisValues: ['白', 'M'], version: 0 }])
  })

  describe('バリアントの追加の入力中に他の人が商品を更新した（409）', () => {
    /** 品番と軸の値を入力し、他の人が価格を変えた後に、画面が取得し直した状態にする。 */
    async function fillVariantAfterOthersUpdate() {
      const state = serveProduct(productDetail({ axes: ['色'], variants: [] }))
      const versions: number[] = []
      server.use(
        stockHandler(0),
        http.post('/products/{productId}/variants', async ({ request, response }) => {
          const body = await request.json()
          versions.push(body.version)
          if (body.version !== state.current.version) {
            return conflict(response)
          }
          state.current = { ...state.current, variants: [variant()], version: state.current.version + 1 }
          return response(201).json({ id: variant().id })
        }),
      )
      const { user } = renderApp(PAGE_PATH)
      await user.type(await screen.findByLabelText('バリアント品番'), 'abc-001-red')
      await user.type(screen.getByLabelText('色'), '赤')
      state.current = productDetail({
        axes: ['色'],
        variants: [],
        priceExclTax: 1200,
        priceInclTax: 1320,
        version: 1,
      })
      returnToTab()
      expect(await screen.findByText('1,320 円（税抜 1,200 円）')).toBeInTheDocument()
      return { user, versions }
    }

    /** 取得し直した後に追加して 409 にし、競合の知らせが出た状態にする。 */
    async function addIntoConflict() {
      const context = await fillVariantAfterOthersUpdate()
      await context.user.click(screen.getByRole('button', { name: 'バリアントを追加' }))
      expect(await screen.findByText(CONFLICT_MESSAGE)).toBeInTheDocument()
      return context
    }

    test('取得し直しても、入力した品番と軸の値は消えない', async () => {
      await fillVariantAfterOthersUpdate()

      expect(screen.getByLabelText('バリアント品番')).toHaveValue('ABC-001-RED')
      expect(screen.getByLabelText('色')).toHaveValue('赤')
    })

    test('追加すると 409 になり、競合を知らせる', async () => {
      const { versions } = await addIntoConflict()

      expect(versions).toEqual([0])
    })

    test('409 になっても、入力した品番は消えない', async () => {
      await addIntoConflict()

      expect(screen.getByLabelText('バリアント品番')).toHaveValue('ABC-001-RED')
    })

    test('再読み込みすると、空のフォームに再作成され、競合の知らせが消える', async () => {
      const { user } = await addIntoConflict()

      await user.click(screen.getByRole('button', { name: '再読み込み' }))

      await waitFor(() => expect(screen.getByLabelText('バリアント品番')).toHaveValue(''))
      expect(screen.getByLabelText('色')).toHaveValue('')
      expect(screen.queryByText(CONFLICT_MESSAGE)).not.toBeInTheDocument()
    })

    test('再読み込みした後は、最新のバージョンで追加できる', async () => {
      const { user, versions } = await addIntoConflict()
      await user.click(screen.getByRole('button', { name: '再読み込み' }))
      await waitFor(() => expect(screen.getByLabelText('バリアント品番')).toHaveValue(''))

      await user.type(screen.getByLabelText('バリアント品番'), 'abc-001-red')
      await user.type(screen.getByLabelText('色'), '赤')
      await user.click(screen.getByRole('button', { name: 'バリアントを追加' }))

      expect(await screen.findByText('バリアントを追加しました')).toBeInTheDocument()
      expect(versions).toEqual([0, 1])
    })
  })

  test('AC-28 バリアントを販売停止にすると「販売停止」と出て、販売を再開できる', async () => {
    const state = serveProduct(productDetail({ variants: [variant()] }))
    const calls: string[] = []
    const setSuspended = (salesSuspended: boolean) => {
      state.current = {
        ...state.current,
        variants: [variant({ salesSuspended })],
        version: state.current.version + 1,
      }
    }
    server.use(
      stockHandler(3),
      http.put('/products/{productId}/variants/{variantId}/suspend', ({ params, response }) => {
        calls.push(`suspend ${params.variantId}`)
        setSuspended(true)
        return response(204).empty()
      }),
      http.put('/products/{productId}/variants/{variantId}/resume', ({ params, response }) => {
        calls.push(`resume ${params.variantId}`)
        setSuspended(false)
        return response(204).empty()
      }),
    )
    const { user } = renderApp(PAGE_PATH)
    const row = () => getRow('ABC-001-RED')

    await user.click(await screen.findByRole('button', { name: 'ABC-001-REDを販売停止' }))

    expect(await screen.findByText('販売停止にしました')).toBeInTheDocument()
    expect(await within(row()).findByText('販売停止')).toBeInTheDocument()

    await user.click(within(row()).getByRole('button', { name: 'ABC-001-REDの販売を再開' }))

    expect(await screen.findByText('販売を再開しました')).toBeInTheDocument()
    expect(await within(row()).findByText('販売中')).toBeInTheDocument()
    expect(calls).toEqual([`suspend ${variant().id}`, `resume ${variant().id}`])
  })

  test('AC-27 バリアントを下へ移すと、移した後の並びを商品のバージョンとともに送る', async () => {
    const SECOND_ID = '00000000-0000-0000-0000-000000000202'
    const state = serveProduct(
      productDetail({
        version: 5,
        variants: [variant(), variant({ id: SECOND_ID, variantCode: 'ABC-001-BLUE', axisValues: ['青'] })],
      }),
    )
    const requests: ReorderVariantsRequest[] = []
    server.use(
      stockHandler(3),
      http.put('/products/{productId}/variants/order', async ({ request, response }) => {
        requests.push(await request.json())
        state.current = {
          ...state.current,
          variants: [...state.current.variants].reverse(),
          version: state.current.version + 1,
        }
        return response(204).empty()
      }),
    )
    const { user } = renderApp(PAGE_PATH)

    await user.click(await screen.findByRole('button', { name: 'ABC-001-REDを下へ' }))

    await waitFor(() => expect(requests).toHaveLength(1))
    expect(requests).toEqual([{ variantIds: [SECOND_ID, variant().id], version: 5 }])
  })

  test('AC-71 C 側に表示されたことのない商品のバリアントは、確認のダイアログで確定すると、商品のバージョンを添えて削除する', async () => {
    const state = serveProduct(productDetail({ version: 5, variants: [variant()] }))
    const calls: string[] = []
    server.use(
      stockHandler(3),
      http.delete('/products/{productId}/variants/{variantId}', ({ params, query, response }) => {
        calls.push(`DELETE ${params.variantId} version=${query.get('version')}`)
        state.current = { ...state.current, variants: [], version: state.current.version + 1 }
        return response(204).empty()
      }),
    )
    const { user } = renderApp(PAGE_PATH)

    const row = await findRow('ABC-001-RED')
    await user.click(within(row).getByRole('button', { name: '削除' }))
    const dialog = await screen.findByRole('alertdialog', { name: 'バリアント「ABC-001-RED」を削除しますか？' })
    await user.click(within(dialog).getByRole('button', { name: '削除する' }))

    expect(await screen.findByText('バリアントを削除しました')).toBeInTheDocument()
    expect(calls).toEqual([`DELETE ${variant().id} version=5`])
  })
})
