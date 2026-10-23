import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, test } from 'vitest'
import { findDetailValue, findRow, getCell, getDetailValue, getRow } from '@/test/queries'

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

/** ボタンを押すと、描画の後で項目と行が現れる */
function Reveal() {
  const [shown, setShown] = useState(false)
  return (
    <div>
      <button type="button" onClick={() => setShown(true)}>
        表示する
      </button>
      {shown && (
        <>
          <dl>
            <dt>遅れた項目</dt>
            <dd>遅れた値</dd>
          </dl>
          <table>
            <tbody>
              <tr>
                <td>遅れた行</td>
              </tr>
            </tbody>
          </table>
        </>
      )}
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
    expect(() => getDetailValue('配送')).toThrow(/配送/)
  })

  test('項目名の次が値（dd）でなければ失敗する', () => {
    render(
      <dl>
        <dt>配送</dt>
        <dt>支払</dt>
      </dl>,
    )
    expect(() => getDetailValue('配送')).toThrow('項目「配送」の次に値（dd）がない')
  })

  test('項目名が後から出ても、非同期で探せる', async () => {
    const user = userEvent.setup()
    render(<Reveal />)
    expect(screen.queryByText('遅れた項目')).not.toBeInTheDocument()

    const found = findDetailValue('遅れた項目')
    await user.click(screen.getByRole('button', { name: '表示する' }))

    expect(await found).toHaveTextContent('遅れた値')
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
    expect(getRow('A (税込)')).toBeInTheDocument()
  })

  test('名前が渡した文字で始まる行だけを返し、行の途中にある文字には当たらない', () => {
    render(
      <table>
        <tbody>
          <tr>
            <td>標準</td>
          </tr>
          <tr>
            <td>標準（旧）</td>
          </tr>
          <tr>
            <td>ABC-001</td>
          </tr>
          <tr>
            <td>ABC-0012</td>
          </tr>
        </tbody>
      </table>,
    )
    expect(getRow('標準（旧）')).toHaveTextContent(/^標準（旧）$/)
    expect(getRow('ABC-0012')).toHaveTextContent(/^ABC-0012$/)
    expect(() => getRow('（旧）')).toThrow()
    expect(() => getRow('-0012')).toThrow()
  })

  test('含む行がなければ失敗する', () => {
    render(<List />)
    expect(() => getRow('非課税')).toThrow(/非課税/)
  })

  test('行が後から出ても、非同期で探せる', async () => {
    const user = userEvent.setup()
    render(<Reveal />)
    expect(screen.queryByRole('row')).not.toBeInTheDocument()

    const found = findRow('遅れた')
    await user.click(screen.getByRole('button', { name: '表示する' }))

    expect(await found).toHaveTextContent('遅れた行')
  })
})

describe('一覧のセル', () => {
  test('行の名前と列の見出しの名前で、セルを返す', () => {
    render(<List />)
    expect(getCell('軽減', '件数')).toHaveTextContent(/^5$/)
    expect(getCell('標準', '名称')).toHaveTextContent(/^標準税率$/)
  })

  test('範囲を渡すと、その中の行だけを探す', () => {
    render(
      <>
        <List />
        <section aria-label="別の表">
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
                <td>99</td>
              </tr>
            </tbody>
          </table>
        </section>
      </>,
    )
    const section = screen.getByRole('region', { name: '別の表' })
    expect(getCell('標準', '件数', within(section))).toHaveTextContent(/^99$/)
  })

  test('列の見出しがなければ失敗する', () => {
    render(<List />)
    expect(() => getCell('標準', '金額')).toThrow('列「金額」がない')
  })

  test('行がなければ失敗する', () => {
    render(<List />)
    expect(() => getCell('非課税', '件数')).toThrow(/非課税/)
  })
})
