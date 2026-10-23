import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import type { components } from '@/api/generated/admin'
import type { CategoryNode } from '@/api/types'
import { categoryNode } from '@/test/fixtures'
import { findGroup } from '@/test/queries'
import { renderApp } from '@/test/renderApp'
import { conflict, http, problem, type Reply, server } from '@/test/server'

/** カテゴリの登録の要求の本文 */
type CreateCategoryRequest = components['schemas']['CreateCategoryRequest']
/** カテゴリの名称の変更の要求の本文 */
type RenameCategoryRequest = components['schemas']['RenameCategoryRequest']

/** 衣類 > トップス > T シャツ と、雑貨 */
const T_SHIRTS = categoryNode('103', 'T シャツ', 3)
const TOPS = categoryNode('102', 'トップス', 2, [T_SHIRTS])
const CLOTHING = categoryNode('101', '衣類', 1, [TOPS])
const GOODS = categoryNode('201', '雑貨', 1)

/**
 * カテゴリの画面が使う API に応答する。木は tree を返し、変更の要求は requests に取っておく。<br />
 * 登録・名称の変更・削除は、それぞれ create・rename・remove の関数があればその応答を、なければ成功を返す。
 */
function serveCategories(
  tree: CategoryNode[],
  responses: {
    create?: Reply<'/categories', 'post', CreateCategoryRequest>
    rename?: Reply<'/categories/{categoryId}', 'put', RenameCategoryRequest>
    remove?: Reply<'/categories/{categoryId}', 'delete', string>
  } = {},
) {
  const requests: { method: string; path: string; body?: unknown }[] = []
  server.use(
    http.get('/categories', ({ response }) => response(200).json({ items: tree })),
    http.post('/categories', async ({ request, response }) => {
      const body = await request.json()
      requests.push({ method: 'POST', path: '/categories', body })
      return responses.create?.(body, response) ?? response(201).json({ id: '00000000-0000-0000-0000-000000000999' })
    }),
    http.put('/categories/{categoryId}', async ({ params, request, response }) => {
      const body = await request.json()
      requests.push({ method: 'PUT', path: `/categories/${params.categoryId}`, body })
      return responses.rename?.(body, response) ?? response(204).empty()
    }),
    http.put('/categories/order', async ({ request, response }) => {
      requests.push({ method: 'PUT', path: '/categories/order', body: await request.json() })
      return response(204).empty()
    }),
    http.delete('/categories/{categoryId}', ({ params, response }) => {
      requests.push({ method: 'DELETE', path: `/categories/${params.categoryId}` })
      return responses.remove?.(params.categoryId, response) ?? response(204).empty()
    }),
  )
  return requests
}

