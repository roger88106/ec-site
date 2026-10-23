import { describe, expect, test } from 'vitest'
import { recordStderr } from '@/test/stderr'

describe('標準エラー出力の記録', () => {
  test('書いた行を JSON として読んだ配列で返す', () => {
    const stderr = recordStderr()

    process.stderr.write('{"level":"error","message":"a"}\n')
    process.stderr.write('{"level":"warn","message":"b"}\n')

    expect(stderr.entries()).toEqual([
      { level: 'error', message: 'a' },
      { level: 'warn', message: 'b' },
    ])
  })

  test('書いた内容を、つなげた文字で返す', () => {
    const stderr = recordStderr()

    process.stderr.write('{"a":1}\n')
    process.stderr.write('{"b":2}\n')

    expect(stderr.text()).toBe('{"a":1}\n{"b":2}\n')
  })

  test('何も書かなければ、空の配列と空の文字を返す', () => {
    const stderr = recordStderr()

    expect(stderr.entries()).toEqual([])
    expect(stderr.text()).toBe('')
  })
})
