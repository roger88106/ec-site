import { render, screen } from '@testing-library/react'
import { useState } from 'react'
import { describe, expect, test } from 'vitest'
import { typeDateTime } from '@/test/inputs'

/** 日時の入力。入力を受けた値を、下に文字で出す */
function DateTimeField() {
  const [value, setValue] = useState('')
  return (
    <div>
      <input type="datetime-local" aria-label="開始" value={value} onChange={(event) => setValue(event.target.value)} />
      <output aria-label="受けた値">{value}</output>
    </div>
  )
}

describe('日時の入力', () => {
  test('値が入り、変更を画面に伝える', () => {
    render(<DateTimeField />)

    typeDateTime(screen.getByLabelText('開始'), '2027-04-01T09:30')

    expect(screen.getByLabelText('開始')).toHaveValue('2027-04-01T09:30')
    expect(screen.getByLabelText('受けた値')).toHaveTextContent('2027-04-01T09:30')
  })

  test('入っている値を、別の値に置き換える', () => {
    render(<DateTimeField />)
    typeDateTime(screen.getByLabelText('開始'), '2027-04-01T09:30')

    typeDateTime(screen.getByLabelText('開始'), '2028-01-02T03:04')

    expect(screen.getByLabelText('受けた値')).toHaveTextContent('2028-01-02T03:04')
  })
})
