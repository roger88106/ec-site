import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'
import { describe, expect, test } from 'vitest'
import { renderStatic } from '@/test/render'

function SampleButtons() {
  const t = useTranslations('components.siteHeader')
  return (
    <div>
      <button type="button" disabled>
        {t('cart')}
      </button>
      <a href="/orders">{t('orders')}</a>
    </div>
  )
}

function EffectLabel() {
  const [label, setLabel] = useState('初期')
  useEffect(() => {
    setLabel('効果の後')
  }, [])
  return <p>{label}</p>
}

describe('HTML に描画した部品の問い合わせ', () => {
  test('役割と名前で要素を探せる', () => {
    const view = renderStatic(<SampleButtons />)

    expect(view.getByRole('button', { name: 'カート' })).toBeInTheDocument()
    expect(view.getByRole('link', { name: 'ご注文履歴' })).toBeInTheDocument()
  })

  test('jest-dom の照合の関数で、属性と状態を確認できる', () => {
    const view = renderStatic(<SampleButtons />)

    expect(view.getByRole('button', { name: 'カート' })).toBeDisabled()
    expect(view.getByRole('link', { name: 'ご注文履歴' })).toHaveAttribute('href', '/orders')
    expect(view.queryByRole('button', { name: '存在しない' })).not.toBeInTheDocument()
  })

  test('効果は実行されず、初期の表示のままになる', () => {
    const view = renderStatic(<EffectLabel />)

    expect(view.getByText('初期')).toBeInTheDocument()
    expect(view.queryByText('効果の後')).not.toBeInTheDocument()
  })

  test('next-intl の日本語の文言が出る', () => {
    const view = renderStatic(<SampleButtons />)

    expect(view.getByText('ご注文履歴')).toBeInTheDocument()
  })
})