describe('カテゴリの画面', () => {
  test('AC-8 カテゴリの木を、階層の入れ子で表示する', async () => {
    serveCategories([CLOTHING, GOODS])
    renderApp('/categories')

    const clothing = await screen.findByRole('listitem', { name: '衣類' })
    expect(within(clothing).getByText('トップス')).toBeInTheDocument()
    expect(within(clothing).getByText('T シャツ')).toBeInTheDocument()
    expect(within(clothing).queryByText('雑貨')).not.toBeInTheDocument()
  })

  test('AC-8 親を指定せずに登録すると、最上位のカテゴリになる', async () => {
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

  test('AC-8 子を追加すると、その行のカテゴリを親として登録する', async () => {
    const requests = serveCategories([CLOTHING])
    const { user } = renderApp('/categories')

    await user.click(within(await findGroup('衣類')).getByRole('button', { name: '子を追加' }))
    const dialog = await screen.findByRole('dialog', { name: '「衣類」の子カテゴリを追加' })
    await user.type(within(dialog).getByLabelText('名称'), 'ボトムス')
    await user.click(within(dialog).getByRole('button', { name: '保存' }))

    expect(await screen.findByText('カテゴリを登録しました')).toBeInTheDocument()
    expect(requests[0]?.body).toEqual({ name: 'ボトムス', parentId: CLOTHING.id })
  })

  test('AC-9、270 子を追加する操作は、階層の深さでなく API のフラグで出し分ける', async () => {
    serveCategories([CLOTHING, categoryNode('301', '深い', 3, [], true), categoryNode('302', '浅い', 1, [], false)])
    renderApp('/categories')

    expect(within(await findGroup('トップス')).getByRole('button', { name: '子を追加' })).toBeInTheDocument()
    expect(within(await findGroup('T シャツ')).queryByRole('button', { name: '子を追加' })).not.toBeInTheDocument()
    expect(within(await findGroup('深い')).getByRole('button', { name: '子を追加' })).toBeInTheDocument()
    expect(within(await findGroup('浅い')).queryByRole('button', { name: '子を追加' })).not.toBeInTheDocument()
  })

  test('AC-10 同じ親の下に同じ名称があれば、ダイアログにエラーを出し、入力を残す', async () => {
    serveCategories([GOODS], {
      create: (_body, response) =>
        response(409).json(
          problem(409, 'CATEGORY_NAME_ALREADY_EXISTS', '同じ親の下に、同じ名称のカテゴリがすでにあります。'),
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

  test('AC-11 名称を変えると、ダイアログを開いたときのバージョンを添えて送る', async () => {
    const requests = serveCategories([GOODS])
    const { user } = renderApp('/categories')

    await user.click(within(await findGroup('雑貨')).getByRole('button', { name: '名称の変更' }))
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

  test('名称の変更が 409 になると、再読み込みでダイアログを閉じて最新の一覧を表示する', async () => {
    // 他の人が名称を変えた後の木。409 を返した後の取得から、これを返す
    let tree = [GOODS]
    server.use(
      http.get('/categories', ({ response }) => response(200).json({ items: tree })),
      http.put('/categories/{categoryId}', ({ response }) => {
        tree = [{ ...GOODS, name: '暮らしの雑貨', version: 4 }]
        return conflict(response)
      }),
    )
    const { user } = renderApp('/categories')

    await user.click(within(await findGroup('雑貨')).getByRole('button', { name: '名称の変更' }))
    const dialog = await screen.findByRole('dialog', { name: '名称の変更' })
    await user.clear(within(dialog).getByLabelText('名称'))
    await user.type(within(dialog).getByLabelText('名称'), '生活雑貨')
    await user.click(within(dialog).getByRole('button', { name: '保存' }))
    await user.click(await within(dialog).findByRole('button', { name: '再読み込み' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(await screen.findByRole('group', { name: '暮らしの雑貨' })).toBeInTheDocument()
    expect(screen.queryByRole('group', { name: '雑貨' })).not.toBeInTheDocument()
  })

  test('AC-11 並び順を変えると、兄弟の並びを送る。先頭は上へ、末尾は下へ動かせない', async () => {
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

  test.each([
    {
      name: '子を持つカテゴリ',
      errorCode: 'CATEGORY_HAS_CHILDREN',
      message: '子カテゴリを持つカテゴリは削除できません。',
    },
    {
      name: '商品が所属しているカテゴリ',
      errorCode: 'CATEGORY_HAS_PRODUCTS',
      message: '商品が所属しているカテゴリは削除できません。',
    },
  ] as const)('AC-12 削除が拒否されたら、拒否の理由を表示する（$name）', async ({ errorCode, message }) => {
    const requests = serveCategories([CLOTHING], {
      remove: (_id, response) => response(400).json(problem(400, errorCode, message)),
    })
    const { user } = renderApp('/categories')

    await user.click(within(await findGroup('衣類')).getByRole('button', { name: '削除' }))
    await user.click(await screen.findByRole('button', { name: '削除する' }))

    expect(await screen.findByText(message)).toBeInTheDocument()
    expect(requests).toEqual([{ method: 'DELETE', path: `/categories/${CLOTHING.id}` }])
  })

  test('AC-12 子も商品もないカテゴリは、確認の後に削除する', async () => {
    const requests = serveCategories([GOODS])
    const { user } = renderApp('/categories')

    await user.click(within(await findGroup('雑貨')).getByRole('button', { name: '削除' }))
    await user.click(await screen.findByRole('button', { name: '削除する' }))

    expect(await screen.findByText('カテゴリを削除しました')).toBeInTheDocument()
    expect(requests).toEqual([{ method: 'DELETE', path: `/categories/${GOODS.id}` }])
  })
})
