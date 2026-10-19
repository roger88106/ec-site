import { fireEvent, screen, within } from '@testing-library/react'
import { HttpResponse, http as rawHttp } from 'msw'
import { describe, expect, it } from 'vitest'
import type { ProductDetail } from '@/api/types'
import { PRODUCT_ID, productActions, productDetail, taxCategory, variant } from '@/test/fixtures'
import { renderApp } from '@/test/renderApp'
import { http, problem, server } from '@/test/server'

/** 商品の編集の画面の URL */
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
    const state = serveProduct(
      productDetail({ status: 'PUBLISHED', actions: productActions({ publish: false, unpublish: true }) }),
    )
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

  it('AC-33、269 削除できない商品には削除のボタンがなく、応答の削除できない理由（C 側に表示された）で販売終了を案内する', async () => {
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
    renderApp(URL)

    expect(
      await screen.findByText(
        '店舗に表示されたことがあるため、削除できません。取り扱いをやめるときは販売終了にしてください。',
      ),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '削除' })).not.toBeInTheDocument()
  })

  it('AC-269 削除できない理由が応答になければ、理由を推し量って出さない', async () => {
    serveProduct(productDetail({ actions: productActions({ delete: false }) }))
    renderApp(URL)

    expect(await screen.findByRole('button', { name: '販売終了にする' })).toBeInTheDocument()
    expect(screen.queryByText(/店舗に表示されたことがあるため/)).not.toBeInTheDocument()
  })
  it('AC-24 軸を持たない商品は、バリアントがなければ追加の欄を出し、1 つあれば追加の欄を出さない', async () => {
    const state = serveProduct(productDetail({ axes: [] }))
    server.use(
      http.get('/inventory/variants/{variantId}', ({ params, response }) =>
        response(200).json({ variantId: params.variantId, quantity: 0 }),
      ),
      http.post('/products/{productId}/variants', ({ response }) => {
        state.current = {
          ...state.current,
          variants: [variant({ axisValues: [] })],
          actions: productActions({ addVariant: false }),
          version: 1,
        }
        return response(201).json({ id: variant().id })
      }),
    )
    const { user } = renderApp(URL)

    await user.type(await screen.findByLabelText('バリアント品番'), 'abc-001-x')
    await user.click(screen.getByRole('button', { name: 'バリアントを追加' }))

    expect(await screen.findByText('軸を持たない商品のバリアントは 1 つだけです。')).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'ABC-001-RED' })).toBeInTheDocument()
    expect(screen.queryByLabelText('バリアント品番')).not.toBeInTheDocument()
  })
  it('AC-28 バリアントを販売停止にすると「販売停止」と出て、販売を再開できる', async () => {
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
      http.get('/inventory/variants/{variantId}', ({ params, response }) =>
        response(200).json({ variantId: params.variantId, quantity: 3 }),
      ),
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
    const { user } = renderApp(URL)
    const row = () => {
      const found = screen.getByRole('cell', { name: 'ABC-001-RED' }).closest('tr')
      if (!found) {
        throw new Error('ABC-001-RED の行がない')
      }
      return found
    }

    await user.click(await screen.findByRole('button', { name: 'ABC-001-REDを販売停止' }))

    expect(await screen.findByText('販売停止にしました')).toBeInTheDocument()
    expect(await within(row()).findByText('販売停止')).toBeInTheDocument()

    await user.click(within(row()).getByRole('button', { name: 'ABC-001-REDの販売を再開' }))

    expect(await screen.findByText('販売を再開しました')).toBeInTheDocument()
    expect(await within(row()).findByText('販売中')).toBeInTheDocument()
    expect(calls).toEqual([`suspend ${variant().id}`, `resume ${variant().id}`])
  })
})

