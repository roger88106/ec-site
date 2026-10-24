import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http as rawHttp } from 'msw'
import { describe, expect, test } from 'vitest'
import type { components } from '@/api/generated/admin'
import { schemas } from '@/api/generated/constraints'
import type { ProductDetail } from '@/api/types'
import { CONFLICT_MESSAGE, PRODUCT_ID, productActions, productDetail, taxCategory, variant } from '@/test/fixtures'
import { returnToTab, typeDateTime } from '@/test/inputs'
import { findRow, getRow } from '@/test/queries'
import { renderApp } from '@/test/renderApp'
import { conflict, http, problem, server, stockHandler } from '@/test/server'

/** 商品の編集の画面のパス */
const PAGE_PATH = `/products/${PRODUCT_ID}`

/** 商品の基本情報の更新の要求の本文（送った内容を確認する） */
type UpdateProductRequest = components['schemas']['UpdateProductRequest']

/** バリアントの追加の要求の本文（送った内容を確認する） */
type AddVariantRequest = components['schemas']['AddVariantRequest']

/** バリアントの並び替えの要求の本文（送った内容を確認する） */
type ReorderVariantsRequest = components['schemas']['ReorderVariantsRequest']

/** 公開期間の設定の要求の本文（送った内容を確認する） */
type SetPublicationPeriodRequest = components['schemas']['SetPublicationPeriodRequest']

/** バリアントの画像の指定の要求の本文（送った内容を確認する） */
type SetVariantImageRequest = components['schemas']['SetVariantImageRequest']

/** 商品の画像の並び替えの要求の本文（送った内容を確認する） */
type ReorderProductImagesRequest = components['schemas']['ReorderProductImagesRequest']

/** 契約の画像の大きさの上限（バイト） */
const MAX_BYTES = schemas.CreateImageUploadRequest.contentLength.maximum

/** 契約の商品の画像の枚数の上限 */
const MAX_IMAGES = schemas.ProductDetail.images.maxItems

/**
 * 商品の編集の画面が使う API に応答する。<br />
 * 基本情報の保存は、送ったバージョンが今のものと違えば 409 にし、同じなら保存してバージョンを上げる。
 * 状態の変更・削除の要求は calls に「メソッド パス」で取っておく。
 */
