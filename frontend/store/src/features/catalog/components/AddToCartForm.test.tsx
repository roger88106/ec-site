// @vitest-environment jsdom
import { screen } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import type { Variant } from '@/api/types'
import { AddToCartForm } from '@/features/catalog/components/AddToCartForm'
import { VARIANT_ID } from '@/test/fixtures'
import { renderInBrowser } from '@/test/render'
import { http, problem, server } from '@/test/server'

/** 買えるバリアント */
const VARIANT: Variant = { id: VARIANT_ID, axisValues: [], inStock: true, salesSuspended: false, imageUrl: null }

describe('カートに入れる操作の失敗', () => {
  test('DC-135-10 失敗の文は、常にある読み上げの領域の中に出す（領域を入れ子にしない）', async () => {
    server.use(
      http.post('/carts', ({ response }) => response(201).json({ id: '00000000-0000-0000-0000-0000000000c2' })),
      http.post('/carts/{cartId}/items', ({ response }) =>
        response(400).json(problem(400, 'INVALID_REQUEST', '入力内容に誤りがあります。')),
      ),
    )
    const { user } = renderInBrowser(<AddToCartForm variant={VARIANT} />)
    // 失敗の前から、領域は DOM にある
    const regionBefore = document.querySelector('[aria-live="polite"]')
    expect(regionBefore).not.toBeNull()

    await user.click(screen.getByRole('button', { name: 'カートに入れる' }))

    const message = await screen.findByText('入力内容に誤りがあります。')
    const regions = document.querySelectorAll('[aria-live]')
    expect(regions).toHaveLength(1)
    expect(regions[0]).toBe(regionBefore)
    expect(regions[0]?.contains(message)).toBe(true)
    expect(message.hasAttribute('aria-live')).toBe(false)
  })
})