describe('公開期間', () => {
  type PeriodBody = { publishStart?: string | null; publishEnd?: string | null; version: number }

  /** 公開期間の設定に応答する。バージョンが今のものと違えば 409、終了が開始以前なら 400（AC-36） */
  function servePeriod(state: ReturnType<typeof serveProduct>) {
    const requests: PeriodBody[] = []
    server.use(
      http.put('/products/{productId}/publication-period', async ({ request, response }) => {
        const body = await request.json()
        requests.push(body)
        if (body.version !== state.current.version) {
          return Response.json(problem(409, 'CONCURRENT_MODIFICATION', 'バージョンが一致しません。'), {
            status: 409,
          }) as never
        }
        if (body.publishStart && body.publishEnd && body.publishStart >= body.publishEnd) {
          return Response.json(
            problem(400, 'INVALID_PUBLICATION_PERIOD', '公開終了日時は、公開開始日時より後にしてください。'),
            { status: 400 },
          ) as never
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

  it('AC-34 日本時間で入れた公開開始・終了を保存し、保存を知らせる', async () => {
    const state = serveProduct(productDetail())
    const requests = servePeriod(state)
    const { user } = renderApp(URL)

    fireEvent.change(await screen.findByLabelText('公開開始'), { target: { value: '2026-11-01T10:00' } })
    fireEvent.change(screen.getByLabelText('公開終了（この日時は含まない）'), { target: { value: '2026-12-01T00:00' } })
    await user.click(screen.getByRole('button', { name: '公開期間を保存' }))

    expect(await screen.findByText('公開期間を保存しました')).toBeInTheDocument()
    expect(requests).toEqual([
      { publishStart: '2026-11-01T10:00:00+09:00', publishEnd: '2026-12-01T00:00:00+09:00', version: 0 },
    ])
  })

  it('AC-36 終了が開始より後でなければ、サーバーのエラーのメッセージを出す', async () => {
    const state = serveProduct(productDetail())
    servePeriod(state)
    const { user } = renderApp(URL)

    fireEvent.change(await screen.findByLabelText('公開開始'), { target: { value: '2026-11-01T10:00' } })
    fireEvent.change(screen.getByLabelText('公開終了（この日時は含まない）'), { target: { value: '2026-11-01T10:00' } })
    await user.click(screen.getByRole('button', { name: '公開期間を保存' }))

    expect(await screen.findByText('公開終了日時は、公開開始日時より後にしてください。')).toBeInTheDocument()
  })

  it('入力中に他の人が更新すると、取得し直しても入力は消えず、保存は 409 になり、再読み込みで最新の期間に作り直す', async () => {
    const state = serveProduct(productDetail())
    const requests = servePeriod(state)
    const { user } = renderApp(URL)

    fireEvent.change(await screen.findByLabelText('公開開始'), { target: { value: '2026-11-01T10:00' } })
    // 入力中に、他の人が商品名を変えた。利用者が別のタブから戻ると、画面は取得し直す
    state.current = productDetail({ name: 'T シャツ（新）', version: 1, publishEnd: '2026-12-31T15:00:00Z' })
    fireEvent(window, new Event('visibilitychange'))
    expect(await screen.findByDisplayValue('T シャツ（新）')).toBeInTheDocument()
    expect(screen.getByLabelText('公開開始')).toHaveValue('2026-11-01T10:00')
    await user.click(screen.getByRole('button', { name: '公開期間を保存' }))

    expect(await screen.findByText('他の人が先に更新しました。再読み込みしてください')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '再読み込み' }))
    expect(await screen.findByDisplayValue('2027-01-01T00:00')).toBe(
      screen.getByLabelText('公開終了（この日時は含まない）'),
    )
    expect(screen.getByLabelText('公開開始')).toHaveValue('')
    expect(requests.map((request) => request.version)).toEqual([0])
  })
})

describe('商品画像', () => {
  const UPLOAD_URL = 'http://storage.test/images-incoming/upload-1?X-Amz-Signature=test'

  /** 画像の受付・ストレージへの PUT・登録に応答する。登録すると、商品の画像の最後に加える */
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

  it('AC-37 選んだ画像を受付・PUT・登録の順に送り、登録した画像を代表画像として出す', async () => {
    const state = serveProduct(productDetail())
    const calls = serveUpload(state)
    const { user } = renderApp(URL)

    await user.upload(
      await screen.findByLabelText('画像のファイル'),
      new File([new Uint8Array([0x89, 0x50])], 'a.png', { type: 'image/png' }),
    )

    expect(await screen.findByText('画像を 1 枚登録しました')).toBeInTheDocument()
    expect(await screen.findByRole('img', { name: '画像 1' })).toBeInTheDocument()
    expect(screen.getByText('代表画像', { selector: 'span' })).toBeInTheDocument()
    expect(calls).toEqual(['受付 image/png 2', 'PUT image/png', '登録 version=0'])
  })

  it('AC-39、67 受け付ける形式以外のファイルは送らずに誤りを出す', async () => {
    serveProduct(productDetail())
    renderApp(URL)

    fireEvent.change(await screen.findByLabelText('画像のファイル'), {
      target: { files: [new File(['GIF89a'], 'a.gif', { type: 'image/gif' })] },
    })

    expect(
      await screen.findByText('画像の形式は JPEG、PNG、WebP、BMP、HEIC / HEIF、AVIF のいずれかにしてください。'),
    ).toBeInTheDocument()
  })

  it('AC-67 ブラウザが形式を空で渡す HEIC も、拡張子で形式を補って送る', async () => {
    const state = serveProduct(productDetail())
    const calls = serveUpload(state)
    renderApp(URL)

    fireEvent.change(await screen.findByLabelText('画像のファイル'), {
      target: { files: [new File([new Uint8Array([0, 0, 0, 0x18])], 'IMG_0001.HEIC', { type: '' })] },
    })

    expect(await screen.findByText('画像を 1 枚登録しました')).toBeInTheDocument()
    expect(calls).toEqual(['受付 image/heic 4', 'PUT image/heic', '登録 version=0'])
  })

  it('AC-70 変換に失敗した画像は登録されず、そのメッセージを出す', async () => {
    const state = serveProduct(productDetail())
    serveUpload(state)
    server.use(
      http.post('/products/{productId}/images', () =>
        Response.json(
          problem(
            400,
            'IMAGE_CONVERSION_FAILED',
            '画像を変換できませんでした。ファイルが壊れていないか確かめて、もう一度アップロードしてください。',
          ),
          { status: 400 },
        ),
      ),
    )
    const { user } = renderApp(URL)

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

  it('AC-40 20 枚ある商品は、画像を追加できない', async () => {
    const images = Array.from({ length: 20 }, (_, index) => ({
      id: `00000000-0000-0000-0000-0000000009${String(index).padStart(2, '0')}`,
      url: `http://cdn.test/${index}.png`,
    }))
    serveProduct(productDetail({ images }))
    renderApp(URL)

    expect(await screen.findByRole('button', { name: '画像を追加' })).toBeDisabled()
  })

  it('AC-40 サーバーが上限で拒否したら、そのメッセージを出す', async () => {
    const state = serveProduct(productDetail())
    serveUpload(state)
    server.use(
      http.post('/products/{productId}/image-uploads', () =>
        Response.json(problem(400, 'TOO_MANY_PRODUCT_IMAGES', '画像は 1 商品あたり 20 枚まで登録できます。'), {
          status: 400,
        }),
      ),
    )
    const { user } = renderApp(URL)

    await user.upload(
      await screen.findByLabelText('画像のファイル'),
      new File([new Uint8Array([0x89, 0x50])], 'a.png', { type: 'image/png' }),
    )

    expect(await screen.findByText('画像は 1 商品あたり 20 枚まで登録できます。')).toBeInTheDocument()
  })

  it('ストレージへの送信に失敗したら、やり直しを促し、画像を登録しない', async () => {
    const state = serveProduct(productDetail())
    const calls = serveUpload(state)
    server.use(rawHttp.put(UPLOAD_URL.split('?')[0], () => new HttpResponse(null, { status: 403 })))
    const { user } = renderApp(URL)

    await user.upload(
      await screen.findByLabelText('画像のファイル'),
      new File([new Uint8Array([0x89, 0x50])], 'a.png', { type: 'image/png' }),
    )

    expect(
      await screen.findByText('画像のファイルを送れませんでした。もう一度やり直してください。'),
    ).toBeInTheDocument()
    expect(calls.filter((call) => call.startsWith('登録'))).toEqual([])
  })

  it('AC-38 バリアントの画像に、その商品の画像を選べる', async () => {
    const images = [
      { id: '00000000-0000-0000-0000-000000000951', url: 'http://cdn.test/1.png' },
      { id: '00000000-0000-0000-0000-000000000952', url: 'http://cdn.test/2.png' },
    ]
    const state = serveProduct(productDetail({ images, variants: [variant()] }))
    const requests: unknown[] = []
    server.use(
      http.get('/inventory/variants/{variantId}', ({ params, response }) =>
        response(200).json({ variantId: params.variantId, quantity: 0 }),
      ),
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
    const { user } = renderApp(URL)

    await user.click(await screen.findByRole('combobox', { name: 'ABC-001-REDの画像' }))
    await user.click(await screen.findByRole('option', { name: '画像 2' }))

    expect(await screen.findByText('バリアントの画像を変更しました')).toBeInTheDocument()
    expect(requests).toEqual([{ imageId: images[1].id, version: 0 }])
    expect(screen.getByRole('combobox', { name: 'ABC-001-REDの画像' })).toHaveTextContent('画像 2')
  })
})