function serveProduct(initial: ProductDetail) {
  const state = { current: initial, versions: [] as number[], calls: [] as string[] }
  server.use(
    http.get('/products/{productId}', ({ response }) => response(200).json(state.current)),
    http.get('/tax-categories', ({ response }) => response(200).json({ items: [taxCategory()] })),
    http.get('/categories', ({ response }) => response(200).json({ items: [] })),
    http.put('/products/{productId}', async ({ request, response }) => {
      const body = await request.json()
      state.versions.push(body.version)
      if (body.version !== state.current.version) {
        return conflict(response)
      }
      state.current = { ...state.current, ...body, version: state.current.version + 1 }
      return response(204).empty()
    }),
    http.put('/products/{productId}/discontinue', ({ response }) => {
      state.calls.push('PUT discontinue')
      state.current = {
        ...state.current,
        status: 'DISCONTINUED',
        actions: productActions({ publish: false, discontinue: false }),
        version: state.current.version + 1,
      }
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
  describe('基本情報の入力中に他の人が更新した（409）', () => {
    /** 商品名を入力し、他の人が商品名と価格を変えた後に、画面が取得し直した状態にする */
    async function editNameAfterOthersUpdate() {
      const state = serveProduct(productDetail())
      const { user } = renderApp(PAGE_PATH)
      const name = await screen.findByLabelText('商品名')
      await user.clear(name)
      await user.type(name, 'T シャツ（改）')
      state.current = productDetail({ name: 'T シャツ（新）', priceExclTax: 1200, priceInclTax: 1320, version: 1 })
      returnToTab()
      expect(await screen.findByText('1,320 円（税抜 1,200 円）')).toBeInTheDocument()
      return { state, user }
    }

    /** 取得し直した後に保存して 409 にし、競合の知らせが出た状態にする */
    async function saveIntoConflict() {
      const context = await editNameAfterOthersUpdate()
      await context.user.click(screen.getByRole('button', { name: '保存' }))
      expect(await screen.findByText(CONFLICT_MESSAGE)).toBeInTheDocument()
      return context
    }

    test('取得し直しても、入力した商品名は消えない', async () => {
      await editNameAfterOthersUpdate()

      expect(screen.getByLabelText('商品名')).toHaveValue('T シャツ（改）')
    })

    test('保存すると 409 になり、競合を知らせる', async () => {
      const { state } = await saveIntoConflict()

      expect(state.versions).toEqual([0])
    })

    test('409 になっても、入力した商品名は消えない', async () => {
      await saveIntoConflict()

      expect(screen.getByLabelText('商品名')).toHaveValue('T シャツ（改）')
    })

    test('再読み込みすると、最新の値に再作成され、競合の知らせが消える', async () => {
      const { user } = await saveIntoConflict()

      await user.click(screen.getByRole('button', { name: '再読み込み' }))

      expect(await screen.findByDisplayValue('T シャツ（新）')).toBe(screen.getByLabelText('商品名'))
      expect(screen.queryByText(CONFLICT_MESSAGE)).not.toBeInTheDocument()
    })

    test('再読み込みした後は、最新のバージョンで保存できる', async () => {
      const { state, user } = await saveIntoConflict()
      await user.click(screen.getByRole('button', { name: '再読み込み' }))
      await screen.findByDisplayValue('T シャツ（新）')

      await user.click(screen.getByRole('button', { name: '保存' }))

      expect(await screen.findByText('基本情報を保存しました')).toBeInTheDocument()
      expect(state.versions).toEqual([0, 1])
    })
  })

  test('AC-18、19 税区分を変えて基本情報を保存すると、入力した値とバージョンを送り、保存を知らせ、変更後の税率の税込価格を出す', async () => {
    const REDUCED_ID = '00000000-0000-0000-0000-000000000002'
    const CATEGORY_ID = '00000000-0000-0000-0000-000000000031'
    const state = serveProduct(productDetail())
    const requests: UpdateProductRequest[] = []
    server.use(
      http.get('/tax-categories', ({ response }) =>
        response(200).json({
          items: [taxCategory(), taxCategory({ id: REDUCED_ID, name: '軽減', currentRatePercent: 8 })],
        }),
      ),
      http.get('/categories', ({ response }) =>
        response(200).json({
          items: [
            {
              id: CATEGORY_ID,
              name: '衣類',
              depth: 1,
              displayOrder: 1,
              version: 0,
              children: [],
              actions: { addChild: true },
            },
          ],
        }),
      ),
      http.put('/products/{productId}', async ({ request, response }) => {
        const body = await request.json()
        requests.push(body)
        // 保存の後に取得し直す商品は、変更後の税率（8%）で計算した税込価格を返す
        state.current = productDetail({ ...body, priceInclTax: 1296, version: body.version + 1 })
        return response(204).empty()
      }),
    )
    const { user } = renderApp(PAGE_PATH)

    const name = await screen.findByLabelText('商品名')
    await user.clear(name)
    await user.type(name, 'T シャツ（改）')
    const description = screen.getByLabelText('説明')
    await user.clear(description)
    await user.type(description, '綿 100%')
    await user.click(screen.getByRole('combobox', { name: '税区分' }))
    await user.click(await screen.findByRole('option', { name: '軽減（現時点の率：8%）' }))
    const price = screen.getByLabelText('税抜価格（円）')
    await user.clear(price)
    await user.type(price, '1200')
    await user.click(await screen.findByRole('checkbox', { name: '衣類' }))
    await user.click(screen.getByRole('button', { name: '保存' }))

    expect(await screen.findByText('基本情報を保存しました')).toBeInTheDocument()
    expect(requests).toEqual([
      {
        name: 'T シャツ（改）',
        description: '綿 100%',
        taxCategoryId: REDUCED_ID,
        priceExclTax: 1200,
        categoryIds: [CATEGORY_ID],
        version: 0,
      },
    ])
    expect(await screen.findByText('1,296 円（税抜 1,200 円）')).toBeInTheDocument()
  })

  test('販売終了は確認のダイアログで確定したときだけ行い、やめれば何もしない', async () => {
    const state = serveProduct(
      productDetail({ status: 'PUBLISHED', actions: productActions({ publish: false, unpublish: true }) }),
    )
    const { user } = renderApp(PAGE_PATH)

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

  test('AC-33 C 側に表示されたことのない商品は、確認のダイアログで確定すると削除し、一覧に移る', async () => {
    const state = serveProduct(productDetail({ version: 3 }))
    const { user, router } = renderApp(PAGE_PATH)

    await user.click(await screen.findByRole('button', { name: '削除' }))
    const dialog = await screen.findByRole('alertdialog', { name: '商品「T シャツ」を削除しますか？' })
    await user.click(within(dialog).getByRole('button', { name: '削除する' }))

    expect(await screen.findByText('商品を削除しました')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/products')
    expect(state.calls).toEqual(['DELETE version=3'])
  })

  test('AC-33、269 削除できない商品には削除のボタンがなく、応答の削除できない理由（C 側に表示された）で販売終了を案内する', async () => {
    serveProduct(
      productDetail({
        status: 'PUBLISHED',
        firstShownAt: '2026-10-01T00:00:00+09:00',
        actions: productActions({
          publish: false,
          unpublish: true,
          delete: false,
          deleteVariants: false,
          deleteBlockedReason: 'ALREADY_SHOWN',
        }),
      }),
    )
    renderApp(PAGE_PATH)

    expect(
      await screen.findByText(
        '店舗に表示されたことがあるため、削除できません。取り扱いをやめるときは販売終了にしてください。',
      ),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '削除' })).not.toBeInTheDocument()
  })

  test('AC-269 削除できない理由が応答になければ、理由を推し量って出さない', async () => {
    serveProduct(productDetail({ actions: productActions({ delete: false }) }))
    renderApp(PAGE_PATH)

    expect(await screen.findByRole('button', { name: '販売終了にする' })).toBeInTheDocument()
    expect(screen.queryByText(/店舗に表示されたことがあるため/)).not.toBeInTheDocument()
  })

  test('AC-21、24 軸を持たない商品は、バリアントがなければ品番を大文字にして追加でき、1 つあれば追加の欄を出さない', async () => {
    const state = serveProduct(productDetail({ axes: [] }))
    const requests: AddVariantRequest[] = []
    server.use(stockHandler(0))
    server.use(
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
    server.use(stockHandler(0))
    server.use(
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
    /** 品番と軸の値を入力し、他の人が価格を変えた後に、画面が取得し直した状態にする */
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

    /** 取得し直した後に追加して 409 にし、競合の知らせが出た状態にする */
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
    server.use(stockHandler(3))
    server.use(
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
    server.use(stockHandler(3))
    server.use(
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
    server.use(stockHandler(3))
    server.use(
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

  test('AC-29、30 公開すると公開中の表示と知らせに変わり、非公開にすると非公開の表示と知らせに戻る', async () => {
    const state = serveProduct(productDetail())
    const calls: string[] = []
    const change = (status: ProductDetail['status'], actions: ProductDetail['actions']) => {
      state.current = { ...state.current, status, actions, version: state.current.version + 1 }
    }
    server.use(
      http.put('/products/{productId}/publish', ({ response }) => {
        calls.push('PUT publish')
        change('PUBLISHED', productActions({ publish: false, unpublish: true }))
        return response(204).empty()
      }),
      http.put('/products/{productId}/unpublish', ({ response }) => {
        calls.push('PUT unpublish')
        change('UNPUBLISHED', productActions())
        return response(204).empty()
      }),
    )
    const { user } = renderApp(PAGE_PATH)

    // 状態の表示は、画面の見出し（商品コードと状態）の中にある
    const header = await screen.findByRole('heading', { level: 1 })
    expect(await within(header).findByText('非公開')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '公開する' }))

    expect(await screen.findByText('公開しました')).toBeInTheDocument()
    expect(await within(header).findByText('公開')).toBeInTheDocument()
    expect(within(header).queryByText('非公開')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '非公開にする' }))

    expect(await screen.findByText('非公開にしました')).toBeInTheDocument()
    expect(await within(header).findByText('非公開')).toBeInTheDocument()
    expect(within(header).queryByText('公開')).not.toBeInTheDocument()
    expect(calls).toEqual(['PUT publish', 'PUT unpublish'])
  })
})

describe('公開期間', () => {
  /** 公開期間の設定に応答する。バージョンが今のものと違えば 409、終了が開始以前なら 400（AC-36） */
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
    /** 公開開始を入力し、他の人が商品名と公開終了を変えた後に、画面が取得し直した状態にする */
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

    /** 取得し直した後に保存して 409 にし、競合の知らせが出た状態にする */
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

describe('商品画像', () => {
  const UPLOAD_URL = 'http://storage.test/images-incoming/upload-1?X-Amz-Signature=test'

  /** 画像のアップロードの受付・ストレージへの PUT・登録に応答する。登録すると、商品の画像の最後に加える */
  function serveUpload(state: ReturnType<typeof serveProduct>) {
    const calls: string[] = []
    server.use(
      http.post('/products/{productId}/image-uploads', async ({ request, response }) => {
        const body = await request.json()
        calls.push(`受付 ${body.contentType} ${body.contentLength}`)
        return response(201).json({
          uploadId: '00000000-0000-0000-0000-000000000901',
          uploadUrl: UPLOAD_URL,
          headers: { 'Content-Type': body.contentType },
          expiresAt: '2026-10-10T10:05:00Z',
        })
      }),
      // ストレージへの送信は admin.yaml にない（署名付き URL への PUT）ので、型のない msw のハンドラーで作る
      rawHttp.put(UPLOAD_URL.split('?')[0], ({ request }) => {
        calls.push(`PUT ${request.headers.get('Content-Type')}`)
        return new HttpResponse(null, { status: 200 })
      }),
      http.post('/products/{productId}/images', async ({ request, response }) => {
        const body = await request.json()
        calls.push(`登録 version=${body.version}`)
        const id = `00000000-0000-0000-0000-00000000095${state.current.images.length}`
        state.current = {
          ...state.current,
          images: [...state.current.images, { id, url: `http://cdn.test/products/p/${id}.png` }],
          version: state.current.version + 1,
        }
        return response(201).json({ id })
      }),
    )
    return calls
  }

  test('AC-37 選んだ画像を受付・PUT・登録の順に送り、登録した画像を代表画像として出す', async () => {
    const state = serveProduct(productDetail())
    const calls = serveUpload(state)
    const { user } = renderApp(PAGE_PATH)

    await user.upload(
      await screen.findByLabelText('画像のファイル'),
      new File([new Uint8Array([0x89, 0x50])], 'a.png', { type: 'image/png' }),
    )

    expect(await screen.findByText('画像を 1 枚登録しました')).toBeInTheDocument()
    expect(await screen.findByRole('img', { name: '画像 1' })).toBeInTheDocument()
    const first = screen.getAllByRole('listitem').find((item) => within(item).queryByRole('img', { name: '画像 1' }))
    if (first === undefined) throw new Error('画像 1 の項目がない')
    expect(within(first).getByText('代表画像')).toBeInTheDocument()
    expect(calls).toEqual(['受付 image/png 2', 'PUT image/png', '登録 version=0'])
  })

  test('AC-39、67 受け付ける形式以外のファイルは送らずに誤りを出す', async () => {
    serveProduct(productDetail())
    renderApp(PAGE_PATH)
    // user.upload は、既定では入力の accept に合わないファイルを選べない（捨てる）ので、受け付けない形式は applyAccept を切って渡す
    const user = userEvent.setup({ applyAccept: false })

    await user.upload(
      await screen.findByLabelText('画像のファイル'),
      new File(['GIF89a'], 'a.gif', { type: 'image/gif' }),
    )

    expect(
      await screen.findByText('画像の形式は JPEG、PNG、WebP、BMP、HEIC / HEIF、AVIF のいずれかにしてください。'),
    ).toBeInTheDocument()
  })

  test('AC-67 ブラウザが形式を空で渡す HEIC も、拡張子で形式を補って送る', async () => {
    const state = serveProduct(productDetail())
    const calls = serveUpload(state)
    const { user } = renderApp(PAGE_PATH)

    await user.upload(
      await screen.findByLabelText('画像のファイル'),
      new File([new Uint8Array([0, 0, 0, 0x18])], 'IMG_0001.HEIC', { type: '' }),
    )

    expect(await screen.findByText('画像を 1 枚登録しました')).toBeInTheDocument()
    expect(calls).toEqual(['受付 image/heic 4', 'PUT image/heic', '登録 version=0'])
  })

  test('AC-70 変換に失敗した画像は登録されず、そのメッセージを出す', async () => {
    const state = serveProduct(productDetail())
    serveUpload(state)
    server.use(
      http.post('/products/{productId}/images', ({ response }) =>
        response(400).json(
          problem(
            400,
            'IMAGE_CONVERSION_FAILED',
            '画像を変換できませんでした。ファイルが壊れていないか確かめて、もう一度アップロードしてください。',
          ),
        ),
      ),
    )
    const { user } = renderApp(PAGE_PATH)

    await user.upload(
      await screen.findByLabelText('画像のファイル'),
      new File([new Uint8Array([0x89, 0x50])], 'a.png', { type: 'image/png' }),
    )

    expect(
      await screen.findByText(
        '画像を変換できませんでした。ファイルが壊れていないか確かめて、もう一度アップロードしてください。',
      ),
    ).toBeInTheDocument()
    expect(screen.queryByRole('img', { name: '画像 1' })).not.toBeInTheDocument()
  })

  test('AC-299 画像のサービスのエラー（502）なら、やり直せることを示す応答のメッセージを出す', async () => {
    const state = serveProduct(productDetail())
    serveUpload(state)
    server.use(
      http.post('/products/{productId}/images', ({ response }) =>
        response(502).json(
          problem(
            502,
            'IMAGE_SERVICE_UNAVAILABLE',
            '画像のサービスに接続できませんでした。しばらくしてから、やり直してください。',
          ),
        ),
      ),
    )
    const { user } = renderApp(PAGE_PATH)

    await user.upload(
      await screen.findByLabelText('画像のファイル'),
      new File([new Uint8Array([0x89, 0x50])], 'a.png', { type: 'image/png' }),
    )

    expect(
      await screen.findByText('画像のサービスに接続できませんでした。しばらくしてから、やり直してください。'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('img', { name: '画像 1' })).not.toBeInTheDocument()
  })

  test('AC-272 画像の説明に、契約の形式・大きさ・枚数の上限を差し込む', async () => {
    serveProduct(productDetail())
    renderApp(PAGE_PATH)

    expect(
      await screen.findByText(
        `JPEG、PNG、WebP、BMP、HEIC / HEIF、AVIF で 1 ファイル ${MAX_BYTES / (1024 * 1024)}MB まで、${MAX_IMAGES} 枚まで登録できます。先頭の画像が代表画像です。登録した画像は WebP に変換し、位置情報などの情報は取り除きます。`,
      ),
    ).toBeInTheDocument()
  })

  test('AC-40、272 契約の上限の枚数がある商品は、画像を追加できない', async () => {
    const images = Array.from({ length: MAX_IMAGES }, (_, index) => ({
      id: `00000000-0000-0000-0000-0000000009${String(index).padStart(2, '0')}`,
      url: `http://cdn.test/${index}.png`,
    }))
    serveProduct(productDetail({ images }))
    renderApp(PAGE_PATH)

    expect(await screen.findByRole('button', { name: '画像を追加' })).toBeDisabled()
  })

  test('AC-40 サーバーが上限で拒否したら、そのメッセージを出す', async () => {
    const state = serveProduct(productDetail())
    serveUpload(state)
    server.use(
      http.post('/products/{productId}/image-uploads', ({ response }) =>
        response(400).json(problem(400, 'TOO_MANY_PRODUCT_IMAGES', '画像は 1 商品あたり 20 枚まで登録できます。')),
      ),
    )
    const { user } = renderApp(PAGE_PATH)

    await user.upload(
      await screen.findByLabelText('画像のファイル'),
      new File([new Uint8Array([0x89, 0x50])], 'a.png', { type: 'image/png' }),
    )

    expect(await screen.findByText('画像は 1 商品あたり 20 枚まで登録できます。')).toBeInTheDocument()
  })

  test('ストレージへの送信に失敗したら、やり直しを促し、画像を登録しない', async () => {
    const state = serveProduct(productDetail())
    const calls = serveUpload(state)
    // ストレージの拒否（403）は admin.yaml にない応答なので、型のない msw のハンドラーで作る
    server.use(rawHttp.put(UPLOAD_URL.split('?')[0], () => new HttpResponse(null, { status: 403 })))
    const { user } = renderApp(PAGE_PATH)

    await user.upload(
      await screen.findByLabelText('画像のファイル'),
      new File([new Uint8Array([0x89, 0x50])], 'a.png', { type: 'image/png' }),
    )

    expect(
      await screen.findByText('画像のファイルを送れませんでした。もう一度やり直してください。'),
    ).toBeInTheDocument()
    expect(calls.filter((call) => call.startsWith('登録'))).toEqual([])
  })

  test('AC-38 バリアントの画像に、その商品の画像を選べる', async () => {
    const images = [
      { id: '00000000-0000-0000-0000-000000000951', url: 'http://cdn.test/1.png' },
      { id: '00000000-0000-0000-0000-000000000952', url: 'http://cdn.test/2.png' },
    ]
    const state = serveProduct(productDetail({ images, variants: [variant()] }))
    const requests: SetVariantImageRequest[] = []
    server.use(stockHandler(0))
    server.use(
      http.put('/products/{productId}/variants/{variantId}/image', async ({ request, response }) => {
        const body = await request.json()
        requests.push(body)
        state.current = {
          ...state.current,
          variants: [variant({ imageId: body.imageId ?? null })],
          version: state.current.version + 1,
        }
        return response(204).empty()
      }),
    )
    const { user } = renderApp(PAGE_PATH)

    await user.click(await screen.findByRole('combobox', { name: 'ABC-001-REDの画像' }))
    await user.click(await screen.findByRole('option', { name: '画像 2' }))

    expect(await screen.findByText('バリアントの画像を変更しました')).toBeInTheDocument()
    expect(requests).toEqual([{ imageId: images[1].id, version: 0 }])
    expect(screen.getByRole('combobox', { name: 'ABC-001-REDの画像' })).toHaveTextContent('画像 2')
  })

  const TWO_IMAGES = [
    { id: '00000000-0000-0000-0000-000000000951', url: 'http://cdn.test/1.png' },
    { id: '00000000-0000-0000-0000-000000000952', url: 'http://cdn.test/2.png' },
  ]

  test('AC-37 画像を後へ移すと、移した後の並びを商品のバージョンとともに送る', async () => {
    const state = serveProduct(productDetail({ version: 5, images: TWO_IMAGES }))
    const requests: ReorderProductImagesRequest[] = []
    server.use(
      http.put('/products/{productId}/images/order', async ({ request, response }) => {
        requests.push(await request.json())
        state.current = {
          ...state.current,
          images: [...state.current.images].reverse(),
          version: state.current.version + 1,
        }
        return response(204).empty()
      }),
    )
    const { user } = renderApp(PAGE_PATH)

    await user.click(await screen.findByRole('button', { name: '画像 1を後へ' }))

    await waitFor(() => expect(requests).toHaveLength(1))
    expect(requests).toEqual([{ imageIds: [TWO_IMAGES[1].id, TWO_IMAGES[0].id], version: 5 }])
  })

  test('確認のダイアログで確定すると、商品のバージョンを添えて画像を削除する', async () => {
    const state = serveProduct(productDetail({ version: 5, images: TWO_IMAGES }))
    const calls: string[] = []
    server.use(
      http.delete('/products/{productId}/images/{imageId}', ({ params, query, response }) => {
        calls.push(`DELETE ${params.imageId} version=${query.get('version')}`)
        state.current = { ...state.current, images: [TWO_IMAGES[1]], version: state.current.version + 1 }
        return response(204).empty()
      }),
    )
    const { user } = renderApp(PAGE_PATH)

    await user.click(await screen.findByRole('button', { name: '画像 1を削除' }))
    const dialog = await screen.findByRole('alertdialog', { name: '画像 1を削除しますか？' })
    await user.click(within(dialog).getByRole('button', { name: '削除する' }))

    expect(await screen.findByText('画像を削除しました')).toBeInTheDocument()
    expect(calls).toEqual([`DELETE ${TWO_IMAGES[0].id} version=5`])
  })
})
