import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { components } from '@/api/generated/admin'
import type { CategoryNode } from '@/api/types'
import { renderApp } from '@/test/renderApp'
import { http, problem, server } from '@/test/server'

/** カテゴリの登録の要求の本文 */
type CreateCategoryRequest = components['schemas']['CreateCategoryRequest']

/**
 * カテゴリ（テストのデータ）。
 *
 * @param id       ID の末尾（3 桁）
 * @param name     名称
 * @param depth    階層（最上位が 1）
 * @param children 子
 */
function category(id: string, name: string, depth: number, children: CategoryNode[] = []): CategoryNode {
  return { id: `00000000-0000-0000-0000-000000000${id}`, name, depth, displayOrder: 0, version: 3, children }
}

/** 衣類 > トップス > T シャツ と、雑貨 */
const T_SHIRTS = category('103', 'T シャツ', 3)
const TOPS = category('102', 'トップス', 2, [T_SHIRTS])
const CLOTHING = category('101', '衣類', 1, [TOPS])
const GOODS = category('201', '雑貨', 1)

/**
 * カテゴリの画面が使う API に応答する。木は tree を返し、変更の要求は requests に取っておく。<br />
 * 登録・名称の変更・削除は、それぞれ create・rename・remove の応答を返す（既定は成功）。
 */
function serveCategories(
  tree: CategoryNode[],
  responses: { create?: Response; rename?: Response; remove?: Response } = {},
) {
  const requests: { method: string; path: string; body?: unknown }[] = []
  server.use(
    http.get('/categories', ({ response }) => response(200).json({ items: tree })),
    http.post('/categories', async ({ request, response }) => {
      requests.push({ method: 'POST', path: '/categories', body: await request.json() })
      return (responses.create as never) ?? response(201).json({ id: '00000000-0000-0000-0000-000000000999' })
    }),
    http.put('/categories/{categoryId}', async ({ params, request, response }) => {
      requests.push({ method: 'PUT', path: `/categories/${params.categoryId}`, body: await request.json() })
      return (responses.rename as never) ?? response(204).empty()
    }),
    http.put('/categories/order', async ({ request, response }) => {
      requests.push({ method: 'PUT', path: '/categories/order', body: await request.json() })
      return response(204).empty()
    }),
    http.delete('/categories/{categoryId}', ({ params, response }) => {
      requests.push({ method: 'DELETE', path: `/categories/${params.categoryId}` })
      return (responses.remove as never) ?? response(204).empty()
    }),
  )
  return requests
}

/** カテゴリの行（名称の span を含む行の要素） */
async function row(name: string) {
  const label = await screen.findByText(name, { selector: 'span' })
  return within(label.parentElement as HTMLElement)
}

