import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test, vi } from 'vitest'
import { Pagination } from '@/components/Pagination'
import { I18nProvider } from '@/i18n/I18nProvider'

function renderPagination(page: number, totalPages: number, totalItems: number) {
  const onChange = vi.fn()
  render(
    <I18nProvider>
      <Pagination onChange={onChange} page={page} totalItems={totalItems} totalPages={totalPages} />
    </I18nProvider>,
  )
  return { onChange, user: userEvent.setup() }
}

describe('ページング', () => {
  test('件数と今のページを出し、前へ・次へで隣のページを選ぶ', async () => {
    const { onChange, user } = renderPagination(2, 3, 45)

    expect(screen.getByText('全 45 件 2 / 3 ページ')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '前へ' }))
    await user.click(screen.getByRole('button', { name: '次へ' }))

    expect(onChange.mock.calls).toEqual([[1], [3]])
  })

  test('最初のページでは前へ、最後のページでは次へを押せない', () => {
    renderPagination(1, 1, 5)

    expect(screen.getByRole('button', { name: '前へ' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '次へ' })).toBeDisabled()
  })

  test('1 件もなければ、0 / 0 ページと出す', () => {
    renderPagination(1, 0, 0)

    expect(screen.getByText('全 0 件 0 / 0 ページ')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '次へ' })).toBeDisabled()
  })
})
