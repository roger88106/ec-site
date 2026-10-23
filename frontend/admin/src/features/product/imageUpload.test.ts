import { describe, expect, it } from 'vitest'
import { createImageUploadRequestContentTypeValues } from '@/api/generated/admin'
import { schemas } from '@/api/generated/constraints'
import { imageContentType, imageFileError, imageFormatsLabel } from '@/features/product/imageUpload'

/** 契約の画像の大きさの上限（バイト） */
const MAX_BYTES = schemas.CreateImageUploadRequest.contentLength.maximum

/** 形式の誤りのメッセージ */
const FORMAT_ERROR = '画像の形式は JPEG、PNG、WebP、BMP、HEIC / HEIF、AVIF のいずれかにしてください。'

/** 大きさの誤りのメッセージ */
const SIZE_ERROR = `画像は 1 ファイル ${MAX_BYTES / (1024 * 1024)}MB までです。`

describe('画像の形式の判定', () => {
  it('AC-39、67、272 受け付ける形式（契約の形式の一覧）は、ブラウザの形式をそのまま使う', () => {
    for (const type of createImageUploadRequestContentTypeValues) {
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

describe('受け付ける画像の形式の表示', () => {
  it('AC-272 契約の形式の一覧を、表示の名前の一覧にする（HEIC と HEIF は 1 つにまとめる）', () => {
    expect(imageFormatsLabel()).toBe('JPEG、PNG、WebP、BMP、HEIC / HEIF、AVIF')
  })
})

describe('画像のファイルの検査', () => {
  it('AC-39、67、272 受け付ける形式で、契約の大きさの上限までなら誤りはない', () => {
    for (const type of createImageUploadRequestContentTypeValues) {
      expect(imageFileError({ type, name: 'a', size: MAX_BYTES })).toBeNull()
    }
    expect(imageFileError({ type: '', name: 'IMG_0001.heic', size: 100 })).toBeNull()
  })

  it('AC-39、272 ほかの形式は誤りで、文に契約の形式の一覧を差し込む', () => {
    expect(imageFileError({ type: 'image/gif', name: 'a.gif', size: 100 })).toBe(FORMAT_ERROR)
    expect(imageFileError({ type: '', name: 'a', size: 100 })).toBe(FORMAT_ERROR)
  })

  it('AC-39、272 契約の大きさの上限を超えるか、0 バイトなら誤りで、文に上限を差し込む', () => {
    expect(imageFileError({ type: 'image/png', name: 'a.png', size: MAX_BYTES + 1 })).toBe(SIZE_ERROR)
    expect(imageFileError({ type: 'image/png', name: 'a.png', size: 0 })).toBe(SIZE_ERROR)
  })
})