describe('カテゴリの画面', () => {
  it('AC-8 カテゴリの木を、階層の入れ子で表示する', async () => {
    serveCategories([CLOTHING, GOODS])
    renderApp('/categories')

    const clothing = (await screen.findByText('衣類', { selector: 'span' })).closest('li') as HTMLElement
    expect(within(clothing).getByText('トップス')).toBeInTheDocument()
    expect(within(clothing).getByText('T シャツ')).toBeInTheDocument()
    expect(within(clothing).queryByText('雑貨')).not.toBeInTheDocument()
  })

  it('AC-8 親を指定せずに登録すると、最上位のカテゴリになる', async () => {
    const requests = serveCategories([GOODS])
    const { user } = renderApp('/categories')

    await user.click(await screen.findByRole('button', { name: '最上位に追加' }))
    const dialog = await screen.findByRole('dialog', { name: '最上位のカテゴリを追加' })
    await user.type(within(dialog).getByLabelText('名称'), '食品')
    await user.click(within(dialog).getByRole('button', { name: '保存' }))

    expect(await screen.findByText('カテゴリを登録しました')).toBeInTheDocument()
    expect(requests).toEqual([
      { method: 'POST', path: '/categories', body: { name: '食品', parentId: null } satisfies CreateCategoryRequest },
    ])
  })

  it('AC-8 子を追加すると、その行のカテゴリを親として登録する', async () => {
    const requests = serveCategories([CLOTHING])
    const { user } = renderApp('/categories')

    await user.click((await row('衣類')).getByRole('button', { name: '子を追加' }))
    const dialog = await screen.findByRole('dialog', { name: '「衣類」の子カテゴリを追加' })
    await user.type(within(dialog).getByLabelText('名称'), 'ボトムス')
    await user.click(within(dialog).getByRole('button', { name: '保存' }))

    expect(await screen.findByText('カテゴリを登録しました')).toBeInTheDocument()
    expect(requests[0]?.body).toEqual({ name: 'ボトムス', parentId: CLOTHING.id })
  })

  it('AC-9 3 階層目のカテゴリには、子を追加する操作を出さない', async () => {
    serveCategories([CLOTHING])
    renderApp('/categories')

    expect((await row('トップス')).getByRole('button', { name: '子を追加' })).toBeInTheDocument()
    expect((await row('T シャツ')).queryByRole('button', { name: '子を追加' })).not.toBeInTheDocument()
  })

  it('AC-10 同じ親の下に同じ名称があれば、ダイアログにエラーを出し、入力を残す', async () => {
    serveCategories([GOODS], {
      create: Response.json(
        problem(409, 'CATEGORY_NAME_ALREADY_EXISTS', '同じ親の下に、同じ名称のカテゴリがすでにあります。'),
        { status: 409 },
      ),
    })
    const { user } = renderApp('/categories')

    await user.click(await screen.findByRole('button', { name: '最上位に追加' }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText('名称'), '雑貨')
    await user.click(within(dialog).getByRole('button', { name: '保存' }))

    expect(await within(dialog).findByText('同じ親の下に、同じ名称のカテゴリがすでにあります。')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('名称')).toHaveValue('雑貨')
  })

  it('AC-11 名称を変えると、ダイアログを開いたときのバージョンを添えて送る', async () => {
    const requests = serveCategories([GOODS])
    const { user } = renderApp('/categories')

    await user.click((await row('雑貨')).getByRole('button', { name: '名称の変更' }))
    const dialog = await screen.findByRole('dialog', { name: '名称の変更' })
    const input = within(dialog).getByLabelText('名称')
    expect(input).toHaveValue('雑貨')
    await user.clear(input)
    await user.type(input, '生活雑貨')
    await user.click(within(dialog).getByRole('button', { name: '保存' }))

    expect(await screen.findByText('名称を変更しました')).toBeInTheDocument()
    expect(requests).toEqual([
      { method: 'PUT', path: `/categories/${GOODS.id}`, body: { name: '生活雑貨', version: 3 } },
    ])
  })

  it('AC-11 並び順を変えると、兄弟の並びを送る。先頭は上へ、末尾は下へ動かせない', async () => {
    const requests = serveCategories([CLOTHING, GOODS])
    const { user } = renderApp('/categories')

    expect(await screen.findByRole('button', { name: '衣類を上へ' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '雑貨を下へ' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: '衣類を下へ' }))

    await waitFor(() => expect(requests).toHaveLength(1))
    expect(requests[0]).toEqual({
      method: 'PUT',
      path: '/categories/order',
      body: { parentId: null, categoryIds: [GOODS.id, CLOTHING.id] },
    })
  })

  it('AC-12 子を持つカテゴリは、確認の後に削除しようとしても、削除できない理由を出す', async () => {
    const requests = serveCategories([CLOTHING], {
      remove: Response.json(problem(400, 'CATEGORY_HAS_CHILDREN', '子カテゴリを持つカテゴリは削除できません。'), {
        status: 400,
      }),
    })
    const { user } = renderApp('/categories')

    await user.click((await row('衣類')).getByRole('button', { name: '削除' }))
    await user.click(await screen.findByRole('button', { name: '削除する' }))

    expect(await screen.findByText('子カテゴリを持つカテゴリは削除できません。')).toBeInTheDocument()
    expect(requests).toEqual([{ method: 'DELETE', path: `/categories/${CLOTHING.id}` }])
  })

  it('AC-12 商品が所属しているカテゴリは、削除できない理由を出す', async () => {
    serveCategories([GOODS], {
      remove: Response.json(problem(400, 'CATEGORY_HAS_PRODUCTS', '商品が所属しているカテゴリは削除できません。'), {
        status: 400,
      }),
    })
    const { user } = renderApp('/categories')

    await user.click((await row('雑貨')).getByRole('button', { name: '削除' }))
    await user.click(await screen.findByRole('button', { name: '削除する' }))

    expect(await screen.findByText('商品が所属しているカテゴリは削除できません。')).toBeInTheDocument()
  })

  it('AC-12 子も商品もないカテゴリは、確認の後に削除する', async () => {
    const requests = serveCategories([GOODS])
    const { user } = renderApp('/categories')

    await user.click((await row('雑貨')).getByRole('button', { name: '削除' }))
    await user.click(await screen.findByRole('button', { name: '削除する' }))

    expect(await screen.findByText('カテゴリを削除しました')).toBeInTheDocument()
    expect(requests).toEqual([{ method: 'DELETE', path: `/categories/${GOODS.id}` }])
  })
})
