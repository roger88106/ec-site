import { describe, expect, it } from 'vitest'
import { imageFileError, MAX_IMAGE_BYTES } from '@/features/product/imageUpload'

describe('imageFileError', () => {
  it('AC-39 JPEG、PNG、WebP で 10MB までなら誤りはない', () => {
    for (const type of ['image/jpeg', 'image/png', 'image/webp']) {
      expect(imageFileError({ type, size: MAX_IMAGE_BYTES })).toBeNull()
    }
  })

  it('AC-39 ほかの形式は誤り', () => {
    expect(imageFileError({ type: 'image/gif', size: 100 })).toBe(
      '画像の形式は JPEG、PNG、WebP のいずれかにしてください。',
    )
    expect(imageFileError({ type: '', size: 100 })).not.toBeNull()
  })

  it('AC-39 10MB を超えるか、0 バイトなら誤り', () => {
    expect(imageFileError({ type: 'image/png', size: MAX_IMAGE_BYTES + 1 })).toBe('画像は 1 ファイル 10MB までです。')
    expect(imageFileError({ type: 'image/png', size: 0 })).toBe('画像は 1 ファイル 10MB までです。')
  })
})
