import { describe, expect, test } from 'vitest'
import { findTag, startTags } from '@/test/markup'

describe('HTML の開始タグの取り出し', () => {
  test('属性の並びに関わらず、タグの属性を名前で取り出し、値のない属性は空の文字になる', () => {
    const html = '<form><option selected="" value="3">3</option><option value="4">4</option></form>'

    expect(startTags(html, 'option')).toEqual([{ selected: '', value: '3' }, { value: '4' }])
    expect(findTag(html, 'option', { value: '3' })).toEqual({ selected: '', value: '3' })
  })

  test('引用符の中の > はタグの終わりとみなさず、探すタグがなければ失敗する', () => {
    const html = '<img alt="a > b" src="x.png"/>'

    expect(findTag(html, 'img', { alt: 'a > b' }).src).toBe('x.png')
    expect(() => findTag(html, 'img', { alt: 'c' })).toThrow('タグがない')
  })
})
