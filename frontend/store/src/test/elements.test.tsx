import { describe, expect, test } from 'vitest'
import { expectInOrder } from '@/test/elements'
import { renderStatic } from '@/test/render'

/** 上から「一」「二」「三」の順に出る段落 */
function Paragraphs() {
  return (
    <div>
      <p>一</p>
      <p>二</p>
      <p>三</p>
    </div>
  )
}

describe('要素の順の確認', () => {
  test('文書の順に渡した要素は、通る', () => {
    const view = renderStatic(<Paragraphs />)

    expect(() => expectInOrder(view.getByText('一'), view.getByText('二'), view.getByText('三'))).not.toThrow()
  })

  test('間を飛ばした要素も、文書の順なら通る', () => {
    const view = renderStatic(<Paragraphs />)

    expect(() => expectInOrder(view.getByText('一'), view.getByText('三'))).not.toThrow()
  })

  test('順が逆の要素は、失敗する', () => {
    const view = renderStatic(<Paragraphs />)

    expect(() => expectInOrder(view.getByText('二'), view.getByText('一'))).toThrow()
  })

  test('途中の組だけが逆でも、失敗する', () => {
    const view = renderStatic(<Paragraphs />)

    expect(() => expectInOrder(view.getByText('一'), view.getByText('三'), view.getByText('二'))).toThrow(
      '2 番目の要素の後に 3 番目の要素',
    )
  })

  test('同じ要素を続けて渡すと、失敗する', () => {
    const view = renderStatic(<Paragraphs />)

    expect(() => expectInOrder(view.getByText('一'), view.getByText('一'))).toThrow()
  })

  test('要素が 2 つに満たないときは、失敗する', () => {
    const view = renderStatic(<Paragraphs />)

    expect(() => expectInOrder(view.getByText('一'))).toThrow('要素が 2 つ以上いる')
  })

  test('入れ子の親子は、含む関係で順ではないので、失敗する', () => {
    const view = renderStatic(
      <section aria-label="親">
        <p>子</p>
      </section>,
    )

    expect(() => expectInOrder(view.getByRole('region', { name: '親' }), view.getByText('子'))).toThrow()
  })
})
