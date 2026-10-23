import { screen } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { renderApp } from '@/test/renderApp'

describe('存在しない URL', () => {
  test('画面の枠の中に「ページが見つかりません」と出す', async () => {
    renderApp('/no-such-page')

    expect(await screen.findByText('ページが見つかりません。')).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: '機能のメニュー' })).toBeInTheDocument()
  })
})
