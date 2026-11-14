import { screen, waitFor, within } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import { HttpResponse, http as rawHttp } from 'msw'
import { describe, expect, test } from 'vitest'
import type { components } from '@/api/generated/admin'
import { schemas } from '@/api/generated/constraints'
import { productDetail, variant } from '@/test/fixtures'
import { PAGE_PATH, serveProduct } from '@/test/productEditServer'
import { getListItemContaining } from '@/test/queries'
import { renderApp } from '@/test/renderApp'
import { http, problem, server, stockHandler } from '@/test/server'

/** バリアントの画像の指定のリクエストの本文（送った内容を確認する） */
type SetVariantImageRequest = components['schemas']['SetVariantImageRequest']

/** 商品の画像の並び替えのリクエストの本文（送った内容を確認する） */
type ReorderProductImagesRequest = components['schemas']['ReorderProductImagesRequest']

/** 契約の画像の大きさの上限（バイト） */
const MAX_BYTES = schemas.CreateImageUploadRequest.contentLength.maximum

/** 契約の商品の画像の枚数の上限 */
const MAX_IMAGES = schemas.ProductDetail.images.maxItems

/** 2 枚の画像を持つ商品の画像 */
const TWO_IMAGES = [
  { id: '00000000-0000-0000-0000-000000000951', url: 'http://cdn.test/1.png' },
  { id: '00000000-0000-0000-0000-000000000952', url: 'http://cdn.test/2.png' },
]

describe('商品画像', () => {
  const UPLOAD_URL = 'http://storage.test/images-incoming/upload-1?X-Amz-Signature=test'

  /** 画像のファイルの欄に、PNG のファイルを選ぶ。 */
  async function uploadPng(user: UserEvent) {
    await user.upload(
      await screen.findByLabelText('画像のファイル'),
      new File([new Uint8Array([0x89, 0x50])], 'a.png', { type: 'image/png' }),
    )
  }

  /**
   * 画像のアップロードの受付・ストレージへの PUT・登録に応答する。
   *
   * 登録すると、商品の画像の最後に加える。
   *
   * @param state 商品の編集の画面が使う API の状態
   * @returns 受けた呼び出し
   */
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

    await uploadPng(user)

    expect(await screen.findByText('画像を 1 枚登録しました')).toBeInTheDocument()
    const image = await screen.findByRole('img', { name: '画像 1' })
    expect(image).toBeInTheDocument()
    const first = getListItemContaining(image)
    expect(within(first).getByText('代表画像')).toBeInTheDocument()
    expect(calls).toEqual(['受付 image/png 2', 'PUT image/png', '登録 version=0'])
  })

  test('AC-39、67 受け付ける形式以外のファイルは送らずに誤りを出す', async () => {
    serveProduct(productDetail())
    renderApp(PAGE_PATH)
    // user.upload は、既定では入力の accept に合わないファイルを選べない（捨てる）ので、受け付けない形式は applyAccept を切って渡す
    const user = userEvent.setup({ delay: null, applyAccept: false })

    await user.upload(
      await screen.findByLabelText('画像のファイル'),
      new File(['GIF89a'], 'a.gif', { type: 'image/gif' }),
    )

    expect(
      await screen.findByText('画像の形式は JPEG、PNG、WebP、BMP、HEIC / HEIF、AVIF のいずれかにしてください。'),
    ).toBeInTheDocument()
  })

  test('AC-67 ブラウザーが形式を空で渡す HEIC も、拡張子で形式を補って送る', async () => {
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

    await uploadPng(user)

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

    await uploadPng(user)

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

    await uploadPng(user)

    expect(await screen.findByText('画像は 1 商品あたり 20 枚まで登録できます。')).toBeInTheDocument()
  })

  test('ストレージへの送信に失敗したら、やり直しを促し、画像を登録しない', async () => {
    const state = serveProduct(productDetail())
    const calls = serveUpload(state)
    // ストレージの拒否（403）は admin.yaml にない応答なので、型のない msw のハンドラーで作る
    server.use(rawHttp.put(UPLOAD_URL.split('?')[0], () => new HttpResponse(null, { status: 403 })))
    const { user } = renderApp(PAGE_PATH)

    await uploadPng(user)

    expect(
      await screen.findByText('画像のファイルを送れませんでした。もう一度やり直してください。'),
    ).toBeInTheDocument()
    expect(calls.filter((call) => call.startsWith('登録'))).toEqual([])
  })

  test('AC-38 バリアントの画像に、その商品の画像を選べる', async () => {
    const state = serveProduct(productDetail({ images: TWO_IMAGES, variants: [variant()] }))
    const requests: SetVariantImageRequest[] = []
    server.use(
      stockHandler(0),
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
    expect(requests).toEqual([{ imageId: TWO_IMAGES[1].id, version: 0 }])
    // 知らせは商品の再取得より前に表示されるので、再取得した商品が表示されるまで待つ
    const select = screen.getByRole('combobox', { name: 'ABC-001-REDの画像' })
    await waitFor(() => expect(select).toHaveTextContent('画像 2'))
  })

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
