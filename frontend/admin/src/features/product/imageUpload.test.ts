import { describe, expect, it } from 'vitest'
import { imageContentType, imageFileError, MAX_IMAGE_BYTES } from '@/features/product/imageUpload'

/** 形式の誤りのメッセージ */
const FORMAT_ERROR = '画像の形式は JPEG、PNG、WebP、BMP、HEIC / HEIF、AVIF のいずれかにしてください。'

describe('imageContentType', () => {
  it('AC-39、67 受け付ける形式は、ブラウザの形式をそのまま使う', () => {
    for (const type of [
      'image/jpeg',
      'image/png',
      'image/webp',
      'image/bmp',
      'image/heic',
      'image/heif',
      'image/avif',
    ]) {
      expect(imageContentType({ type, name: 'a' })).toBe(type)
    }
  })

  it('AC-67 ブラウザが形式を空や汎用で渡したら、拡張子で補う', () => {
    expect(imageContentType({ type: '', name: 'IMG_0001.HEIC' })).toBe('image/heic')
    expect(imageContentType({ type: '', name: 'photo.heif' })).toBe('image/heif')
    expect(imageContentType({ type: 'application/octet-stream', name: 'photo.avif' })).toBe('image/avif')
    expect(imageContentType({ type: '', name: 'photo.jpeg' })).toBe('image/jpeg')
  })

  it('対象外の形式は、拡張子が画像でも補わない', () => {
    expect(imageContentType({ type: 'image/gif', name: 'a.gif' })).toBeNull()
    expect(imageContentType({ type: 'text/plain', name: 'a.png' })).toBeNull()
    expect(imageContentType({ type: '', name: 'a.gif' })).toBeNull()
    expect(imageContentType({ type: '', name: 'noextension' })).toBeNull()
  })
})

describe('imageFileError', () => {
  it('AC-39、67 受け付ける形式で 10MB までなら誤りはない', () => {
    for (const type of ['image/jpeg', 'image/png', 'image/webp', 'image/bmp', 'image/heic', 'image/avif']) {
      expect(imageFileError({ type, name: 'a', size: MAX_IMAGE_BYTES })).toBeNull()
    }
    expect(imageFileError({ type: '', name: 'IMG_0001.heic', size: 100 })).toBeNull()
  })

  it('AC-39 ほかの形式は誤り', () => {
    expect(imageFileError({ type: 'image/gif', name: 'a.gif', size: 100 })).toBe(FORMAT_ERROR)
    expect(imageFileError({ type: '', name: 'a', size: 100 })).toBe(FORMAT_ERROR)
  })

  it('AC-39 10MB を超えるか、0 バイトなら誤り', () => {
    expect(imageFileError({ type: 'image/png', name: 'a.png', size: MAX_IMAGE_BYTES + 1 })).toBe(
      '画像は 1 ファイル 10MB までです。',
    )
    expect(imageFileError({ type: 'image/png', name: 'a.png', size: 0 })).toBe('画像は 1 ファイル 10MB までです。')
  })
})
