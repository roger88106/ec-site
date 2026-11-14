import { screen, within } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import type { components } from '@/api/generated/admin'
import type { ProductDetail } from '@/api/types'
import { CONFLICT_MESSAGE, productActions, productDetail, taxCategory } from '@/test/fixtures'
import { returnToTab } from '@/test/inputs'
import { PAGE_PATH, serveProduct } from '@/test/productEditServer'
import { renderApp } from '@/test/renderApp'
import { http, server } from '@/test/server'

/** 商品の基本情報の更新のリクエストの本文（送った内容を確認する） */
type UpdateProductRequest = components['schemas']['UpdateProductRequest']

describe('商品の編集', () => {
  describe('基本情報の入力中に他の人が更新した（409）', () => {
    /** 商品名を入力し、他の人が商品名と価格を変えた後に、画面が取得し直した状態にする。 */
    async function editNameAfterOthersUpdate() {
      const state = serveProduct(productDetail())
      const { user } = renderApp(PAGE_PATH)
      const nameField = await screen.findByLabelText('商品名')
      await user.clear(nameField)
      await user.type(nameField, 'T シャツ（改）')
      state.current = productDetail({ name: 'T シャツ（新）', priceExclTax: 1200, priceInclTax: 1320, version: 1 })
      returnToTab()
      expect(await screen.findByText('1,320 円（税抜 1,200 円）')).toBeInTheDocument()
      return { state, user }
    }

    /** 取得し直した後に保存して 409 にし、競合の知らせが出た状態にする。 */
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

    const nameField = await screen.findByLabelText('商品名')
    await user.clear(nameField)
    await user.type(nameField, 'T シャツ（改）')
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
