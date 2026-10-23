import { render, screen, within } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { findDetailValue, findRow, getDetailValue, getRow } from '@/test/queries'

function Detail() {
  return (
    <div>
      <dl>
        <dt>支払</dt>
        <dd>クレジットカード</dd>
        <dt>与信期限</dt>
        <dd>2026/10/20</dd>
      </dl>
      <section aria-label="お届け先">
        <dl>
          <dt>支払</dt>
          <dd>お届け先の支払</dd>
        </dl>
      </section>
    </div>
  )
}

function List() {
  return (
    <table>
      <thead>
        <tr>
          <th>名称</th>
          <th>件数</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td>標準税率</td>
          <td>3</td>
        </tr>
        <tr>
          <td>軽減税率</td>
          <td>5</td>
        </tr>
      </tbody>
    </table>
  )
}

describe('詳細の項目の値', () => {
  test('項目名の次の値の要素を返す', () => {
    render(<Detail />)
    expect(getDetailValue('与信期限')).toHaveTextContent('2026/10/20')
  })

  test('範囲を渡すと、その中の項目だけを探す', () => {
    render(<Detail />)
    const section = screen.getByRole('region', { name: 'お届け先' })
    expect(getDetailValue('支払', within(section))).toHaveTextContent('お届け先の支払')
  })

  test('項目名がなければ失敗する', () => {
    render(<Detail />)
    expect(() => getDetailValue('配送')).toThrow()
  })

  test('項目名が後から出ても、非同期で探せる', async () => {
    render(<Detail />)
    expect(await findDetailValue('与信期限')).toHaveTextContent('2026/10/20')
  })
})

describe('一覧の行', () => {
  test('名前に含む文字で行を返す', () => {
    render(<List />)
    expect(within(getRow('軽減')).getByRole('cell', { name: '5' })).toBeInTheDocument()
  })

  test('範囲を渡すと、その中の行だけを探す', () => {
    render(
      <>
        <List />
        <section aria-label="別の表">
          <table>
            <tbody>
              <tr>
                <td>標準税率</td>
                <td>99</td>
              </tr>
            </tbody>
          </table>
        </section>
      </>,
    )
    const section = screen.getByRole('region', { name: '別の表' })
    expect(within(getRow('標準', within(section))).getByRole('cell', { name: '99' })).toBeInTheDocument()
  })

  test('正規表現の記号を含む文字も、そのまま探す', () => {
    render(
      <table>
        <tbody>
          <tr>
            <td>A (税込)</td>
          </tr>
        </tbody>
      </table>,
    )
    expect(getRow('(税込)')).toBeInTheDocument()
  })

  test('含む行がなければ失敗する', () => {
    render(<List />)
    expect(() => getRow('非課税')).toThrow()
  })

  test('行が後から出ても、非同期で探せる', async () => {
    render(<List />)
    expect(await findRow('標準')).toBeInTheDocument()
  })
})
